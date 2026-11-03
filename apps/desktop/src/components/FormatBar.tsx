import type { Alignment } from "../engine";
import type { Editor } from "../editor/useEditor";
import { Icon } from "./Icon";

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
    <div className="format-bar" role="toolbar" aria-label="서식" onMouseDown={(event) => event.preventDefault()}>
      <button className="icon" onClick={run(editor.undo)} title={`되돌리기 (${mod}Z)`}>
        <Icon name="undo" />
      </button>
      <button className="icon" onClick={run(editor.redo)} title={`다시 하기 (${isMac ? "⇧⌘Z" : "Ctrl+Y"})`}>
        <Icon name="redo" />
      </button>
      <span className="divider" />

      <span className="font-name" title="글꼴">
        {charFormat.fontFamily ?? "—"}
      </span>
      <select
        className="size-select"
        aria-label="글자 크기"
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

      <button className="icon text" aria-pressed={!!charFormat.bold} disabled={disabled} onClick={toggle("bold")} title={`굵게 (${mod}B)`}>
        <b>가</b>
      </button>
      <button className="icon text" aria-pressed={!!charFormat.italic} disabled={disabled} onClick={toggle("italic")} title={`기울임 (${mod}I)`}>
        <i>가</i>
      </button>
      <button className="icon text" aria-pressed={!!charFormat.underline} disabled={disabled} onClick={toggle("underline")} title={`밑줄 (${mod}U)`}>
        <u>가</u>
      </button>
      <button className="icon text" aria-pressed={!!charFormat.strikethrough} disabled={disabled} onClick={toggle("strikethrough")} title="취소선">
        <s>가</s>
      </button>
      <div className="color-pick" title="글자 색">
        {COLORS.map((color) => (
          <button
            key={color}
            className="swatch"
            disabled={disabled}
            aria-label={`글자 색 ${color}`}
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
          title={{ left: "왼쪽 정렬", center: "가운데 정렬", right: "오른쪽 정렬", justify: "양쪽 정렬" }[value]}
        >
          <Icon name={`align-${value}`} />
        </button>
      ))}
      {onAdvanced && (
        <>
          <span className="spacer" />
          <button className="ghost small" onClick={onAdvanced} title="표·그림·머리말까지 고치는 편집기">
            고급 편집
          </button>
        </>
      )}
    </div>
  );
}
