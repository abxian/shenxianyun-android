package com.github.kr328.clash

import org.junit.Assert.*
import org.junit.Test

class StartupRefreshPolicyTest {
    @Test fun sameCodeRecentAuthorityIsSharedWithFirstStart() {
        assertTrue(canReuseAccessCheck("current", "current", 1_500))
        assertTrue(canReuseAccessCheck("current", "current", 30_000))
    }
    @Test fun oldOrDifferentAuthorityCannotBeReused() {
        assertFalse(canReuseAccessCheck("old", "current", 1_500))
        assertFalse(canReuseAccessCheck("current", "current", 30_001))
        assertFalse(canReuseAccessCheck("current", "current", -1))
        assertFalse(canReuseAccessCheck("", "", 0))
    }
    @Test fun openingAlwaysRefreshesEvenWhenBackendVersionDidNotChange() {
        assertTrue(shouldRefreshAccessSubscription(true, false, false, 5, 5, "same", "same"))
        assertFalse(shouldRefreshAccessSubscription(false, false, false, 5, 5, "same", "same"))
    }
}
