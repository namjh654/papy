//! Papy PDF 어댑터 — PDFium(`pdfium-render`) 기반 (docs/decisions/0003-pdf-engine.md).
//!
//! PDFium 은 프로세스 전역 상태를 쓰고 스레드 안전하지 않다. 그래서 **전용 워커 스레드 하나**가 PDFium 과
//! 모든 PDF 문서를 소유하고, 각 [`Document`] 는 채널로 그 스레드에 작업을 보낸다.
//! PDFium 동적 라이브러리는 호스트(앱)가 경로를 알려 준다 — `scripts/fetch-pdfium.mjs` 로 받는다.

use std::collections::HashMap;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::sync::mpsc::{self, Sender};

use papy_model::{
    Annotation, Capabilities, DocumentFormat, DocumentInfo, EditOutcome, Error, ErrorCode,
    OutlineItem, PageOp, PageRect, PageSize, Result, SaveFormat,
};
use papy_render::{BackendOpenOptions, Bitmap, Document, FormatBackend, RenderRequest, Source};
use pdfium_render::prelude::*;

mod edit;

/// PDF 파일 시그니처.
pub const SIGNATURE: &[u8] = b"%PDF-";

/// 목차 탐색 상한 — 순환·과도한 목차를 가진 악성 파일 방어.
const MAX_OUTLINE_ITEMS: usize = 10_000;
const MAX_OUTLINE_DEPTH: usize = 32;

/// 앞부분 바이트가 PDF 시그니처로 시작하는지 본다. 파일 전체가 올바른 PDF 라는 뜻은 아니다.
pub fn has_signature(head: &[u8]) -> bool {
    head.starts_with(SIGNATURE)
}

/// 현재 플랫폼의 PDFium 라이브러리 파일 이름(`libpdfium.dylib`, `pdfium.dll`, `libpdfium.so`).
pub fn library_file_name() -> PathBuf {
    Pdfium::pdfium_platform_library_name_at_path("")
}

/// PDFium 으로 PDF 를 여는 어댑터.
#[derive(Debug, Clone)]
pub struct PdfBackend {
    library: PathBuf,
    text_font: Option<PathBuf>,
}

impl PdfBackend {
    /// `library` 는 PDFium 동적 라이브러리 파일 경로. 실제 로드는 첫 PDF 를 열 때 한 번만 한다.
    /// PDFium 은 프로세스에 하나라서 다른 경로로 다시 만들어도 처음 로드한 라이브러리를 쓴다.
    pub fn new(library: impl Into<PathBuf>) -> Self {
        Self {
            library: library.into(),
            text_font: None,
        }
    }

    /// 텍스트 상자에 쓸 TrueType 글꼴(한글 포함). 없으면 라틴 글자만 넣을 수 있다.
    pub fn with_text_font(mut self, font: impl Into<PathBuf>) -> Self {
        self.text_font = Some(font.into());
        self
    }

    /// 라이브러리를 지금 로드해 본다(앱 시작 시 진단용).
    pub fn check(&self) -> Result<()> {
        worker(&self.library).map(|_| ())
    }
}

impl FormatBackend for PdfBackend {
    fn format(&self) -> DocumentFormat {
        DocumentFormat::Pdf
    }

    fn open(&self, source: Source<'_>, options: &BackendOpenOptions) -> Result<Box<dyn Document>> {
        let worker = worker(&self.library)?;
        let input = match source {
            Source::Path(path) => Input::Path(path.to_path_buf()),
            Source::Bytes(bytes) => Input::Bytes(bytes.to_vec()),
        };
        let password = options.password.clone();
        let max_pages = options.max_pages;
        let cancel = options.cancel.clone();
        let text_font = self.text_font.clone();
        let (id, info) = worker.call(move |state| {
            cancel.check()?;
            if text_font.is_some() {
                state.text_font = text_font;
            }
            state.open(input, password, max_pages)
        })?;
        Ok(Box::new(PdfDocument {
            id,
            info,
            worker: worker.clone(),
        }))
    }
}

/// 워커 스레드에서 열린 PDF 하나를 가리키는 핸들.
struct PdfDocument {
    id: u64,
    info: DocumentInfo,
    worker: Worker,
}

impl Drop for PdfDocument {
    fn drop(&mut self) {
        let id = self.id;
        self.worker.send(move |state| {
            state.docs.remove(&id);
        });
    }
}

impl Document for PdfDocument {
    fn info(&self) -> DocumentInfo {
        self.info.clone()
    }

