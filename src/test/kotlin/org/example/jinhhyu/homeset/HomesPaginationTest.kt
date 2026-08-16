package org.example.jinhhyu.homeset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomesPaginationTest {
    @Test
    fun `five homes fit on each inventory page`() {
        assertEquals(1, homesPageCount(0))
        assertEquals(1, homesPageCount(5))
        assertEquals(2, homesPageCount(6))
        assertEquals(3, homesPageCount(11))
    }

    @Test
    fun `shared homes use a distinct admin-editable page`() {
        assertEquals("Private Homes", HomesViewMode.PERSONAL.title)
        assertTrue(HomesViewMode.PERSONAL.allowsDeletion)
        assertFalse(HomesViewMode.PERSONAL.allowsSharedManagement)
        assertEquals("Shared Homes", HomesViewMode.SHARED.title)
        assertFalse(HomesViewMode.SHARED.allowsDeletion)
        assertTrue(HomesViewMode.SHARED.allowsSharedManagement)
    }
}
