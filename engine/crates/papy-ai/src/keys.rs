//! API 키 보관: 운영체제 키체인(macOS 키체인, Windows 자격 증명 관리자, Linux Secret Service).
//! 설정 파일·로그에는 남기지 않는다. 시험에서는 `PAPY_AI_KEYSTORE=memory` 로 메모리에만 둔다.

use std::collections::HashMap;
use std::sync::Mutex;

use crate::llm::Kind;

const SERVICE: &str = "Papy AI";

static MEMORY: Mutex<Option<HashMap<String, String>>> = Mutex::new(None);

fn memory() -> bool {
    std::env::var("PAPY_AI_KEYSTORE").is_ok_and(|v| v == "memory")
}

fn entry(kind: Kind) -> Result<keyring::Entry, String> {
    keyring::Entry::new(SERVICE, kind.id()).map_err(|e| format!("keychain unavailable: {e}"))
}

/// 키를 저장한다.
pub fn set(kind: Kind, key: &str) -> Result<(), String> {
    let key = key.trim();
    if key.is_empty() {
        return Err("the key is empty".to_owned());
    }
    if memory() {
        MEMORY
            .lock()
            .unwrap_or_else(|p| p.into_inner())
            .get_or_insert_with(HashMap::new)
            .insert(kind.id().to_owned(), key.to_owned());
        return Ok(());
    }
    entry(kind)?
        .set_password(key)
        .map_err(|e| format!("could not save the key: {e}"))
}

/// 키를 읽는다. 없으면 `None`.
pub fn get(kind: Kind) -> Option<String> {
    if memory() {
        return MEMORY
            .lock()
            .unwrap_or_else(|p| p.into_inner())
            .as_ref()
            .and_then(|m| m.get(kind.id()).cloned());
    }
    entry(kind).ok()?.get_password().ok()
}

/// 키를 지운다(없어도 성공).
pub fn delete(kind: Kind) -> Result<(), String> {
    if memory() {
        if let Some(map) = MEMORY.lock().unwrap_or_else(|p| p.into_inner()).as_mut() {
            map.remove(kind.id());
        }
        return Ok(());
    }
    match entry(kind)?.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(format!("could not delete the key: {e}")),
    }
}

/// 키가 있는가(값은 돌려주지 않는다).
pub fn has(kind: Kind) -> bool {
    get(kind).is_some()
}
