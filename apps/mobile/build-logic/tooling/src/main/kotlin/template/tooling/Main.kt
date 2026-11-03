package template.tooling

import kotlin.system.exitProcess

private const val USAGE = """템플릿 도구 (JDK 만으로 실행, 루트 Gradle 빌드 밖에서 돈다)

  ./gradlew -p build-logic :tooling:run --args="newFeature <name> [--title-ko 제목] [--title-en Title] [--tab] [--db] [--dry-run]"
  ./gradlew -p build-logic :tooling:run --args="renameApp --package com.acme.shop --app-name 'Acme Shop' [--bundle-id …] [--team-id …] [--dry-run]"
  ./gradlew -p build-logic :tooling:run --args="designTokens [--check]"
  ./gradlew -p build-logic :tooling:run --args="checkStrings"
  ./gradlew -p build-logic :tooling:run --args="mockServer [--port 8765]"   (로컬 목업 서버, Ctrl+C 로 종료)

점검(designTokens --check · checkStrings · 아키텍처)은 루트에서 ./gradlew templateCheck 로 한 번에 돈다."""

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in setOf("-h", "--help", "help")) {
        println(USAGE); return
    }
    val command = args[0]
    val options = Options(args.drop(1))
    try {
        val repo = Repo.find()
        when (command) {
            "newFeature" -> println(
                NewFeature(
                    repo, options.positional(0, "name"), options["--title-ko"], options["--title-en"],
                    dryRun = options.flag("--dry-run"), tab = options.flag("--tab"), db = options.flag("--db"),
                ).run(),
            )
            "renameApp" -> println(
                RenameApp(
                    repo,
                    newPackage = options.required("--package"),
                    appName = options.required("--app-name"),
                    bundleId = options["--bundle-id"],
                    teamId = options["--team-id"],
                    dryRun = options.flag("--dry-run"),
                ).run(),
            )
            "designTokens" -> {
                val tokens = DesignTokens(repo)
                if (options.flag("--check")) {
                    val stale = tokens.stale()
                    if (stale.isNotEmpty()) fail("토큰 생성물이 오래되었습니다. ./gradlew generateDesignTokens 후 커밋:\n  " + stale.joinToString("\n  "))
                    println("디자인 토큰 최신")
                } else {
                    val written = tokens.generate()
                    println(if (written.isEmpty()) "디자인 토큰 최신 (변경 없음)" else written.joinToString("\n") { "생성: $it" })
                }
            }
            "checkStrings" -> {
                val result = StringsCheck(repo).run()
                if (result.errors.isNotEmpty()) fail("문자열 키 점검 실패:\n  " + result.errors.joinToString("\n  "))
                println(result.summary)
            }
            "mockServer" -> {
                val port = options["--port"]?.toIntOrNull() ?: 8765
                MockServer(port).start()
                println("목업 서버: http://127.0.0.1:$port  (env/dev.local.properties 에 api.baseUrl=http://127.0.0.1:$port)")
                Thread.currentThread().join()
            }
            else -> fail("알 수 없는 명령: $command\n\n$USAGE")
        }
    } catch (e: IllegalArgumentException) {
        fail(e.message ?: e.toString())
    } catch (e: IllegalStateException) {
        fail(e.message ?: e.toString())
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

/** `--key value`, `--flag`, 위치 인자 */
private class Options(args: List<String>) {
    private val values = mutableMapOf<String, String>()
    private val flags = mutableSetOf<String>()
    private val positional = mutableListOf<String>()

    init {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a.startsWith("--") && i + 1 < args.size && !args[i + 1].startsWith("--") && a != "--dry-run" && a != "--check" && a != "--tab" && a != "--db" -> {
                    values[a] = args[i + 1]; i += 2
                }
                a.startsWith("--") -> {
                    flags += a; i++
                }
                else -> {
                    positional += a; i++
                }
            }
        }
    }

    operator fun get(key: String): String? = values[key]
    fun flag(key: String) = key in flags
    fun required(key: String) = values[key] ?: throw IllegalArgumentException("$key 가 필요하다")
    fun positional(index: Int, label: String) = positional.getOrNull(index) ?: throw IllegalArgumentException("$label 이 필요하다")
}
