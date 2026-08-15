package org.example.jinhhyu.homeset

import kotlin.test.Test
import kotlin.test.assertEquals

class HomesPaginationTest {
    @Test
    fun `five homes fit on each inventory page`() {
        assertEquals(1, homesPageCount(0))
        assertEquals(1, homesPageCount(5))
        assertEquals(2, homesPageCount(6))
        assertEquals(3, homesPageCount(11))
    }
}
