import { useEffect, useRef, useState } from "react";
import type { OpenedDocument } from "../engine";
import { Icon } from "./Icon";
import { t } from "../i18n";

export type ZoomMode = { kind: "fit-width" } | { kind: "fit-page" } | { kind: "custom"; value: number };
export type Mode = "view" | "edit";

const isMac = navigator.userAgent.includes("Mac");
const mod = isMac ? "⌘" : "Ctrl+";
const FORMAT_LABELS: Record<string, string> = {
  hwpx: "HWPX",
  hwp: "HWP",
  docx: "Word(DOCX)",
  pdf: "PDF",
  txt: t.formats.txtLabel,
};

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
  aiOpen: boolean;
  onAi(): void;
}

export function Toolbar(props: Props) {
  const { doc, current, zoom, zoomMode, sidebarOpen } = props;
  return (
    <header className="toolbar">
      <div className="toolbar-group">
        <button className="icon" onClick={props.onToggleSidebar} disabled={!doc} aria-pressed={!!doc && sidebarOpen} title={t.toolbar.sidebar}>
          <Icon name="sidebar" />
        </button>
        <button className="icon" onClick={props.onOpen} title={t.toolbar.open}>
          <Icon name="open" />
        </button>
        {doc && (
          <div className="doc-title" title={doc.fileName}>
            <span className="format-badge">{doc.format === "OLE compound file" ? "HWP" : doc.format}</span>
            <span className="name">{doc.title || doc.fileName}</span>
            {props.dirty && <span className="dirty-dot" title={t.toolbar.unsaved} aria-label={t.toolbar.unsavedShort} />}
            <button className="icon small" onClick={props.onClose} title={t.toolbar.closeDocument}>
              <Icon name="close" size={14} />
            </button>
          </div>
        )}
      </div>

      {doc && (
        <div className="toolbar-group center">
          <button className="icon" onClick={() => props.onGoTo(current - 1)} disabled={current <= 0} title={t.toolbar.previousPage}>
            <Icon name="prev" />
          </button>
          <PageInput current={current} count={doc.pageCount} onGoTo={props.onGoTo} />
          <button className="icon" onClick={() => props.onGoTo(current + 1)} disabled={current >= doc.pageCount - 1} title={t.toolbar.nextPage}>
            <Icon name="next" />
          </button>
        </div>
      )}

      {doc && (
        <div className="toolbar-group end">
          <div className="mode-switch" role="radiogroup" aria-label={t.toolbar.mode}>
            <button role="radio" aria-checked={props.mode === "view"} onClick={() => props.onMode("view")} title={t.toolbar.viewMode(mod)}>
              <Icon name="eye" size={15} /> {t.toolbar.view}
            </button>
            <button
              role="radio"
              aria-checked={props.mode === "edit"}
              disabled={!(doc.canEdit || doc.canAnnotate || doc.canEditPages)}
              onClick={() => props.onMode("edit")}
              title={
                doc.canEdit || doc.canAnnotate || doc.canEditPages
                  ? t.toolbar.editMode(mod)
                  : t.toolbar.cannotEdit
              }
            >
              <Icon name="edit" size={15} /> {t.toolbar.edit}
            </button>
          </div>
          <SaveMenu doc={doc} dirty={props.dirty} onSave={props.onSave} onSaveAs={props.onSaveAs} onExport={props.onExport} />
          <span className="divider" />
          <button className="icon" onClick={() => props.onZoomStep(-1)} title={t.toolbar.zoomOut}>
            <Icon name="zoomOut" />
          </button>
          <span className="zoom-value">{Math.round(zoom * 100)}%</span>
          <button className="icon" onClick={() => props.onZoomStep(1)} title={t.toolbar.zoomIn}>
            <Icon name="zoomIn" />
          </button>
          <span className="divider" />
          <button
            className="icon"
            aria-pressed={zoomMode.kind === "fit-width"}
            onClick={() => props.onZoom({ kind: "fit-width" })}
            title={t.toolbar.fitWidth}
          >
            <Icon name="fitWidth" />
          </button>
          <button
            className="icon"
            aria-pressed={zoomMode.kind === "fit-page"}
            onClick={() => props.onZoom({ kind: "fit-page" })}
            title={t.toolbar.fitPage}
          >
            <Icon name="fitPage" />
          </button>
        </div>
      )}
      <div className="toolbar-group ai">
        <button className="icon" onClick={props.onAi} aria-pressed={props.aiOpen} title={t.ai.open}>
          <Icon name="sparkle" />
        </button>
      </div>
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
        aria-label={t.toolbar.pageNumber}
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
      <button className="icon" onClick={onSave} disabled={!dirty} title={t.toolbar.save(mod)}>
        <Icon name="save" />
      </button>
      <button className="icon small-chevron" onClick={() => setOpen(!open)} aria-expanded={open} aria-haspopup="menu" title={t.toolbar.saveMenu}>
        <Icon name="chevron" size={12} />
      </button>
      {open && (
        <div className="menu" role="menu">
          <button role="menuitem" onClick={pick(onSave)} disabled={!dirty}>
            {t.common.save} <kbd>{mod}S</kbd>
          </button>
          <button role="menuitem" onClick={pick(onSaveAs)}>
            {t.toolbar.saveAs} <kbd>{isMac ? "⇧⌘S" : "Ctrl+Shift+S"}</kbd>
          </button>
          <div className="menu-label">{t.toolbar.exportAs}</div>
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
