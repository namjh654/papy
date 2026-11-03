//! Papy HWP/HWPX 어댑터.
//!
//! 파싱·조판은 rhwp(MIT, `scripts/fetch-rhwp.mjs` 로 받음)가 하고, 이 crate 는 rhwp 가 그린 페이지 SVG 를
//! resvg 로 비트맵으로 바꿔 `papy-render` 계약에 맞춘다(ADR 0004). 같은 어댑터가 HWPX(ZIP)와
//! HWP 5.x(OLE 복합 문서)를 모두 연다 — 포맷마다 [`HwpBackend`] 를 하나씩 등록한다.
//!
//! - rhwp 오류 메시지는 밖으로 내보내지 않는다(문서 내용이 섞일 수 있다). 고정 문구로 바꾼다.
//! - rhwp 안의 패닉은 잡아서 `Internal` 오류로 바꾸고, 그 문서는 더 쓰지 않는다.
//! - SVG 안의 외부 파일 참조는 따라가지 않는다(내장 data: 이미지만 그린다).

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::{Arc, OnceLock};

use papy_model::{Capabilities, DocumentFormat, DocumentInfo, Error, ErrorCode, PageSize, Result};
use papy_render::{BackendOpenOptions, Bitmap, Document, FormatBackend, RenderRequest, Source};
use resvg::{tiny_skia, usvg};
use rhwp::document_core::DocumentCore;

/// HWPX 의 `mimetype` 항목 내용.
pub const HWPX_MIMETYPE: &[u8] = b"application/hwp+zip";

/// OLE 복합 문서(CFB) 시그니처. HWP 5.x 가 이 컨테이너를 쓴다.
pub const CFB_SIGNATURE: &[u8] = &[0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1];

const ZIP_LOCAL_HEADER: &[u8] = b"PK\x03\x04";
const ZIP_LOCAL_HEADER_LEN: usize = 30;

/// rhwp 가 페이지 좌표에 쓰는 DPI(px). 1pt = 96/72 px.
const RHWP_DPI: f32 = 96.0;

/// 앞부분 바이트가 CFB 시그니처로 시작하는지 본다.
pub fn has_cfb_signature(head: &[u8]) -> bool {
    head.starts_with(CFB_SIGNATURE)
}

/// 앞부분 바이트가 ZIP 로컬 헤더로 시작하는지 본다.
pub fn has_zip_signature(head: &[u8]) -> bool {
    head.starts_with(ZIP_LOCAL_HEADER)
}

/// ZIP 의 첫 항목이 압축 없이 저장된 `mimetype` 이고 내용이 HWPX mimetype 인지 본다.
///
/// 앞부분 바이트만 읽고 할당하지 않는다. 헤더 길이 값은 입력 범위를 넘지 않는지 확인한다.
pub fn has_hwpx_mimetype(head: &[u8]) -> bool {
    if !has_zip_signature(head) || head.len() < ZIP_LOCAL_HEADER_LEN {
        return false;
    }
    let u16_at = |i: usize| usize::from(u16::from_le_bytes([head[i], head[i + 1]]));
    let stored = u16_at(8) == 0;
    let name_len = u16_at(26);
    let extra_len = u16_at(28);
    let name_end = ZIP_LOCAL_HEADER_LEN + name_len;
    let data_start = name_end + extra_len;
    stored
        && head.get(ZIP_LOCAL_HEADER_LEN..name_end) == Some(b"mimetype".as_slice())
        && head
            .get(data_start..data_start + HWPX_MIMETYPE.len())
            .is_some_and(|data| data == HWPX_MIMETYPE)
}

/// HWP 계열 어댑터. 등록할 포맷을 정해서 만든다.
#[derive(Debug, Clone, Copy)]
pub struct HwpBackend {
    format: DocumentFormat,
}

impl HwpBackend {
    /// HWPX 어댑터.
    pub fn hwpx() -> Self {
        Self {
            format: DocumentFormat::Hwpx,
        }
    }

    /// HWP 5.x(OLE 복합 문서) 어댑터. 다른 CFB 문서(구형 DOC 등)는 `UnsupportedFormat` 으로 거절한다.
    pub fn hwp() -> Self {
        Self {
            format: DocumentFormat::CompoundFile,
        }
    }

    /// 글꼴 목록을 미리 읽어 둔다. 첫 렌더가 느려지지 않게 앱 시작 때 백그라운드에서 부르면 좋다.
    pub fn warm_up() {
        let _ = svg_options();
    }
}

