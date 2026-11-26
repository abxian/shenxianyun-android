package com.github.kr328.clash.service.traffic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedStateRoutingTest {
    @Test
    fun structuredAccountDenialAndExplicitBackoffAreAuthoritative() {
        assertTrue(isManagedStateReply(200, true, 0))
        assertTrue(isManagedStateReply(403, true, 0))
        assertTrue(isManagedStateReply(404, true, 0))
        assertTrue(isManagedStateReply(429, false, 0))
        assertTrue(isManagedStateReply(503, false, 30_000))
    }

    @Test
    fun nonJsonGatewayErrorsCanFailOver() {
        assertFalse(isManagedStateReply(200, false, 0))
        assertFalse(isManagedStateReply(403, false, 0))
        assertFalse(isManagedStateReply(502, true, 0))
        assertFalse(isManagedStateReply(503, true, 0))
    }
}
