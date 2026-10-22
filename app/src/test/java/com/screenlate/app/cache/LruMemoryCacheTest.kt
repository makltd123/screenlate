package com.screenlate.app.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LruMemoryCacheTest {
    @Test
    fun evictsLeastRecentlyUsed() {
        val cache = LruMemoryCache<String, Int>(capacity = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache["a"]
        cache.put("c", 3)
        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size)
    }
}