impl FormatBackend for HwpBackend {
    fn format(&self) -> DocumentFormat {
        self.format
    }

    fn open(&self, source: Source<'_>, options: &BackendOpenOptions) -> Result<Box<dyn Document>> {
        options.cancel.check()?;
        let owned;
        let bytes: &[u8] = match source {
            Source::Path(path) => {
                owned = std::fs::read(path)?;
                &owned
            }
            Source::Bytes(bytes) => bytes,
        };
        let password = options.password.as_deref();
        let core = guard(|| match password {
            Some(password) => DocumentCore::from_bytes_with_password(bytes, password.as_bytes()),
            None => DocumentCore::from_bytes(bytes),
        })?
        .map_err(|error| map_open_error(&error.to_string()))?;
        options.cancel.check()?;

        let page_count = core.page_count();
        if page_count > options.max_pages {
            return Err(Error::new(ErrorCode::LimitExceeded, "too many pages"));
        }
        let info = DocumentInfo {
            format: self.format,
            page_count,
            byte_size: bytes.len() as u64,
            title: None,
            capabilities: Capabilities {
                render: true,
                extract_text: true,
                ..Capabilities::default()
            },
        };
        Ok(Box::new(HwpDocument {
            core,
            info,
            sizes: vec![None; page_count as usize],
            broken: false,
        }))
    }
}

/// rhwp 오류를 분류한다. 원문은 버린다.
fn map_open_error(message: &str) -> Error {
    if message.contains("비밀번호가 필요한") || message.contains("비밀번호가 일치하지 않")
    {
        Error::new(
            ErrorCode::PasswordRequired,
            "password required or incorrect",
        )
    } else if message.contains("UNSUPPORTED_FILE_FORMAT") {
        Error::new(ErrorCode::UnsupportedFormat, "not a HWP/HWPX document")
    } else {
        Error::new(
            ErrorCode::InvalidDocument,
            "damaged or invalid HWP/HWPX document",
        )
    }
}

/// rhwp 호출을 패닉으로부터 지킨다.
fn guard<T>(f: impl FnOnce() -> T) -> Result<T> {
    catch_unwind(AssertUnwindSafe(f))
        .map_err(|_| Error::new(ErrorCode::Internal, "HWP engine failed on this document"))
}

struct HwpDocument {
    core: DocumentCore,
    info: DocumentInfo,
    sizes: Vec<Option<PageSize>>,
    /// rhwp 가 패닉한 뒤에는 내부 상태를 믿을 수 없으니 더 부르지 않는다.
    broken: bool,
}

impl HwpDocument {
    fn call<T>(
        &mut self,
        f: impl FnOnce(&DocumentCore) -> std::result::Result<T, rhwp::HwpError>,
        what: &str,
    ) -> Result<T> {
        if self.broken {
            return Err(Error::new(
                ErrorCode::Internal,
                "document is unusable after an engine failure",
            ));
        }
        match guard(|| f(&self.core)) {
            Ok(result) => result
                .map_err(|_| Error::new(ErrorCode::InvalidDocument, format!("could not {what}"))),
            Err(error) => {
                self.broken = true;
                Err(error)
            }
        }
    }
}

impl Document for HwpDocument {
    fn info(&self) -> DocumentInfo {
        self.info.clone()
    }

    fn page_size(&mut self, page: u32) -> Result<PageSize> {
        if let Some(size) = self.sizes[page as usize] {
            return Ok(size);
        }
        let json = self.call(|core| core.get_page_info_native(page), "read page size")?;
        let size = parse_page_size(&json).ok_or_else(|| {
            Error::new(ErrorCode::Internal, "unexpected page info from HWP engine")
        })?;
        self.sizes[page as usize] = Some(size);
        Ok(size)
    }

    fn render_page(&mut self, page: u32, request: &RenderRequest) -> Result<Bitmap> {
        let (width, height) = request.pixel_size(self.page_size(page)?);
        request.cancel.check()?;
        let svg = self.call(|core| core.render_page_svg_native(page), "lay out page")?;
        request.cancel.check()?;
        guard(|| rasterize(&svg, width, height, request.white_background))?
    }

    fn extract_text(&mut self, page: u32) -> Result<String> {
        self.call(|core| core.extract_page_text_native(page), "extract text")
    }
}

