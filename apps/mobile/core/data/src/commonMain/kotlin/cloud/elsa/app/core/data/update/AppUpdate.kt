package cloud.elsa.app.core.data.update

import cloud.elsa.app.core.data.flags.FeatureFlags
import cloud.elsa.app.core.data.network.BaseApi
import cloud.elsa.app.core.data.storage.LocalStorage
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 운영 공지(27, DEC-132). [blocking] 이면 점검 — 닫을 수 없고 앱을 쓸 수 없다. 아니면 "확인"으로 닫고 같은 [id] 는 다시 보이지 않는다.
 * 문구는 서버가 사용자 언어로 준다. Swift: `AppNotice`(`title` 은 nil 가능)
 */
@Serializable
data class AppNotice(
    val id: String,
    val message: String,
    val title: String? = null,
    val blocking: Boolean = false,
)

/**
 * 최소·최신 버전·공지·기능 켜고 끄기 API (가정 계약 — DEC-055·132·135). 경로는 env `update.checkPath`(비우면 확인 안 함).
 * `GET {update.checkPath}?platform=android|ios` → `{minimumVersion?, latestVersion?, storeUrl?, notice?, features?: {키: Boolean}}`
 */
@Serializable
data class AppVersionResponse(
    val minimumVersion: String? = null,
    val latestVersion: String? = null,
    val storeUrl: String? = null,
    val notice: AppNotice? = null,
    /** 기능 켜고 끄기(FeatureFlags) */
    val features: Map<String, Boolean> = emptyMap(),
)

interface AppVersionApi {
    suspend fun version(platform: String): AppVersionResponse
}

internal class RemoteAppVersionApi(
    private val baseApi: BaseApi,
    private val path: String,
) : AppVersionApi {
    override suspend fun version(platform: String): AppVersionResponse =
        baseApi.get(path, query = mapOf("platform" to platform))
}

/** 서버 없이 업데이트 안내를 확인할 때: DataModule 의 AppVersionApi 를 이것으로 바꾼다(기본 99.0.0 → 강제 안내, 수 버전만 올리면 권장) */
class SampleAppVersionApi(
    private val response: AppVersionResponse = AppVersionResponse(latestVersion = "99.0.0"),
) : AppVersionApi {
    override suspend fun version(platform: String) = response
}

/** 업데이트 상태. Swift: `onEnum(of: status)` → `.upToDate` / `.optional(let o)` / `.required(let r)` */
sealed interface AppUpdateStatus {
    data object UpToDate : AppUpdateStatus

    /** 새 버전이 있다(수 버전만 다르다) — "나중에" 가능 */
    data class Optional(val latestVersion: String, val storeUrl: String?) : AppUpdateStatus

    /** 이 버전은 더 이상 쓸 수 없다 — 닫을 수 없는 안내. [minimumVersion]: 올려야 하는 버전 */
    data class Required(val minimumVersion: String, val storeUrl: String?) : AppUpdateStatus
}

/**
 * 앱 시작 때 [check] 한 번 → 루트 화면이 [status]·[notice] 를 보고 안내한다. 확인 실패(네트워크 등)는 조용히 넘어간다.
 *
 * 버전 정책(DEC-132, `major.minor.patch`):
 * - 최신 버전과 **주·부(major·minor)** 가 다르면 → 강제(닫기 없음)
 * - **수(patch)** 만 다르면 → 권장("나중에" 가능, 나중에 한 그 버전은 다시 묻지 않는다)
 * - 서버 `minimumVersion` 보다 낮으면 수 버전 차이여도 강제(긴급 수정용)
 *
 * 공지: 업데이트 안내가 없을 때 [notice] 를 보인다(점검은 닫을 수 없다).
 *
 * @param currentVersion 이 앱 버전(AppConfig.APP_VERSION_NAME — env app.versionName)
 * @param api null 이면 확인하지 않는다(env update.checkPath 비움)
 */
class AppUpdateManager(
    private val api: AppVersionApi?,
    private val storage: LocalStorage,
    private val currentVersion: String,
    private val platform: String,
    private val scope: CoroutineScope,
    /** 응답의 `features` 를 넘긴다(28-2) */
    private val featureFlags: FeatureFlags? = null,
) {
    private val log = Logger.withTag("AppUpdate")

    private val _status = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.UpToDate)
    val status: StateFlow<AppUpdateStatus> = _status.asStateFlow()

    private val _notice = MutableStateFlow<AppNotice?>(null)
    /** 보여 줄 공지(없으면 null). Swift: `IOSAppGraph.shared.appUpdateManager().notice` */
    val notice: StateFlow<AppNotice?> = _notice.asStateFlow()

    fun check() {
        val api = api ?: return
        scope.launch {
            try {
                val response = api.version(platform)
                featureFlags?.update(response.features)
                _status.value = evaluate(response)
                _notice.value = response.notice?.takeIf { it.blocking || storage.getString(KEY_NOTICE_SEEN) != it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log.i(e) { "업데이트 확인 실패 — 건너뜀" }
            }
        }
    }

    /** 선택 업데이트 "나중에" */
    fun dismissOptional() {
        val optional = _status.value as? AppUpdateStatus.Optional ?: return
        _status.value = AppUpdateStatus.UpToDate
        scope.launch { storage.setString(KEY_DISMISSED, optional.latestVersion) }
    }

    /** 공지 "확인"(점검은 닫을 수 없다) */
    fun dismissNotice() {
        val notice = _notice.value ?: return
        if (notice.blocking) return
        _notice.value = null
        scope.launch { storage.setString(KEY_NOTICE_SEEN, notice.id) }
    }

    internal suspend fun evaluate(response: AppVersionResponse): AppUpdateStatus {
        val minimum = response.minimumVersion
        if (minimum != null && compareVersions(currentVersion, minimum) < 0) {
            return AppUpdateStatus.Required(minimum, response.storeUrl)
        }
        val latest = response.latestVersion
        if (latest == null || compareVersions(currentVersion, latest) >= 0) return AppUpdateStatus.UpToDate
        if (differsBeyondPatch(currentVersion, latest)) return AppUpdateStatus.Required(latest, response.storeUrl)
        if (storage.getString(KEY_DISMISSED) != latest) return AppUpdateStatus.Optional(latest, response.storeUrl)
        return AppUpdateStatus.UpToDate
    }

    companion object {
        internal const val KEY_DISMISSED = "update.dismissed.version"
        internal const val KEY_NOTICE_SEEN = "notice.seen.id"

        /** 주·부 버전(앞 두 자리)이 다른지 — "1.3.0" vs "1.2.9" → true, "1.2.5" vs "1.2.0" → false */
        fun differsBeyondPatch(a: String, b: String): Boolean {
            fun majorMinor(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 0 } to it.getOrElse(1) { 0 } }
            return majorMinor(a) != majorMinor(b)
        }

        /** "1.2.10" > "1.2.9". 숫자가 아닌 꼬리(-dev, -beta)는 무시, 빈 자리는 0 */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val x = parts(a)
            val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val diff = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