    fn page_size(&mut self, page: u32) -> Result<PageSize> {
        let id = self.id;
        self.worker.call(move |state| {
            let page = state.page(id, page)?;
            Ok(PageSize {
                width: page.width().value,
                height: page.height().value,
            })
        })
    }

    fn render_page(&mut self, page: u32, request: &RenderRequest) -> Result<Bitmap> {
        let id = self.id;
        let request = request.clone();
        self.worker.call(move |state| {
            request.cancel.check()?;
            let page = state.page(id, page)?;
            let size = PageSize {
                width: page.width().value,
                height: page.height().value,
            };
            let (width, height) = request.pixel_size(size);
            let clear = if request.white_background {
                PdfColor::WHITE
            } else {
                PdfColor::new(255, 255, 255, 0)
            };
            let config = PdfRenderConfig::new()
                .set_target_width(width as Pixels)
                .set_target_height(height as Pixels)
                .set_clear_color(clear)
                .render_annotations(true)
                .render_form_data(true);
            let bitmap = page.render_with_config(&config).map_err(map_error)?;
            let (actual_w, actual_h) = (bitmap.width() as u32, bitmap.height() as u32);
            let pixels = tight_rgba(bitmap.as_rgba_bytes(), actual_w, actual_h)?;
            Ok(Bitmap {
                width: actual_w,
                height: actual_h,
                pixels,
            })
        })
    }

    fn extract_text(&mut self, page: u32) -> Result<String> {
        let id = self.id;
        self.worker.call(move |state| {
            let mut page = state.page(id, page)?;
            // PDFium 은 회전한 쪽의 글자 순서를 잘못 읽는다. 잠깐 회전을 풀고 읽은 뒤 되돌린다
            // (워커 스레드 안이라 그 사이 다른 작업이 끼어들지 않는다).
            let rotation = page.rotation().map_err(map_error)?;
            if rotation == PdfPageRenderRotation::None {
                return Ok(page.text().map_err(map_error)?.all());
            }
            page.set_rotation(PdfPageRenderRotation::None);
            let text = page.text().map(|text| text.all()).map_err(map_error);
            page.set_rotation(rotation);
            text
        })
    }

    fn outline(&mut self) -> Result<Vec<OutlineItem>> {
        let id = self.id;
        self.worker.call(move |state| {
            let doc = state.doc(id)?;
            let mut budget = MAX_OUTLINE_ITEMS;
            Ok(outline_level(doc.bookmarks().root(), 0, &mut budget))
        })
    }

    fn save(&mut self, format: SaveFormat) -> Result<Vec<u8>> {
        let id = self.id;
        match format {
            SaveFormat::Pdf => self
                .worker
                .call(move |state| state.doc(id)?.save_to_bytes().map_err(map_error)),
            SaveFormat::Text => {
                let mut text = String::new();
                for page in 0..self.info.page_count {
                    text.push_str(self.extract_text(page)?.trim_end_matches('\n'));
                    text.push('\n');
                }
                Ok(text.into_bytes())
            }
            _ => Err(Error::new(
                ErrorCode::NotImplemented,
                "unsupported save format",
            )),
        }
    }

    fn annotate(&mut self, annotation: &Annotation) -> Result<()> {
        let id = self.id;
        let annotation = annotation.clone();
        self.worker
            .call(move |state| state.annotate(id, &annotation))
    }

    fn text_rects(&mut self, page: u32, area: PageRect) -> Result<Vec<PageRect>> {
        let id = self.id;
        self.worker
            .call(move |state| state.text_rects(id, page, area))
    }

    fn text_in_area(&mut self, page: u32, area: PageRect) -> Result<String> {
        let id = self.id;
        self.worker
            .call(move |state| state.text_in_area(id, page, area))
    }

    fn edit_pages(&mut self, op: PageOp) -> Result<()> {
        let id = self.id;
        self.info.page_count = self.worker.call(move |state| state.edit_pages(id, op))?;
        Ok(())
    }

    fn undo(&mut self) -> Result<Option<EditOutcome>> {
        self.history(false)
    }

    fn redo(&mut self) -> Result<Option<EditOutcome>> {
        self.history(true)
    }
}

impl PdfDocument {
    fn history(&mut self, redo: bool) -> Result<Option<EditOutcome>> {
        let id = self.id;
        let pages = self.worker.call(move |state| state.history(id, redo))?;
        Ok(pages.map(|count| {
            self.info.page_count = count;
            EditOutcome {
                caret: papy_model::TextPos::default(),
            }
        }))
    }
}

