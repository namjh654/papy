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

    // --- 편집(OpenedDocument.canEdit 인 문서만: HWP·HWPX·DOCX). 실패하면 null 또는 -1 ---

    /** 쪽의 (x, y)(pt, 쪽 왼쪽 위 기준) 자리에 있는 문단 */
    fun paragraphAt(session: Long, page: Int, x: Float, y: Float): ParagraphHit?

    /** 문단 글자를 바꾼다(`\n` 은 문단 나누기, 바뀌지 않은 글자의 모양은 지킨다). 편집 뒤 되돌리기 단계 수 */
    fun replaceParagraph(session: Long, section: Int, paragraph: Int, text: String): Int

    /** 쪽의 (x, y)(pt) 자리에 가장 가까운 커서 */
    fun caretAt(session: Long, page: Int, x: Float, y: Float): Caret?

    /** 위치의 커서 */
    fun caretAtPosition(session: Long, position: TextPos): Caret?

    /** 커서를 옮긴다. [preferredX]: 위·아래로 옮길 때 지킬 가로 위치(pt) */
    fun moveCaret(session: Long, position: TextPos, move: CaretMove, preferredX: Float): Caret?

    /** 글자를 넣는다(`\n` 은 문단 나누기). 앞 글자의 모양을 따른다 */
    fun insertText(session: Long, position: TextPos, text: String): EditResult?

    /** 커서 앞 글자를 지운다(문단 처음이면 앞 문단과 합친다) */
    fun deleteBackward(session: Long, position: TextPos): EditResult?

    /** 범위를 지운다 */
    fun deleteRange(session: Long, range: TextRange): EditResult?

    /** 범위를 줄마다 덮는 사각형 */
    fun selectionBoxes(session: Long, range: TextRange): List<PageBox>

    /** 위치가 든 단어의 범위 */
    fun wordAt(session: Long, position: TextPos): TextRange?

    /** 위치(커서 앞 글자)의 글자 모양 */
    fun textStyle(session: Long, position: TextPos): TextStyle?

    /** 범위에 글자 모양을 적용한다(null 이 아닌 값만). 편집 뒤 되돌리기 단계 수 */
    fun applyTextStyle(session: Long, range: TextRange, style: TextStyle): Int

    /** 마지막 편집 한 단계를 되돌린다. 실패하면 null */
    fun undo(session: Long): UndoResult?

    /** 원래 파일([ref])에 원래 형식으로 쓴다. 성공하면 null */
    fun save(session: Long, ref: String): DocumentError?

    /** PDF 로 바꿔 [target] 에 쓴다(Android: 만든 문서의 content URI, iOS: 임시 파일 경로). 성공하면 null */
    fun exportPdf(session: Long, target: String): DocumentError?

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
    /** 옮기지 못한 요소 수(DOCX 묶음 도형 등). 0 이면 손실 없음 */
    val droppedObjects: Int,
    /** 본문을 고칠 수 있다(HWP·HWPX·DOCX) */
    val canEdit: Boolean = false,
) {
    /** PDF 로 바꿀 수 있다(PDF 가 아닌 문서) */
    val canExportPdf: Boolean get() = kind != DocumentKind.Pdf
}

/** 쪽 위 사각형(pt) */
data class PageBox(val page: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/** 누른 문단: 위치, 글자, 화면에 표시할 사각형 */
data class ParagraphHit(val section: Int, val paragraph: Int, val text: String, val boxes: List<PageBox>)

/** 본문 위치. [offset] 은 문단 안 글자(유니코드 스칼라) 위치 */
data class TextPos(val section: Int, val paragraph: Int, val offset: Int) : Comparable<TextPos> {
    override fun compareTo(other: TextPos): Int =
        compareValuesBy(this, other, TextPos::section, TextPos::paragraph, TextPos::offset)
}

/** 범위([start] ≤ [end]) */
data class TextRange(val start: TextPos, val end: TextPos) {
    val isEmpty: Boolean get() = start == end

    companion object {
        /** 두 위치를 앞뒤 순서로 */
        fun of(a: TextPos, b: TextPos) = if (a <= b) TextRange(a, b) else TextRange(b, a)
    }
}

/** 커서: 위치와 화면에 그릴 세로 막대(pt) */
data class Caret(val position: TextPos, val box: PageBox)

/** 편집 결과: 커서와 지금 되돌릴 수 있는 단계 수(이어 친 글자는 한 단계로 묶인다) */
data class EditResult(val caret: Caret, val undoDepth: Int)

/** 되돌리기 결과: 되돌린 자리의 커서(없을 수 있음), 남은 단계 수 */
data class UndoResult(val caret: Caret?, val undoDepth: Int)

enum class CaretMove { Left, Right, Up, Down, LineStart, LineEnd }

/** 글자 모양. 읽을 때는 다 채워지고, 적용할 때는 null 이 아닌 값만 바꾼다. 색은 `#rrggbb` */
data class TextStyle(
    val fontSize: Float? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikethrough: Boolean? = null,
    val color: String? = null,
)

/** 성공이면 [document], 실패면 [error] */
data class OpenResult(val document: OpenedDocument?, val error: DocumentError?) {
    companion object {
        fun success(document: OpenedDocument) = OpenResult(document, null)
        fun failure(error: DocumentError) = OpenResult(null, error)
    }
}

data class PageSizePt(val width: Float, val height: Float)
