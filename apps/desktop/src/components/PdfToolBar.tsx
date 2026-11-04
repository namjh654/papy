import type { OpenedDocument, PageOp } from "../engine";
import { Icon, type IconName } from "./Icon";
import type { PdfTool, PdfToolSettings } from "./PdfLayer";
import { t } from "../i18n";

const COLORS = ["#facc15", "#4ade80", "#60a5fa", "#f472b6", "#d92d20", "#000000"];
const isMac = navigator.userAgent.includes("Mac");
const mod = isMac ? "⌘" : "Ctrl+";

const TOOLS: [PdfTool, string, IconName][] = [
  ["select", t.pdf.select, "cursor"],
  ["highlight", t.pdf.highlight, "highlighter"],
  ["underline", t.pdf.underline, "underline"],
  ["strikeOut", t.pdf.strikeOut, "strike"],
  ["pen", t.pdf.pen, "pen"],
  ["text", t.pdf.text, "textbox"],
  ["note", t.pdf.note, "note"],
  ["replace", t.pdf.replace, "edit"],
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
    <div className="format-bar" role="toolbar" aria-label={t.pdf.toolbar}>
      <button className="icon" onClick={onUndo} title={t.common.undo(mod)}>
        <Icon name="undo" />
      </button>
      <button className="icon" onClick={onRedo} title={t.common.redo(isMac ? "⇧⌘Z" : "Ctrl+Y")}>
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
          <div className="color-pick" title={t.common.color}>
            {COLORS.map((color) => (
              <button
                key={color}
                className="swatch"
                aria-label={t.pdf.colorOf(color)}
                aria-pressed={settings.color === color}
                style={{ background: color }}
                onClick={() => set({ color })}
              />
            ))}
          </div>
          {settings.tool === "pen" && (
            <label className="inline-field">
              {t.pdf.width}
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
              {t.pdf.size}
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
          <span className="bar-label">{t.common.page(current + 1)}</span>
          <button className="icon" onClick={() => onPages({ type: "rotate", page: current, quarterTurns: -1 })} title={t.pdf.rotateLeft}>
            <Icon name="rotate-left" />
          </button>
          <button className="icon" onClick={() => onPages({ type: "rotate", page: current, quarterTurns: 1 })} title={t.pdf.rotateRight}>
            <Icon name="rotate-right" />
          </button>
          <button
            className="icon"
            disabled={current === 0}
            onClick={() => onPages({ type: "move", from: current, to: current - 1 })}
            title={t.pdf.moveBack}
          >
            <Icon name="move-up" />
          </button>
          <button
            className="icon"
            disabled={current >= doc.pageCount - 1}
            onClick={() => onPages({ type: "move", from: current, to: current + 1 })}
            title={t.pdf.moveForward}
          >
            <Icon name="move-down" />
          </button>
          <button
            className="icon danger"
            disabled={doc.pageCount <= 1}
            onClick={() => onPages({ type: "delete", page: current })}
            title={t.pdf.deletePage}
          >
            <Icon name="trash" />
          </button>
        </>
      )}
    </div>
  );
}
