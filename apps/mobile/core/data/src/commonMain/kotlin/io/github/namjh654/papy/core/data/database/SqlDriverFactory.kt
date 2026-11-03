package io.github.namjh654.papy.core.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * 기능 모듈의 로컬 DB(SQLDelight, DEC-130) 드라이버를 만든다. 기능마다 자기 DB 파일(`<name>.db`)을 가진다.
 * ```
 * single { ChatDatabase(get<SqlDriverFactory>().create(ChatDatabase.Schema, "chat")) }
 * ```
 * - Android: AndroidSqliteDriver(앱 databases/ 폴더), iOS: NativeSqliteDriver(앱 Application Support)
 * - 스키마 버전이 오르면 드라이버가 migrations(`.sqm`)를 차례로 적용한다 — docs/local-db.md
 */
fun interface SqlDriverFactory {
    fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver
}
