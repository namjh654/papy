package io.github.namjh654.papy.feature.community

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual fun testSqlDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { schema.create(it) }
