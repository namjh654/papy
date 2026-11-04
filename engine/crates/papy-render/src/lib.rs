//! Papy 렌더링 추상화와 포맷 어댑터 계약.
//!
//! - 렌더 요청·결과(`RenderRequest`, `Bitmap`)와 취소 토큰(`CancelToken`)
//! - 포맷 어댑터가 구현하는 계약(`FormatBackend`, `Document`) — `papy-pdf`, `papy-hwp` 등이 구현하고
//!   `papy-core` 가 세션으로 관리한다.
//!
//! 페이지 캐시·작업 큐는 첫 실제 어댑터(PDF PoC)에서 측정한 뒤 이 crate 에 추가한다.

use std::path::Path;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use papy_model::{
    Annotation, CaretMotion, CellPos, CharFormat, DocumentFormat, DocumentInfo, EditOutcome, Error,
    ErrorCode, HitTarget, ObjectRef, OutlineItem, PageObject, PageOp, PageRect, PageSize,
    ParaFormat, Result, SaveFormat, TableOp, TextPos,
};

/// 렌더링 결과. RGBA 8비트, 알파는 premultiplied 가 아님, 행 사이 여백 없음(`stride == width * 4`).
#[derive(Clone, PartialEq, Eq)]
pub struct Bitmap {
    /// 너비(px).
    pub width: u32,
    /// 높이(px).
    pub height: u32,
    /// 픽셀 데이터, 길이 `width * height * 4`.
    pub pixels: Vec<u8>,
}

impl Bitmap {
    /// 한 행의 바이트 수.
    pub fn stride(&self) -> usize {
        self.width as usize * 4
    }
}

impl std::fmt::Debug for Bitmap {
    // 픽셀 내용은 찍지 않는다.
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Bitmap")
            .field("width", &self.width)
            .field("height", &self.height)
            .finish_non_exhaustive()
    }
}

/// 작업 취소 신호. 복제본끼리 같은 상태를 공유한다. 어댑터는 긴 작업 중간중간 확인한다.
#[derive(Debug, Clone, Default)]
pub struct CancelToken(Arc<AtomicBool>);

impl CancelToken {
    /// 새 토큰.
    pub fn new() -> Self {
        Self::default()
    }

    /// 취소를 요청한다.
    pub fn cancel(&self) {
        self.0.store(true, Ordering::Relaxed);
    }

    /// 취소됐는지 본다.
    pub fn is_cancelled(&self) -> bool {
        self.0.load(Ordering::Relaxed)
    }

    /// 취소됐으면 `Cancelled` 오류를 돌려준다.
    pub fn check(&self) -> Result<()> {
        if self.is_cancelled() {
            Err(Error::new(ErrorCode::Cancelled, "cancelled"))
        } else {
            Ok(())
        }
    }
}

/// 페이지 렌더 요청.
#[derive(Debug, Clone)]
pub struct RenderRequest {
    /// 배율. 1.0 = 1pt 를 1px 로(72 DPI). 화면 DPI 보정은 호출자가 곱한다.
    pub scale: f32,
    /// 배경을 흰색으로 채울지(아니면 투명).
    pub white_background: bool,
    /// 취소 신호.
    pub cancel: CancelToken,
}

impl RenderRequest {
    /// 배율만 정한 기본 요청(흰 배경).
    pub fn with_scale(scale: f32) -> Self {
        Self {
            scale,
            white_background: true,
            cancel: CancelToken::new(),
        }
    }

    /// 페이지 크기와 배율로 결과 픽셀 크기를 계산한다(올림, 최소 1px).
    pub fn pixel_size(&self, page: PageSize) -> (u32, u32) {
        let px = |pt: f32| ((pt * self.scale).ceil().max(1.0)).min(u32::MAX as f32) as u32;
        (px(page.width), px(page.height))
    }
}

