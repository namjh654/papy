package cloud.elsa.app.core.data.flags

import cloud.elsa.app.core.data.serialization.AppJson
import cloud.elsa.app.core.data.storage.LocalStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 기능 켜고 끄기(28-2, DEC-135): 앱 업데이트 없이 서버가 기능을 끈다. 값은 버전 확인 응답의 `features`(같은 API, release.md §5)로 오고,
 * 마지막에 받은 값을 저장해 다음 실행·오프라인에서도 쓴다. 서버가 모르는 키·받기 전에는 [FeatureKey.default].
 *
 * 새 기능 키: [FeatureKey] 에 한 줄 → ViewModel 이 `flags.observe(FeatureKey.X)` 를 상태에 담고 화면이 그것으로 숨긴다.
 */
class FeatureFlags(
    private val storage: LocalStorage,
    scope: CoroutineScope,
) {
    private val _values = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val values: StateFlow<Map<String, Boolean>> = _values.asStateFlow()

    init {
        scope.launch {
            val saved = storage.getString(KEY)?.let { runCatching { AppJson.decodeFromString<Map<String, Boolean>>(it) }.getOrNull() }
            // 저장값을 읽는 동안 서버 값이 먼저 왔으면 그것을 둔다
            if (saved != null && _values.value.isEmpty()) _values.value = saved
        }
    }

    fun isEnabled(key: FeatureKey): Boolean = _values.value[key.id] ?: key.default

    fun observe(key: FeatureKey): Flow<Boolean> = values.map { it[key.id] ?: key.default }.distinctUntilChanged()

    /** 서버 응답을 받았을 때(AppUpdateManager.check). 빈 값이면 바꾸지 않는다(서버가 아직 플래그를 주지 않는 경우) */
    suspend fun update(features: Map<String, Boolean>) {
        if (features.isEmpty()) return
        _values.value = features
        storage.setString(KEY, AppJson.encodeToString(features))
    }

    private companion object {
        const val KEY = "feature.flags"
    }
}

/**
 * 서버가 켜고 끌 수 있는 기능. [id] 는 서버 응답 `features` 의 키, [default] 는 값이 없을 때.
 * Swift: `FeatureKey.chatAttachments`
 */
enum class FeatureKey(val id: String, val default: Boolean) {
    /** 대화방 첨부(사진·카메라·파일) 버튼 */
    ChatAttachments("chat_attachments", true),
}
