package io.github.namjh654.papy.core.data.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val databaseDispatcher: CoroutineDispatcher = Dispatchers.IO
