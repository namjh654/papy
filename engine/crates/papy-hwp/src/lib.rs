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

use papy_model::{
    Capabilities, DocumentFormat, DocumentInfo, Error, ErrorCode, Result, SaveFormat,
};
use papy_render::{BackendOpenOptions, Bitmap, Document, FormatBackend, Source};
use resvg::{tiny_skia, usvg};
use rhwp::wasm_api::HwpDocument as Rhwp;

mod document;
mod notes;
mod repair;
mod shapes;

use document::HwpDocument;
#[cfg(test)]
use document::parse_page_size;

/// HWPX 의 `mimetype` 항목 내용.
pub const HWPX_MIMETYPE: &[u8] = b"application/hwp+zip";

/// OLE 복합 문서(CFB) 시그니처. HWP 5.x 가 이 컨테이너를 쓴다.
pub const CFB_SIGNATURE: &[u8] = &[0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1];

const ZIP_LOCAL_HEADER: &[u8] = b"PK\x03\x04";
const ZIP_LOCAL_HEADER_LEN: usize = 30;

/// 앞부분 바이트가 CFB 시그니처로 시작하는지 본다.
pub fn has_cfb_signature(head: &[u8]) -> bool {
    head.starts_with(CFB_SIGNATURE)
}

/// HWP 3.x 문서의 시작 문자열.
pub const HWP3_SIGNATURE: &[u8] = b"HWP Document File";

/// 앞부분 바이트가 HWP 3.x 시그니처로 시작하는지 본다. rhwp 가 HWP 3.x 를 읽어 HWP 5.x 와 같은 문서 구조로 바꾼다.
pub fn has_hwp3_signature(head: &[u8]) -> bool {
    head.starts_with(HWP3_SIGNATURE)
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

    /// DOCX(Word) 어댑터. ZIP 컨테이너로 판별된 파일을 열어 `word/document.xml` 이 있으면 rhwp 문서로 바꿔
    /// HWP 와 같은 엔진으로 보이고 편집한다. 그림·도형 등은 옮기지 않는다.
    pub fn docx() -> Self {
        Self {
            format: DocumentFormat::ZipContainer,
        }
    }

    /// HWP 5.x(OLE 복합 문서)·HWP 3.x 어댑터. 다른 CFB 문서(구형 DOC 등)는 `UnsupportedFormat` 으로 거절한다.
    pub fn hwp() -> Self {
        Self {
            format: DocumentFormat::CompoundFile,
        }
    }

    /// 시스템에 한글 글꼴이 없을 때 쓸 글꼴 파일. 첫 렌더 전에 한 번 부른다(그 뒤에는 무시된다).
    pub fn set_fallback_font(path: impl Into<std::path::PathBuf>) {
        let _ = FALLBACK_FONT.set(path.into());
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
        if self.format == DocumentFormat::ZipContainer {
            // `mimetype` 이 첫 항목이 아니거나 압축된 HWPX(다른 프로그램이 만든 파일)는 앞부분만으로는 HWPX 로
            // 판별되지 않는다. DOCX 가 아니면 중앙 디렉터리의 HWPX 필수 항목으로 다시 본다.
            if !papy_office::is_docx(bytes)
                && rhwp::parser::detect_format(bytes) == rhwp::parser::FileFormat::Hwpx
            {
                return Self::hwpx().open_hwp(bytes, options);
            }
            return self.open_docx(bytes, options);
        }
        self.open_hwp(bytes, options)
    }
}

