import { forwardRef, useCallback, useEffect, useImperativeHandle, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { Annotation, OpenedDocument, PageRect, PageSize } from "../engine";
import type { Editor } from "../editor/useEditor";
import type { RenderQueue } from "../renderQueue";
import { EditInput } from "./EditInput";
import { PageCanvas } from "./PageCanvas";
import { PdfLayer, type PdfToolSettings } from "./PdfLayer";

/** 1pt 를 100% 배율에서 몇 CSS px 로 보이나(실제 크기). */
export const PX_PER_PT = 96 / 72;
const GAP = 16;
const PAD = 24;
/** 화면 밖이라도 미리 그려 둘 앞뒤 쪽 수 */
const OVERSCAN = 2;
/** 한 쪽 비트맵의 최대 픽셀 수(메모리 상한, 엔진 상한보다 작게) */
const MAX_PIXELS = 16_000_000;

export interface ViewerHandle {
  goToPage(page: number): void;
  /** 뷰어 영역 크기(CSS px, 여백 제외) */
  viewport(): { width: number; height: number };
  /** 쪽 위 사각형(pt)이 보이도록 스크롤한다. */
  reveal(rect: PageRect): void;
  /** 편집 입력란에 포커스. */
  focusInput(): void;
}

interface Props {
  doc: OpenedDocument;
  /** 100% = 1 */
  zoom: number;
  queue: RenderQueue;
  onPageChange(page: number): void;
  /** 편집 모드일 때만 */
  editor: Editor | null;
  /** 편집될 때마다 올라가는 번호 */
  revision: number;
  /** PDF 편집 모드일 때만 */
  pdf: { settings: PdfToolSettings; onAnnotate(annotation: Annotation): Promise<void> } | null;
}

/** 쪽 크기에 맞춰 엔진 배율을 정한다: 화면 배율 × 기기 픽셀 비, 메모리 상한 안에서. */
export function renderScale(size: PageSize, cssPerPt: number): number {
  const wanted = cssPerPt * window.devicePixelRatio;
  const limit = Math.sqrt(MAX_PIXELS / (size.width * size.height));
  const scale = Math.min(wanted, limit, 16);
  // 작은 변화로 다시 그리지 않도록 반올림한다.
  return Math.max(0.05, Math.round(scale * 50) / 50);
}

export const Viewer = forwardRef<ViewerHandle, Props>(function Viewer(
  { doc, zoom, queue, onPageChange, editor, revision, pdf },
  ref,
) {
  const scroller = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const cssPerPt = PX_PER_PT * zoom;

  const layout = useMemo(() => {
    const tops: number[] = [];
    let y = PAD;
    let maxWidth = 0;
    for (const page of doc.pages) {
      tops.push(y);
      y += page.height * cssPerPt + GAP;
      maxWidth = Math.max(maxWidth, page.width * cssPerPt);
    }
    return { tops, height: y - GAP + PAD, width: maxWidth + PAD * 2 };
  }, [doc, cssPerPt]);

  const [range, setRange] = useState({ first: 0, last: Math.min(doc.pageCount - 1, 2), current: 0 });
  // 배율이 바뀔 때 같은 자리를 보도록 기억하는 위치(쪽 번호 + 쪽 안 비율)
  const anchor = useRef({ page: 0, fraction: 0 });

  const pageAt = useCallback(
    (y: number) => {
      const { tops } = layout;
      let low = 0;
      let high = tops.length - 1;
      while (low < high) {
        const mid = (low + high + 1) >> 1;
        if (tops[mid]! <= y) low = mid;
        else high = mid - 1;
      }
      return low;
    },
    [layout],
  );

  // 보이는 영역 너비. 쪽은 전체 내용 너비(가장 넓은 쪽 기준)가 아니라 보이는 영역 가운데에 놓는다.
  const [visibleWidth, setVisibleWidth] = useState(0);

  const update = useCallback(() => {
    const element = scroller.current;
    if (!element) return;
    setVisibleWidth(element.clientWidth);
    const top = element.scrollTop;
    const bottom = top + element.clientHeight;
    const first = pageAt(top);
    const last = pageAt(bottom);
    const current = pageAt(top + element.clientHeight * 0.3);
    const pageHeight = doc.pages[first]!.height * cssPerPt;
    // 첫 쪽 위 여백에 있으면 음수가 된다 — 그대로 둬야 배율을 바꿔도 여백이 보인다.
    anchor.current = { page: first, fraction: (top - layout.tops[first]!) / pageHeight };
    setRange((old) =>
      old.first === first && old.last === last && old.current === current ? old : { first, last, current },
    );
  }, [pageAt, doc, cssPerPt, layout]);

  // 배율이 바뀌면 보던 자리를 유지한다.
  useLayoutEffect(() => {
    const element = scroller.current;
    if (!element) return;
    const { page, fraction } = anchor.current;
    if (page < doc.pageCount) {
      element.scrollTop = layout.tops[page]! + fraction * doc.pages[page]!.height * cssPerPt;
    }
    update();
    // 배율(layout) 이 바뀔 때만 실행한다. 스크롤 위치(anchor)는 ref 라 의존성이 아니다.
  }, [layout]);

  useEffect(() => onPageChange(range.current), [range.current, onPageChange]);

  useEffect(() => {
    const element = scroller.current;
    if (!element) return;
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(update);
    };
    const observer = new ResizeObserver(onScroll);
    observer.observe(element);
    element.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      element.removeEventListener("scroll", onScroll);
    };
  }, [update]);

  useImperativeHandle(
    ref,
    () => ({
      goToPage(page) {
        const element = scroller.current;
        if (!element || page < 0 || page >= doc.pageCount) return;
        element.scrollTop = layout.tops[page]! - GAP / 2;
      },
      viewport() {
        const element = scroller.current;
        return {
          width: (element?.clientWidth ?? 800) - PAD * 2,
          height: (element?.clientHeight ?? 600) - PAD * 2,
        };
      },
      reveal(rect) {
        const element = scroller.current;
        const top = layout.tops[rect.page];
        if (!element || top === undefined) return;
        const y = top + rect.y * cssPerPt;
        const bottom = y + rect.height * cssPerPt;
        const margin = 40;
        if (y < element.scrollTop + margin) element.scrollTop = y - margin;
        else if (bottom > element.scrollTop + element.clientHeight - margin)
          element.scrollTop = bottom - element.clientHeight + margin;
      },
      focusInput() {
        input.current?.focus({ preventScroll: true });
      },
    }),
    [doc, layout, cssPerPt],
  );

  const pageLeft = (width: number) => Math.max(PAD, (visibleWidth - width) / 2);

  // ---- 편집: 마우스 ----
  const dragging = useRef(false);
  const busy = useRef(false);
  const pointAt = (event: React.MouseEvent, page: number) => {
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    return { page, x: (event.clientX - box.left) / cssPerPt, y: (event.clientY - box.top) / cssPerPt };
  };
  const onPageMouseDown = (event: React.MouseEvent, page: number) => {
    if (!editor || event.button !== 0) return;
    event.preventDefault();
    const { x, y } = pointAt(event, page);
    dragging.current = true;
    void editor.clickAt(page, x, y, event.shiftKey);
    input.current?.focus({ preventScroll: true });
  };
  const onPageMouseMove = (event: React.MouseEvent, page: number) => {
    if (!editor || !dragging.current || busy.current || event.buttons !== 1) return;
    const { x, y } = pointAt(event, page);
    busy.current = true;
    void editor.clickAt(page, x, y, true).finally(() => (busy.current = false));
  };
  useEffect(() => {
    const stop = () => (dragging.current = false);
    window.addEventListener("mouseup", stop);
    return () => window.removeEventListener("mouseup", stop);
  }, []);

  // 편집 모드에 들어오면 바로 입력할 수 있게 한다.
  useEffect(() => {
    if (editor) input.current?.focus({ preventScroll: true });
  }, [!!editor]);

  const caret = editor?.state.caretRect;
  const selection = editor?.state.selection ?? [];

  const first = Math.max(0, range.first - OVERSCAN);
  const last = Math.min(doc.pageCount - 1, range.last + OVERSCAN);
  const pages = [];
  for (let page = first; page <= last; page++) {
    const size = doc.pages[page]!;
    const width = size.width * cssPerPt;
    const height = size.height * cssPerPt;
    const marks = selection.filter((rect) => rect.page === page);
    pages.push(
      <div
        key={page}
        className={`page ${editor ? "editing" : ""}`}
        style={{ top: layout.tops[page], width, height, left: pageLeft(width) }}
        aria-label={`${page + 1}쪽`}
        onMouseDown={(event) => onPageMouseDown(event, page)}
        onMouseMove={(event) => onPageMouseMove(event, page)}
      >
        <PageCanvas
          docId={doc.id}
          page={page}
          width={width}
          height={height}
          scale={renderScale(size, cssPerPt)}
          queue={queue}
          priority={Math.abs(page - range.current)}
          kind="page"
          revision={revision}
        />
        {pdf && (
          <PdfLayer docId={doc.id} page={page} scale={cssPerPt} settings={pdf.settings} onAnnotate={pdf.onAnnotate} />
        )}
        {marks.map((rect, index) => (
          <div
            key={index}
            className="selection-mark"
            style={{
              left: rect.x * cssPerPt,
              top: rect.y * cssPerPt,
              width: rect.width * cssPerPt,
              height: rect.height * cssPerPt,
            }}
          />
        ))}
        {caret && caret.page === page && selection.length === 0 && (
          <div
            className="caret"
            // key 를 바꿔 움직일 때마다 깜빡임을 처음부터 시작한다(움직이는 동안은 보이게).
            key={`${caret.x}:${caret.y}`}
            style={{ left: caret.x * cssPerPt, top: caret.y * cssPerPt, height: Math.max(8, caret.height * cssPerPt) }}
          />
        )}
      </div>,
    );
  }

  return (
    <div className="viewer" ref={scroller} tabIndex={-1}>
      <div className="viewer-content" style={{ height: layout.height, minWidth: layout.width }}>
        {pages}
        {editor && (
          <EditInput
            ref={input}
            editor={editor}
            // 입력란을 커서 자리에 둬서 한글 입력기 후보 창이 커서 옆에 뜨게 한다.
            position={
              caret && doc.pages[caret.page]
                ? {
                    left: pageLeft(doc.pages[caret.page]!.width * cssPerPt) + caret.x * cssPerPt,
                    top: (layout.tops[caret.page] ?? 0) + caret.y * cssPerPt,
                    height: caret.height * cssPerPt,
                  }
                : null
            }
          />
        )}
      </div>
    </div>
  );
});
