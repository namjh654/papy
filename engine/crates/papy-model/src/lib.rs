//! Papy 공통 문서 모델.
//!
//! 포맷과 무관한 문서 정보·페이지·목차 타입과 엔진 오류 모델을 둔다. 렌더링 결과물(화면에 그릴 데이터)은
//! 이 crate 가 아니라 `papy-render` 에 둔다 — 편집 가능한 구조와 그린 결과를 같은 타입으로 다루지 않는다.
//! 편집 명령(InsertText 등)은 편집 단계(기획서 7장)에서 추가한다.

use std::fmt;

/// 파일 시그니처로 판별한 문서 포맷.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum DocumentFormat {
    /// PDF (`%PDF-` 시그니처).
    Pdf,
    /// HWPX — ZIP 컨테이너, 첫 항목 `mimetype` 이 `application/hwp+zip`.
    Hwpx,
    /// HWP 5.x — OLE 복합 문서(CFB) 컨테이너. 다른 CFB 포맷(구형 DOC/XLS/PPT)과 구분하려면 스트림 확인이 더 필요하다.
    CompoundFile,
    /// HWPX 로 판별되지 않은 ZIP 컨테이너(DOCX/XLSX/PPTX 후보). 내부 항목 확인이 더 필요하다.
    ZipContainer,
}

impl DocumentFormat {
    /// 사람이 읽는 이름. 로그·오류 메시지에 써도 되는 값이다.
    pub fn name(self) -> &'static str {
        match self {
            Self::Pdf => "PDF",
            Self::Hwpx => "HWPX",
            Self::CompoundFile => "OLE compound file",
            Self::ZipContainer => "ZIP container",
        }
    }
}

/// 페이지 크기(PostScript 포인트, 1/72 인치).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct PageSize {
    /// 너비(pt).
    pub width: f32,
    /// 높이(pt).
    pub height: f32,
}

/// 열린 문서의 요약 정보. 본문 내용은 넣지 않는다.
#[derive(Debug, Clone, PartialEq)]
pub struct DocumentInfo {
    /// 포맷.
    pub format: DocumentFormat,
    /// 페이지 수.
    pub page_count: u32,
    /// 원본 크기(바이트). 메모리에서 연 경우 버퍼 크기.
    pub byte_size: u64,
    /// 문서 메타데이터의 제목(있으면).
    pub title: Option<String>,
    /// 이 문서에서 쓸 수 있는 기능.
    pub capabilities: Capabilities,
}

/// 포맷 어댑터가 지원하는 기능. 앱은 이 값으로 메뉴를 켜고 끈다.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Capabilities {
    /// 페이지 렌더링.
    pub render: bool,
    /// 텍스트 추출.
    pub extract_text: bool,
    /// 목차.
    pub outline: bool,
    /// 저장.
    pub save: bool,
    /// 본문 편집(커서·입력·서식·되돌리기).
    pub edit: bool,
    /// 저장·변환할 수 있는 형식.
    pub save_formats: Vec<SaveFormat>,
}

/// 저장·변환 형식.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum SaveFormat {
    /// PDF.
    Pdf,
    /// HWPX.
    Hwpx,
    /// HWP 5.x.
    Hwp,
    /// 일반 텍스트(UTF-8). 서식은 버린다.
    Text,
}

impl SaveFormat {
    /// 기본 확장자(점 없이).
    pub fn extension(self) -> &'static str {
        match self {
            Self::Pdf => "pdf",
            Self::Hwpx => "hwpx",
            Self::Hwp => "hwp",
            Self::Text => "txt",
        }
    }

    /// 확장자로 찾는다(대소문자 무시).
    pub fn from_extension(extension: &str) -> Option<Self> {
        [Self::Pdf, Self::Hwpx, Self::Hwp, Self::Text]
            .into_iter()
            .find(|format| format.extension().eq_ignore_ascii_case(extension))
    }
}

/// 본문 안의 위치. 구역·문단은 0부터, `offset` 은 문단 안 글자(유니코드 스칼라) 위치.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Default)]
pub struct TextPos {
    /// 구역.
    pub section: u32,
    /// 문단.
    pub paragraph: u32,
    /// 문단 안 글자 위치.
    pub offset: u32,
}