/// `get_page_info_native` 의 JSON(px, 96 DPI)에서 페이지 크기를 pt 로 읽는다.
fn parse_page_size(json: &str) -> Option<PageSize> {
    let value: serde_json::Value = serde_json::from_str(json).ok()?;
    let px = |key: &str| value.get(key)?.as_f64().map(|v| v as f32);
    let (width, height) = (px("width")?, px("height")?);
    let pt = |px: f32| px * 72.0 / RHWP_DPI;
    (width > 0.0 && height > 0.0).then(|| PageSize {
        width: pt(width),
        height: pt(height),
    })
}

/// SVG 를 정확히 `width`×`height` 비트맵으로 그린다.
fn rasterize(svg: &str, width: u32, height: u32, white_background: bool) -> Result<Bitmap> {
    let tree = usvg::Tree::from_str(svg, svg_options()).map_err(|_| {
        Error::new(
            ErrorCode::Internal,
            "HWP engine produced an unreadable page",
        )
    })?;
    let mut pixmap = tiny_skia::Pixmap::new(width, height)
        .ok_or_else(|| Error::new(ErrorCode::LimitExceeded, "page bitmap too large"))?;
    if white_background {
        pixmap.fill(tiny_skia::Color::WHITE);
    }
    let size = tree.size();
    let transform = tiny_skia::Transform::from_scale(
        width as f32 / size.width(),
        height as f32 / size.height(),
    );
    resvg::render(&tree, transform, &mut pixmap.as_mut());
    // tiny-skia 는 premultiplied 알파다. Bitmap 계약은 straight 알파.
    let pixels = pixmap
        .pixels()
        .iter()
        .flat_map(|p| {
            let c = p.demultiply();
            [c.red(), c.green(), c.blue(), c.alpha()]
        })
        .collect();
    Ok(Bitmap {
        width,
        height,
        pixels,
    })
}

const KOREAN_SANS: &[&str] = &[
    "Malgun Gothic",
    "Apple SD Gothic Neo",
    "Noto Sans CJK KR",
    "Noto Sans KR",
    "NanumGothic",
];
const KOREAN_SERIF: &[&str] = &[
    "Batang",
    "AppleMyungjo",
    "Noto Serif CJK KR",
    "Noto Serif KR",
    "NanumMyeongjo",
];
const LATIN_FALLBACK: &[&str] = &[
    "Arial",
    "Helvetica",
    "DejaVu Sans",
    "Liberation Sans",
    "Noto Sans",
];

/// SVG 해석 옵션. 시스템 글꼴 목록은 처음 한 번만 읽는다(수백 ms).
fn svg_options() -> &'static usvg::Options<'static> {
    static OPTIONS: OnceLock<usvg::Options<'static>> = OnceLock::new();
    OPTIONS.get_or_init(|| {
        let mut fonts = usvg::fontdb::Database::new();
        fonts.load_system_fonts();
        // 문서가 지정한 글꼴이 없으면 generic(serif 등)으로 떨어진다. fontdb 기본값(Times New Roman 등)이
        // 설치돼 있지 않으면 글자가 통째로 빠지므로, 한글 글꼴 → 흔한 라틴 글꼴 → 아무 글꼴 순으로 반드시 채운다.
        let has = |fonts: &usvg::fontdb::Database, name: &str| {
            fonts
                .faces()
                .any(|face| face.families.iter().any(|(family, _)| family == name))
        };
        let pick = |fonts: &usvg::fontdb::Database, names: &[&str]| {
            names
                .iter()
                .find(|name| has(fonts, name))
                .map(|name| name.to_string())
        };
        let any_font = fonts
            .faces()
            .next()
            .and_then(|face| face.families.first())
            .map(|(family, _)| family.clone());
        let sans = pick(&fonts, KOREAN_SANS)
            .or_else(|| pick(&fonts, LATIN_FALLBACK))
            .or(any_font);
        let serif = pick(&fonts, KOREAN_SERIF).or_else(|| sans.clone());
        if let Some(sans) = &sans {
            fonts.set_sans_serif_family(sans.clone());
            fonts.set_monospace_family(sans.clone());
            fonts.set_cursive_family(sans.clone());
            fonts.set_fantasy_family(sans.clone());
        }
        if let Some(serif) = serif {
            fonts.set_serif_family(serif);
        }
        let mut options = usvg::Options {
            fontdb: Arc::new(fonts),
            ..usvg::Options::default()
        };
        if let Some(sans) = sans {
            options.font_family = sans;
        }
        // 파일 경로 참조는 따라가지 않는다. data: 이미지는 기본 처리.
        options.image_href_resolver.resolve_string = Box::new(|_, _| None);
        options
    })
}

#[cfg(test)]
mod tests;
