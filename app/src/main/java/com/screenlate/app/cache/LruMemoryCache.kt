package com.screenlate.app.cache

/** Thread-safe least-recently-used map holding at most [capacity] entries. */
class LruMemoryCache<K : Any, V : Any>(private val capacity: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
    }

    val size: Int
        @Synchronized get() = map.size

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun clear() = map.clear()
}
