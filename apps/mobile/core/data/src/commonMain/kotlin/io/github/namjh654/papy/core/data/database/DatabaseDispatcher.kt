package io.github.namjh654.papy.core.data.database

import kotlinx.coroutines.CoroutineDispatcher

/** DB 읽기·쓰기는 이 디스패처에서(`withContext(databaseDispatcher) { … }`) — 화면 스레드를 막지 않게. 두 플랫폼 모두 IO */
expect val databaseDispatcher: CoroutineDispatcher
