package io.github.namjh654.papy.core.data.database

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

internal class AndroidSqlDriverFactory(private val context: Context) : SqlDriverFactory {
    override fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver =
        AndroidSqliteDriver(schema = schema, context = context, name = "$name.db")
}
