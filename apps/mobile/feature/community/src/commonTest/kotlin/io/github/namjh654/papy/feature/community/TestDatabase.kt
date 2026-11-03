package io.github.namjh654.papy.feature.community

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.github.namjh654.papy.feature.community.cache.CommunityCache
import io.github.namjh654.papy.feature.community.db.CommunityDatabase
import kotlin.coroutines.EmptyCoroutineContext

/** 테스트마다 새 메모리 DB(Android host: JDBC, iOS: native) */
expect fun testSqlDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver

/** DB 작업을 부른 코루틴에서 그대로 한다(디스패처를 바꾸지 않는다) → 테스트 스케줄러 순서가 그대로 */
fun testCommunityCache() = CommunityCache(CommunityDatabase(testSqlDriver(CommunityDatabase.Schema)), context = EmptyCoroutineContext)
