package com.screenlate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test
    fun labelContainsNameAndCode() {
        assertEquals("v0.1.0 (1)", AppVersion.label("0.1.0", 1))
    }

    @Test
    fun currentMatchesBuildConfig() {
        assertEquals(
            AppVersion.label(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            AppVersion.current,
        )
    }
}
