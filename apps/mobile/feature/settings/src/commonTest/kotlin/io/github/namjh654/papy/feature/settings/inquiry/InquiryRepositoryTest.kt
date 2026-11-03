package io.github.namjh654.papy.feature.settings.inquiry

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class FakeInquiryApi : InquiryApi {
    val requests = mutableListOf<InquiryRequest>()
    var error: Throwable? = null
    override suspend fun submit(request: InquiryRequest) {
        error?.let { throw it }
        requests += request
    }
}

class InquiryRepositoryTest {

    private val api = FakeInquiryApi()
    private val repository = InquiryRepository(api, appVersion = "1.2.3", platform = "android")

    @Test
    fun sendsTrimmedContentWithAppInfo() = runTest {
        repository.submit(InquiryCategory.Bug, "  crash on start  ", " me@example.com ")

        assertEquals(InquiryRequest(InquiryCategory.Bug, "crash on start", "me@example.com", "1.2.3", "android"), api.requests.single())
    }

    @Test
    fun emptyEmailIsSentAsNull() = runTest {
        repository.submit(InquiryCategory.Other, "hi", "  ")
        assertEquals(null, api.requests.single().email)
    }

    @Test
    fun validatesBeforeSending() = runTest {
        assertEquals(ErrorCode.ContentRequired, assertFailsWith<AppError.Validation> { repository.submit(InquiryCategory.Bug, " ", "") }.code)
        val long = "x".repeat(InquiryRepository.MAX_CONTENT + 1)
        assertEquals(ErrorCode.ContentTooLong, assertFailsWith<AppError.Validation> { repository.submit(InquiryCategory.Bug, long, "") }.code)
        assertEquals(ErrorCode.InvalidEmail, assertFailsWith<AppError.Validation> { repository.submit(InquiryCategory.Bug, "hi", "nope") }.code)
        assertTrue(api.requests.isEmpty())
    }
}
