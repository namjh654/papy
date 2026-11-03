package io.github.namjh654.papy.feature.community.di

import io.github.namjh654.papy.core.data.database.LocalCache
import io.github.namjh654.papy.core.data.database.SqlDriverFactory
import io.github.namjh654.papy.feature.community.cache.CommunityCache
import io.github.namjh654.papy.feature.community.db.CommunityDatabase
import io.github.namjh654.papy.feature.community.CommunityApi
import io.github.namjh654.papy.feature.community.CommunityRepository
import io.github.namjh654.papy.feature.community.CommunityViewModel
import io.github.namjh654.papy.feature.community.SampleCommunityApi
import io.github.namjh654.papy.feature.community.blocked.BlockedUsersViewModel
import io.github.namjh654.papy.feature.community.detail.PostDetailViewModel
import io.github.namjh654.papy.feature.community.editor.PostEditorViewModel
import io.github.namjh654.papy.feature.community.user.UserProfileViewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** feature:community 의 Koin 모듈 */
val communityModule = module {
    // 서버가 준비되면 아래 줄을 이것으로 바꾼다: single<CommunityApi> { RemoteCommunityApi(baseApi = get()) }
    single<CommunityApi> { SampleCommunityApi() }
    // 화면 사이 변경 알림·차단 목록을 같이 쓰므로 하나
    // 로컬 DB(DEC-130): 로그아웃하면 LocalCache 로 지워진다
    single { CommunityDatabase(get<SqlDriverFactory>().create(CommunityDatabase.Schema, "community")) }
    single { CommunityCache(database = get()) } bind LocalCache::class
    single { CommunityRepository(api = get(), cache = get()) }
    factory { CommunityViewModel(repository = get()) }
    factory { (postId: String) -> PostDetailViewModel(postId = postId, repository = get()) }
    // 새 글은 "" 로 넘긴다(Koin 인자는 null 을 담지 못한다) → null
    factory { (postId: String) -> PostEditorViewModel(postId = postId.ifEmpty { null }, repository = get()) }
    factory { BlockedUsersViewModel(repository = get()) }
    factory { (userId: String) -> UserProfileViewModel(userId = userId, repository = get()) }
}
