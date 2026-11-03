import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { message, open as openDialog, save as saveDialog } from "@tauri-apps/plugin-dialog";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { listen } from "@tauri-apps/api/event";
import {
  closeDocument,
  describeError,
  edit,
  EngineError,
  ErrorCode,
  openDocument,
  pageLayout,
  saveDocument,
  takePendingOpens,
  type Annotation,
  type Changed,
  type EditOp,
  type OpenedDocument,
} from "./engine";
import { PdfToolBar } from "./components/PdfToolBar";
import type { PdfToolSettings } from "./components/PdfLayer";
import { useEditor } from "./editor/useEditor";
import { FormatBar } from "./components/FormatBar";
import { clearRecent, forgetRecent, loadRecent, rememberRecent, type RecentFile } from "./recent";
import { RenderQueue } from "./renderQueue";
import { PasswordDialog } from "./components/PasswordDialog";
import { Sidebar } from "./components/Sidebar";
import { Toolbar, type Mode, type ZoomMode } from "./components/Toolbar";
import { PX_PER_PT, Viewer, type ViewerHandle } from "./components/Viewer";
import { Welcome } from "./components/Welcome";

const ZOOM_STEPS = [0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4, 5];
const ZOOM_MIN = ZOOM_STEPS[0]!;
const ZOOM_MAX = ZOOM_STEPS[ZOOM_STEPS.length - 1]!;
const EXTENSIONS = ["pdf", "hwp", "hwpx", "docx"];

type Opened = OpenedDocument & { path: string };
type PasswordPrompt = { path: string; fileName: string; retry: boolean; attempt: number };

const fileNameOf = (path: string) => path.split(/[\\/]/).pop() ?? path;
const extensionOf = (path: string) => (/\.([^.\\/]+)$/.exec(path)?.[1] ?? "").toLowerCase();
const stemOf = (name: string) => name.replace(/\.[^.]+$/, "");
/** 편집한 내용을 그대로 다시 열 수 있는 형식(저장하면 이 파일이 작업 파일이 된다). 나머지는 내보내기. */
const workFormats = (doc: OpenedDocument) =>
  doc.format === "PDF" ? ["pdf"] : doc.saveFormats.filter((ext) => ext === "hwpx" || ext === "hwp" || ext === "docx");
const canModify = (doc: OpenedDocument | null) => !!doc && (doc.canEdit || doc.canAnnotate || doc.canEditPages);
const FORMAT_NAMES: Record<string, string> = {
  hwpx: "HWPX 문서",
  hwp: "HWP 문서",
  docx: "Word 문서",
  pdf: "PDF",
  txt: "텍스트",
};
const clampZoom = (zoom: number) => Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, zoom));

