import { useEffect, useState } from "react";
import type { OpenedDocument } from "../engine";
import { Icon } from "./Icon";

export type ZoomMode = { kind: "fit-width" } | { kind: "fit-page" } | { kind: "custom"; value: number };

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
}

export function Toolbar(props: Props) {
  const { doc, current, zoom, zoomMode, sidebarOpen } = props;
  return (
    <header className="toolbar">
      <div className="toolbar-group">
        <button className="icon" onClick={props.onToggleSidebar} disabled={!doc} aria-pressed={!!doc && sidebarOpen} title="사이드바 (Ctrl/⌘+B)">
          <Icon name="sidebar" />
        </button>
        <button className="icon" onClick={props.onOpen} title="열기 (Ctrl/⌘+O)">
          <Icon name="open" />
        </button>
        {doc && (
          <div className="doc-title" title={doc.fileName}>
            <span className="format-badge">{doc.format === "OLE compound file" ? "HWP" : doc.format}</span>
            <span className="name">{doc.title || doc.fileName}</span>
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
