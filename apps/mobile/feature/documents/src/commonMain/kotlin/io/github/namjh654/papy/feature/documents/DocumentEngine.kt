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

    /** 목차(나무를 앞에서부터 편 목록, [OutlineEntry.depth] 로 들여쓴다). 없거나 읽지 못하면 빈 목록 */
    fun outline(session: Long): List<OutlineEntry>

    /**
     * 문서에서 글자를 찾는다(대소문자·공백 종류는 가리지 않는다). 쪽 순서로 [max] 개까지.
     * 찾는 동안 다시 부르면 앞의 찾기는 빈 목록으로 끝날 수 있다.
     */
    fun findText(session: Long, query: String, max: Int): List<TextHit>

    /** 쪽에서 제대로 그리지 못한 개체 수(차트·OLE 자리표시, 데이터 없는 그림). 쪽을 다시 배치하므로 보이는 쪽에만 부른다 */
    fun unsupportedObjects(session: Long, page: Int): UnsupportedCount

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

    // --- 표·그림(본문 것만) ---

    /** 쪽에 그려진 표·그림 */
    fun pageObjects(session: Long, page: Int): List<PageObject>

    /** 본문 위치에 표를 넣는다. 편집 뒤 되돌리기 단계 수 */
    fun insertTable(session: Long, position: TextPos, rows: Int, cols: Int): Int

    /** 표를 고친다. [row]·[col] 은 기준 칸, 합치기는 [endRow]·[endCol] 까지(양 끝 포함). 편집 뒤 되돌리기 단계 수 */
    fun editTable(session: Long, table: ObjectRef, action: TableAction, row: Int, col: Int, endRow: Int, endCol: Int): Int

    /**
     * 본문 위치에 그림을 글자처럼 넣는다. [source]: 플랫폼 그림 참조(Android content URI, iOS 파일 경로).
     * PNG·JPEG·GIF·BMP 가 아니면 플랫폼이 JPEG 로 바꿔 넣는다. 크기는 pt. 편집 뒤 되돌리기 단계 수
     */
    fun insertPicture(session: Long, position: TextPos, source: String, width: Float, height: Float): Int

    /** 그림 크기(pt). 편집 뒤 되돌리기 단계 수 */
    fun resizePicture(session: Long, picture: ObjectRef, width: Float, height: Float): Int

    /** 표·그림을 지운다. 편집 뒤 되돌리기 단계 수 */
    fun deleteObject(session: Long, target: ObjectRef, kind: ObjectKind): Int

    /** 마지막 편집 한 단계를 되돌린다. 실패하면 null */
    fun undo(session: Long): UndoResult?

    /** 원래 파일([ref])에 원래 형식으로 쓴다. 성공하면 null */
    fun save(session: Long, ref: String): DocumentError?

    /** PDF 로 바꿔 [target] 에 쓴다(Android: 만든 문서의 content URI, iOS: 임시 파일 경로). 성공하면 null */
    fun exportPdf(session: Long, target: String): DocumentError?

    fun close(session: Long)
}

enum class DocumentKind { Pdf, Hwp, Hwpx, Docx, Doc }

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

/** 목차 항목: 제목, 이동할 쪽(모르면 null), 깊이(0 이 맨 위) */
data class OutlineEntry(val title: String, val page: Int?, val depth: Int)

/** 찾은 글자: 쪽, 앞 글자, 찾은 글자(문서 그대로), 뒤 글자 */
data class TextHit(val page: Int, val before: String, val matched: String, val after: String)

/** 쪽에서 제대로 그리지 못한 개체: 자리만 점선으로 그린 것(차트·OLE), 데이터가 없어 비워 둔 그림 */
data class UnsupportedCount(val placeholders: Int = 0, val missingImages: Int = 0) {
    val isEmpty: Boolean get() = placeholders == 0 && missingImages == 0
}

/** 쪽 위 사각형(pt) */
data class PageBox(val page: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/** 누른 문단: 위치, 글자, 화면에 표시할 사각형 */
data class ParagraphHit(val section: Int, val paragraph: Int, val text: String, val boxes: List<PageBox>)

/**
 * 본문 위치. [offset] 은 문단 안 커서 자리(글자, 그리고 글자처럼 놓인 그림·표도 한 칸).
 * 표 칸·글상자 안이면 [cell] 이 있고, [paragraph] 는 표를 가진 본문 문단, [offset] 은 칸 문단 안 위치다.
 */
data class TextPos(val section: Int, val paragraph: Int, val offset: Int, val cell: CellRef? = null) : Comparable<TextPos> {
    val inCell: Boolean get() = cell != null

    /** 같은 곳(둘 다 본문, 또는 같은 표 칸)이다. 범위는 같은 곳 안에서만 고른다 */
    fun sameBlock(other: TextPos): Boolean = when {
        cell == null && other.cell == null -> true
        cell != null && other.cell != null ->
            section == other.section && paragraph == other.paragraph &&
                cell.control == other.cell.control && cell.cell == other.cell.cell
        else -> false
    }

    override fun compareTo(other: TextPos): Int =
        compareValuesBy(this, other, TextPos::section, TextPos::paragraph, { it.cell?.cell ?: -1 }, { it.cell?.cellParagraph ?: -1 }, TextPos::offset)
}

/** 표 칸·글상자: 본문 문단 안 개체 번호, 칸 번호(글상자는 0), 칸 안 문단 */
data class CellRef(val control: Int, val cell: Int, val cellParagraph: Int)

/** 문서 안 개체(표·그림): 본문 문단과 그 안 개체 번호 */
data class ObjectRef(val section: Int, val paragraph: Int, val control: Int)

enum class ObjectKind { Table, Picture }

/** 표 칸 자리 */
data class TableCell(val cell: Int, val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int, val box: PageBox)

/** 쪽에 그려진 표·그림 */
data class PageObject(
    val kind: ObjectKind,
    val target: ObjectRef,
    val box: PageBox,
    val rows: Int = 0,
    val cols: Int = 0,
    val cells: List<TableCell> = emptyList(),
) {
    fun contains(x: Float, y: Float) = x >= box.x && x <= box.x + box.width && y >= box.y && y <= box.y + box.height
}

/** 엔진의 표 고치기 */
enum class TableAction { InsertRowAbove, InsertRowBelow, InsertColumnLeft, InsertColumnRight, DeleteRow, DeleteColumn, Merge }

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
