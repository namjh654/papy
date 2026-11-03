// 공통 기반: 에러(AppError·ErrorCode·UiError), UiState/UiEvent 계약, ViewModel 베이스
plugins {
    id("elsa.kmp.library")
}

kotlin {
    sourceSets.commonMain.dependencies {
        // feature ViewModel 이 androidx ViewModel 을 상속하므로 api
        api(libs.androidx.lifecycle.viewmodel)
        // 로그: 모든 모듈이 co.touchlab.kermit.Logger 로 쓴다. 수준·출력은 core:data configureLogging (env log.*)
        api(libs.kermit)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.datetime) // time/RelativeTime (달력 기준 날짜 차이)
    }
}
