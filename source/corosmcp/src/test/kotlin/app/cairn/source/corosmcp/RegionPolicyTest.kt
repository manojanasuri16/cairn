package app.cairn.source.corosmcp

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class RegionPolicyTest {
    @Test fun orderIsUsEuCnWithCnLast() {
        assertEquals(listOf(Region.US, Region.EU, Region.CN), RegionPolicy.ORDER)
        assertEquals(Region.US, RegionPolicy.start(null))
        assertEquals(Region.EU, RegionPolicy.start(Region.EU))
        assertEquals(Region.EU, RegionPolicy.next(Region.US))
        assertEquals(Region.CN, RegionPolicy.next(Region.EU))
        assertNull(RegionPolicy.next(Region.CN))
    }

    @Test fun onlyAccountNotFoundAdvances() {
        assertTrue(RegionPolicy.isWrongRegion(AuthorizationException("account_not_found")))
        assertTrue(RegionPolicy.isWrongRegion(AuthorizationException("USER_NOT_FOUND")))
        assertFalse(RegionPolicy.isWrongRegion(AuthorizationException("access_denied")))
        assertFalse(RegionPolicy.isWrongRegion(AuthorizationException("server_error")))
        assertFalse(RegionPolicy.isWrongRegion(CorosException("Timed out waiting for sign-in")))
        assertFalse(RegionPolicy.isWrongRegion(java.io.IOException("network")))
        assertFalse(RegionPolicy.isWrongRegion(CorosException("State mismatch")))
    }
}
