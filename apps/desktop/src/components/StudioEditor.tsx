// HWP·HWPX 고급 편집(표·개체·머리말 등): 앱에 넣은 rhwp-studio 를 iframe 으로 띄운다(ADR 0005).
// 문서는 Papy 엔진에서 바이트로 꺼내 studio 에 넣고, "적용"하면 studio 가 내보낸 바이트를 엔진이 새로 연다.
// 파일 읽기·쓰기는 하지 않는다 — 저장은 평소처럼 Papy 의 저장·다른 이름으로 저장으로 한다.
import { useEffect, useRef, useState } from "react";
import { message } from "@tauri-apps/plugin-dialog";
import { createStudio, type RhwpEditor } from "../vendor/rhwp-editor/index.js";
import { t } from "../i18n";

/** 앱 번들 안의 studio(scripts/fetch-rhwp-studio.mjs 가 public/studio 에 넣는다) */
const STUDIO_URL = "/studio/index.html";

export type StudioFormat = "hwp" | "hwpx";

interface Props {
  fileName: string;
  format: StudioFormat;
  load(): Promise<Uint8Array>;
  onApply(bytes: Uint8Array): Promise<void>;
  onCancel(): void;
}

/** studio 가 앱에 들어 있는지(개발 중 fetch 스크립트를 돌리지 않았으면 없다). */
export async function studioAvailable(): Promise<boolean> {
  try {
    const res = await fetch(STUDIO_URL, { method: "HEAD" });
    return res.ok;
  } catch {
    return false;
  }
}

export function StudioEditor({ fileName, format, load, onApply, onCancel }: Props) {
  const host = useRef<HTMLDivElement>(null);
  const studio = useRef<RhwpEditor | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "applying" | "failed">("loading");

  useEffect(() => {
    let cancelled = false;
    const element = host.current;
    if (!element) return;
    void (async () => {
      try {
        const started = performance.now();
        const [editor, bytes] = await Promise.all([createStudio(element, { studioUrl: STUDIO_URL }), load()]);
        if (cancelled) return editor.destroy();
        studio.current = editor;
        await editor.loadFile(bytes, fileName);
        console.info(`[papy] studio ready in ${Math.round(performance.now() - started)} ms`);
        if (!cancelled) setState("ready");
      } catch (error) {
        console.error("[papy] studio failed", error instanceof Error ? error.message : String(error));
        if (!cancelled) setState("failed");
      }
    })();
    return () => {
      cancelled = true;
      studio.current?.destroy();
      studio.current = null;
    };
    // 한 번만 띄운다.
  }, []);

  const apply = async () => {
    const editor = studio.current;
    if (!editor) return;
    setState("applying");
    try {
      const bytes = format === "hwp" ? await editor.exportHwp() : await editor.exportHwpx();
      await onApply(bytes);
    } catch {
      setState("ready");
    }
  };

  /** studio 에서 고친 것이 있으면 버릴지 묻는다. */
  const cancel = async () => {
    const dirty = await studio.current?.getDocumentState().then((state) => state.dirty, () => false);
    if (dirty) {
      const answer = await message(t.studio.discardQuestion, {
        title: t.studio.discardTitle,
        kind: "warning",
        buttons: { ok: t.studio.discard, cancel: t.studio.keepEditing },
      });
      if (answer !== t.studio.discard && answer !== "Ok") return;
    }
    onCancel();
  };

  return (
    <div className="studio" role="dialog" aria-label={t.studio.title}>
      <div className="studio-bar">
        <strong>{t.studio.title}</strong>
        <span className="studio-file">{fileName}</span>
        <span className="studio-hint">{t.studio.hint}</span>
        <button className="ghost" onClick={() => void cancel()} disabled={state === "applying"}>
          {t.common.cancel}
        </button>
        <button className="primary" onClick={() => void apply()} disabled={state !== "ready"}>
          {state === "applying" ? t.studio.applying : t.studio.apply}
        </button>
      </div>
      <div className="studio-frame" ref={host}>
        {state === "loading" && <div className="studio-status">{t.studio.loading}</div>}
        {state === "failed" && <div className="studio-status">{t.studio.failed}</div>}
      </div>
    </div>
  );
}
