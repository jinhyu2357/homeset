package org.example.jinhhyu.homeset

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalHomeLimitTest {
    @Test
    fun `new personal home is rejected when personal limit is full`() {
        assertTrue(
            wouldExceedPersonalHomeLimit(
                maxHomesPerPlayer = 3,
                personalHomeCount = 3,
                homeAlreadyPersonal = false,
                visibilityOptionIsShared = false
            )
        )
    }

    @Test
    fun `existing personal home can be updated when personal limit is full`() {
        assertFalse(
            wouldExceedPersonalHomeLimit(
                maxHomesPerPlayer = 3,
                personalHomeCount = 3,
                homeAlreadyPersonal = true,
                visibilityOptionIsShared = false
            )
        )
    }

    @Test
    fun `shared home can be saved when personal limit is full`() {
        assertFalse(
            wouldExceedPersonalHomeLimit(
                maxHomesPerPlayer = 3,
                personalHomeCount = 3,
                homeAlreadyPersonal = false,
                visibilityOptionIsShared = true
            )
        )
    }

    @Test
    fun `shared home converted to personal is rejected when personal limit is full`() {
        assertTrue(
            wouldExceedPersonalHomeLimit(
                maxHomesPerPlayer = 3,
                personalHomeCount = 3,
                homeAlreadyPersonal = false,
                visibilityOptionIsShared = false
            )
        )
    }

    @Test
    fun `zero disables the personal home limit`() {
        assertFalse(
            wouldExceedPersonalHomeLimit(
                maxHomesPerPlayer = 0,
                personalHomeCount = 3,
                homeAlreadyPersonal = false,
                visibilityOptionIsShared = false
            )
        )
    }
}
