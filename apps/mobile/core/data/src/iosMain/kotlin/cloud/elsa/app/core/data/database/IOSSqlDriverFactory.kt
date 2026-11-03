package cloud.elsa.app.core.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver

internal class IOSSqlDriverFactory : SqlDriverFactory {
    override fun create(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver =
        NativeSqliteDriver(schema = schema, name = "$name.db")
}