impl HwpBackend {
    fn open_hwp(&self, bytes: &[u8], options: &BackendOpenOptions) -> Result<Box<dyn Document>> {
        let password = options.password.as_deref();
        let mut doc = guard(|| match password {
            Some(password) => Rhwp::from_bytes_with_password(bytes, password.as_bytes()),
            None => Rhwp::from_bytes(bytes),
        })?
        .map_err(|error| map_open_error(&error.to_string()))?;
        if self.format == DocumentFormat::Hwpx {
            guard(|| repair::repair_inline_pictures(&mut doc))?;
        }
        options.cancel.check()?;

        let page_count = doc.page_count();
        if page_count > options.max_pages {
            return Err(Error::new(ErrorCode::LimitExceeded, "too many pages"));
        }
        // HWP 3.x 는 rhwp 가 HWP 5.x 구조로 바꿔 읽는데, 다시 HWP/HWPX 로 쓰면 그림·도형·양식 글상자가 빠진다
        // (문서 모음 회귀 점검, docs/formats/test-corpus.md). 원본을 덮어쓰지 않게 보기 전용으로 열고 PDF·텍스트로만 내보낸다.
        let hwp3 = has_hwp3_signature(bytes);
        let save_formats = if hwp3 {
            vec![SaveFormat::Pdf, SaveFormat::Text]
        } else {
            vec![
                SaveFormat::Hwpx,
                SaveFormat::Hwp,
                SaveFormat::Docx,
                SaveFormat::Pdf,
                SaveFormat::Text,
            ]
        };
        let info = DocumentInfo {
            format: self.format,
            page_count,
            byte_size: bytes.len() as u64,
            title: None,
            dropped_objects: 0,
            capabilities: Capabilities {
                render: true,
                extract_text: true,
                save: true,
                edit: !hwp3,
                save_formats,
                ..Capabilities::default()
            },
        };
        Ok(Box::new(HwpDocument::new(
            doc,
            info,
            options.password.clone(),
        )))
    }
}

