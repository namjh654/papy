package cloud.elsa.app.feature.chat

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.inMemoryDriver

actual fun testSqlDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver = inMemoryDriver(schema)
