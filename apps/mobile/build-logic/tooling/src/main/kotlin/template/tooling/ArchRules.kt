package template.tooling

/**
 * 아키텍처 규칙 (docs/conventions.md). 모듈 의존은 Gradle 이 준 실제 그래프로, 나머지는 소스로 검사한다.
 *
 * 1. feature 끼리 의존하지 않는다 (그래프 + 다른 feature 패키지 import)
 * 2. core 는 feature 를 모른다 (그래프 + import)
 * 3. commonMain 은 UI 프레임워크를 import 하지 않는다 (Compose, android.*, UIKit, SwiftUI)
 * 4. 추상 아닌 ViewModel·Repository 는 같은 모듈 commonTest 에 <이름>Test 가 있다
 * 5. feature 디렉터리는 모두 Gradle 프로젝트이고, :shared 가 의존(export)하며, AppKoin.appModules 에 Koin 모듈이 있다
 * 6. 파일 배치·이름 (docs/conventions.md §3): KMP package = 디렉터리, Android Kotlin 파일은 PascalCase 이고 화면은
 *    screen/<소문자>/<Name>Screen.kt, iOS Swift 파일은 소문자 점 표기이고 view/ 아래는 <이름>.screen.swift
 *
 * @param graph Gradle 프로젝트 경로 → 그 프로젝트가 의존하는 프로젝트 경로들 (모든 configuration)
 */
class ArchRules(private val repo: Repo, private val graph: Map<String, Set<String>>) {

    data class Result(val errors: List<String>, val summary: String)

    fun run(): Result {
        val errors = mutableListOf<String>()

        // 1·2. 그래프
        graph.forEach { (project, deps) ->
            val featureDeps = deps.filter { it.startsWith(":feature:") && it != project }
            when {
                project.startsWith(":core:") && featureDeps.isNotEmpty() -> errors += "[2] $project 가 feature 에 의존: $featureDeps"
                project.startsWith(":feature:") && featureDeps.isNotEmpty() -> errors += "[1] $project 가 다른 feature 에 의존: $featureDeps"
            }
        }

        // 1~4. 소스
        val features = mutableListOf<String>()
        for (group in listOf("core", "feature")) {
            val dirs = repo.file(group).listFiles { f -> f.resolve("build.gradle.kts").isFile }?.sortedBy { it.name } ?: continue
            for (dir in dirs) {
                val name = dir.name
                val path = ":$group:$name"
                if (group == "feature") features += name
                val tested = dir.resolve("src/commonTest").walkTopDown().filter { it.extension == "kt" }.map { it.nameWithoutExtension }.toSet()
                dir.resolve("src").listFiles()?.forEach { sourceSet ->
                    sourceSet.resolve("kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { src ->
                        val rel = src.relativeTo(repo.root).invariantSeparatorsPath
                        val text = src.readText()
                        if (sourceSet.name == "commonMain" && FORBIDDEN_COMMON_IMPORTS.containsMatchIn(text)) {
                            errors += "[3] $rel: commonMain 에서 UI 프레임워크 import"
                        }
                        IMPORT_FEATURE.findAll(text).map { it.groupValues[1] }.distinct().forEach { imported ->
                            if (group == "core") errors += "[2] $rel: core 가 feature.$imported import"
                            else if (imported != name) errors += "[1] $rel: feature.$name 가 feature.$imported import"
                        }
                        if (sourceSet.name == "commonMain") {
                            CLASS_DECL.findAll(text).map { it.groupValues[1] }.forEach { cls ->
                                if ("${cls}Test" !in tested) errors += "[4] $rel: ${cls}Test 없음 ($group/$name/src/commonTest)"
                            }
                        }
                    }
                }
                if (path !in graph) errors += "[5] $path 가 Gradle 프로젝트가 아님 (settings.gradle.kts include 누락)"
            }
        }

        // 6. 파일 배치·이름
        errors += placementErrors()

        // 5. 등록
        val sharedDeps = graph[":shared"].orEmpty()
        val appKoin = repo.file("shared/src/commonMain/kotlin/${repo.basePath}/di/AppKoin.kt").readText()
        for (name in features) {
            if (":feature:$name" !in sharedDeps) errors += "[5] :shared 가 :feature:$name 에 의존하지 않음 (shared/build.gradle.kts exported)"
            if (!Regex("""\b${name}Module\b""").containsMatchIn(appKoin)) errors += "[5] AppKoin.appModules 에 ${name}Module 없음"
        }
        return Result(errors, "아키텍처 규칙 통과 (feature ${features.size}개: ${features.joinToString(", ")})")
    }

