// Rust 호스트(src-tauri/src/lib.rs) command 의 타입 있는 래퍼.
import { invoke } from "@tauri-apps/api/core";

export interface PageSize {
  /** pt */
  width: number;
  /** pt */
  height: number;
}

export interface OpenedDocument {
  id: number;
  fileName: string;
  format: string;
  pageCount: number;
  byteSize: number;
  title: string | null;
  canExtractText: boolean;
  hasOutline: boolean;
  canEdit: boolean;
  /** PDF: 주석(형광펜·펜·텍스트 상자·메모) */
  canAnnotate: boolean;
  /** PDF: 쪽 회전·삭제·이동 */
  canEditPages: boolean;
  /** 저장·변환 가능한 형식의 확장자(pdf, hwpx, hwp, txt) */
  saveFormats: string[];
  pages: PageSize[];
  /** 열 때 옮기지 못한 요소 수(DOCX 도형 등) */
  droppedObjects: number;
}

// ---- 편집 ----

/** 본문 위치: 구역·문단(0부터)·문단 안 글자 위치 */
export interface TextPos {
  section: number;
  paragraph: number;
  offset: number;
}

/** 쪽 위 사각형(pt) */
export interface PageRect {
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface CharFormat {
  fontFamily?: string | null;
  fontSize?: number | null;
  bold?: boolean | null;
  italic?: boolean | null;
  underline?: boolean | null;
  strikethrough?: boolean | null;
  color?: string | null;
}

export type Alignment = "left" | "center" | "right" | "justify";

export interface ParaFormat {
  alignment?: Alignment | null;
  lineSpacing?: number | null;
}

export type CaretMotion = "left" | "right" | "lineStart" | "lineEnd" | "documentStart" | "documentEnd";

export type EditOp =
  | { kind: "hitTest"; page: number; x: number; y: number }
  | { kind: "caretRect"; pos: TextPos }
  | { kind: "moveVertical"; pos: TextPos; lines: number; preferredX: number }
  | { kind: "moveCaret"; pos: TextPos; motion: CaretMotion }
  | { kind: "selectionRects"; start: TextPos; end: TextPos }
  | { kind: "textInRange"; start: TextPos; end: TextPos }
  | { kind: "insertText"; pos: TextPos; text: string }
  | { kind: "deleteRange"; start: TextPos; end: TextPos }
  | { kind: "deleteBackward"; pos: TextPos }
  | { kind: "deleteForward"; pos: TextPos }
  | { kind: "charFormat"; pos: TextPos }
  | { kind: "applyCharFormat"; start: TextPos; end: TextPos; format: CharFormat }
  | { kind: "paraFormat"; pos: TextPos }
  | { kind: "applyParaFormat"; start: TextPos; end: TextPos; format: ParaFormat }
  | { kind: "undo" }
  | { kind: "redo" }
  | { kind: "textRects"; page: number; area: Area }
  | { kind: "annotate"; annotation: Annotation }
  | { kind: "editPages"; op: PageOp };

/** 쪽 위 영역(pt, 화면에 보이는 쪽의 왼쪽 위 기준) */
export interface Area {
  x: number;
  y: number;
  width: number;
  height: number;
}

export type MarkupKind = "highlight" | "underline" | "strikeOut";

export type Annotation =
  | { type: "textBox"; page: number; x: number; y: number; text: string; fontSize: number; color: string }
  | { type: "markup"; page: number; kind: MarkupKind; area: Area; color: string }
  | { type: "ink"; page: number; points: [number, number][]; color: string; width: number }
  | { type: "note"; page: number; x: number; y: number; text: string };

export type PageOp =
  | { type: "rotate"; page: number; quarterTurns: number }
  | { type: "delete"; page: number }
  | { type: "move"; from: number; to: number };

/** 위치 + 커서 사각형 */
export interface Located {
  pos: TextPos;
  rect: PageRect;
}

/** 문서를 바꾸는 작업의 응답 */
export interface Changed {
  changed: boolean;
  caret?: TextPos;
  caretRect?: PageRect | null;
  pageCount?: number;
}

export function edit<T>(id: number, op: EditOp): Promise<T> {
  return call("edit", { id, op });
}

export function pageLayout(id: number): Promise<PageSize[]> {
  return call("page_layout", { id });
}

/** 확장자로 형식을 정해 저장한다(임시 파일에 쓴 뒤 바꿔치기). */
export function saveDocument(id: number, path: string): Promise<void> {
  return call("save_document", { id, path });
}

export function comparePos(a: TextPos, b: TextPos): number {
  return a.section - b.section || a.paragraph - b.paragraph || a.offset - b.offset;
}

export const samePos = (a: TextPos, b: TextPos) => comparePos(a, b) === 0;

export interface OutlineItem {
  title: string;
  page: number | null;
  children: OutlineItem[];
}

/** papy_core::ErrorCode 숫자 값 (바뀌지 않는다). */
export const ErrorCode = {
  InvalidArgument: 1,
  Io: 2,
  UnsupportedFormat: 3,
  InvalidDocument: 4,
  PasswordRequired: 5,
  LimitExceeded: 6,
  PageOutOfRange: 7,
  Cancelled: 8,
  NotImplemented: 9,
  SessionNotFound: 10,
  Internal: 99,
} as const;

export class EngineError extends Error {
  constructor(
    readonly code: number,
    message: string,
  ) {
    super(message);
  }
}

function toEngineError(error: unknown): EngineError {
  if (error && typeof error === "object" && "code" in error) {
    const { code, message } = error as { code: number; message?: string };
    return new EngineError(code, message ?? "");
  }
  return new EngineError(ErrorCode.Internal, String(error));
}

async function call<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  try {
    return await invoke<T>(command, args);
  } catch (error) {
    throw toEngineError(error);
  }
}

