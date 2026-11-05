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
    /// HWPX — ZIP 컨테이너, 첫 항목 `mimetype` 이 `application/hwp+zip`. `mimetype` 이 없거나 압축된 HWPX 는
    /// 처음에 [`ZipContainer`](Self::ZipContainer) 로 판별되고, 열 때 HWPX 로 확정된다.
    Hwpx,
    /// HWP 5.x — OLE 복합 문서(CFB) 컨테이너. 다른 CFB 포맷(구형 DOC/XLS/PPT)과 구분하려면 스트림 확인이 더 필요하다.
    /// HWP 3.x(`HWP Document File` 로 시작)도 이 형식으로 열린다(저장하면 HWP 5.x).
    CompoundFile,
    /// HWPX 로 판별되지 않은 ZIP 컨테이너(DOCX/XLSX/PPTX 후보). 내부 항목 확인이 더 필요하다.
    ZipContainer,
    /// Word 문서(DOCX). ZIP 컨테이너를 연 뒤 내부 항목으로 확정한다.
    Docx,
    /// 구형 Word 문서(DOC, Word 97~2003). CFB 컨테이너를 연 뒤 `WordDocument` 스트림으로 확정한다.
    Doc,
}

impl DocumentFormat {
    /// 사람이 읽는 이름. 로그·오류 메시지에 써도 되는 값이다.
    pub fn name(self) -> &'static str {
        match self {
            Self::Pdf => "PDF",
            Self::Hwpx => "HWPX",
            Self::CompoundFile => "OLE compound file",
            Self::ZipContainer => "ZIP container",
            Self::Docx => "DOCX",
            Self::Doc => "DOC",
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
    /// 열 때 옮기지 못한 요소 수(예: DOCX 의 도형·지원하지 않는 그림). 0 이면 손실 없음.
    /// 앱은 0 보다 크면 "일부 내용이 빠졌다"고 알린다.
    pub dropped_objects: u32,
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
    /// 주석 달기(텍스트 상자·형광펜·펜·메모).
    pub annotate: bool,
    /// 쪽 편집(회전·삭제·순서 바꾸기).
    pub page_edit: bool,
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
    /// Word 문서(DOCX). 본문·글자/문단 서식·표만 옮긴다.
    Docx,
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
            Self::Docx => "docx",
            Self::Text => "txt",
        }
    }

    /// 확장자로 찾는다(대소문자 무시).
    pub fn from_extension(extension: &str) -> Option<Self> {
        [Self::Pdf, Self::Hwpx, Self::Hwp, Self::Docx, Self::Text]
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

/// 표 칸(또는 글상자) 안의 위치. 본문 문단 [`paragraph`](Self::paragraph) 의 [`control`](Self::control) 번째
/// 개체(표·글상자)의 [`cell`](Self::cell) 번째 칸, 그 안 [`cell_paragraph`](Self::cell_paragraph) 문단의 글자 위치.
/// 칸 안의 표(중첩)는 아직 다루지 않는다.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Default)]
pub struct CellPos {
    /// 구역.
    pub section: u32,
    /// 표를 가진 본문 문단.
    pub paragraph: u32,
    /// 문단 안 개체 번호.
    pub control: u32,
    /// 칸 번호(글상자는 0).
    pub cell: u32,
    /// 칸 안 문단.
    pub cell_paragraph: u32,
    /// 칸 문단 안 글자 위치.
    pub offset: u32,
}

/// 쪽을 누른 자리: 본문 또는 표 칸(글상자) 안.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HitTarget {
    /// 본문.
    Body(TextPos),
    /// 표 칸·글상자 안.
    Cell(CellPos),
}

/// 문서 안 개체(표·그림·도형)를 가리킨다: 본문 문단과 그 안 개체 번호.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ObjectRef {
    /// 구역.
    pub section: u32,
    /// 개체를 가진 본문 문단.
    pub paragraph: u32,
    /// 문단 안 개체 번호.
    pub control: u32,
}

/// 쪽 위 개체의 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ObjectKind {
    /// 표.
    Table,
    /// 그림.
    Picture,
}

/// 표 칸 하나의 자리.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TableCellBox {
    /// 칸 번호.
    pub cell: u32,
    /// 행·열(0부터).
    pub row: u32,
    /// 열.
    pub col: u32,
    /// 합친 행 수.
    pub row_span: u32,
    /// 합친 열 수.
    pub col_span: u32,
    /// 칸 사각형.
    pub rect: PageRect,
}

/// 쪽에 그려진 개체(표·그림): 고를 때 쓴다.
#[derive(Debug, Clone, PartialEq)]
pub struct PageObject {
    /// 종류.
    pub kind: ObjectKind,
    /// 가리키는 개체.
    pub object: ObjectRef,
    /// 쪽 위 사각형.
    pub rect: PageRect,
    /// 표의 행 수(그림은 0).
    pub rows: u32,
    /// 표의 열 수.
    pub cols: u32,
    /// 표 칸(그림은 비어 있음).
    pub cells: Vec<TableCellBox>,
}

/// 표 고치기.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TableOp {
    /// 행 넣기(`below` 면 아래에).
    InsertRow {
        /// 기준 행.
        row: u32,
        /// 아래에 넣는다.
        below: bool,
    },
    /// 열 넣기(`right` 면 오른쪽에).
    InsertColumn {
        /// 기준 열.
        col: u32,
        /// 오른쪽에 넣는다.
        right: bool,
    },
    /// 행 지우기.
    DeleteRow {
        /// 지울 행.
        row: u32,
    },
    /// 열 지우기.
    DeleteColumn {
        /// 지울 열.
        col: u32,
    },
    /// 칸 합치기(양 끝 포함).
    Merge {
        /// 처음 행.
        start_row: u32,
        /// 처음 열.
        start_col: u32,
        /// 끝 행.
        end_row: u32,
        /// 끝 열.
        end_col: u32,
    },
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

