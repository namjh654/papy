package template.tooling

import java.io.File

/**
 * 저장소 경로와 앱 기본 패키지. 패키지는 하드코딩하지 않고 shared 의 AppKoin.kt(`<base>.di`)에서 읽는다
 * → renameApp 뒤에도 모든 도구가 그대로 동작한다.
 */
class Repo(val root: File) {

    val basePackage: String by lazy {
        val koin = root.resolve("shared/src/commonMain/kotlin").walkTopDown()
            .firstOrNull { it.isFile && it.name == "AppKoin.kt" && it.parentFile.name == "di" }
            ?: error("shared/src/commonMain/kotlin/**/di/AppKoin.kt 를 찾지 못함")
        Regex("""^package ([\w.]+)\.di$""", RegexOption.MULTILINE).find(koin.readText())?.groupValues?.get(1)
            ?: error("AppKoin.kt 에서 package 를 읽지 못함")
    }

    val basePath: String get() = basePackage.replace('.', '/')

    /** androidApp 의 앱 패키지 소스 루트 */
    val androidSrc: String get() = "androidApp/src/main/kotlin/$basePath"

    fun file(path: String): File = root.resolve(path)

    fun read(path: String): String = file(path).readText()

    fun write(path: String, text: String) {
        val f = file(path)
        f.parentFile.mkdirs()
        f.writeText(text)
    }

    companion object {
        /** 현재 디렉터리에서 위로 올라가며 settings.gradle.kts + shared/ 가 있는 곳을 찾는다 */
        fun find(start: File = File("").absoluteFile): Repo {
            var dir: File? = start
            while (dir != null) {
                if (dir.resolve("settings.gradle.kts").isFile && dir.resolve("shared").isDirectory) return Repo(dir)
                dir = dir.parentFile
            }
            error("저장소 루트를 찾지 못함 (settings.gradle.kts + shared/): $start")
        }
    }
}
