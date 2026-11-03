import app.cash.sqldelight.gradle.SqlDelightExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `papy.kmp.database` — 로컬 DB(SQLDelight, DEC-130)를 가진 feature 모듈. `papy.kmp.feature` 다음에 적용한다.
 *
 * - DB 이름은 모듈에서 정한다: `:feature:chat` → `ChatDatabase`(패키지 `io.github.namjh654.papy.feature.chat.db`)
 * - 테이블·쿼리: `src/commonMain/sqldelight/<패키지 경로>/<이름>.sq`, 스키마 변경: 같은 폴더 `<버전>.sqm` (docs/local-db.md)
 * - 테스트용 메모리 DB 드라이버: androidHostTest 는 JDBC, iosTest 는 native (commonTest 의 expect `testSqlDriver`)
 */
class KmpDatabaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("app.cash.sqldelight")

        val databaseName = path.substringAfterLast(':').split('-', '_')
            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } } + "Database"
        extensions.configure<SqlDelightExtension> {
            databases.create(databaseName) {
                packageName.set("$papyNamespace.db")
                // 출시한 스키마 보관(<버전>.db) — 스키마를 바꾸기 전에 generate…Schema 로 만든다 (docs/local-db.md §3)
                schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
                // 스키마 변경(.sqm)이 실제 스키마와 맞는지 빌드에서 확인
                verifyMigrations.set(true)
            }
        }

        extensions.configure<KotlinMultiplatformExtension> {
            // 기본 계층(iosTest)은 나중에 만들어질 수 있어 이름으로 기다린다
            val testDrivers = mapOf("androidHostTest" to "sqldelight-sqlite-driver", "iosTest" to "sqldelight-native-driver")
            sourceSets.matching { it.name in testDrivers }.configureEach {
                val driver = libs.lib(testDrivers.getValue(name))
                dependencies { implementation(driver) }
            }
        }
    }
}
