import { forwardRef, useCallback, useEffect, useImperativeHandle, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { OpenedDocument, PageSize } from "../engine";
import type { RenderQueue } from "../renderQueue";
import { PageCanvas } from "./PageCanvas";

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
}

interface Props {
  doc: OpenedDocument;
  /** 100% = 1 */
  zoom: number;
  queue: RenderQueue;
  onPageChange(page: number): void;
}

/** 쪽 크기에 맞춰 엔진 배율을 정한다: 화면 배율 × 기기 픽셀 비, 메모리 상한 안에서. */
export function renderScale(size: PageSize, cssPerPt: number): number {
  const wanted = cssPerPt * window.devicePixelRatio;
  const limit = Math.sqrt(MAX_PIXELS / (size.width * size.height));
  const scale = Math.min(wanted, limit, 16);
  // 작은 변화로 다시 그리지 않도록 반올림한다.
  return Math.max(0.05, Math.round(scale * 50) / 50);
}

export const Viewer = forwardRef<ViewerHandle, Props>(function Viewer({ doc, zoom, queue, onPageChange }, ref) {
  const scroller = useRef<HTMLDivElement>(null);
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
    }),
    [doc, layout],
  );

  const first = Math.max(0, range.first - OVERSCAN);
  const last = Math.min(doc.pageCount - 1, range.last + OVERSCAN);
  const pages = [];
  for (let page = first; page <= last; page++) {
    const size = doc.pages[page]!;
    const width = size.width * cssPerPt;
    const height = size.height * cssPerPt;
    pages.push(
      <div
        key={page}
        className="page"
        style={{ top: layout.tops[page], width, height, left: Math.max(PAD, (visibleWidth - width) / 2) }}
        aria-label={`${page + 1}쪽`}
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
        />
      </div>,
    );
  }

  return (
    <div className="viewer" ref={scroller} tabIndex={-1}>
      <div className="viewer-content" style={{ height: layout.height, minWidth: layout.width }}>
        {pages}
      </div>
    </div>
  );
});
