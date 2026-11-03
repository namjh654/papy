package io.github.namjh654.papy.feature.community.cache

import io.github.namjh654.papy.core.data.database.LocalCache
import io.github.namjh654.papy.core.data.database.databaseDispatcher
import io.github.namjh654.papy.core.data.serialization.AppJson
import io.github.namjh654.papy.feature.community.Post
import io.github.namjh654.papy.feature.community.db.CommunityDatabase
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * 커뮤니티 로컬 DB(SQLDelight `Community.sq`, DEC-130): 전체 글 목록 첫 페이지.
 * 수정·삭제·차단은 저장된 목록에도 반영해 다시 켰을 때 지운 글이 보이지 않게 한다. 로그아웃하면 [clear].
 */
class CommunityCache(
    private val database: CommunityDatabase,
    /** DB 를 읽고 쓰는 곳. 테스트는 EmptyCoroutineContext(부른 코루틴에서 그대로 — 테스트 스케줄러 순서 유지) */
    private val context: CoroutineContext = databaseDispatcher,
) : LocalCache {

    private val queries get() = database.communityQueries

    suspend fun posts(): List<Post> = withContext(context) { queries.posts().executeAsList().map(AppJson::decodeFromString) }

    /** 첫 페이지를 통째로 바꾼다 */
    suspend fun savePosts(items: List<Post>) = write {
        queries.deleteAll()
        items.forEachIndexed { index, post -> queries.insertPost(post.id, post.author.id, index.toLong(), AppJson.encodeToString(post)) }
    }

    /** 저장된 글이면 내용을 바꾼다(없으면 그대로 — 새 글은 다음에 받을 때 들어온다) */
    suspend fun update(post: Post) = write { queries.updatePayload(AppJson.encodeToString(post), post.id) }

    suspend fun remove(postId: String) = write { queries.deletePost(postId) }

    suspend fun removeAuthor(authorId: String) = write { queries.deleteByAuthor(authorId) }

    override suspend fun clear() = write { queries.deleteAll() }

    private suspend fun write(block: () -> Unit) = withContext(context) { database.transaction { block() } }
}