export function App() {
  const [doc, setDoc] = useState<Opened | null>(null);
  const [current, setCurrent] = useState(0);
  const [zoomMode, setZoomMode] = useState<ZoomMode>({ kind: "fit-width" });
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [recent, setRecent] = useState<RecentFile[]>(loadRecent);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [password, setPassword] = useState<PasswordPrompt | null>(null);
  const [dragging, setDragging] = useState(false);
  const [viewport, setViewport] = useState({ width: 800, height: 600 });
  const [mode, setMode] = useState<Mode>("view");
  const [dirty, setDirty] = useState(false);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const [pdfSettings, setPdfSettings] = useState<PdfToolSettings>({
    tool: "highlight",
    color: "#facc15",
    penWidth: 2,
    fontSize: 14,
  });

  const viewer = useRef<ViewerHandle>(null);
  const main = useRef<HTMLDivElement>(null);
  const queue = useMemo(() => new RenderQueue(), []);
  const docRef = useRef(doc);
  docRef.current = doc;
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;

  // ---- 편집 ----

  const editor = useEditor(doc?.id ?? 0, mode === "edit" && !!doc?.canEdit, {
    onChanged(pageCount) {
      setDirty(true);
      setRevision((value) => value + 1);
      const current = docRef.current;
      // 쪽 수가 바뀌었을 때만 쪽 크기 목록을 다시 받는다.
      if (current && pageCount !== undefined && pageCount !== current.pageCount) {
        void pageLayout(current.id).then((pages) =>
          setDoc((old) => (old && old.id === current.id ? { ...old, pages, pageCount: pages.length } : old)),
        );
      }
    },
    reveal: (rect) => viewer.current?.reveal(rect),
    onError: (caught) => setError(describeError(caught)),
  });

  // ---- PDF 편집(주석·쪽) ----

  /** 바뀐 뒤: 다시 그리기, 쪽 크기 다시 받기(회전·삭제·이동으로 바뀐다). */
  const refreshAfterPdfChange = useCallback(() => {
    setDirty(true);
    setRevision((value) => value + 1);
    const current = docRef.current;
    if (!current) return;
    void pageLayout(current.id).then((pages) =>
      setDoc((old) => (old && old.id === current.id ? { ...old, pages, pageCount: pages.length } : old)),
    );
  }, []);

  const pdfEdit = useCallback(
    async (op: EditOp) => {
      const current = docRef.current;
      if (!current) return;
      try {
        const reply = await edit<Changed>(current.id, op);
        if (reply.changed) refreshAfterPdfChange();
      } catch (caught) {
        setError(describeError(caught));
      }
    },
    [refreshAfterPdfChange],
  );

  const annotate = useCallback((annotation: Annotation) => pdfEdit({ kind: "annotate", annotation }), [pdfEdit]);
  const pdfMode = mode === "edit" && !!doc && !doc.canEdit && (doc.canAnnotate || doc.canEditPages);

  // ---- 저장 ----

  /** 작업 파일로 저장(HWP/HWPX). 성공하면 true. */
  const saveTo = useCallback(async (path: string) => {
    const current = docRef.current;
    if (!current) return false;
    try {
      await saveDocument(current.id, path);
      const name = fileNameOf(path);
      setDoc((old) => (old && old.id === current.id ? { ...old, path, fileName: name } : old));
      setDirty(false);
      setRecent(rememberRecent({ path, name, format: current.format }));
      setNotice(`저장했습니다: ${name}`);
      return true;
    } catch (caught) {
      setError(describeError(caught));
      return false;
    }
  }, []);

  const saveAs = useCallback(async () => {
    const current = docRef.current;
    if (!current) return false;
    const formats = workFormats(current);
    if (formats.length === 0) return false;
    const preferred = formats.includes(extensionOf(current.path)) ? extensionOf(current.path) : formats[0]!;
    const chosen = await saveDialog({
      defaultPath: `${stemOf(current.fileName)}.${preferred}`,
      filters: [preferred, ...formats.filter((ext) => ext !== preferred)].map((ext) => ({
        name: FORMAT_NAMES[ext] ?? ext,
        extensions: [ext],
      })),
    });
    if (!chosen) return false;
    const path = formats.includes(extensionOf(chosen)) ? chosen : `${chosen}.${preferred}`;
    return saveTo(path);
  }, [saveTo]);

  const save = useCallback(async () => {
    const current = docRef.current;
    if (!current) return false;
    // 원래 형식으로 다시 쓸 수 있으면 덮어쓴다. 아니면(PDF 등) 다른 이름으로 저장한다.
    if (workFormats(current).includes(extensionOf(current.path))) {
      return saveTo(current.path);
    }
    return saveAs();
  }, [saveTo, saveAs]);

  /** 다른 형식으로 내보내기. 작업 파일은 그대로 둔다. */
  const exportAs = useCallback(async (ext: string) => {
    const current = docRef.current;
    if (!current) return;
    const chosen = await saveDialog({
      defaultPath: `${stemOf(current.fileName)}.${ext}`,
      filters: [{ name: FORMAT_NAMES[ext] ?? ext, extensions: [ext] }],
    });
    if (!chosen) return;
    const path = extensionOf(chosen) === ext ? chosen : `${chosen}.${ext}`;
    try {
      await saveDocument(current.id, path);
      setNotice(`내보냈습니다: ${fileNameOf(path)}`);
    } catch (caught) {
      setError(describeError(caught));
    }
  }, []);

  /** 저장하지 않은 변경이 있으면 묻는다. 계속해도 되면 true. */
  const confirmDiscard = useCallback(async () => {
    const current = docRef.current;
    if (!current || !dirtyRef.current) return true;
    const answer = await message(`"${current.fileName}"의 변경 내용을 저장할까요?\n저장하지 않으면 변경 내용이 사라집니다.`, {
      title: "저장하지 않은 변경",
      kind: "warning",
      buttons: { yes: "저장", no: "저장 안 함", cancel: "취소" },
    });
    if (answer === "저장" || answer === "Yes") return save();
    return answer === "저장 안 함" || answer === "No";
  }, [save]);

  // 창을 닫을 때도 확인한다.
  useEffect(() => {
    const window = getCurrentWindow();
    const unlisten = window.onCloseRequested(async (event) => {
      if (!dirtyRef.current) return;
      event.preventDefault();
      if (await confirmDiscard()) {
        setDirty(false);
        await window.destroy();
      }
    });
    return () => {
      void unlisten.then((stop) => stop());
    };
  }, [confirmDiscard]);

  // ---- 문서 열기/닫기 ----

  const openPath = useCallback(
    async (path: string, secret?: string) => {
      if (secret === undefined && !(await confirmDiscard())) return;
      setBusy(true);
      setError(null);
      try {
        const opened = await openDocument(path, secret);
        const previous = docRef.current;
        queue.clear();
        if (previous) void closeDocument(previous.id);
        setDoc({ ...opened, path });
        setMode("view");
        setDirty(false);
        setCurrent(0);
        setZoomMode({ kind: "fit-width" });
        setPassword(null);
        setRecent(rememberRecent({ path, name: opened.fileName, format: opened.format }));
      } catch (caught) {
        const code = caught instanceof EngineError ? caught.code : ErrorCode.Internal;
        if (code === ErrorCode.PasswordRequired) {
          setPassword((prompt) => ({
            path,
            fileName: fileNameOf(path),
            retry: secret !== undefined,
            attempt: (prompt?.attempt ?? 0) + 1,
          }));
        } else {
          setPassword(null);
          setError(describeError(caught));
          if (code === ErrorCode.Io) setRecent(forgetRecent(path));
        }
      } finally {
        setBusy(false);
      }
    },
    [queue, confirmDiscard],
  );

  const chooseFile = useCallback(async () => {
    const selected = await openDialog({
      multiple: false,
      directory: false,
      filters: [{ name: "문서 (PDF, HWP, HWPX, DOCX)", extensions: EXTENSIONS }],
    });
    if (typeof selected === "string") void openPath(selected);
  }, [openPath]);

  const closeCurrent = useCallback(async () => {
    const previous = docRef.current;
    if (!previous || !(await confirmDiscard())) return;
    queue.clear();
    void closeDocument(previous.id);
    setDoc(null);
    setDirty(false);
    setMode("view");
  }, [queue, confirmDiscard]);

  const toggleMode = useCallback(() => {
    setMode((value) => (value === "view" && canModify(docRef.current) ? "edit" : "view"));
  }, []);

  // ---- 배율 ----

  useEffect(() => {
    const element = main.current;
    if (!element) return;
    const observer = new ResizeObserver(() => {
      const size = viewer.current?.viewport();
      if (size) setViewport(size);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [doc]);

  const zoom = useMemo(() => {
    if (!doc) return 1;
    if (zoomMode.kind === "custom") return zoomMode.value;
    // 가로 쪽이 섞여 있어도 대부분의 쪽(중앙값 너비)에 맞춘다.
    const widths = doc.pages.map((page) => page.width).sort((a, b) => a - b);
    const typical = widths[widths.length >> 1]! * PX_PER_PT;
    const byWidth = viewport.width / typical;
    if (zoomMode.kind === "fit-width") return clampZoom(byWidth);
    const page = doc.pages[current] ?? doc.pages[0]!;
    return clampZoom(Math.min(byWidth, viewport.height / (page.height * PX_PER_PT)));
    // fit-page 가 쪽을 넘길 때마다 바뀌지 않도록 current 는 의존성에 넣지 않는다.
  }, [doc, zoomMode, viewport]);

  const zoomStep = useCallback(
    (direction: 1 | -1) => {
      const next =
        direction > 0
          ? (ZOOM_STEPS.find((step) => step > zoom + 0.001) ?? ZOOM_MAX)
          : ([...ZOOM_STEPS].reverse().find((step) => step < zoom - 0.001) ?? ZOOM_MIN);
      setZoomMode({ kind: "custom", value: next });
    },
    [zoom],
  );

  const goTo = useCallback((page: number) => viewer.current?.goToPage(page), []);

  // ---- 파일 연결(탐색기·Finder 에서 더블클릭) ----

  useEffect(() => {
    // 여러 개를 한꺼번에 열면 마지막 파일을 연다(창 하나에 문서 하나).
    const openPending = () =>
      void takePendingOpens().then((paths) => {
        const last = paths[paths.length - 1];
        if (last) void openPath(last);
      });
    openPending();
    const unlisten = listen("open-files", openPending);
    return () => {
      void unlisten.then((stop) => stop());
    };
  }, [openPath]);

  // ---- 끌어다 놓기 ----

  useEffect(() => {
    const unlisten = getCurrentWebview().onDragDropEvent((event) => {
      const { type } = event.payload;
      if (type === "enter" || type === "over") setDragging(true);
      else if (type === "leave") setDragging(false);
      else if (type === "drop") {
        setDragging(false);
        const path = event.payload.paths.find((item) =>
          EXTENSIONS.includes(item.split(".").pop()?.toLowerCase() ?? ""),
        );
        if (path) void openPath(path);
        else if (event.payload.paths.length > 0) setError("PDF, HWP, HWPX, DOCX 파일만 열 수 있습니다.");
      }
    });
    return () => {
      void unlisten.then((stop) => stop());
    };
  }, [openPath]);

  // ---- 단축키 ----

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const mod = event.metaKey || event.ctrlKey;
      const typing = (event.target as HTMLElement)?.closest("input, textarea");
      const key = event.key.toLowerCase();
      if (mod && key === "o") {
        event.preventDefault();
        void chooseFile();
      } else if (!docRef.current || password) {
        return;
      } else if (mod && key === "s") {
        event.preventDefault();
        void (event.shiftKey ? saveAs() : save());
      } else if (mod && key === "e") {
        event.preventDefault();
        toggleMode();
      } else if (pdfMode && mod && (key === "z" || (key === "y" && !event.metaKey))) {
        event.preventDefault();
        void pdfEdit({ kind: key === "z" && !event.shiftKey ? "undo" : "redo" });
      } else if (pdfMode && event.key === "Escape") {
        setPdfSettings((settings) => ({ ...settings, tool: "select" }));
      } else if (mod && (event.key === "=" || event.key === "+")) {
        event.preventDefault();
        zoomStep(1);
      } else if (mod && event.key === "-") {
        event.preventDefault();
        zoomStep(-1);
      } else if (mod && event.key === "0") {
        event.preventDefault();
        setZoomMode({ kind: "fit-width" });
      } else if (mod && event.key === "\\") {
        event.preventDefault();
        setSidebarOpen((open) => !open);
      } else if (!typing && !mod && (event.key === "ArrowRight" || event.key === "ArrowLeft")) {
        event.preventDefault();
        goTo(current + (event.key === "ArrowRight" ? 1 : -1));
      } else if (!typing && !mod && (event.key === "Home" || event.key === "End")) {
        event.preventDefault();
        goTo(event.key === "Home" ? 0 : docRef.current.pageCount - 1);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [chooseFile, zoomStep, goTo, current, password, save, saveAs, toggleMode, pdfMode, pdfEdit]);

  // 오류 안내는 잠시 뒤 사라진다.
  useEffect(() => {
    if (!error) return;
    const timer = setTimeout(() => setError(null), 7000);
    return () => clearTimeout(timer);
  }, [error]);

  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 3500);
    return () => clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    document.title = doc ? `${dirty ? "• " : ""}${doc.title || doc.fileName} — Papy` : "Papy";
  }, [doc, dirty]);

  return (
    <div className="app">
      <Toolbar
        doc={doc}
        current={current}
        zoom={zoom}
        zoomMode={zoomMode}
        sidebarOpen={sidebarOpen}
        onOpen={chooseFile}
        onClose={() => void closeCurrent()}
        onToggleSidebar={() => setSidebarOpen((open) => !open)}
        onGoTo={goTo}
        onZoom={setZoomMode}
        onZoomStep={zoomStep}
        mode={mode}
        dirty={dirty}
        onMode={(next) => setMode(next === "edit" && canModify(doc) ? "edit" : "view")}
        onSave={() => void save()}
        onSaveAs={() => void saveAs()}
        onExport={(ext) => void exportAs(ext)}
      />
      {doc && mode === "edit" && doc.canEdit && <FormatBar editor={editor} refocus={() => viewer.current?.focusInput()} />}
      {doc && pdfMode && (
        <PdfToolBar
          doc={doc}
          current={current}
          settings={pdfSettings}
          onSettings={setPdfSettings}
          onUndo={() => void pdfEdit({ kind: "undo" })}
          onRedo={() => void pdfEdit({ kind: "redo" })}
          onPages={(op) =>
            void pdfEdit({ kind: "editPages", op }).then(() => {
              // 옮긴 쪽을 계속 보여 준다(쪽 크기 목록이 갱신된 뒤).
              if (op.type === "move") setTimeout(() => goTo(op.to), 150);
            })
          }
        />
      )}
      <div className="workspace">
        {doc && sidebarOpen && <Sidebar doc={doc} current={current} queue={queue} onGoTo={goTo} revision={revision} />}
        <main className="main" ref={main}>
          {doc ? (
            <Viewer
              key={doc.id}
              ref={viewer}
              doc={doc}
              zoom={zoom}
              queue={queue}
              onPageChange={setCurrent}
              editor={mode === "edit" && doc.canEdit ? editor : null}
              revision={revision}
              pdf={pdfMode ? { settings: pdfSettings, onAnnotate: annotate } : null}
            />
          ) : (
            <Welcome
              recent={recent}
              onOpen={chooseFile}
              onOpenRecent={(file) => void openPath(file.path)}
              onClearRecent={() => setRecent(clearRecent())}
            />
          )}
        </main>
      </div>

      {busy && (
        <div className="busy" role="status">
          <span className="spinner" /> 문서를 여는 중…
        </div>
      )}
      {notice && !error && (
        <div className="toast" role="status">
          <span>{notice}</span>
        </div>
      )}
      {error && (
        <div className="toast" role="alert">
          <span>{error}</span>
          <button className="ghost small" onClick={() => setError(null)}>
            닫기
          </button>
        </div>
      )}
      {dragging && (
        <div className="drop-overlay">
          <div>여기에 놓으면 문서를 엽니다</div>
        </div>
      )}
      {password && (
        <PasswordDialog
          // 시도마다 새로 만들어 입력란을 비운다.
          key={password.attempt}
          fileName={password.fileName}
          retry={password.retry}
          onSubmit={(secret) => void openPath(password.path, secret)}
          onCancel={() => setPassword(null)}
        />
      )}
    </div>
  );
}
