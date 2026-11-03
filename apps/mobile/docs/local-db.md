# 로컬 DB (SQLDelight) — DEC-130

서버가 원본이고, 로컬 DB 는 **다시 켰을 때 바로 보일 것**과 **오프라인일 때 보일 것**, **아직 서버에 못 보낸 것**만 둔다.
키-값 설정은 지금처럼 `LocalStorage`(DataStore·UserDefaults), 토큰은 `SecureStorage`(Keystore·Keychain). 목록·메시지처럼 행이 많은 데이터만 DB 에 둔다.

## 1. 구조

| 위치 | 하는 일 |
|---|---|
| `core:data` `database/` | `SqlDriverFactory`(Android `AndroidSqliteDriver` → 앱 `databases/<이름>.db`, iOS `NativeSqliteDriver` → Application Support), `databaseDispatcher`(IO), `LocalCache`(사용자별 데이터 정리 계약)·`LocalCacheCleaner` |
| `build-logic` `papy.kmp.database` | SQLDelight 플러그인 + DB 이름(`:feature:chat` → `ChatDatabase`, 패키지 `io.github.namjh654.papy.feature.chat.db`) + 스키마 변경 확인 + 테스트용 메모리 드라이버 의존성 |
| 기능 모듈 | 테이블·쿼리(`.sq`), `XCache`(DB 를 감싼 클래스, `LocalCache`), Repository 가 캐시를 쓴다 |
| `shared` | iOS framework 에 `-lsqlite3`(시스템 sqlite) |

- **DB 는 기능마다 따로**(파일도 따로): 기능 모듈을 지우면 DB 도 같이 사라지고, 다른 기능의 스키마를 건드리지 않는다
- **ViewModel 은 DB 를 모른다**: Repository 가 "받으면 저장, 화면 진입 때 저장된 것 먼저"를 맡는다
- **DB 실패는 화면을 막지 않는다**: Repository 는 캐시 읽기 실패 → 빈 목록, 쓰기 실패 → 넘어감
- **로그아웃(탈퇴 포함)** 하면 모든 `LocalCache.clear()`, **다른 사용자로 로그인**하면(토큰 만료 뒤 다른 계정) 먼저 지운다

## 2. 새 기능에 DB 넣기

**새 기능이면 생성기 한 번**: `./gradlew -p build-logic :tooling:run --args="newFeature memo --db"` — 아래 1~6 을 모두 만든다(목록 첫 페이지 테이블, 캐시, 저장소 저장, Paginator cache, 테스트용 메모리 DB). 이미 있는 기능에 붙일 때는 아래 순서대로.


1. `feature/<이름>/build.gradle.kts` 에 `id("papy.kmp.database")` (`papy.kmp.feature` 다음)
2. 테이블·쿼리: `feature/<이름>/src/commonMain/sqldelight/io/github/namjh654/papy/feature/<이름>/db/<Name>.sq`
   - 서버 모델은 `payload TEXT`(AppJson)로 통째로, 정렬·삭제·검색에 쓰는 값만 열로 뺀다 → 서버 필드가 늘어도 스키마 변경이 없다
   - 예시: `feature/chat/.../db/Chat.sq`(목록·메시지·보낼 대기열), `feature/community/.../db/Community.sq`
3. 캐시 클래스 `cache/<Name>Cache.kt`: DB 를 받고 `LocalCache` 를 구현, 읽기·쓰기는 `withContext(context)`(기본 `databaseDispatcher`), 여러 줄 쓰기는 `database.transaction { }`
4. Koin(`di/<Name>Module.kt`):
   ```kotlin
   single { NameDatabase(get<SqlDriverFactory>().create(NameDatabase.Schema, "name")) }
   single { NameCache(database = get()) } bind LocalCache::class
   single { NameRepository(api = get(), cache = get()) }
   ```
