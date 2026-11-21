package com.tomatoinsight.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberedAccountTest {
    @Test
    fun sessionRequiresOptInForThisAccount() {
        assertTrue(matchesRememberedAccount(true, "one", "", "one", null))
        assertFalse(matchesRememberedAccount(false, "one", "", "one", null))
        assertFalse(matchesRememberedAccount(true, "one", "farm@example.com", "two", "farm@example.com"))
    }

    @Test
    fun existingEmailOnlyPreferenceRequiresMatchingEmail() {
        assertTrue(matchesRememberedAccount(true, null, "Farm@example.com", "one", "farm@example.com"))
        assertFalse(matchesRememberedAccount(true, null, "", "one", null))
        assertFalse(matchesRememberedAccount(false, null, "farm@example.com", "one", "farm@example.com"))
    }
}
