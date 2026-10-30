package dev.justinsmith.interval.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreditsTest {
    @Test
    fun `reflow joins a paragraph's lines and keeps the breaks between paragraphs`() {
        val wrapped = "  Copyright 2017 The Authors\n(https://example.org)\n\n\nPREAMBLE\nThe goals are\n  to help.\n"
        val flowed = "Copyright 2017 The Authors (https://example.org)\n\nPREAMBLE The goals are to help."
        assertEquals(flowed, reflow(wrapped))
    }

    @Test
    fun `reflow treats a line of spaces as a blank line and takes Windows line ends`() {
        assertEquals("one two\n\nthree", reflow("one\r\ntwo\r\n   \r\nthree"))
    }

    @Test
    fun `every credit with a license text names a file under files`() {
        val files = (fontCredits + libraryCredits).mapNotNull { it.license.file }
        assertTrue(files.isNotEmpty())
        files.forEach { assertTrue(it.startsWith("files/") && it.endsWith(".txt"), it) }
    }
}
