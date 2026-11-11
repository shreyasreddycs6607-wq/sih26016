package com.focusforce.app

import com.focusforce.app.permissions.Oem
import com.focusforce.app.permissions.PermissionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemTest {
    @Test fun detectsKnownOems() {
        assertEquals(Oem.XIAOMI, Oem.fromBuild("Xiaomi", "Redmi"))
        assertEquals(Oem.XIAOMI, Oem.fromBuild("Xiaomi", "POCO"))
        assertEquals(Oem.OPPO, Oem.fromBuild("OPPO", "OPPO"))
        assertEquals(Oem.VIVO, Oem.fromBuild("vivo", "iQOO"))
        assertEquals(Oem.SAMSUNG, Oem.fromBuild("samsung", "samsung"))
    }

    @Test fun realmeWinsOverOppoWhenBothAppear() {
        assertEquals(Oem.REALME, Oem.fromBuild("OPPO", "realme"))
    }

    @Test fun unknownDeviceHasNoOem() {
        assertNull(Oem.fromBuild("Google", "google"))
    }

    @Test fun applicabilityFollowsSdk() {
        assertFalse(PermissionKind.NOTIFICATIONS.isApplicable(32))
        assertTrue(PermissionKind.NOTIFICATIONS.isApplicable(33))
        assertFalse(PermissionKind.EXACT_ALARMS.isApplicable(30))
        assertTrue(PermissionKind.applicable(26).contains(PermissionKind.USAGE_ACCESS))
    }
}