/// 행 끝 여백(stride 패딩)이 있으면 제거해 `width * 4` 바이트 행으로 맞춘다.
fn tight_rgba(bytes: Vec<u8>, width: u32, height: u32) -> Result<Vec<u8>> {
    let row = width as usize * 4;
    let rows = height as usize;
    if rows == 0 || bytes.len() == row * rows {
        return Ok(bytes);
    }
    let stride = bytes.len() / rows;
    if stride < row {
        return Err(Error::new(
            ErrorCode::Internal,
            "pdfium bitmap is smaller than expected",
        ));
    }
    Ok(bytes
        .chunks_exact(stride)
        .take(rows)
        .flat_map(|r| &r[..row])
        .copied()
        .collect())
}

fn outline_level(
    mut node: Option<PdfBookmark<'_>>,
    depth: usize,
    budget: &mut usize,
) -> Vec<OutlineItem> {
    let mut items = Vec::new();
    while let Some(bookmark) = node {
        if *budget == 0 || depth >= MAX_OUTLINE_DEPTH {
            break;
        }
        *budget -= 1;
        let page = bookmark
            .destination()
            .and_then(|d| d.page_index().ok())
            .and_then(|i| u32::try_from(i).ok());
        let children = outline_level(bookmark.first_child(), depth + 1, budget);
        items.push(OutlineItem {
            title: bookmark.title().unwrap_or_default(),
            page,
            children,
        });
        node = bookmark.next_sibling();
    }
    items
}

/// PDFium 오류를 엔진 오류로. 경로·내용이 섞일 수 있는 원문은 버린다.
fn map_error(error: PdfiumError) -> Error {
    match error {
        PdfiumError::PdfiumLibraryInternalError(internal) => match internal {
            PdfiumInternalError::PasswordError => Error::new(
                ErrorCode::PasswordRequired,
                "password required or incorrect",
            ),
            PdfiumInternalError::SecurityError => Error::new(
                ErrorCode::InvalidDocument,
                "unsupported PDF security handler",
            ),
            PdfiumInternalError::FileError => {
                Error::new(ErrorCode::Io, "pdfium could not read the file")
            }
            PdfiumInternalError::FormatError => {
                Error::new(ErrorCode::InvalidDocument, "invalid or damaged PDF")
            }
            PdfiumInternalError::PageError => {
                Error::new(ErrorCode::InvalidDocument, "invalid or damaged PDF page")
            }
            _ => Error::new(
                ErrorCode::InvalidDocument,
                "pdfium could not open the document",
            ),
        },
        PdfiumError::IoError(io) => io.into(),
        PdfiumError::PageIndexOutOfBounds => {
            Error::new(ErrorCode::PageOutOfRange, "page is out of range")
        }
        PdfiumError::LoadLibraryError(_) | PdfiumError::LoadLibraryFunctionNameError(_) => {
            Error::new(
                ErrorCode::UnsupportedFormat,
                "PDF engine (PDFium) could not be loaded",
            )
        }
        _ => Error::new(ErrorCode::Internal, "pdfium error"),
    }
}

// ---- 워커 스레드 ----

enum Input {
    Path(PathBuf),
    Bytes(Vec<u8>),
}

/// 워커가 가진 열린 문서 하나.
struct Entry {
    doc: pdfium_render::prelude::PdfDocument<'static>,
    /// 되돌리기 스냅샷을 다시 열 때 쓴다.
    password: Option<String>,
    undo: Vec<Vec<u8>>,
    redo: Vec<Vec<u8>>,
    /// 텍스트 상자용으로 넣은 글꼴(문서마다 한 번).
    font: Option<PdfFontToken>,
}

struct State {
    pdfium: &'static Pdfium,
    docs: HashMap<u64, Entry>,
    next_id: u64,
    text_font: Option<PathBuf>,
}

