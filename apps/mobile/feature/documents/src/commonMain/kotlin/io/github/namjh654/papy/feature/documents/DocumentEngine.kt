package io.github.namjh654.papy.feature.documents

/**
 * 문서 엔진 계약. 플랫폼이 구현해 Koin 으로 넣는다.
 * - Android: Rust 엔진(UniFFI) + PDF 는 PdfRenderer
 * - iOS: Rust 엔진(UniFFI Swift) + PDF 는 PDFKit (Swift 클래스가 이 인터페이스를 구현)
 *
 * 함수는 막는(blocking) 호출이다. ViewModel 이 백그라운드에서 부른다(Swift 구현이 쉽도록 suspend·예외를 쓰지 않는다).
 * 쪽 그리기는 큰 픽셀 버퍼가 Kotlin/Native 를 오가지 않도록 플랫폼 화면이 [OpenedDocument.session] 으로 직접 한다.
 */
interface DocumentEngine {
    /** [ref]: 플랫폼 파일 참조(Android content URI, iOS 북마크). 문서 내용·경로는 기록하지 않는다. */
    fun open(ref: String, password: String?): OpenResult

    /** 쪽 크기(pt). 없는 쪽·닫힌 문서면 null */
    fun pageSize(session: Long, page: Int): PageSizePt?

    /** 쪽의 글자. 읽을 수 없으면(스캔 PDF·이 기기에서 지원 안 함) null */
    fun pageText(session: Long, page: Int): String?

    /** 지금 쪽 수(편집하면 바뀐다). 실패하면 null */
    fun pageCount(session: Long): Int?

    // --- 편집(OpenedDocument.canEdit 인 문서만: HWP·HWPX·DOCX) ---

    /** 쪽의 (x, y)(pt, 쪽 왼쪽 위 기준) 자리에 있는 문단. 없거나 편집할 수 없으면 null */
    fun paragraphAt(session: Long, page: Int, x: Float, y: Float): ParagraphHit?

    /** 문단 글자를 바꾼다(`\n` 은 문단 나누기). 되돌리기 단계 수(0 이상), 실패하면 -1 */
    fun replaceParagraph(session: Long, section: Int, paragraph: Int, text: String): Int

    /** 마지막 편집 한 단계를 되돌린다 */
    fun undo(session: Long): Boolean

    /** 원래 파일([ref])에 원래 형식으로 쓴다. 성공하면 null */
    fun save(session: Long, ref: String): DocumentError?

    fun close(session: Long)
}

enum class DocumentKind { Pdf, Hwp, Hwpx, Docx }

/** 열기 실패 이유. 화면 문구는 플랫폼 문자열 리소스가 고른다. */
enum class DocumentError {
    PasswordRequired, Unsupported, Invalid, TooLarge, Unreadable, Internal,
    /** 저장: 파일에 쓸 권한이 없다(읽기 전용 위치·다른 앱의 파일) */
    ReadOnly,
}

data class OpenedDocument(
    val session: Long,
    val kind: DocumentKind,
    val pageCount: Int,
    val title: String?,
    /** 옮기지 못한 요소 수(DOCX 도형 등). 0 이면 손실 없음 */
    val droppedObjects: Int,
    /** 본문을 고칠 수 있다(HWP·HWPX·DOCX) */
    val canEdit: Boolean = false,
)

/** 쪽 위 사각형(pt) */
data class PageBox(val page: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/** 누른 문단: 위치, 글자, 화면에 표시할 사각형 */
data class ParagraphHit(val section: Int, val paragraph: Int, val text: String, val boxes: List<PageBox>)

/** 성공이면 [document], 실패면 [error] */
data class OpenResult(val document: OpenedDocument?, val error: DocumentError?) {
    companion object {
        fun success(document: OpenedDocument) = OpenResult(document, null)
        fun failure(error: DocumentError) = OpenResult(null, error)
    }
}

data class PageSizePt(val width: Float, val height: Float)
