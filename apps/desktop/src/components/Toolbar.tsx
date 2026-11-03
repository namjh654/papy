import { useEffect, useRef, useState } from "react";
import type { OpenedDocument } from "../engine";
import { Icon } from "./Icon";

export type ZoomMode = { kind: "fit-width" } | { kind: "fit-page" } | { kind: "custom"; value: number };
export type Mode = "view" | "edit";

const isMac = navigator.userAgent.includes("Mac");
const mod = isMac ? "⌘" : "Ctrl+";
const FORMAT_LABELS: Record<string, string> = { hwpx: "HWPX", hwp: "HWP", pdf: "PDF", txt: "텍스트(TXT)" };

interface Props {
  doc: OpenedDocument | null;
  current: number;
  zoom: number;
  zoomMode: ZoomMode;
  sidebarOpen: boolean;
  onOpen(): void;
  onClose(): void;
  onToggleSidebar(): void;
  onGoTo(page: number): void;
  onZoom(mode: ZoomMode): void;
  onZoomStep(direction: 1 | -1): void;
  mode: Mode;
  dirty: boolean;
  onMode(mode: Mode): void;
  onSave(): void;
  onSaveAs(): void;
  onExport(extension: string): void;
}

export function Toolbar(props: Props) {
  const { doc, current, zoom, zoomMode, sidebarOpen } = props;
  return (
    <header className="toolbar">
      <div className="toolbar-group">
        <button className="icon" onClick={props.onToggleSidebar} disabled={!doc} aria-pressed={!!doc && sidebarOpen} title={"사이드바 (Ctrl/⌘+\\)"}>
          <Icon name="sidebar" />
        </button>
        <button className="icon" onClick={props.onOpen} title="열기 (Ctrl/⌘+O)">
          <Icon name="open" />
        </button>
        {doc && (
          <div className="doc-title" title={doc.fileName}>
            <span className="format-badge">{doc.format === "OLE compound file" ? "HWP" : doc.format}</span>
            <span className="name">{doc.title || doc.fileName}</span>
            {props.dirty && <span className="dirty-dot" title="저장하지 않은 변경이 있습니다" aria-label="저장 안 됨" />}
            <button className="icon small" onClick={props.onClose} title="문서 닫기">
              <Icon name="close" size={14} />
            </button>
          </div>
        )}
      </div>

      {doc && (
        <div className="toolbar-group center">
          <button className="icon" onClick={() => props.onGoTo(current - 1)} disabled={current <= 0} title="이전 쪽">
            <Icon name="prev" />
          </button>
          <PageInput current={current} count={doc.pageCount} onGoTo={props.onGoTo} />
          <button className="icon" onClick={() => props.onGoTo(current + 1)} disabled={current >= doc.pageCount - 1} title="다음 쪽">
            <Icon name="next" />
          </button>
        </div>
      )}

      {doc && (
        <div className="toolbar-group end">
          <div className="mode-switch" role="radiogroup" aria-label="모드">
            <button role="radio" aria-checked={props.mode === "view"} onClick={() => props.onMode("view")} title={`보기 모드 (${mod}E)`}>
              <Icon name="eye" size={15} /> 보기
            </button>
            <button
              role="radio"
              aria-checked={props.mode === "edit"}
              disabled={!doc.canEdit}
              onClick={() => props.onMode("edit")}
              title={doc.canEdit ? `편집 모드 (${mod}E)` : "이 형식은 아직 편집할 수 없습니다"}
            >
              <Icon name="edit" size={15} /> 편집
            </button>
          </div>
          <SaveMenu doc={doc} dirty={props.dirty} onSave={props.onSave} onSaveAs={props.onSaveAs} onExport={props.onExport} />
          <span className="divider" />
          <button className="icon" onClick={() => props.onZoomStep(-1)} title="축소 (Ctrl/⌘ −)">
            <Icon name="zoomOut" />
          </button>
          <span className="zoom-value">{Math.round(zoom * 100)}%</span>
          <button className="icon" onClick={() => props.onZoomStep(1)} title="확대 (Ctrl/⌘ +)">
            <Icon name="zoomIn" />
          </button>
          <span className="divider" />
          <button
            className="icon"
            aria-pressed={zoomMode.kind === "fit-width"}
            onClick={() => props.onZoom({ kind: "fit-width" })}
            title="너비 맞춤 (Ctrl/⌘+0)"
          >
            <Icon name="fitWidth" />
          </button>
          <button
            className="icon"
            aria-pressed={zoomMode.kind === "fit-page"}
            onClick={() => props.onZoom({ kind: "fit-page" })}
            title="쪽 맞춤"
          >
            <Icon name="fitPage" />
          </button>
        </div>
      )}
    </header>
  );
}

function PageInput({ current, count, onGoTo }: { current: number; count: number; onGoTo(page: number): void }) {
  const [value, setValue] = useState(String(current + 1));
  useEffect(() => setValue(String(current + 1)), [current]);
  const commit = () => {
    const page = Number.parseInt(value, 10);
    if (Number.isFinite(page)) onGoTo(Math.min(count, Math.max(1, page)) - 1);
    else setValue(String(current + 1));
  };
  return (
    <label className="page-input">
      <input
        value={value}
        inputMode="numeric"
        aria-label="쪽 번호"
        onChange={(event) => setValue(event.target.value.replace(/\D/g, ""))}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            commit();
            (event.target as HTMLInputElement).blur();
          }
        }}
        style={{ width: `${Math.max(2, String(count).length) + 1.5}ch` }}
      />
      <span>/ {count}</span>
    </label>
  );
}

function SaveMenu({
  doc,
  dirty,
  onSave,
  onSaveAs,
  onExport,
}: {
  doc: OpenedDocument;
  dirty: boolean;
  onSave(): void;
  onSaveAs(): void;
  onExport(extension: string): void;
}) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false);
    };
    window.addEventListener("mousedown", close);
    return () => window.removeEventListener("mousedown", close);
  }, [open]);
  if (doc.saveFormats.length === 0) return null;
  const pick = (action: () => void) => () => {
    setOpen(false);
    action();
  };
  return (
    <div className="save-menu" ref={box}>
      <button className="icon" onClick={onSave} disabled={!dirty} title={`저장 (${mod}S)`}>
        <Icon name="save" />
      </button>
      <button className="icon small-chevron" onClick={() => setOpen(!open)} aria-expanded={open} aria-haspopup="menu" title="저장·내보내기">
        <Icon name="chevron" size={12} />
      </button>
      {open && (
        <div className="menu" role="menu">
          <button role="menuitem" onClick={pick(onSave)} disabled={!dirty}>
            저장 <kbd>{mod}S</kbd>
          </button>
          <button role="menuitem" onClick={pick(onSaveAs)}>
            다른 이름으로 저장… <kbd>{isMac ? "⇧⌘S" : "Ctrl+Shift+S"}</kbd>
          </button>
          <div className="menu-label">다른 형식으로 내보내기</div>
          {doc.saveFormats.map((extension) => (
            <button key={extension} role="menuitem" onClick={pick(() => onExport(extension))}>
              <Icon name="export" size={14} /> {FORMAT_LABELS[extension] ?? extension.toUpperCase()}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
