//! Papy 데스크톱 호스트. Tauri command 는 `papy-core` 를 부르는 얇은 어댑터만 둔다.
//! 문서 파싱·렌더링 로직은 여기에 두지 않는다.
//!
//! - 문서 작업은 블로킹 스레드에서 돌린다(UI 스레드를 막지 않는다).
//! - 페이지 비트맵은 JSON 이 아니라 바이너리 응답으로 보낸다: `[너비 u32 LE][높이 u32 LE][RGBA…]`.
//! - 경로·본문은 로그에 남기지 않는다.

use std::path::PathBuf;
use std::sync::Arc;

use papy_core::{
    DocumentSource, Engine, Error, ErrorCode, Limits, OpenOptions, OutlineItem, RenderRequest,
    SessionId,
};
use serde::Serialize;
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

fn internal(message: &str) -> CommandError {
    Error::new(ErrorCode::Internal, message).into()
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct PageSizeDto {
    width: f32,
    height: f32,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OpenedDocument {
    id: u64,
    file_name: String,
    format: &'static str,
    page_count: u32,
    byte_size: u64,
    title: Option<String>,
    can_extract_text: bool,
    has_outline: bool,
    /// 쪽마다 크기(pt). 앱이 스크롤 영역을 미리 잡는 데 쓴다.
    pages: Vec<PageSizeDto>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct OutlineDto {
    title: String,
    page: Option<u32>,
    children: Vec<OutlineDto>,
}

impl From<OutlineItem> for OutlineDto {
    fn from(item: OutlineItem) -> Self {
        Self {
            title: item.title,
            page: item.page,
            children: item.children.into_iter().map(Into::into).collect(),
        }
    }
}

/// 엔진 작업을 블로킹 스레드에서 돌린다.
async fn blocking<T: Send + 'static>(
    state: &State<'_, AppState>,
    job: impl FnOnce(&Engine) -> papy_core::Result<T> + Send + 'static,
) -> CommandResult<T> {
    let engine = Arc::clone(&state.engine);
    tauri::async_runtime::spawn_blocking(move || job(&engine))
        .await
        .map_err(|_| internal("engine task failed"))?
        .map_err(Into::into)
}

fn session(id: u64) -> papy_core::Result<SessionId> {
    SessionId::from_raw(id).ok_or_else(|| Error::new(ErrorCode::SessionNotFound, "invalid session"))
}

/// 엔진 버전을 돌려준다(엔진 연결 확인용).
#[tauri::command]
fn engine_version() -> &'static str {
    papy_core::ENGINE_VERSION
}

/// 파일을 연다. 암호가 필요하면 `PasswordRequired`(5)로 실패하고, 앱이 암호를 받아 다시 부른다.
#[tauri::command]
async fn open_document(
    state: State<'_, AppState>,
    path: String,
    password: Option<String>,
) -> CommandResult<OpenedDocument> {
    let path = PathBuf::from(path);
    let file_name = path
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_default();
    blocking(&state, move |engine| {
        if !path.is_file() {
            return Err(Error::new(ErrorCode::Io, "not a regular file"));
        }
        let options = OpenOptions {
            password,
            ..OpenOptions::default()
        };
        let id = engine.open(DocumentSource::Path(path), options)?;
        let describe = || -> papy_core::Result<OpenedDocument> {
            let info = engine.info(id)?;
            let mut pages = Vec::with_capacity(info.page_count as usize);
            for page in 0..info.page_count {
                let size = engine.page_size(id, page)?;
                pages.push(PageSizeDto {
                    width: size.width,
                    height: size.height,
                });
            }
            Ok(OpenedDocument {
                id: id.get(),
                file_name,
                format: info.format.name(),
                page_count: info.page_count,
                byte_size: info.byte_size,
                title: info.title,
                can_extract_text: info.capabilities.extract_text,
                has_outline: info.capabilities.outline,
                pages,
            })
        };
        describe().inspect_err(|_| {
            let _ = engine.close(id);
        })
    })
    .await
}

/// 페이지를 그린다. `scale` 1.0 = 1pt 를 1px 로. 결과는 바이너리 응답.
#[tauri::command]
async fn render_page(
    state: State<'_, AppState>,
    id: u64,
    page: u32,
    scale: f32,
) -> CommandResult<Response> {
    let bitmap = blocking(&state, move |engine| {
        engine.render_page(session(id)?, page, &RenderRequest::with_scale(scale))
    })
    .await?;
    let mut bytes = Vec::with_capacity(8 + bitmap.pixels.len());
    bytes.extend_from_slice(&bitmap.width.to_le_bytes());
    bytes.extend_from_slice(&bitmap.height.to_le_bytes());
    bytes.extend_from_slice(&bitmap.pixels);
    Ok(Response::new(bytes))
}

/// 페이지 텍스트.
#[tauri::command]
async fn extract_text(state: State<'_, AppState>, id: u64, page: u32) -> CommandResult<String> {
    blocking(&state, move |engine| {
        engine.extract_text(session(id)?, page)
    })
    .await
}

/// 목차.
#[tauri::command]
async fn outline(state: State<'_, AppState>, id: u64) -> CommandResult<Vec<OutlineDto>> {
    let items = blocking(&state, move |engine| engine.outline(session(id)?)).await?;
    Ok(items.into_iter().map(Into::into).collect())
}

/// 문서를 닫는다. 이미 닫혔으면 아무것도 하지 않는다.
#[tauri::command]
async fn close_document(state: State<'_, AppState>, id: u64) -> CommandResult<()> {
    blocking(&state, move |engine| match engine.close(session(id)?) {
        Err(error) if error.code == ErrorCode::SessionNotFound => Ok(()),
        other => other,
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
            close_document
        ])
        .run(tauri::generate_context!())
        .expect("error while running Papy desktop");
}
