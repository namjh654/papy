import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { open as openDialog } from "@tauri-apps/plugin-dialog";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import {
  closeDocument,
  describeError,
  EngineError,
  ErrorCode,
  openDocument,
  type OpenedDocument,
} from "./engine";
import { clearRecent, forgetRecent, loadRecent, rememberRecent, type RecentFile } from "./recent";
import { RenderQueue } from "./renderQueue";
import { PasswordDialog } from "./components/PasswordDialog";
import { Sidebar } from "./components/Sidebar";
import { Toolbar, type ZoomMode } from "./components/Toolbar";
import { PX_PER_PT, Viewer, type ViewerHandle } from "./components/Viewer";
import { Welcome } from "./components/Welcome";

const ZOOM_STEPS = [0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4, 5];
const ZOOM_MIN = ZOOM_STEPS[0]!;
const ZOOM_MAX = ZOOM_STEPS[ZOOM_STEPS.length - 1]!;
const EXTENSIONS = ["pdf", "hwp", "hwpx"];

type Opened = OpenedDocument & { path: string };
type PasswordPrompt = { path: string; fileName: string; retry: boolean; attempt: number };

const fileNameOf = (path: string) => path.split(/[\\/]/).pop() ?? path;
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

  const viewer = useRef<ViewerHandle>(null);
  const main = useRef<HTMLDivElement>(null);
  const queue = useMemo(() => new RenderQueue(), []);
  const docRef = useRef(doc);
  docRef.current = doc;

  // ---- 문서 열기/닫기 ----

  const openPath = useCallback(
    async (path: string, secret?: string) => {
      setBusy(true);
      setError(null);
      try {
        const opened = await openDocument(path, secret);
        const previous = docRef.current;
        queue.clear();
        if (previous) void closeDocument(previous.id);
        setDoc({ ...opened, path });
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
    [queue],
  );

  const chooseFile = useCallback(async () => {
    const selected = await openDialog({
      multiple: false,
      directory: false,
      filters: [{ name: "문서 (PDF, HWP, HWPX)", extensions: EXTENSIONS }],
    });
    if (typeof selected === "string") void openPath(selected);
  }, [openPath]);

  const closeCurrent = useCallback(() => {
    const previous = docRef.current;
    if (!previous) return;
    queue.clear();
    void closeDocument(previous.id);
    setDoc(null);
  }, [queue]);

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
        else if (event.payload.paths.length > 0) setError("PDF, HWP, HWPX 파일만 열 수 있습니다.");
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
      if (mod && event.key.toLowerCase() === "o") {
        event.preventDefault();
        void chooseFile();
      } else if (!docRef.current || password) {
        return;
      } else if (mod && (event.key === "=" || event.key === "+")) {
        event.preventDefault();
        zoomStep(1);
      } else if (mod && event.key === "-") {
        event.preventDefault();
        zoomStep(-1);
      } else if (mod && event.key === "0") {
        event.preventDefault();
        setZoomMode({ kind: "fit-width" });
      } else if (mod && event.key.toLowerCase() === "b") {
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
  }, [chooseFile, zoomStep, goTo, current, password]);

  // 오류 안내는 잠시 뒤 사라진다.
  useEffect(() => {
    if (!error) return;
    const timer = setTimeout(() => setError(null), 7000);
    return () => clearTimeout(timer);
  }, [error]);

  useEffect(() => {
    document.title = doc ? `${doc.title || doc.fileName} — Papy` : "Papy";
  }, [doc]);

  return (
    <div className="app">
      <Toolbar
        doc={doc}
        current={current}
        zoom={zoom}
        zoomMode={zoomMode}
        sidebarOpen={sidebarOpen}
        onOpen={chooseFile}
        onClose={closeCurrent}
        onToggleSidebar={() => setSidebarOpen((open) => !open)}
        onGoTo={goTo}
        onZoom={setZoomMode}
        onZoomStep={zoomStep}
      />
      <div className="workspace">
        {doc && sidebarOpen && <Sidebar doc={doc} current={current} queue={queue} onGoTo={goTo} />}
        <main className="main" ref={main}>
          {doc ? (
            <Viewer key={doc.id} ref={viewer} doc={doc} zoom={zoom} queue={queue} onPageChange={setCurrent} />
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