/// 쪽 위 사각형(pt, 쪽 왼쪽 위 기준).
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct PageRect {
    /// 쪽(0부터).
    pub page: u32,
    /// 왼쪽.
    pub x: f32,
    /// 위.
    pub y: f32,
    /// 너비.
    pub width: f32,
    /// 높이.
    pub height: f32,
}

/// 문단 정렬.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Alignment {
    /// 왼쪽.
    Left,
    /// 가운데.
    Center,
    /// 오른쪽.
    Right,
    /// 양쪽.
    Justify,
}

/// 글자 서식. 읽을 때는 모든 값이 채워지고, 적용할 때는 `Some` 인 값만 바꾼다.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct CharFormat {
    /// 글꼴 이름.
    pub font_family: Option<String>,
    /// 글자 크기(pt).
    pub font_size: Option<f32>,
    /// 굵게.
    pub bold: Option<bool>,
    /// 기울임.
    pub italic: Option<bool>,
    /// 밑줄.
    pub underline: Option<bool>,
    /// 취소선.
    pub strikethrough: Option<bool>,
    /// 글자 색(`#rrggbb`).
    pub color: Option<String>,
}

/// 문단 서식. 읽을 때는 모든 값이 채워지고, 적용할 때는 `Some` 인 값만 바꾼다.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct ParaFormat {
    /// 정렬.
    pub alignment: Option<Alignment>,
    /// 줄 간격(%).
    pub line_spacing: Option<f32>,
}

/// 편집 결과: 커서가 갈 위치.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EditOutcome {
    /// 편집 뒤 커서 위치.
    pub caret: TextPos,
}

/// 목차 항목.
#[derive(Debug, Clone, PartialEq)]
pub struct OutlineItem {
    /// 제목.
    pub title: String,
    /// 이동할 페이지(0부터). 대상이 없으면 `None`.
    pub page: Option<u32>,
    /// 하위 항목.
    pub children: Vec<OutlineItem>,
}

/// 엔진 경계의 오류 코드. 값은 C ABI·앱에서 그대로 쓰므로 **바꾸지 않고 추가만 한다.**
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[repr(u32)]
#[non_exhaustive]
pub enum ErrorCode {
    /// 호출 인자가 잘못됨(배율 범위 등).
    InvalidArgument = 1,
    /// 파일을 읽지 못함.
    Io = 2,
    /// 지원하지 않는 포맷이거나 해당 포맷 엔진이 아직 없음.
    UnsupportedFormat = 3,
    /// 손상됐거나 규격에 맞지 않는 문서.
    InvalidDocument = 4,
    /// 암호가 필요하거나 암호가 틀림.
    PasswordRequired = 5,
    /// 파일 크기·페이지 수·픽셀 수 등 안전 상한 초과.
    LimitExceeded = 6,
    /// 없는 페이지.
    PageOutOfRange = 7,
    /// 호출자가 작업을 취소함.
    Cancelled = 8,
    /// 이 포맷에서 아직 지원하지 않는 기능.
    NotImplemented = 9,
    /// 닫혔거나 없는 세션 핸들.
    SessionNotFound = 10,
    /// 엔진 내부 오류(버그).
    Internal = 99,
}

impl ErrorCode {
    /// C ABI 등에서 쓰는 숫자 값.
    pub fn as_u32(self) -> u32 {
        self as u32
    }
}

/// 엔진 오류. `message` 에는 **문서 본문·추출 텍스트·파일 경로를 넣지 않는다**(로그로 흘러갈 수 있다).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Error {
    /// 분류 코드.
    pub code: ErrorCode,
    /// 개발자용 짧은 설명.
    pub message: String,
}

impl Error {
    /// 오류를 만든다.
    pub fn new(code: ErrorCode, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
        }
    }
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{:?}: {}", self.code, self.message)
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    /// 경로가 들어갈 수 있는 원문 메시지는 버리고 종류만 남긴다.
    fn from(error: std::io::Error) -> Self {
        Self::new(ErrorCode::Io, format!("io error: {:?}", error.kind()))
    }
}

/// 엔진 결과 타입.
pub type Result<T> = std::result::Result<T, Error>;
