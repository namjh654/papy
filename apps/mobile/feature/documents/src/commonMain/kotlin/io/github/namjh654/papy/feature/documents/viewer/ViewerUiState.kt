package io.github.namjh654.papy.feature.documents.viewer

import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.feature.documents.Caret
import io.github.namjh654.papy.feature.documents.DocumentError
import io.github.namjh654.papy.feature.documents.ObjectKind
import io.github.namjh654.papy.feature.documents.PageObject
import io.github.namjh654.papy.feature.documents.TableCell
import io.github.namjh654.papy.feature.documents.OpenedDocument
import io.github.namjh654.papy.feature.documents.OutlineEntry
import io.github.namjh654.papy.feature.documents.TextHit
import io.github.namjh654.papy.feature.documents.UnsupportedCount
import io.github.namjh654.papy.feature.documents.PageBox
import io.github.namjh654.papy.feature.documents.PageSizePt
import io.github.namjh654.papy.feature.documents.ParagraphHit
import io.github.namjh654.papy.feature.documents.TextPos
import io.github.namjh654.papy.feature.documents.TextRange
import io.github.namjh654.papy.feature.documents.TextStyle

data class ViewerUiState(
    val name: String,
    val phase: ViewerPhase = ViewerPhase.Opening,
    val document: OpenedDocument? = null,
    /** 쪽마다 크기(pt). 쪽 수와 같은 길이 */
    val pageSizes: List<PageSizePt> = emptyList(),
    /** 지금 보이는 쪽(0부터) */
    val currentPage: Int = 0,
    /** 암호를 넣었는데 틀렸다 */
    val wrongPassword: Boolean = false,
    val error: DocumentError? = null,
    /** 글자 보기 창: null 이면 닫힘 */
    val text: PageText? = null,
    /** 옮기지 못한 요소 안내를 닫았다 */
    val lossDismissed: Boolean = false,
    /** 목차(열 때 읽는다). 비어 있으면 목차 단추를 숨긴다 */
    val outline: List<OutlineEntry> = emptyList(),
    /** 목차 창이 열려 있다 */
    val showOutline: Boolean = false,
    /** 찾기 창이 열려 있다 */
    val showSearch: Boolean = false,
    /** 마지막 찾기(창을 닫아도 남겨 다시 열면 이어 본다). null 이면 찾은 적 없음 */
    val search: SearchState? = null,
    /** 지금 쪽에서 제대로 그리지 못한 개체 */
    val unsupported: UnsupportedCount = UnsupportedCount(),
    /** 그리지 못한 개체 안내를 닫았다(이 문서에서는 다시 띄우지 않는다) */
    val unsupportedDismissed: Boolean = false,
    /** 이 쪽으로 옮겨 달라는 요청. [PageJump.serial] 이 바뀔 때마다 플랫폼이 스크롤한다 */
    val jump: PageJump? = null,
    /** 편집 모드(누르면 커서, 두 번 누르면 단어, 길게 누르면 문단 고치기) */
    val editing: Boolean = false,
    /** 커서(편집 모드). 선택이 있으면 선택의 움직이는 끝 */
    val caret: Caret? = null,
    /** 선택의 고정된 끝. null 이거나 커서와 같으면 선택 없음 */
    val anchor: TextPos? = null,
    /** 선택을 덮는 사각형 */
    val selectionBoxes: List<PageBox> = emptyList(),
    /** 커서 자리의 글자 모양(도구 막대 표시) */
    val style: TextStyle? = null,
    /** 누르면 선택을 그 자리까지 늘린다 */
    val extending: Boolean = false,
    /** 고른 표(칸 안에 커서가 있을 때)나 그림 */
    val selected: ObjectSelection? = null,
    /** 문단 고치기 창(길게 눌러 고른 문단) */
    val paragraph: ParagraphHit? = null,
    /** 편집 뒤 쪽을 다시 그리라는 표시. 바뀔 때마다 플랫폼이 쪽을 다시 그린다 */
    val revision: Int = 0,
    /** 저장하지 않은 변경 */
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val saving: Boolean = false,
    /** PDF 로 바꾸는 중 */
    val exporting: Boolean = false,
    /** 나가려는데 저장하지 않은 변경이 있다 → 저장·버리기·취소를 묻는다 */
    val confirmExit: Boolean = false,
) : UiState {
    val canEdit: Boolean get() = document?.canEdit == true
    val canExportPdf: Boolean get() = document?.canExportPdf == true
    val pageCount: Int get() = document?.pageCount ?: 0
    val showLoss: Boolean get() = (document?.droppedObjects ?: 0) > 0 && !lossDismissed
    val showUnsupported: Boolean get() = !unsupported.isEmpty && !unsupportedDismissed && !showLoss

    /** PDF 로 내보낼 때 권하는 파일 이름 */
    val pdfName: String get() = name.substringBeforeLast('.').ifEmpty { name } + ".pdf"

    /** 선택 범위(비어 있지 않을 때만) */
    val selection: TextRange?
        get() {
            val end = caret?.position ?: return null
            val start = anchor ?: return null
            return TextRange.of(start, end).takeUnless { it.isEmpty }
        }
}

/** 고른 개체: 표면 커서가 든 칸([cell]), 그림이면 칸 없음 */
data class ObjectSelection(val target: PageObject, val cell: TableCell? = null) {
    val isTable: Boolean get() = target.kind == ObjectKind.Table
    val isPicture: Boolean get() = target.kind == ObjectKind.Picture
}

/** 표 도구 막대 */
enum class TableCommand { RowAbove, RowBelow, ColumnLeft, ColumnRight, DeleteRow, DeleteColumn, MergeRight, MergeDown, DeleteTable }

/** 찾기 창: 찾는 글자, 결과(null 이면 찾는 중), 결과가 상한에 닿아 더 있을 수 있음 */
data class SearchState(val query: String = "", val hits: List<TextHit>? = emptyList(), val more: Boolean = false)

/** 쪽 옮기기 요청. 같은 쪽을 다시 골라도 옮기도록 [serial] 을 늘린다 */
data class PageJump(val page: Int, val serial: Int)

/** 글자 보기 창 내용. [text] 가 null 이면 읽는 중, [unavailable] 이면 이 쪽 글자를 읽을 수 없음 */
data class PageText(val page: Int, val text: String? = null, val unavailable: Boolean = false)

enum class ViewerPhase { Opening, NeedsPassword, Ready, Failed }

/** 도구 막대의 글자 모양 단추 */
enum class StyleToggle { Bold, Italic, Underline, Strikethrough }

sealed interface ViewerEvent : UiEvent {
    data object Saved : ViewerEvent
    data class SaveFailed(val error: DocumentError) : ViewerEvent
    /** 편집이 적용되지 않았다(문단을 찾지 못함 등) */
    data object EditFailed : ViewerEvent
    /** 표·그림은 본문에만 넣을 수 있다(커서가 표 칸 안이다) */
    data object InsertNeedsBody : ViewerEvent
    /** 그림을 넣지 못했다(읽을 수 없거나 지원하지 않는 형식) */
    data object PictureFailed : ViewerEvent
    /** PDF 를 [target] 에 썼다(iOS 는 이어서 공유·저장 창을 연다) */
    data class Exported(val target: String) : ViewerEvent
    data class ExportFailed(val error: DocumentError) : ViewerEvent
    /** 화면을 닫는다 */
    data object Exit : ViewerEvent
}