export function openDocument(path: string, password?: string): Promise<OpenedDocument> {
  return call("open_document", { path, password: password ?? null });
}

/** `scale` 1 = 1pt 를 1px 로. */
export async function renderPage(id: number, page: number, scale: number): Promise<ImageData> {
  const buffer = await call<ArrayBuffer>("render_page", { id, page, scale });
  const view = new DataView(buffer);
  const width = view.getUint32(0, true);
  const height = view.getUint32(4, true);
  const pixels = new Uint8ClampedArray(buffer, 8, width * height * 4);
  return new ImageData(pixels, width, height);
}

export function extractText(id: number, page: number): Promise<string> {
  return call("extract_text", { id, page });
}

export function outline(id: number): Promise<OutlineItem[]> {
  return call("outline", { id });
}

export function closeDocument(id: number): Promise<void> {
  return call("close_document", { id });
}

/** 문서를 `format`(hwp·hwpx 등) 바이트로 내보낸다(파일에 쓰지 않는다). */
export async function exportDocument(id: number, format: string): Promise<Uint8Array> {
  return new Uint8Array(await call<ArrayBuffer>("export_document", { id, format }));
}

/** 메모리의 문서 바이트를 새 세션으로 연다. `inheritFrom` 세션의 저장 암호를 이어받는다. */
export async function openDocumentBytes(bytes: Uint8Array, fileName: string, inheritFrom?: number): Promise<OpenedDocument> {
  const headers: Record<string, string> = { "x-papy-file-name": encodeURIComponent(fileName) };
  if (inheritFrom !== undefined) headers["x-papy-inherit"] = String(inheritFrom);
  try {
    return await invoke<OpenedDocument>("open_document_bytes", bytes, { headers });
  } catch (error) {
    throw toEngineError(error);
  }
}

/** 파일 연결(더블클릭 등)로 들어온 열기 요청을 꺼낸다. */
export function takePendingOpens(): Promise<string[]> {
  return call("take_pending_opens");
}

/** 서드파티 라이선스 고지 전문 */
export function licenseNotices(): Promise<string> {
  return call("license_notices");
}

export function engineVersion(): Promise<string> {
  return call("engine_version");
}

/** 사용자에게 보여 줄 문구. 엔진 메시지(영문, 개발자용)는 쓰지 않는다. */
export function describeError(error: unknown): string {
  const code = error instanceof EngineError ? error.code : ErrorCode.Internal;
  switch (code) {
    case ErrorCode.Io:
      return "파일을 읽을 수 없습니다. 파일이 옮겨졌거나 지워졌는지 확인해 주세요.";
    case ErrorCode.UnsupportedFormat:
      return "지원하지 않는 형식입니다. PDF, HWP, HWPX, DOCX 문서를 열 수 있습니다.";
    case ErrorCode.InvalidDocument:
      return "문서가 손상됐거나 형식이 올바르지 않아 열 수 없습니다.";
    case ErrorCode.PasswordRequired:
      return "암호가 필요하거나 암호가 맞지 않습니다.";
    case ErrorCode.NotImplemented:
      return "이 문서에서는 아직 지원하지 않는 기능입니다.";
    case ErrorCode.LimitExceeded:
      return "문서가 너무 커서 열 수 없습니다(파일 크기·쪽 수 제한).";
    default:
      return "문제가 생겨 작업을 끝내지 못했습니다. 다시 시도해 주세요.";
  }
}
