package template.tooling

import java.io.File

/**
 * 템플릿을 새 앱으로 바꾼다: 패키지명 · 앱 이름 · iOS 번들 ID · Apple Team ID (docs/template-setup.md §1).
 *
 * 1. 패키지 디렉터리 이동: `**` + `/src/<sourceSet>/kotlin/<현재 패키지>` → 새 패키지
 * 2. 텍스트 치환 `<현재 패키지>` / `<현재 경로>` → 새 값 (kt·kts·xml·swift·xcconfig·md·toml·json·yml·properties, .orca 기록 제외)
 *    → Android applicationId·namespace, KMP namespace, 저장소 이름(DataStore·Keychain·UserDefaults suite)도 함께 바뀐다
 * 3. 앱 이름: env/common.properties app.name(Android·iOS 표시 이름), iOS PRODUCT_NAME, Gradle rootProject.name
 * 4. iOS 번들 ID(+ 환경 접미사), Team ID (xcconfig + pbxproj DEVELOPMENT_TEAM = "$(TEAM_ID)"), Env.generated.xcconfig 재생성
 * 5. 남은 옛 이름 흔적 출력
 *
 * 현재 패키지는 AppKoin.kt 에서 읽으므로 여러 번 실행해도 된다. 루트 Gradle 빌드 밖(build-logic tooling)에서
 * 실행하므로 빌드 중에 자기 소스·settings 를 옮기는 문제가 없다(DEC-057).
 * 이미 배포한 앱에는 쓰지 않는다: 패키지·번들 ID·저장소 이름이 바뀌면 기존 설치 데이터와 이어지지 않는다.
 */
class RenameApp(
    private val repo: Repo,
    private val newPackage: String,
    private val appName: String,
    bundleId: String?,
    private val teamId: String?,
    private val dryRun: Boolean,
) {
    private val oldPackage = repo.basePackage
    private val bundle = bundleId ?: newPackage
    private val project = appName.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { newPackage.substringAfterLast('.') }
    private val changes = mutableListOf<String>()

    init {
        require(Regex("""[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+""").matches(newPackage)) { "package 는 소문자 점 표기여야 한다 (예: com.acme.shop)" }
        require(newPackage != oldPackage && !newPackage.startsWith("$oldPackage.") && !oldPackage.startsWith("$newPackage.")) {
            "기존 패키지($oldPackage)와 겹치지 않는 새 패키지를 쓴다"
        }
    }

    fun run(): String {
        val oldPath = oldPackage.replace('.', '/')
        val newPath = newPackage.replace('.', '/')

        // 1. 패키지 디렉터리 이동
        sourceRoots().forEach { kotlinDir ->
            val src = kotlinDir.resolve(oldPath)
            if (!src.isDirectory) return@forEach
            val dst = kotlinDir.resolve(newPath)
            changes += "move ${rel(src)} → ${rel(dst)}"
            if (!dryRun) {
                dst.parentFile.mkdirs()
                check(src.renameTo(dst)) { "이동 실패: ${rel(src)}" }
                var parent = src.parentFile
                while (parent != kotlinDir && parent.listFiles().isNullOrEmpty()) {
                    parent.delete()
                    parent = parent.parentFile
                }
            }
        }

        // 2. 텍스트 치환
        textFiles().forEach { f ->
            val text = f.readText()
            val new = text.replace(oldPackage, newPackage).replace(oldPath, newPath)
            if (new != text) {
                changes += "edit ${rel(f)}"
                if (!dryRun) f.writeText(new)
            }
        }

        // 3·4. 앱 이름 · 번들 ID · Team ID
        // 앱 이름은 env/common.properties 의 app.name (환경 접미사는 env/<env>.properties 의 app.nameSuffix)
        sub(EnvConfig.COMMON, """^app\.name=.*$""", "app.name=${escape(appName)}")
        sub("settings.gradle.kts", """^rootProject\.name = ".*"$""", """rootProject.name = "$project"""")
        val xcconfig = "iosApp/Configuration/Config.xcconfig"
        sub(xcconfig, "^PRODUCT_NAME=.*$", "PRODUCT_NAME=${escape(appName)}")
        sub(xcconfig, "^PRODUCT_BUNDLE_IDENTIFIER=.*$", "PRODUCT_BUNDLE_IDENTIFIER=$bundle\\$(BUNDLE_ID_SUFFIX)")
        sub(xcconfig, "^TEAM_ID=.*$", "TEAM_ID=${teamId.orEmpty()}")
        sub("iosApp/iosApp.xcodeproj/project.pbxproj", """DEVELOPMENT_TEAM = [^;]*;""", "DEVELOPMENT_TEAM = \"\\$(TEAM_ID)\";")
        // 앱 이름이 바뀌었으니 iOS 환경 xcconfig(표시 이름) 재생성
        if (!dryRun) repo.write(EnvConfig.IOS_XCCONFIG, EnvConfig(repo).iosXcconfig())
        changes += "edit ${EnvConfig.IOS_XCCONFIG} (재생성)"

        // 5. 남은 흔적
        val leftovers = textFiles().filterNot { rel(it).startsWith("build-logic/tooling/") }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (LEFTOVER.containsMatchIn(line)) "${rel(f)}:${i + 1}: ${line.trim().take(100)}" else null
            }
        }
        return buildString {
            appendLine((if (dryRun) "[dry-run] " else "") + "$oldPackage → $newPackage, 앱 이름 '$appName', 번들 ID $bundle")
            changes.forEach { appendLine("  $it") }
            if (leftovers.isNotEmpty()) {
                appendLine()
                appendLine("확인할 옛 이름 흔적 ${leftovers.size}곳 (필요하면 직접 수정):")
                leftovers.take(40).forEach { appendLine("  $it") }
            }
            appendLine()
            append("다음: scripts/orca.sh check → docs/template-setup.md 의 Firebase·API URL 단계")
        }
    }

    private fun sub(path: String, pattern: String, replacement: String) {
        val f = repo.file(path)
        val text = f.readText()
        val regex = Regex(pattern, RegexOption.MULTILINE)
        require(regex.containsMatchIn(text)) { "패턴을 찾지 못함: $path $pattern" }
        changes += "edit $path (${pattern.take(30)}…)"
        if (!dryRun) f.writeText(regex.replace(text, replacement))
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("$", "\\$")

    private fun rel(f: File) = f.relativeTo(repo.root).invariantSeparatorsPath

    private fun walk(): Sequence<File> = repo.root.walkTopDown()
        .onEnter { dir -> dir == repo.root || dir.name !in SKIP_DIRS }

    /** `…/src/<sourceSet>/kotlin` 디렉터리들 */
    private fun sourceRoots(): List<File> = walk()
        .filter { it.isDirectory && it.name == "kotlin" && it.parentFile?.parentFile?.name == "src" }
        .toList()

    private fun textFiles(): List<File> = walk().filter { it.isFile && it.extension in TEXT_EXT }.toList()

    companion object {
        private val TEXT_EXT = setOf("kt", "kts", "xml", "swift", "xcconfig", "md", "toml", "json", "yml", "properties", "tmpl", "sh")
        private val SKIP_DIRS = setOf(".git", ".gradle", ".kotlin", ".idea", "build", ".orca", "node_modules", "xcuserdata")
        private val LEFTOVER = Regex("""\bELSA\b|elsaapp|elsa app|LWDTN6PM35""")
    }
}
