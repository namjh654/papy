package cloud.elsa.app.feature.community.di

import cloud.elsa.app.core.data.database.LocalCache
import cloud.elsa.app.core.data.database.SqlDriverFactory
import cloud.elsa.app.feature.community.cache.CommunityCache
import cloud.elsa.app.feature.community.db.CommunityDatabase
import cloud.elsa.app.feature.community.CommunityApi
import cloud.elsa.app.feature.community.CommunityRepository
import cloud.elsa.app.feature.community.CommunityViewModel
import cloud.elsa.app.feature.community.SampleCommunityApi
import cloud.elsa.app.feature.community.blocked.BlockedUsersViewModel
import cloud.elsa.app.feature.community.detail.PostDetailViewModel
import cloud.elsa.app.feature.community.editor.PostEditorViewModel
import cloud.elsa.app.feature.community.user.UserProfileViewModel
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
