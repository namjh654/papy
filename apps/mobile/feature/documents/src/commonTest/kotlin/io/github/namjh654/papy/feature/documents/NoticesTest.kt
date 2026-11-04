package io.github.namjh654.papy.feature.documents

import io.github.namjh654.papy.feature.documents.licenses.parseNotices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoticesTest {
    private val rule = "=".repeat(78)

    @Test
    fun splitsComponents() {
        val text = "Papy — Third-party notices\n\nPapy itself is licensed under PolyForm.\nComponents: 2\n" +
            "\n$rule\nfoo 1.0 (Rust crate)\nLicense: MIT\n$rule\nSource: https://crates.io/crates/foo/1.0\n\n--- LICENSE ---\nMIT text\n" +
            "\n$rule\nNoto Sans KR Regular (font)\nLicense: OFL-1.1\n$rule\n\n--- LICENSE: same text as foo ---\n"
        val notices = parseNotices(text)
        assertTrue(notices.header.startsWith("Papy — Third-party notices"))
        assertEquals(listOf("foo 1.0 (Rust crate)", "Noto Sans KR Regular (font)"), notices.items.map { it.title })
        assertEquals("MIT", notices.items[0].license)
        assertTrue(notices.items[0].text.contains("MIT text"))
        assertEquals("OFL-1.1", notices.items[1].license)
    }

    @Test
    fun emptyFileHasNoItems() {
        assertEquals(0, parseNotices("").items.size)
    }
}
