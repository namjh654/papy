// 메시지 (newFeature 로 생성, 19-1)
plugins {
    id("papy.kmp.feature")
    // 로컬 DB(DEC-130): ChatDatabase — src/commonMain/sqldelight/…/Chat.sq
    id("papy.kmp.database")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // 실시간 수신(WebSocket, DEC-126) — core 의 HttpClient 엔진을 그대로 쓰고 WebSockets 만 더한다
            implementation(libs.ktor.client.core)
            // 날짜 구분선(기기 시간대의 달력 날짜)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            // 파일 받기(FileDownloads) 테스트용 메모리 파일 시스템
            implementation(libs.okio)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
