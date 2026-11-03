// 설정 탭: 테마·언어·앱 정보·약관·로그아웃
plugins {
    id("elsa.kmp.feature")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            // 저장한 파일(FileDownloads) 테스트용 메모리 파일 시스템
            implementation(libs.okio)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
