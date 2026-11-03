package cloud.elsa.app.feature.example

import cloud.elsa.app.core.common.paging.Page
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 예시 기능 데이터. 캐시·저장·여러 API 조합·검증 규칙이 생기면 여기에 둔다.
 *
 * 수정 결과는 [changes] 로 알린다 → 목록 화면이 구독해 그 항목만 바꾼다.
 * (상세 화면이 목록 화면을 직접 알 필요가 없고, 어느 화면에서 고쳐도 목록이 맞는다)
 */
class ExampleRepository(
    private val api: ExampleApi,
) {
    private val _changes = MutableSharedFlow<ExampleItem>(extraBufferCapacity = 16)

    /** 수정된 항목. 목록 화면이 구독한다 */
    val changes: SharedFlow<ExampleItem> = _changes.asSharedFlow()

    suspend fun items(page: String?): Page<ExampleItem> = api.items(page)

    suspend fun item(id: String): ExampleItem = api.item(id)

    suspend fun setFavorite(id: String, favorite: Boolean): ExampleItem =
        api.setFavorite(id, favorite).also { _changes.emit(it) }
}