impl HwpBackend {
    fn open_docx(&self, bytes: &[u8], options: &BackendOpenOptions) -> Result<Box<dyn Document>> {
        if !papy_office::is_docx(bytes) {
            return Err(Error::new(
                ErrorCode::UnsupportedFormat,
                "only DOCX is supported among ZIP-based office files",
            ));
        }
        let (html, report) = papy_office::docx_to_html(bytes)?;
        options.cancel.check()?;
        let (doc, dropped_notes) =
            guard(|| -> std::result::Result<(Rhwp, usize), rhwp::HwpError> {
                let mut doc = Rhwp::create_empty();
                doc.create_blank_document_native()?;
                doc.paste_html_native(0, 0, 0, &html)?;
                let dropped =
                    notes::apply(&mut doc, &report)? + shapes::apply(&mut doc, &report.shapes)?;
                Ok((doc, dropped))
            })?
            .map_err(|_| {
                Error::new(
                    ErrorCode::InvalidDocument,
                    "could not convert the DOCX document",
                )
            })?;
        let page_count = doc.page_count();
        if page_count > options.max_pages {
            return Err(Error::new(ErrorCode::LimitExceeded, "too many pages"));
        }
        let info = DocumentInfo {
            format: DocumentFormat::Docx,
            page_count,
            byte_size: bytes.len() as u64,
            title: None,
            dropped_objects: u32::try_from(
                report.dropped_drawings + report.dropped_notes + dropped_notes,
            )
            .unwrap_or(u32::MAX),
            capabilities: Capabilities {
                render: true,
                extract_text: true,
                save: true,
                edit: true,
                save_formats: vec![
                    SaveFormat::Docx,
                    SaveFormat::Hwpx,
                    SaveFormat::Hwp,
                    SaveFormat::Pdf,
                    SaveFormat::Text,
                ],
                ..Capabilities::default()
            },
        };
        Ok(Box::new(HwpDocument::new(doc, info, None)))
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
pub(crate) fn guard<T>(f: impl FnOnce() -> T) -> Result<T> {
    catch_unwind(AssertUnwindSafe(f))
        .map_err(|_| Error::new(ErrorCode::Internal, "HWP engine failed on this document"))
}

/// SVG 를 정확히 `width`×`height` 비트맵으로 그린다.
pub(crate) fn rasterize(
    svg: &str,
    width: u32,
    height: u32,
    white_background: bool,
) -> Result<Bitmap> {
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
    // tiny-skia 는 premultiplied 알파다. Bitmap 계약은 straight 알파라서 투명한 점만 되돌린다.
    // 버퍼는 새로 만들지 않고 그대로 넘긴다(흰 바탕이면 모든 점이 불투명이라 바꿀 것이 없다).
    let mut pixels = pixmap.take();
    if !white_background {
        demultiply_in_place(&mut pixels);
    }
    Ok(Bitmap {
        width,
        height,
        pixels,
    })
}

/// 글꼴 파일을 한 번만 메모리에 매핑해 두고 계속 쓴다.
///
/// fontdb 는 파일 글꼴의 데이터를 쓸 때마다 파일을 열고 매핑한다. usvg 는 글자 하나마다(윤곽선·대체 글꼴 찾기)
/// 그 데이터를 읽으므로, 그대로 두면 한 쪽 그리는 시간의 대부분이 파일 열기·매핑에 든다(측정: 쪽당 약 130 ms → 20 ms).
/// 매핑은 가상 메모리만 잡고, 실제로 읽은 쪽만 메모리에 올라온다.
fn share_font_data(fonts: &mut usvg::fontdb::Database) {
    let ids: Vec<_> = fonts.faces().map(|face| face.id).collect();
    for id in ids {
        // SAFETY: 매핑한 글꼴 파일을 다른 프로세스가 고치면 내용이 바뀔 수 있다(fontdb 문서). 시스템·앱 글꼴은
        // 실행 중에 고쳐 쓰지 않으며, 지우는 것은 매핑에 영향을 주지 않는다. 같은 파일의 다른 face 는 함께 공유된다.
        let _ = unsafe { fonts.make_shared_face_data(id) };
    }
}

/// premultiplied RGBA 를 straight RGBA 로 바꾼다. 불투명(255)·완전 투명(0) 점은 그대로 둔다.
fn demultiply_in_place(pixels: &mut [u8]) {
    for p in pixels.chunks_exact_mut(4) {
        let a = p[3];
        if a == 0 || a == 255 {
            continue;
        }
        // tiny-skia 의 demultiply 와 같은 식(f64 반올림까지 같게).
        let a = f64::from(a) / 255.0;
        for c in &mut p[..3] {
            *c = (f64::from(*c) / a + 0.5) as u8;
        }
    }
}

static FALLBACK_FONT: OnceLock<std::path::PathBuf> = OnceLock::new();
/// 화면 그리기에서 고른 기본 글꼴(sans, serif). PDF 내보내기도 같은 글꼴로 떨어지게 한다.
static FALLBACK_FAMILIES: OnceLock<(String, String)> = OnceLock::new();

/// PDF 내보내기 옵션. 앱에 넣은 한글 글꼴(모바일은 시스템에 한글 글꼴이 없을 수 있다)과
/// 화면에서 쓰는 기본 글꼴을 그대로 쓴다.
pub(crate) fn pdf_options() -> rhwp::renderer::pdf::PdfExportOptions {
    let _ = svg_options();
    let mut options = rhwp::renderer::pdf::PdfExportOptions::default();
    if let Some(dir) = FALLBACK_FONT.get().and_then(|path| path.parent()) {
        options.font_paths.push(dir.to_path_buf());
    }
    if let Some((sans, serif)) = FALLBACK_FAMILIES.get() {
        options.fallback_sans = sans.clone();
        options.fallback_mono = sans.clone();
        options.fallback_serif = serif.clone();
    }
    options
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
        if let Some(path) = FALLBACK_FONT.get() {
            // 읽지 못하면(파일 없음 등) 시스템 글꼴만 쓴다.
            let _ = fonts.load_font_file(path);
        }
        share_font_data(&mut fonts);
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
        if let Some(serif) = &serif {
            fonts.set_serif_family(serif.clone());
        }
        if let (Some(sans), Some(serif)) = (&sans, &serif) {
            let _ = FALLBACK_FAMILIES.set((sans.clone(), serif.clone()));
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
