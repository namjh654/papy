package io.github.namjh654.papy.core.data.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val databaseDispatcher: CoroutineDispatcher = Dispatchers.IO
