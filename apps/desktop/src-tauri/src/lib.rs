//! Papy 데스크톱 호스트. Tauri command 는 `papy-core` 를 부르는 얇은 어댑터만 둔다.
//! 문서 파싱·렌더링 로직은 여기에 두지 않는다. 명령 본체는 [`host`] 에 있다.
//!
//! - 문서 작업은 블로킹 스레드에서 돌린다(UI 스레드를 막지 않는다).
//! - 페이지 비트맵은 JSON 이 아니라 바이너리 응답으로 보낸다: `[너비 u32 LE][높이 u32 LE][RGBA…]`.
//! - 경로·본문은 로그에 남기지 않는다.

pub mod host;

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use host::{EditOp, OpenedDocument, OutlineDto, PageSizeDto};
use papy_core::{Engine, EngineResources, Error, ErrorCode, Limits};
use serde::Serialize;
use serde_json::Value;
use tauri::{Emitter, Manager, State, ipc::Response};

struct AppState {
    engine: Arc<Engine>,
    /// 파일 연결(더블클릭·"다음으로 열기")로 들어온, 화면이 아직 열지 않은 파일 경로.
    pending_opens: Mutex<Vec<String>>,
}

/// Papy 가 여는 확장자. 파일 연결 인자에서 이것만 받는다.
const OPENABLE: [&str; 4] = ["pdf", "hwp", "hwpx", "docx"];

fn openable(path: &Path) -> bool {
    path.extension()
        .and_then(|ext| ext.to_str())
        .is_some_and(|ext| OPENABLE.iter().any(|known| ext.eq_ignore_ascii_case(known)))
        && path.is_file()
}

/// 앱이 받은 "열어 달라"는 파일을 쌓고 화면에 알린다.
fn queue_opens(app: &tauri::AppHandle, paths: Vec<PathBuf>) {
    let paths: Vec<String> = paths
        .into_iter()
        .filter(|path| openable(path))
        .map(|path| path.to_string_lossy().into_owned())
        .collect();
    if paths.is_empty() {
        return;
    }
    if let Some(state) = app.try_state::<AppState>() {
        state
            .pending_opens
            .lock()
            .unwrap_or_else(|poison| poison.into_inner())
            .extend(paths);
        let _ = app.emit("open-files", ());
    }
}

/// 쌓인 열기 요청을 꺼낸다(화면이 시작할 때와 `open-files` 이벤트를 받을 때 부른다).
#[tauri::command]
fn take_pending_opens(state: State<'_, AppState>) -> Vec<String> {
    std::mem::take(
        &mut *state
            .pending_opens
            .lock()
            .unwrap_or_else(|poison| poison.into_inner()),
    )
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

/// 번들된 리소스 파일 위치(`resources/` 아래 상대 경로). 개발 빌드에서 리소스 폴더에 없으면
/// build.rs 가 모아 둔 src-tauri/resources 를 본다.
fn resource(app: &tauri::App, relative: &Path) -> PathBuf {
    let bundled = app.path().resource_dir().map(|dir| dir.join(relative)).ok();
    match bundled {
        Some(path) if path.exists() => path,
        _ if cfg!(debug_assertions) => PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("resources")
            .join(relative),
        _ => bundled.unwrap_or_else(|| relative.to_path_buf()),
    }
}

/// Tauri 앱을 실행한다.
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let pdfium = Path::new("pdfium").join(papy_core::pdfium_library_file_name());
            let font = resource(app, Path::new("fonts/NotoSansKR-Regular.ttf"));
            let engine = Engine::with_resources(
                Limits::default(),
                EngineResources {
                    pdfium_library: resource(app, &pdfium),
                    korean_font: font.exists().then_some(font),
                },
            );
            app.manage(AppState {
                engine: Arc::new(engine),
                pending_opens: Mutex::new(Vec::new()),
            });
            // Windows·Linux: 파일을 더블클릭하면 경로가 실행 인자로 온다.
            let args: Vec<PathBuf> = std::env::args_os().skip(1).map(PathBuf::from).collect();
            queue_opens(app.handle(), args);
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
            save_document,
            take_pending_opens
        ])
        .build(tauri::generate_context!())
        .expect("error while building Papy desktop")
        .run(|_app, _event| {
            // macOS: Finder 에서 연 파일은 실행 인자가 아니라 이벤트로 온다.
            #[cfg(any(target_os = "macos", target_os = "ios"))]
            if let tauri::RunEvent::Opened { urls } = _event {
                let paths = urls
                    .into_iter()
                    .filter_map(|url| url.to_file_path().ok())
                    .collect();
                queue_opens(_app, paths);
            }
        });
}