    private fun placementErrors(): List<String> {
        val errors = mutableListOf<String>()
        // KMP: package 선언 = kotlin/ 아래 디렉터리
        for (group in listOf("core", "feature", "shared")) {
            val roots = if (group == "shared") listOf(repo.file("shared")) else repo.file(group).listFiles()?.toList().orEmpty()
            roots.forEach { module ->
                module.resolve("src").listFiles()?.forEach { sourceSet ->
                    val kotlinRoot = sourceSet.resolve("kotlin")
                    kotlinRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { src ->
                        val expected = src.parentFile.relativeTo(kotlinRoot).invariantSeparatorsPath.replace('/', '.')
                        val declared = PACKAGE.find(src.readText())?.groupValues?.get(1).orEmpty()
                        if (declared != expected) errors += "[6] ${src.relativeTo(repo.root).invariantSeparatorsPath}: package $declared ≠ 디렉터리 $expected"
                    }
                }
            }
        }
        // Android: PascalCase 파일, 화면은 screen/<소문자>/<Name>Screen.kt
        val android = repo.file(repo.androidSrc)
        android.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { src ->
            val rel = src.relativeTo(android).invariantSeparatorsPath
            if (!PASCAL_FILE.matches(src.name)) errors += "[6] ${repo.androidSrc}/$rel: Android 파일은 PascalCase.kt"
            if (rel.startsWith("screen/") && src.name.endsWith("Screen.kt") && !ANDROID_SCREEN.matches(rel)) {
                errors += "[6] ${repo.androidSrc}/$rel: 화면은 screen/<소문자>/<Name>Screen.kt"
            }
        }
        // iOS: 소문자 점 표기, view/ 아래는 .screen.swift
        val ios = repo.file("iosApp/iosApp")
        ios.walkTopDown().filter { it.isFile && it.extension == "swift" }.forEach { src ->
            val rel = src.relativeTo(ios).invariantSeparatorsPath
            if (!SWIFT_FILE.matches(src.name)) errors += "[6] iosApp/iosApp/$rel: iOS 파일은 소문자 점 표기(예: login.screen.swift)"
            if (rel.startsWith("view/") && !src.name.endsWith(".screen.swift")) errors += "[6] iosApp/iosApp/$rel: view/ 아래는 <이름>.screen.swift"
        }
        return errors
    }

    companion object {
        private val PACKAGE = Regex("""^package ([\w.]+)""", RegexOption.MULTILINE)
        private val PASCAL_FILE = Regex("""[A-Z][A-Za-z0-9]*(\.(android|ios))?\.kt""")
        private val ANDROID_SCREEN = Regex("""screen/[a-z][a-z0-9]*/[A-Z][A-Za-z0-9]*Screen\.kt""")
        private val SWIFT_FILE = Regex("""[a-z0-9]+(\.[a-z0-9]+)*\.swift""")
        private val FORBIDDEN_COMMON_IMPORTS = Regex("""^import (androidx\.compose|android\.|platform\.UIKit|SwiftUI)""", RegexOption.MULTILINE)
        private val IMPORT_FEATURE = Regex("""^import (?:[a-z0-9_]+\.)+feature\.(\w+)""", RegexOption.MULTILINE)
        private val CLASS_DECL = Regex("""^(?:internal |public )?(?:open )?class (\w+(?:ViewModel|Repository))\b""", RegexOption.MULTILINE)
    }
}
