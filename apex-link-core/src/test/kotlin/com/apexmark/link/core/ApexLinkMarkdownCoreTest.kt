package com.apexmark.link.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ApexLinkMarkdownCoreTest {

    private val core = ApexLinkMarkdownCore()

    @Test
    fun `inline styled markdown contains table border`() {
        val md = "| a | b |\n| --- | --- |\n| 1 | 2 |"
        val html = core.toInlineStyledHtml(md)
        assertTrue(html.contains("border=\"1\""))
    }
}
