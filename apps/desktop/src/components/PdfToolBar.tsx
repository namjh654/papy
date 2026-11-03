import type { OpenedDocument, PageOp } from "../engine";
import { Icon, type IconName } from "./Icon";
import type { PdfTool, PdfToolSettings } from "./PdfLayer";

const COLORS = ["#facc15", "#4ade80", "#60a5fa", "#f472b6", "#d92d20", "#000000"];
const isMac = navigator.userAgent.includes("Mac");
const mod = isMac ? "⌘" : "Ctrl+";

const TOOLS: [PdfTool, string, IconName][] = [
  ["select", "보기·스크롤", "cursor"],
  ["highlight", "형광펜 (글줄을 끌어서)", "highlighter"],
  ["underline", "밑줄", "underline"],
  ["strikeOut", "취소선", "strike"],
  ["pen", "펜", "pen"],
  ["text", "텍스트 상자", "textbox"],
  ["note", "메모", "note"],
];

interface Props {
  doc: OpenedDocument;
  current: number;
  settings: PdfToolSettings;
  onSettings(settings: PdfToolSettings): void;
  onUndo(): void;
  onRedo(): void;
  onPages(op: PageOp): void;
}

export function PdfToolBar({ doc, current, settings, onSettings, onUndo, onRedo, onPages }: Props) {
  const set = (patch: Partial<PdfToolSettings>) => onSettings({ ...settings, ...patch });
  return (
    <div className="format-bar" role="toolbar" aria-label="PDF 편집">
      <button className="icon" onClick={onUndo} title={`되돌리기 (${mod}Z)`}>
        <Icon name="undo" />
      </button>
      <button className="icon" onClick={onRedo} title={`다시 하기 (${isMac ? "⇧⌘Z" : "Ctrl+Y"})`}>
        <Icon name="redo" />
      </button>
      <span className="divider" />

      {doc.canAnnotate &&
        TOOLS.map(([tool, label, icon]) => (
          <button key={tool} className="icon" aria-pressed={settings.tool === tool} onClick={() => set({ tool })} title={label}>
            <Icon name={icon} />
          </button>
        ))}
      {doc.canAnnotate && (
        <>
          <div className="color-pick" title="색">
            {COLORS.map((color) => (
              <button
                key={color}
                className="swatch"
                aria-label={`색 ${color}`}
                aria-pressed={settings.color === color}
                style={{ background: color }}
                onClick={() => set({ color })}
              />
            ))}
          </div>
          {settings.tool === "pen" && (
            <label className="inline-field">
              굵기
              <select value={settings.penWidth} onChange={(event) => set({ penWidth: Number(event.target.value) })}>
                {[1, 2, 3, 5, 8].map((width) => (
                  <option key={width} value={width}>
                    {width}pt
                  </option>
                ))}
              </select>
            </label>
          )}
          {settings.tool === "text" && (
            <label className="inline-field">
              크기
              <select value={settings.fontSize} onChange={(event) => set({ fontSize: Number(event.target.value) })}>
                {[8, 10, 12, 14, 16, 20, 24, 32].map((size) => (
                  <option key={size} value={size}>
                    {size}pt
                  </option>
                ))}
              </select>
            </label>
          )}
        </>
      )}

      {doc.canEditPages && (
        <>
          <span className="divider" />
          <span className="bar-label">{current + 1}쪽</span>
          <button className="icon" onClick={() => onPages({ type: "rotate", page: current, quarterTurns: -1 })} title="왼쪽으로 돌리기">
            <Icon name="rotate-left" />
          </button>
          <button className="icon" onClick={() => onPages({ type: "rotate", page: current, quarterTurns: 1 })} title="오른쪽으로 돌리기">
            <Icon name="rotate-right" />
          </button>
          <button
            className="icon"
            disabled={current === 0}
            onClick={() => onPages({ type: "move", from: current, to: current - 1 })}
            title="앞으로 옮기기"
          >
            <Icon name="move-up" />
          </button>
          <button
            className="icon"
            disabled={current >= doc.pageCount - 1}
            onClick={() => onPages({ type: "move", from: current, to: current + 1 })}
            title="뒤로 옮기기"
          >
            <Icon name="move-down" />
          </button>
          <button
            className="icon danger"
            disabled={doc.pageCount <= 1}
            onClick={() => onPages({ type: "delete", page: current })}
            title="이 쪽 삭제"
          >
            <Icon name="trash" />
          </button>
        </>
      )}
    </div>
  );
}
