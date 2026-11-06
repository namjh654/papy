//! Papy 데스크톱 호스트. Tauri command 는 `papy-core` 를 부르는 얇은 어댑터만 둔다.
//! 문서 파싱·렌더링 로직은 여기에 두지 않는다. 명령 본체는 [`host`] 에 있다.
//!
//! - 문서 작업은 블로킹 스레드에서 돌린다(UI 스레드를 막지 않는다).
//! - 페이지 비트맵은 JSON 이 아니라 바이너리 응답으로 보낸다: `[너비 u32 LE][높이 u32 LE][RGBA…]`.
//! - 경로·본문은 로그에 남기지 않는다.

pub mod host;

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use host::{EditOp, OpenedDocument, OutlineDto, PageSizeDto, TextMatchDto, UnsupportedDto};
use papy_core::{CancelToken, Engine, EngineResources, Error, ErrorCode, Limits};
use serde::Serialize;
use serde_json::Value;
use tauri::{Emitter, Manager, State, ipc::Response};

struct AppState {
    engine: Arc<Engine>,
    /// 파일 연결(더블클릭·"다음으로 열기")로 들어온, 화면이 아직 열지 않은 파일 경로.
    pending_opens: Mutex<Vec<String>>,
    /// 번들된 라이선스 고지 폴더(`resources/licenses`).
    licenses: PathBuf,
    /// 진행 중인 찾기. 새 찾기가 오면 앞 것을 멈춘다(글자를 칠 때마다 찾는다).
    search: Mutex<CancelToken>,
}

/// Papy 가 여는 확장자. 파일 연결 인자에서 이것만 받는다.
const OPENABLE: [&str; 5] = ["pdf", "hwp", "hwpx", "docx", "doc"];

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

/// 서드파티 라이선스 고지 전문(`licenses/THIRD_PARTY_NOTICES.txt`, scripts/third-party-notices.mjs 가 만든다).
#[tauri::command]
async fn license_notices(state: State<'_, AppState>) -> CommandResult<String> {
    let path = state.licenses.join("THIRD_PARTY_NOTICES.txt");
    tauri::async_runtime::spawn_blocking(move || std::fs::read_to_string(path))
        .await
        .map_err(|_| Error::new(papy_core::ErrorCode::Internal, "notice reader stopped"))?
        .map_err(|error| Error::from(error).into())
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
async fn find_text(
    state: State<'_, AppState>,
    id: u64,
    query: String,
) -> CommandResult<Vec<TextMatchDto>> {
    let cancel = CancelToken::new();
    let previous = std::mem::replace(
        &mut *state
            .search
            .lock()
            .unwrap_or_else(|poison| poison.into_inner()),
        cancel.clone(),
    );
    previous.cancel();
    blocking(&state, move |engine| {
        host::find_text(engine, id, &query, &cancel)
    })
    .await
}

#[tauri::command]
async fn unsupported_objects(
    state: State<'_, AppState>,
    id: u64,
    page: u32,
) -> CommandResult<UnsupportedDto> {
    blocking(&state, move |engine| host::unsupported(engine, id, page)).await
}

#[tauri::command]
async fn outline(state: State<'_, AppState>, id: u64) -> CommandResult<Vec<OutlineDto>> {
    blocking(&state, move |engine| host::outline(engine, id)).await
}

#[tauri::command]
async fn review(state: State<'_, AppState>, id: u64) -> CommandResult<host::ReviewDto> {
    blocking(&state, move |engine| host::review(engine, id)).await
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

/// 문서를 `format`(hwp·hwpx 등 확장자) 바이트로 내보낸다. HWP 고급 편집기에 넘길 때 쓴다.
#[tauri::command]
async fn export_document(
    state: State<'_, AppState>,
    id: u64,
    format: String,
) -> CommandResult<Response> {
    let bytes = blocking(&state, move |engine| host::export(engine, id, &format)).await?;
    Ok(Response::new(bytes))
}

/// 고급 편집기가 돌려준 문서 바이트(요청 본문)를 새 세션으로 연다. 파일 이름은 `x-papy-file-name`
/// 헤더(URI 인코딩), 저장 암호를 이어받을 원래 세션은 `x-papy-inherit` 헤더로 받는다. 원래 세션은 화면이 닫는다.
#[tauri::command]
async fn open_document_bytes(
    state: State<'_, AppState>,
    request: tauri::ipc::Request<'_>,
) -> CommandResult<OpenedDocument> {
    let bytes = match request.body() {
        tauri::ipc::InvokeBody::Raw(bytes) => bytes.clone(),
        // IPC 가 JSON 으로 떨어진 경우(숫자 배열)도 받는다.
        tauri::ipc::InvokeBody::Json(serde_json::Value::Array(items)) => items
            .iter()
            .map(|item| item.as_u64().and_then(|n| u8::try_from(n).ok()))
            .collect::<Option<Vec<u8>>>()
            .ok_or_else(|| Error::new(papy_core::ErrorCode::InvalidArgument, "expected bytes"))?,
        _ => {
            return Err(Error::new(papy_core::ErrorCode::InvalidArgument, "expected bytes").into());
        }
    };
    let file_name = request
        .headers()
        .get("x-papy-file-name")
        .and_then(|value| value.to_str().ok())
        .and_then(percent_decode)
        .unwrap_or_else(|| "document.hwp".to_owned());
    let inherit = request
        .headers()
        .get("x-papy-inherit")
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.parse::<u64>().ok());
    blocking(&state, move |engine| {
        host::open_bytes(engine, bytes, file_name, inherit)
    })
    .await
}

/// `encodeURIComponent` 결과를 되돌린다.
fn percent_decode(value: &str) -> Option<String> {
    let mut out = Vec::with_capacity(value.len());
    let bytes = value.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' {
            let hex = std::str::from_utf8(bytes.get(i + 1..i + 3)?).ok()?;
            out.push(u8::from_str_radix(hex, 16).ok()?);
            i += 3;
        } else {
            out.push(bytes[i]);
            i += 1;
        }
    }
    String::from_utf8(out).ok()
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
        // 가장 먼저 등록해야 한다: 두 번째 실행은 여기서 첫 창에 인자를 넘기고 끝난다.
        .plugin(tauri_plugin_single_instance::init(|app, args, cwd| {
            let cwd = PathBuf::from(cwd);
            let paths = args
                .into_iter()
                .skip(1)
                .map(PathBuf::from)
                .map(|path| {
                    if path.is_absolute() {
                        path
                    } else {
                        cwd.join(path)
                    }
                })
                .collect();
            queue_opens(app, paths);
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.set_focus();
            }
        }))
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
                licenses: resource(app, Path::new("licenses")),
                search: Mutex::new(CancelToken::new()),
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
            review,
            find_text,
            unsupported_objects,
            close_document,
            edit,
            page_layout,
            save_document,
            export_document,
            open_document_bytes,
            take_pending_opens,
            license_notices
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

#[cfg(test)]
mod tests {
    use super::percent_decode;

    #[test]
    fn percent_decode_round_trips_encode_uri_component() {
        // encodeURIComponent("보고서 1.hwp")
        assert_eq!(
            percent_decode("%EB%B3%B4%EA%B3%A0%EC%84%9C%201.hwp").as_deref(),
            Some("보고서 1.hwp")
        );
        assert_eq!(percent_decode("plain.hwpx").as_deref(), Some("plain.hwpx"));
        assert_eq!(percent_decode("%E"), None);
        assert_eq!(percent_decode("%ZZ"), None);
        assert_eq!(percent_decode("%FF"), None);
    }
}
