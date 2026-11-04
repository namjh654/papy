package io.github.namjh654.papy.feature.documents.di

import io.github.namjh654.papy.feature.documents.DocumentsViewModel
import io.github.namjh654.papy.feature.documents.RecentDocuments
import io.github.namjh654.papy.feature.documents.viewer.ViewerViewModel
import kotlin.time.Clock
import org.koin.dsl.module

/** feature:documents 의 Koin 모듈. [io.github.namjh654.papy.feature.documents.DocumentEngine] 은 플랫폼이 넣는다. */
val documentsModule = module {
    single { RecentDocuments(storage = get(), now = { Clock.System.now().toEpochMilliseconds() }) }
    factory { DocumentsViewModel(recents = get()) }
    factory { (ref: String, name: String) -> ViewerViewModel(ref = ref, name = name, engine = get(), recents = get()) }
}
