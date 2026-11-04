include(":androidApp")
// Android UI 흐름 테스트(에뮬레이터·기기 필요): scripts/orca.sh uitest android
include(":androidUiTest")
include(":shared")
include(":core:common")
include(":core:data")
include(":feature:auth")
include(":feature:home")
include(":feature:example")
include(":feature:settings")
include(":feature:notice")
include(":feature:permission")
include(":feature:inbox")
include(":feature:search")
include(":feature:chat")
include(":feature:community")
include(":feature:documents")

rootProject.name = "papykmp"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // 카카오 SDK 는 자체 저장소에만 있다(DEC-119) — 그 그룹만 여기서 받는다
        maven("https://devrepo.kakao.com/nexus/content/groups/public/") {
            mavenContent { includeGroup("com.kakao.sdk") }
        }
    }
}