/// 글줄에 긋는 표시.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MarkupKind {
    /// 형광펜.
    Highlight,
    /// 밑줄.
    Underline,
    /// 취소선.
    StrikeOut,
}

/// 쪽에 더하는 주석. 좌표는 pt, 화면에 보이는 쪽(회전 반영)의 왼쪽 위 기준. 색은 `#rrggbb`.
#[derive(Debug, Clone, PartialEq)]
pub enum Annotation {
    /// 텍스트 상자(쪽 내용으로 들어간다). `x`,`y` 는 첫 줄의 왼쪽 위.
    TextBox {
        /// 쪽.
        page: u32,
        /// 왼쪽.
        x: f32,
        /// 위.
        y: f32,
        /// 글자(`\n` 으로 줄 나눔).
        text: String,
        /// 글자 크기(pt).
        font_size: f32,
        /// 글자 색.
        color: String,
    },
    /// 영역 안의 글줄에 형광펜·밑줄·취소선.
    Markup {
        /// 쪽.
        page: u32,
        /// 종류.
        kind: MarkupKind,
        /// 사용자가 끈 영역. 그 안의 글줄을 찾아 표시한다.
        area: PageRect,
        /// 색.
        color: String,
    },
    /// 펜으로 그린 선 하나.
    Ink {
        /// 쪽.
        page: u32,
        /// 지나간 점들.
        points: Vec<(f32, f32)>,
        /// 색.
        color: String,
        /// 굵기(pt).
        width: f32,
    },
    /// 쪽에 이미 있는 글자 고쳐 쓰기: 영역 안 글자 개체를 지우고 그 자리에 새 글자를 넣는다
    /// (덮어 가리지 않는다 — 지운 글자는 검색·복사에서도 사라진다).
    ReplaceText {
        /// 쪽.
        page: u32,
        /// 고칠 글자가 있는 영역. 가운데가 이 안에 있는 글자 개체를 바꾼다.
        area: PageRect,
        /// 새 글자(`\n` 으로 줄 나눔). 비우면 지우기만 한다.
        text: String,
        /// 글자 색.
        color: String,
        /// 글자 크기(pt). 없으면 지운 글자의 크기를 쓴다.
        font_size: Option<f32>,
    },
    /// 메모(아이콘 + 내용).
    Note {
        /// 쪽.
        page: u32,
        /// 아이콘 왼쪽.
        x: f32,
        /// 아이콘 위.
        y: f32,
        /// 내용.
        text: String,
    },
}

/// 쪽 편집.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PageOp {
    /// 시계 방향으로 90도 × `quarter_turns`(음수면 반시계).
    Rotate {
        /// 쪽.
        page: u32,
        /// 90도 단위 횟수.
        quarter_turns: i32,
    },
    /// 지운다(마지막 한 쪽은 지울 수 없다).
    Delete {
        /// 쪽.
        page: u32,
    },
    /// `from` 쪽을 `to` 자리로 옮긴다.
    Move {
        /// 옮길 쪽.
        from: u32,
        /// 갈 자리(옮긴 뒤의 번호).
        to: u32,
    },
}

/// 커서 이동 방향.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CaretMotion {
    /// 한 글자 앞(문단 처음이면 앞 문단 끝).
    Left,
    /// 한 글자 뒤(문단 끝이면 다음 문단 처음).
    Right,
    /// 지금 줄의 처음.
    LineStart,
    /// 지금 줄의 끝.
    LineEnd,
    /// 문서 처음.
    DocumentStart,
    /// 문서 끝.
    DocumentEnd,
}

/// 편집 결과: 커서가 갈 위치.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EditOutcome {
    /// 편집 뒤 커서 위치.
    pub caret: TextPos,
}

/// 쪽에 있지만 Papy 가 제대로 그리지 못한 개체의 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum UnsupportedKind {
    /// 차트(자리만 점선으로 그린다).
    Chart,
    /// OLE 등 다른 프로그램의 개체.
    EmbeddedObject,
    /// 문서에 데이터가 없거나 읽지 못한 그림.
    MissingImage,
}

/// 쪽에서 제대로 그리지 못한 개체 하나와 그 자리(pt). 앱은 "이 쪽의 개체 N개를 표시하지 못했다"고 알린다.
#[derive(Debug, Clone, PartialEq)]
pub struct UnsupportedObject {
    /// 종류.
    pub kind: UnsupportedKind,
    /// 쪽 위 자리.
    pub rect: PageRect,
}

/// 문서에서 찾은 글자 하나. 앞뒤 글자를 조금 붙여 목록에 보여 줄 수 있게 한다.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TextMatch {
    /// 쪽(0부터).
    pub page: u32,
    /// 이 쪽에서 몇 번째로 찾은 것인가(0부터). 화면에서 같은 쪽의 찾은 자리를 차례로 고를 때 쓴다.
    pub index: u32,
    /// 찾은 글자 앞 몇 글자(줄바꿈은 공백으로).
    pub before: String,
    /// 찾은 글자(문서에 있는 그대로, 대소문자 포함).
    pub matched: String,
    /// 찾은 글자 뒤 몇 글자.
    pub after: String,
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
