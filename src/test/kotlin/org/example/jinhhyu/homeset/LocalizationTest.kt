package org.example.jinhhyu.homeset

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationTest {
    @Test
    fun `language catalogs are selected without language-specific code`() {
        assertEquals("messages_ko", localizedHomeSection("messages", "ko"))
        assertEquals("messages_ja", localizedHomeSection("messages", "JA"))
        assertEquals("gui_fr", localizedHomeSection("gui", "fr"))
    }
}
