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
  pages: PageSize[];
}

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
      return "지원하지 않는 형식입니다. PDF, HWP, HWPX 문서를 열 수 있습니다.";
    case ErrorCode.InvalidDocument:
      return "문서가 손상됐거나 형식이 올바르지 않아 열 수 없습니다.";
    case ErrorCode.PasswordRequired:
      return "암호가 필요하거나 암호가 맞지 않습니다.";
    case ErrorCode.LimitExceeded:
      return "문서가 너무 커서 열 수 없습니다(파일 크기·쪽 수 제한).";
    default:
      return "문제가 생겨 작업을 끝내지 못했습니다. 다시 시도해 주세요.";
  }
}
