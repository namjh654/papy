import type { Alignment } from "../engine";
import type { Editor } from "../editor/useEditor";
import { Icon } from "./Icon";
import { t } from "../i18n";

const SIZES = [8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72];
const COLORS = ["#000000", "#5b6474", "#d92d20", "#dc6803", "#079455", "#1570ef", "#6938ef"];
const isMac = navigator.userAgent.includes("Mac");
const mod = isMac ? "⌘" : "Ctrl+";

interface Props {
  editor: Editor;
  /** 툴바 버튼을 누른 뒤 입력란으로 포커스를 되돌린다. */
  refocus(): void;
  /** 고급 편집(표·개체·머리말, rhwp-studio). 없으면 버튼을 숨긴다 */
  onAdvanced?: () => void;
}

export function FormatBar({ editor, refocus, onAdvanced }: Props) {
  const { charFormat, paraFormat, caret } = editor.state;
  const disabled = !caret;
  const run = (action: () => unknown) => () => {
    void action();
    refocus();
  };
  const toggle = (key: "bold" | "italic" | "underline" | "strikethrough") => run(() => editor.toggleChar(key));
  const align = (alignment: Alignment) => run(() => editor.formatParagraphs({ alignment }));

  return (
    <div className="format-bar" role="toolbar" aria-label={t.format.toolbar} onMouseDown={(event) => event.preventDefault()}>
      <button className="icon" onClick={run(editor.undo)} title={t.common.undo(mod)}>
        <Icon name="undo" />
      </button>
      <button className="icon" onClick={run(editor.redo)} title={t.common.redo(isMac ? "⇧⌘Z" : "Ctrl+Y")}>
        <Icon name="redo" />
      </button>
      <span className="divider" />

      <span className="font-name" title={t.format.font}>
        {charFormat.fontFamily ?? "—"}
      </span>
      <select
        className="size-select"
        aria-label={t.format.fontSize}
        disabled={disabled}
        value={charFormat.fontSize ? String(Math.round(charFormat.fontSize * 10) / 10) : ""}
        onChange={(event) => {
          void editor.formatChars({ fontSize: Number(event.target.value) });
          refocus();
        }}
      >
        {charFormat.fontSize && !SIZES.includes(Math.round(charFormat.fontSize * 10) / 10) && (
          <option value={String(Math.round(charFormat.fontSize * 10) / 10)}>{Math.round(charFormat.fontSize * 10) / 10}</option>
        )}
        {!charFormat.fontSize && <option value="">—</option>}
        {SIZES.map((size) => (
          <option key={size} value={String(size)}>
            {size}
          </option>
        ))}
      </select>
      <span className="divider" />

      <button className="icon text" aria-pressed={!!charFormat.bold} disabled={disabled} onClick={toggle("bold")} title={t.format.bold(mod)}>
        <b>{t.format.sample}</b>
      </button>
      <button className="icon text" aria-pressed={!!charFormat.italic} disabled={disabled} onClick={toggle("italic")} title={t.format.italic(mod)}>
        <i>{t.format.sample}</i>
      </button>
      <button className="icon text" aria-pressed={!!charFormat.underline} disabled={disabled} onClick={toggle("underline")} title={t.format.underline(mod)}>
        <u>{t.format.sample}</u>
      </button>
      <button className="icon text" aria-pressed={!!charFormat.strikethrough} disabled={disabled} onClick={toggle("strikethrough")} title={t.format.strikethrough}>
        <s>{t.format.sample}</s>
      </button>
      <div className="color-pick" title={t.format.textColor}>
        {COLORS.map((color) => (
          <button
            key={color}
            className="swatch"
            disabled={disabled}
            aria-label={t.format.textColorOf(color)}
            aria-pressed={charFormat.color?.toLowerCase() === color}
            style={{ background: color }}
            onClick={run(() => editor.formatChars({ color }))}
          />
        ))}
      </div>
      <span className="divider" />

      {(["left", "center", "right", "justify"] as const).map((value) => (
        <button
          key={value}
          className="icon"
          disabled={disabled}
          aria-pressed={paraFormat.alignment === value}
          onClick={align(value)}
          title={{ left: t.format.left, center: t.format.center, right: t.format.right, justify: t.format.justify }[value]}
        >
          <Icon name={`align-${value}`} />
        </button>
      ))}
      {onAdvanced && (
        <>
          <span className="spacer" />
          <button className="ghost small" onClick={onAdvanced} title={t.format.advancedHint}>
            {t.format.advanced}
          </button>
        </>
      )}
    </div>
  );
}
