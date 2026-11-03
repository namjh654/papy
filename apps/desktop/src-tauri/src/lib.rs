//! Papy 데스크톱 호스트. Tauri command 는 `papy-core` 를 부르는 얇은 어댑터만 둔다.
//! 문서 파싱·렌더링 로직은 여기에 두지 않는다.

/// 엔진 버전을 돌려준다(엔진 연결 확인용).
#[tauri::command]
fn engine_version() -> &'static str {
    papy_core::ENGINE_VERSION
}

/// Tauri 앱을 실행한다.
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![engine_version])
        .run(tauri::generate_context!())
        .expect("error while running Papy desktop");
}
