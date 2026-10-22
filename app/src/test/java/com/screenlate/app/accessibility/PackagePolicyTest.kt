package com.screenlate.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagePolicyTest {
    @Test
    fun combinesSystemExcludedAndPausedApps() {
        val policy = PackagePolicy(setOf("com.screenlate.app", "com.android.systemui"))
        policy.excluded = setOf("org.telegram.messenger")
        policy.paused = setOf("com.android.chrome")

        assertTrue(policy.isIgnored(null))
        assertTrue(policy.isIgnored("com.android.systemui"))
        assertTrue(policy.isIgnored("org.telegram.messenger"))
        assertTrue(policy.isIgnored("com.android.chrome"))
        assertFalse(policy.isIgnored("com.android.settings"))

        assertTrue(policy.isSystem("com.screenlate.app"))
        assertFalse(policy.isSystem("org.telegram.messenger"))
    }
}