5. 목록 첫 페이지를 저장했으면 ViewModel 의 `Paginator(cache = repository::cachedItems) { page -> repository.items(page) }`
   → 저장된 목록이 먼저 보이고(새로고침 표시), 받지 못하면 그대로 두고 위에 짧게 오류
6. 테스트: `commonTest` 에 `expect fun testSqlDriver(schema)` + `androidHostTest`(JDBC `IN_MEMORY`)·`iosTest`(`inMemoryDriver`) — `feature/chat/src/*Test/.../TestDatabase*.kt` 를 복사.
   테스트용 캐시는 `context = EmptyCoroutineContext`(부른 코루틴에서 그대로 — 디스패처를 바꾸면 상태 순서가 달라진다)

## 3. 스키마 바꾸기

출시한 뒤 테이블을 바꾸면 사용자 기기의 DB 를 옮겨야 한다.

1. 바꾸기 전 스키마를 저장: `./gradlew :feature:<이름>:generateCommonMain<Name>DatabaseSchema` → `src/commonMain/sqldelight/databases/1.db` (처음 한 번)
2. `.sq` 를 새 모양으로 고치고, 같은 폴더에 `1.sqm`(1 → 2 로 가는 SQL: `ALTER TABLE …`, `CREATE TABLE …`)
3. `./gradlew :feature:<이름>:verifyCommonMain<Name>DatabaseMigration` — `papy.kmp.database` 가 켜 둔 확인. 빌드(`orca.sh check`)에서도 돈다
4. 드라이버가 앱 시작 때 버전을 보고 `.sqm` 을 차례로 적용한다

캐시만 담는 테이블이라 옮길 가치가 없으면 `.sqm` 에서 `DROP TABLE` 후 다시 만들어도 된다(다음에 서버에서 다시 받는다).
보낼 대기열처럼 **사용자가 만든 데이터**가 있는 테이블은 반드시 옮긴다.

## 4. 지금 저장하는 것

| 기능 | 저장 | 지우는 때 |
|---|---|---|
| 메시지 | 대화 목록 첫 페이지, 대화방마다 최근 200개, 못 보낸 글 메시지(대기열) | 로그아웃·다른 사용자 로그인. 대기열은 서버에 가면·말풍선을 지우면 |
| 커뮤니티 | 전체 글 목록 첫 페이지(수정·삭제·차단 반영) | 로그아웃·다른 사용자 로그인 |

- 못 보낸 글 메시지: 대화방을 다시 열면 자동으로 보내고, 대화방이 열린 채 다시 연결되면(`NetworkMonitor`) 실패한 것을 다시 보낸다
- 첨부(사진·파일)는 원본을 저장하지 않는다 — 실패한 첨부는 그 실행 안에서만 다시 보낼 수 있다(known-limitations §3)

## 5. 이미지 캐시

서버 이미지는 DB 가 아니라 이미지 로더가 캐시한다. Android 는 Coil(메모리 + 디스크), iOS 는 Nuke(DEC-131 — 메모리의 디코딩된 이미지 + 디스크 200MB, `PapyImagePipeline`).
화면은 `PapyRemoteImage`·`PapyAvatar` 만 쓴다. 업로드마다 주소가 바뀐다는 전제라 서버 캐시 헤더와 상관없이 디스크에 둔다 — 같은 주소로 이미지를 바꾸는 서버면 주소에 버전(`?v=`)을 붙인다.

## 6. 받은 파일 (30-1)

DB 가 아니라 파일로 둔다: `core:data` `files/FileDownloads` — 앱 전용 폴더(Android `filesDir/downloads`, iOS Application Support/downloads)에
`<주소 해시>/<이름>` 으로 받고, 목록(이름·크기·받은 시각)은 LocalStorage. 로그아웃하면 다른 캐시와 같이 지운다.
쓰는 곳에서는 `downloads.download(url, name, mimeType)` → 받은 파일을 이벤트로 넘기면 플랫폼이 연다(Android `files/FileOpener`, iOS `.quickLookPreview`).
