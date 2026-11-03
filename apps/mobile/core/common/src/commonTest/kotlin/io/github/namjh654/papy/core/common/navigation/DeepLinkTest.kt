package io.github.namjh654.papy.core.common.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkTest {

    @Test
    fun customSchemeUsesHostAsPath() {
        assertEquals(DeepLink("example", mapOf("id" to "1")), DeepLink.parse("papy://example?id=1"))
        assertEquals(DeepLink("notice/detail"), DeepLink.parse("papy://notice/detail/"))
    }

    @Test
    fun httpDropsHost() {
        assertEquals(DeepLink("example", mapOf("id" to "7")), DeepLink.parse("https://app.acme.com/example?id=7#top"))
    }

    @Test
    fun relativePathAndDecoding() {
        assertEquals(
            DeepLink("search", mapOf("q" to "a b&c", "name" to "엘사")),
            DeepLink.parse("/search?q=a+b%26c&name=%EC%97%98%EC%82%AC"),
        )
    }

    @Test
    fun emptyPathIsNull() {
        assertNull(DeepLink.parse(""))
        assertNull(DeepLink.parse("papy://"))
        assertNull(DeepLink.parse("https://app.acme.com/"))
    }
}
