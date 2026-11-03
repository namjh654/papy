//! Papy 데스크톱 호스트. Tauri command 는 `papy-core` 를 부르는 얇은 어댑터만 둔다.
//! 문서 파싱·렌더링 로직은 여기에 두지 않는다. 명령 본체는 [`host`] 에 있다.
//!
//! - 문서 작업은 블로킹 스레드에서 돌린다(UI 스레드를 막지 않는다).
//! - 페이지 비트맵은 JSON 이 아니라 바이너리 응답으로 보낸다: `[너비 u32 LE][높이 u32 LE][RGBA…]`.
//! - 경로·본문은 로그에 남기지 않는다.

pub mod host;

use std::path::PathBuf;
use std::sync::Arc;

use host::{EditOp, OpenedDocument, OutlineDto, PageSizeDto};
use papy_core::{Engine, Error, ErrorCode, Limits};
use serde::Serialize;
use serde_json::Value;
use tauri::{Manager, State, ipc::Response};

struct AppState {
    engine: Arc<Engine>,
}

/// 앱으로 넘기는 오류. `code` 는 `papy_core::ErrorCode` 숫자 값.
#[derive(Debug, Serialize)]
struct CommandError {
    code: u32,
    message: String,
}

impl From<Error> for CommandError {
    fn from(error: Error) -> Self {
        Self {
            code: error.code.as_u32(),
            message: error.message,
        }
    }
}

type CommandResult<T> = Result<T, CommandError>;

/// 엔진 작업을 블로킹 스레드에서 돌린다.
async fn blocking<T: Send + 'static>(
    state: &State<'_, AppState>,
    job: impl FnOnce(&Engine) -> papy_core::Result<T> + Send + 'static,
) -> CommandResult<T> {
    let engine = Arc::clone(&state.engine);
    tauri::async_runtime::spawn_blocking(move || job(&engine))
        .await
        .map_err(|_| CommandError::from(Error::new(ErrorCode::Internal, "engine task failed")))?
        .map_err(Into::into)
}

/// 엔진 버전을 돌려준다(엔진 연결 확인용).
#[tauri::command]
fn engine_version() -> &'static str {
    papy_core::ENGINE_VERSION
}

#[tauri::command]
async fn open_document(
    state: State<'_, AppState>,
    path: String,
    password: Option<String>,
) -> CommandResult<OpenedDocument> {
    blocking(&state, move |engine| {
        host::open(engine, PathBuf::from(path), password)
    })
    .await
}

#[tauri::command]
async fn render_page(
    state: State<'_, AppState>,
    id: u64,
    page: u32,
    scale: f32,
) -> CommandResult<Response> {
    let bytes = blocking(&state, move |engine| host::render(engine, id, page, scale)).await?;
    Ok(Response::new(bytes))
}

#[tauri::command]
async fn extract_text(state: State<'_, AppState>, id: u64, page: u32) -> CommandResult<String> {
    blocking(&state, move |engine| {
        engine.extract_text(host::session(id)?, page)
    })
    .await
}

#[tauri::command]
async fn outline(state: State<'_, AppState>, id: u64) -> CommandResult<Vec<OutlineDto>> {
    blocking(&state, move |engine| host::outline(engine, id)).await
}

#[tauri::command]
async fn close_document(state: State<'_, AppState>, id: u64) -> CommandResult<()> {
    blocking(&state, move |engine| host::close(engine, id)).await
}

#[tauri::command]
async fn edit(state: State<'_, AppState>, id: u64, op: EditOp) -> CommandResult<Value> {
    blocking(&state, move |engine| host::edit(engine, id, op)).await
}

#[tauri::command]
async fn page_layout(state: State<'_, AppState>, id: u64) -> CommandResult<Vec<PageSizeDto>> {
    blocking(&state, move |engine| {
        host::page_sizes(engine, host::session(id)?)
    })
    .await
}

#[tauri::command]
async fn save_document(state: State<'_, AppState>, id: u64, path: String) -> CommandResult<()> {
    blocking(&state, move |engine| {
        host::save(engine, id, PathBuf::from(path))
    })
    .await
}

/// 번들된 PDFium 위치. 개발 빌드에서 리소스 폴더에 없으면 engine/vendor 를 본다.
fn pdfium_library(app: &tauri::App) -> PathBuf {
    let name = papy_core::pdfium_library_file_name();
    let bundled = app
        .path()
        .resource_dir()
        .map(|dir| dir.join("pdfium").join(&name))
        .ok();
    match bundled {
        Some(path) if path.exists() => path,
        _ if cfg!(debug_assertions) => PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("resources/pdfium")
            .join(name),
        _ => bundled.unwrap_or(name),
    }
}

/// Tauri 앱을 실행한다.
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let engine = Engine::with_pdfium(Limits::default(), pdfium_library(app));
            app.manage(AppState {
                engine: Arc::new(engine),
            });
            // 글꼴 목록 읽기(수백 ms)를 첫 문서 전에 끝내 둔다.
            std::thread::spawn(papy_core::warm_up);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            engine_version,
            open_document,
            render_page,
            extract_text,
            outline,
            close_document,
            edit,
            page_layout,
            save_document
        ])
        .run(tauri::generate_context!())
        .expect("error while running Papy desktop");
}
