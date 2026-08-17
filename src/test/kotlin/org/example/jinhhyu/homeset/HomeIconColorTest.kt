package org.example.jinhhyu.homeset

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeIconColorTest {
    @Test
    fun `home icon cycles through every Minecraft bed color`() {
        assertEquals(16, HOME_ICON_COLORS.size)
        assertEquals("ORANGE", nextHomeIconColor("WHITE"))
        assertEquals("WHITE", nextHomeIconColor("BLACK"))
        assertEquals("WHITE", nextHomeIconColor("invalid"))
    }
}
