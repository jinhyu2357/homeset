package org.example.jinhhyu.homeset

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeListMessageTest {
    @Test
    fun `home lists select populated and empty messages`() {
        assertEquals(
            "homes_list" to mapOf("homes" to "base, mine"),
            homeListMessage("homes", listOf("base", "mine"))
        )
        assertEquals(
            "shared_homes_empty" to emptyMap(),
            homeListMessage("shared_homes", emptyList())
        )
    }
}
