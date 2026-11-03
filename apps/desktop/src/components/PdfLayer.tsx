// PDF 편집 모드에서 쪽 위에 겹치는 층: 끌어서 형광펜·밑줄·취소선, 펜으로 그리기, 텍스트 상자·메모 입력.
import { useEffect, useRef, useState } from "react";
import { edit, type Annotation, type Area, type PageRect } from "../engine";

export type PdfTool = "select" | "highlight" | "underline" | "strikeOut" | "pen" | "text" | "note";

export interface PdfToolSettings {
  tool: PdfTool;
  color: string;
  /** 펜 굵기(pt) */
  penWidth: number;
  /** 텍스트 상자 글자 크기(pt) */
  fontSize: number;
}

interface Props {
  docId: number;
  page: number;
  /** 1pt 가 몇 CSS px 인가 */
  scale: number;
  settings: PdfToolSettings;
  onAnnotate(annotation: Annotation): Promise<void>;
}

type Point = [number, number];
interface Draft {
  kind: "text" | "note";
  x: number;
  y: number;
}

const normalize = (a: Point, b: Point): Area => ({
  x: Math.min(a[0], b[0]),
  y: Math.min(a[1], b[1]),
  width: Math.abs(a[0] - b[0]),
  height: Math.abs(a[1] - b[1]),
});

export function PdfLayer({ docId, page, scale, settings, onAnnotate }: Props) {
  const { tool, color } = settings;
  const [drag, setDrag] = useState<[Point, Point] | null>(null);
  const [preview, setPreview] = useState<PageRect[]>([]);
  const [stroke, setStroke] = useState<Point[] | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const previewBusy = useRef(false);
  /** 끌기마다 올라가는 번호. 끌기가 끝난 뒤 도착한 미리 보기 응답은 버린다. */
  const dragId = useRef(0);
  const input = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (draft) input.current?.focus();
  }, [draft]);

  // 도구를 바꾸면 하던 것을 버린다.
  useEffect(() => {
    setDrag(null);
    setPreview([]);
    setStroke(null);
    setDraft(null);
  }, [tool]);

  const at = (event: React.PointerEvent): Point => {
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    return [(event.clientX - box.left) / scale, (event.clientY - box.top) / scale];
  };

  const markup = tool === "highlight" || tool === "underline" || tool === "strikeOut";

  const onPointerDown = (event: React.PointerEvent) => {
    if (event.button !== 0 || tool === "select" || draft) return;
    event.preventDefault();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    const point = at(event);
    if (markup) {
      dragId.current += 1;
      setDrag([point, point]);
    }
    else if (tool === "pen") setStroke([point]);
    else setDraft({ kind: tool === "note" ? "note" : "text", x: point[0], y: point[1] });
  };

  const onPointerMove = (event: React.PointerEvent) => {
    const point = at(event);
    if (drag) {
      const next: [Point, Point] = [drag[0], point];
      setDrag(next);
      if (!previewBusy.current) {
        previewBusy.current = true;
        const id = dragId.current;
        void edit<PageRect[]>(docId, { kind: "textRects", page, area: normalize(...next) })
          .then(
            (rects) => id === dragId.current && setPreview(rects),
            () => setPreview([]),
          )
          .finally(() => (previewBusy.current = false));
      }
    } else if (stroke) {
      const last = stroke[stroke.length - 1]!;
      if (Math.hypot(point[0] - last[0], point[1] - last[1]) > 0.75) setStroke([...stroke, point]);
    }
  };

  const onPointerUp = () => {
    if (drag) {
      const area = normalize(...drag);
      dragId.current += 1;
      setDrag(null);
      setPreview([]);
      if (area.width > 2 || area.height > 2) {
        void onAnnotate({ type: "markup", page, kind: tool as "highlight", area, color });
      }
    } else if (stroke) {
      const points = stroke;
      setStroke(null);
      if (points.length >= 2) void onAnnotate({ type: "ink", page, points, color, width: settings.penWidth });
    }
  };

  const commitDraft = () => {
    const text = input.current?.value.trim() ?? "";
    const current = draft;
    setDraft(null);
    if (!current || !text) return;
    void onAnnotate(
      current.kind === "note"
        ? { type: "note", page, x: current.x, y: current.y, text }
        : { type: "textBox", page, x: current.x, y: current.y, text, fontSize: settings.fontSize, color },
    );
  };

  const area = drag ? normalize(...drag) : null;
  return (
    <div
      className={`pdf-layer tool-${tool}`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
    >
      {area && preview.length === 0 && (
        <div
          className="pdf-drag"
          style={{ left: area.x * scale, top: area.y * scale, width: area.width * scale, height: area.height * scale }}
        />
      )}
      {preview.map((rect, index) => (
        <div
          key={index}
          className={`pdf-preview ${tool}`}
          style={{
            left: rect.x * scale,
            top: rect.y * scale,
            width: rect.width * scale,
            height: rect.height * scale,
            ["--mark" as string]: color,
          }}
        />
      ))}
      {stroke && (
        <svg className="pdf-stroke" aria-hidden="true">
          <polyline
            points={stroke.map(([x, y]) => `${x * scale},${y * scale}`).join(" ")}
            fill="none"
            stroke={color}
            strokeWidth={settings.penWidth * scale}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}
      {draft && (
        <div className={`pdf-draft ${draft.kind}`} style={{ left: draft.x * scale, top: draft.y * scale }}>
          <textarea
            ref={input}
            rows={draft.kind === "note" ? 3 : 1}
            placeholder={draft.kind === "note" ? "메모 내용" : "텍스트 입력"}
            style={
              draft.kind === "text"
                ? { fontSize: settings.fontSize * scale, color, lineHeight: 1.25 }
                : undefined
            }
            onPointerDown={(event) => event.stopPropagation()}
            onKeyDown={(event) => {
              event.stopPropagation();
              if (event.key === "Escape") setDraft(null);
              else if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) commitDraft();
            }}
            onInput={(event) => {
              // 내용에 맞춰 입력란을 늘린다.
              const element = event.currentTarget;
              element.style.height = "auto";
              element.style.height = `${element.scrollHeight}px`;
              element.style.width = "auto";
              element.style.width = `${Math.max(120, element.scrollWidth + 4)}px`;
            }}
            onBlur={commitDraft}
          />
          <span className="pdf-draft-hint">{navigator.userAgent.includes("Mac") ? "⌘" : "Ctrl"}+Enter 또는 바깥 클릭으로 넣기 · Esc 취소</span>
        </div>
      )}
    </div>
  );
}