/// 어댑터가 받는 문서 원본.
#[derive(Debug, Clone, Copy)]
pub enum Source<'a> {
    /// 파일 경로. 엔진이 크기 상한을 이미 확인했다.
    Path(&'a Path),
    /// 메모리 버퍼.
    Bytes(&'a Arc<[u8]>),
}

/// 어댑터가 받는 열기 옵션.
#[derive(Debug, Clone, Default)]
pub struct BackendOpenOptions {
    /// 문서 암호.
    pub password: Option<String>,
    /// 이 문서에서 허용하는 최대 페이지 수.
    pub max_pages: u32,
    /// 취소 신호.
    pub cancel: CancelToken,
}

/// 한 포맷을 여는 어댑터. 엔진에 등록되며 여러 스레드에서 공유된다.
pub trait FormatBackend: Send + Sync {
    /// 이 어댑터가 여는 포맷.
    fn format(&self) -> DocumentFormat;

    /// 문서를 연다.
    fn open(&self, source: Source<'_>, options: &BackendOpenOptions) -> Result<Box<dyn Document>>;
}

/// 열린 문서 하나. 엔진이 세션마다 잠금으로 감싸 한 번에 한 스레드만 호출한다.
///
/// 페이지 번호는 0부터, 범위 확인은 엔진이 먼저 한다. 지원하지 않는 기능은 `NotImplemented` 를 돌려준다.
pub trait Document: Send {
    /// 요약 정보.
    fn info(&self) -> DocumentInfo;

    /// 페이지 크기.
    fn page_size(&mut self, page: u32) -> Result<PageSize>;

    /// 페이지를 비트맵으로 그린다.
    fn render_page(&mut self, page: u32, request: &RenderRequest) -> Result<Bitmap>;

    /// 페이지 텍스트를 추출한다.
    fn extract_text(&mut self, _page: u32) -> Result<String> {
        Err(Error::new(
            ErrorCode::NotImplemented,
            "text extraction is not supported for this format",
        ))
    }

    /// 목차.
    fn outline(&mut self) -> Result<Vec<OutlineItem>> {
        Ok(Vec::new())
    }

    /// 지금 상태를 `format` 으로 직렬화한다. 지원 형식은 `info().capabilities.save_formats`.
    fn save(&mut self, _format: SaveFormat) -> Result<Vec<u8>> {
        Err(not_supported("saving"))
    }

    /// 암호를 걸지 않고 직렬화한다(다른 편집기에 잠시 넘길 때). 암호가 없는 문서는 `save` 와 같다.
    /// 결과는 파일로 쓰지 않는다 — 다시 열어 [`Document::set_save_password`] 로 암호를 되돌린다.
    fn save_unprotected(&mut self, format: SaveFormat) -> Result<Vec<u8>> {
        self.save(format)
    }

    /// 저장할 때 걸 암호(열 때 쓴 암호). 앱 화면으로 보내지 않는다 — 세션 사이에 옮길 때만 쓴다.
    fn save_password(&self) -> Option<String> {
        None
    }

    /// 저장할 때 걸 암호를 바꾼다. `None` 이면 암호 없이 저장한다.
    fn set_save_password(&mut self, _password: Option<String>) -> Result<()> {
        Err(not_supported("document passwords"))
    }

    // ---- 본문 편집. `info().capabilities.edit` 가 참일 때만 부른다. 좌표는 pt(쪽 왼쪽 위 기준). ----

    /// 쪽 위 점에 가장 가까운 본문 위치.
    fn hit_test(&mut self, _page: u32, _x: f32, _y: f32) -> Result<TextPos> {
        Err(not_supported("editing"))
    }

    /// 위치의 커서 사각형(너비 0).
    fn caret_rect(&mut self, _pos: TextPos) -> Result<PageRect> {
        Err(not_supported("editing"))
    }

    /// 한 줄 위(`lines` < 0)나 아래로 옮긴 위치. `preferred_x` 는 줄을 옮겨도 유지할 가로 위치(pt).
    fn move_vertical(&mut self, _pos: TextPos, _lines: i32, _preferred_x: f32) -> Result<TextPos> {
        Err(not_supported("editing"))
    }

    /// 커서를 옮긴 위치.
    fn move_caret(&mut self, _pos: TextPos, _motion: CaretMotion) -> Result<TextPos> {
        Err(not_supported("editing"))
    }

    /// 선택 영역(`start` ≤ `end`)을 줄마다 덮는 사각형.
    fn selection_rects(&mut self, _start: TextPos, _end: TextPos) -> Result<Vec<PageRect>> {
        Err(not_supported("editing"))
    }

    /// 선택 영역의 텍스트(문단 사이는 `\n`).
    fn text_in_range(&mut self, _start: TextPos, _end: TextPos) -> Result<String> {
        Err(not_supported("editing"))
    }

    /// 글자를 넣는다. `\n` 은 문단 나누기.
    fn insert_text(&mut self, _pos: TextPos, _text: &str) -> Result<EditOutcome> {
        Err(not_supported("editing"))
    }

    /// 범위(`start` ≤ `end`)를 지운다. 문단 경계를 넘으면 문단을 합친다.
    fn delete_range(&mut self, _start: TextPos, _end: TextPos) -> Result<EditOutcome> {
        Err(not_supported("editing"))
    }

    /// 커서 앞 글자를 지운다(문단 처음이면 앞 문단과 합친다).
    fn delete_backward(&mut self, _pos: TextPos) -> Result<EditOutcome> {
        Err(not_supported("editing"))
    }

    /// 커서 뒤 글자를 지운다(문단 끝이면 뒤 문단을 합친다).
    fn delete_forward(&mut self, _pos: TextPos) -> Result<EditOutcome> {
        Err(not_supported("editing"))
    }

    /// 위치의 글자 서식.
    fn char_format(&mut self, _pos: TextPos) -> Result<CharFormat> {
        Err(not_supported("editing"))
    }

    /// 범위에 글자 서식을 적용한다(`Some` 인 값만).
    fn apply_char_format(
        &mut self,
        _start: TextPos,
        _end: TextPos,
        _format: &CharFormat,
    ) -> Result<()> {
        Err(not_supported("editing"))
    }

    /// 위치가 속한 문단의 서식.
    fn para_format(&mut self, _pos: TextPos) -> Result<ParaFormat> {
        Err(not_supported("editing"))
    }

    /// 범위에 걸친 문단들에 문단 서식을 적용한다.
    fn apply_para_format(
        &mut self,
        _start: TextPos,
        _end: TextPos,
        _format: &ParaFormat,
    ) -> Result<()> {
        Err(not_supported("editing"))
    }

    /// 주석을 더한다(`capabilities.annotate`).
    fn annotate(&mut self, _annotation: &Annotation) -> Result<()> {
        Err(not_supported("annotations"))
    }

    /// 영역 안 글줄의 사각형(형광펜 미리 보기용).
    fn text_rects(&mut self, _page: u32, _area: PageRect) -> Result<Vec<PageRect>> {
        Err(not_supported("annotations"))
    }

    /// 영역 안의 글자(PDF 글자 고치기 입력란을 채울 때).
    fn text_in_area(&mut self, _page: u32, _area: PageRect) -> Result<String> {
        Err(not_supported("annotations"))
    }

    /// 쪽을 회전·삭제·이동한다(`capabilities.page_edit`).
    fn edit_pages(&mut self, _op: PageOp) -> Result<()> {
        Err(not_supported("page editing"))
    }

    /// 마지막 편집을 되돌린다. 되돌릴 것이 없으면 `None`.
    fn undo(&mut self) -> Result<Option<EditOutcome>> {
        Err(not_supported("editing"))
    }

    /// 되돌린 편집을 다시 한다. 없으면 `None`.
    fn redo(&mut self) -> Result<Option<EditOutcome>> {
        Err(not_supported("editing"))
    }

    /// 지금 되돌릴 수 있는 단계 수. 이어 친 글자는 한 단계로 묶이므로, 호스트가 "한 동작 = 몇 단계"를
    /// 셀 때 편집 전후의 값을 비교한다.
    fn undo_depth(&self) -> usize {
        0
    }

    // ---- 표 칸·글상자 안 편집, 표·그림 고치기. `capabilities.edit` 가 참일 때만 부른다. ----

    /// 쪽 위 점에 가장 가까운 위치(표 칸·글상자 안이면 칸 위치). 기본은 본문만 본다.
    fn hit_test_target(&mut self, page: u32, x: f32, y: f32) -> Result<HitTarget> {
        self.hit_test(page, x, y).map(HitTarget::Body)
    }

    /// 칸 안 커서 사각형.
    fn cell_caret_rect(&mut self, _pos: CellPos) -> Result<PageRect> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안에서 한 글자 앞·뒤로(칸 안 문단 사이만 오간다). `Left`·`Right` 만.
    fn cell_move_caret(&mut self, _pos: CellPos, _motion: CaretMotion) -> Result<CellPos> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안에 글자를 넣는다(`\n` 은 칸 안 문단 나누기). 커서 위치를 돌려준다.
    fn cell_insert_text(&mut self, _pos: CellPos, _text: &str) -> Result<CellPos> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안에서 커서 앞 글자를 지운다(칸 문단 처음이면 앞 칸 문단과 합친다).
    fn cell_delete_backward(&mut self, _pos: CellPos) -> Result<CellPos> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안 문단의 글자 수.
    fn cell_paragraph_len(&mut self, _pos: CellPos) -> Result<u32> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안 범위를 줄마다 덮는 사각형. 두 끝은 같은 칸이고 `start` ≤ `end`.
    fn cell_selection_rects(&mut self, _start: CellPos, _end: CellPos) -> Result<Vec<PageRect>> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안 범위의 글자(칸 문단 사이는 `\n`).
    fn cell_text_in_range(&mut self, _start: CellPos, _end: CellPos) -> Result<String> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안 범위를 지운다(칸 문단을 넘으면 합친다). 커서(`start`)를 돌려준다.
    fn cell_delete_range(&mut self, _start: CellPos, _end: CellPos) -> Result<CellPos> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안 위치의 글자 모양(커서 앞 글자).
    fn cell_char_format(&mut self, _pos: CellPos) -> Result<CharFormat> {
        Err(not_supported("cell editing"))
    }

    /// 칸 안 범위에 글자 모양을 적용한다(값이 있는 것만).
    fn cell_apply_char_format(
        &mut self,
        _start: CellPos,
        _end: CellPos,
        _format: &CharFormat,
    ) -> Result<()> {
        Err(not_supported("cell editing"))
    }

    /// 쪽에 그려진 표·그림.
    fn page_objects(&mut self, _page: u32) -> Result<Vec<PageObject>> {
        Ok(Vec::new())
    }

    /// 본문 위치에 `rows`×`cols` 표를 넣는다.
    fn insert_table(&mut self, _pos: TextPos, _rows: u32, _cols: u32) -> Result<()> {
        Err(not_supported("tables"))
    }

    /// 표를 고친다.
    fn edit_table(&mut self, _table: ObjectRef, _op: TableOp) -> Result<()> {
        Err(not_supported("tables"))
    }

    /// 본문 위치에 그림(PNG·JPEG·GIF·BMP)을 글자처럼 넣는다. 크기는 pt.
    fn insert_picture(
        &mut self,
        _pos: TextPos,
        _image: &[u8],
        _width: f32,
        _height: f32,
    ) -> Result<()> {
        Err(not_supported("pictures"))
    }

    /// 그림 크기를 바꾼다(pt).
    fn resize_picture(&mut self, _picture: ObjectRef, _width: f32, _height: f32) -> Result<()> {
        Err(not_supported("pictures"))
    }

    /// 표·그림을 지운다.
    fn delete_object(&mut self, _object: ObjectRef, _kind: papy_model::ObjectKind) -> Result<()> {
        Err(not_supported("objects"))
    }
}

fn not_supported(what: &str) -> Error {
    Error::new(
        ErrorCode::NotImplemented,
        format!("{what} is not supported for this format"),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pixel_size_rounds_up_and_never_zero() {
        let request = RenderRequest::with_scale(2.0);
        assert_eq!(
            request.pixel_size(PageSize {
                width: 595.3,
                height: 841.9
            }),
            (1191, 1684)
        );
        let tiny = RenderRequest::with_scale(0.001);
        assert_eq!(
            tiny.pixel_size(PageSize {
                width: 10.0,
                height: 10.0
            }),
            (1, 1)
        );
    }

    #[test]
    fn cancel_token_is_shared_between_clones() {
        let token = CancelToken::new();
        let clone = token.clone();
        assert!(token.check().is_ok());
        clone.cancel();
        assert_eq!(token.check().unwrap_err().code, ErrorCode::Cancelled);
    }
}