impl State {
    fn open(
        &mut self,
        input: Input,
        password: Option<String>,
        max_pages: u32,
    ) -> Result<(u64, DocumentInfo)> {
        let doc = match input {
            Input::Path(path) => self.pdfium.load_pdf_from_file(&path, password.as_deref()),
            Input::Bytes(bytes) => self
                .pdfium
                .load_pdf_from_byte_vec(bytes, password.as_deref()),
        }
        .map_err(map_error)?;
        let page_count = u32::try_from(doc.pages().len()).unwrap_or(0);
        if page_count > max_pages {
            return Err(Error::new(ErrorCode::LimitExceeded, "too many pages"));
        }
        let title = doc
            .metadata()
            .get(PdfDocumentMetadataTagType::Title)
            .map(|tag| tag.value().trim().to_string())
            .filter(|title| !title.is_empty());
        let info = DocumentInfo {
            format: DocumentFormat::Pdf,
            page_count,
            byte_size: 0,
            title,
            dropped_objects: 0,
            capabilities: Capabilities {
                render: true,
                extract_text: true,
                outline: true,
                save: true,
                annotate: true,
                page_edit: true,
                save_formats: vec![SaveFormat::Pdf, SaveFormat::Text],
                ..Capabilities::default()
            },
        };
        self.next_id += 1;
        self.docs.insert(
            self.next_id,
            Entry {
                doc,
                password,
                undo: Vec::new(),
                redo: Vec::new(),
                font: None,
            },
        );
        Ok((self.next_id, info))
    }

    fn doc(&self, id: u64) -> Result<&pdfium_render::prelude::PdfDocument<'static>> {
        self.docs
            .get(&id)
            .map(|entry| &entry.doc)
            .ok_or_else(|| Error::new(ErrorCode::SessionNotFound, "pdf document is closed"))
    }

    fn page(&self, id: u64, page: u32) -> Result<PdfPage<'static>> {
        let index = PdfPageIndex::try_from(page)
            .map_err(|_| Error::new(ErrorCode::PageOutOfRange, "page is out of range"))?;
        self.doc(id)?.pages().get(index).map_err(map_error)
    }
}

type Job = Box<dyn FnOnce(&mut State) + Send>;

#[derive(Clone)]
struct Worker {
    jobs: Sender<Job>,
}

impl Worker {
    fn start(library: &Path) -> Result<Self> {
        let library = library.to_path_buf();
        let (jobs, inbox) = mpsc::channel::<Job>();
        let (ready_tx, ready_rx) = mpsc::sync_channel::<Result<()>>(1);
        std::thread::Builder::new()
            .name("papy-pdfium".into())
            .spawn(move || {
                let bindings = match Pdfium::bind_to_library(&library) {
                    Ok(bindings) => bindings,
                    Err(error) => {
                        let _ = ready_tx.send(Err(map_error(error)));
                        return;
                    }
                };
                // PDFium 은 프로세스 수명 동안 유지한다(문서가 &'static 로 빌려 쓴다).
                let pdfium: &'static Pdfium = Box::leak(Box::new(Pdfium::new(bindings)));
                let _ = ready_tx.send(Ok(()));
                let mut state = State {
                    pdfium,
                    docs: HashMap::new(),
                    next_id: 0,
                    text_font: None,
                };
                for job in inbox {
                    // 작업 하나가 패닉해도 워커는 계속 돈다. 호출자는 응답 채널이 끊겨 Internal 오류를 받는다.
                    let _ = catch_unwind(AssertUnwindSafe(|| job(&mut state)));
                }
            })
            .map_err(|_| {
                Error::new(ErrorCode::Internal, "could not start the PDF worker thread")
            })?;
        ready_rx.recv().map_err(|_| {
            Error::new(
                ErrorCode::Internal,
                "PDF worker thread stopped during startup",
            )
        })??;
        Ok(Self { jobs })
    }

    fn send(&self, job: impl FnOnce(&mut State) + Send + 'static) {
        let _ = self.jobs.send(Box::new(job));
    }

    fn call<T: Send + 'static>(
        &self,
        job: impl FnOnce(&mut State) -> Result<T> + Send + 'static,
    ) -> Result<T> {
        let (reply, response) = mpsc::sync_channel(1);
        self.send(move |state| {
            let _ = reply.send(job(state));
        });
        response.recv().map_err(|_| {
            Error::new(
                ErrorCode::Internal,
                "PDF worker failed while handling the request",
            )
        })?
    }
}

/// 프로세스 전역 워커. 첫 호출의 라이브러리 경로로 한 번만 시작하며, 실패도 기억한다.
fn worker(library: &Path) -> Result<&'static Worker> {
    static WORKER: OnceLock<Result<Worker>> = OnceLock::new();
    WORKER
        .get_or_init(|| Worker::start(library))
        .as_ref()
        .map_err(Clone::clone)
}

#[cfg(test)]
mod tests;
