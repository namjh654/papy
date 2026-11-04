package io.github.namjh654.papy.di

import io.github.namjh654.papy.core.data.settings.AppSettingsRepository
import io.github.namjh654.papy.feature.documents.DocumentsViewModel
import io.github.namjh654.papy.feature.documents.viewer.ViewerViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

/**
 * Swift에서 Koin 그래프에 접근하는 창구입니다.
 *
 * ViewModel은 factory이므로 호출할 때마다 새 인스턴스를 반환합니다.
 * 화면 수명과 맞추려면 Swift의 `SharedViewModel` 래퍼로 보관하세요.
 */
object IOSAppGraph : KoinComponent {

    /** 테마·언어. 앱 시작 때 저장값을 읽어 창에 적용한다 */
    fun appSettings(): AppSettingsRepository = get()

    /** 첫 화면: 최근 문서 */
    fun documentsViewModel(): DocumentsViewModel = get()

    /** 문서 보기. [ref] 는 보안 범위 북마크(base64), [name] 은 파일 이름 */
    fun viewerViewModel(ref: String, name: String): ViewerViewModel = get { parametersOf(ref, name) }
}
