package project.witty.keys.app.context

import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReplyCacheTest {

    private lateinit var cache: ReplyCache

    @Before
    fun setup() {
        // Reset singleton
        val field = ReplyCache::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        cache = ReplyCache.getInstance()
    }

    @After
    fun teardown() {
        val field = ReplyCache::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    // === Basic Put/Get ===

    @Test
    fun `put and get returns cached replies`() {
        val replies = listOf("Sure!", "Okay!", "Got it!")
        cache.put("com.whatsapp|Priya", replies)
        assertEquals(replies, cache.get("com.whatsapp|Priya"))
    }

    @Test
    fun `cache is scoped by latest incoming message id`() {
        val replies = listOf("First")
        cache.put("com.whatsapp|Priya", "in-1", replies)

        assertEquals(replies, cache.get("com.whatsapp|Priya", "in-1"))
        assertNull(cache.get("com.whatsapp|Priya", "in-2"))
    }

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get("com.whatsapp|Unknown"))
    }

    @Test
    fun `hasFreshReplies returns true for cached entry`() {
        cache.put("com.whatsapp|Priya", listOf("Hi!"))
        assertTrue(cache.hasFreshReplies("com.whatsapp|Priya"))
    }

    @Test
    fun `hasFreshReplies returns false for missing entry`() {
        assertFalse(cache.hasFreshReplies("com.whatsapp|Unknown"))
    }

    // === TTL Expiration ===

    @Test
    fun `get returns null after TTL expires`() {
        cache.put("com.whatsapp|Priya", listOf("Hi!"))

        // Use reflection to expire the entry by setting its timestamp to 11 minutes ago
        expireEntry("com.whatsapp|Priya", 11 * 60 * 1000L)

        assertNull(cache.get("com.whatsapp|Priya"))
    }

    @Test
    fun `hasFreshReplies returns false after TTL`() {
        cache.put("com.whatsapp|Priya", listOf("Hi!"))
        expireEntry("com.whatsapp|Priya", 11 * 60 * 1000L)
        assertFalse(cache.hasFreshReplies("com.whatsapp|Priya"))
    }

    // === LRU Eviction ===

    @Test
    fun `LRU evicts oldest entry when exceeding 10`() {
        for (i in 0..10) {
            cache.put("pkg|contact_$i", listOf("reply_$i"))
        }
        // contact_0 should be evicted (oldest)
        assertNull(cache.get("pkg|contact_0"))
        // contact_10 should still exist
        assertNotNull(cache.get("pkg|contact_10"))
        assertTrue(cache.size() <= 10)
    }

    @Test
    fun `accessing entry refreshes LRU position`() {
        // Insert 10 entries (0-9)
        for (i in 0..9) {
            cache.put("pkg|contact_$i", listOf("reply_$i"))
        }
        // Access contact_0 (refresh its LRU position)
        cache.get("pkg|contact_0")
        // Insert contact_10 (should evict contact_1, not contact_0)
        cache.put("pkg|contact_10", listOf("reply_10"))

        assertNotNull(cache.get("pkg|contact_0")) // Should still exist
        assertNull(cache.get("pkg|contact_1"))     // Should be evicted
    }

    // === Invalidation ===

    @Test
    fun `invalidate removes specific key`() {
        cache.put("com.whatsapp|Priya", listOf("Hi!"))
        cache.invalidate("com.whatsapp|Priya")
        assertNull(cache.get("com.whatsapp|Priya"))
    }

    @Test
    fun `invalidateByPackage removes all entries for package`() {
        cache.put("com.whatsapp|Priya", listOf("Hi!"))
        cache.put("com.whatsapp|Boss", listOf("Sure!"))
        cache.put("org.telegram|Alex", listOf("Hey!"))
        cache.invalidateByPackage("com.whatsapp")
        assertNull(cache.get("com.whatsapp|Priya"))
        assertNull(cache.get("com.whatsapp|Boss"))
        assertNotNull(cache.get("org.telegram|Alex"))
    }

    @Test
    fun `clear removes all entries`() {
        cache.put("com.whatsapp|Priya", listOf("Hi!"))
        cache.put("org.telegram|Alex", listOf("Hey!"))
        cache.clear()
        assertEquals(0, cache.size())
    }

    // === Size ===

    @Test
    fun `size reflects number of entries`() {
        assertEquals(0, cache.size())
        cache.put("key1", listOf("a"))
        assertEquals(1, cache.size())
        cache.put("key2", listOf("b"))
        assertEquals(2, cache.size())
    }

    // === Thread Safety ===

    @Test
    fun `concurrent put and get does not throw`() {
        val latch = CountDownLatch(10)
        val errors = mutableListOf<Throwable>()

        for (i in 0 until 10) {
            Thread {
                try {
                    cache.put("pkg|contact_$i", listOf("reply_$i"))
                    cache.get("pkg|contact_$i")
                    cache.hasFreshReplies("pkg|contact_$i")
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("Threads timed out", latch.await(5, TimeUnit.SECONDS))
        assertTrue("Concurrent errors: $errors", errors.isEmpty())
        assertTrue(cache.size() <= 10)
    }

    // === Helper ===

    private fun expireEntry(key: String, ageMs: Long) {
        try {
            // Access the internal LinkedHashMap
            val cacheMapField = ReplyCache::class.java.getDeclaredField("cache")
                ?: ReplyCache::class.java.declaredFields.find { it.type.name.contains("Map") }
            cacheMapField?.isAccessible = true
            val cacheMap = cacheMapField?.get(cache) as? Map<*, *> ?: return

            val entry = cacheMap[key] ?: return

            // Set the timestamp field on the CacheEntry to expire it
            val timestampField = entry::class.java.getDeclaredField("timestamp")
            timestampField.isAccessible = true
            timestampField.setLong(entry, System.currentTimeMillis() - ageMs)
        } catch (e: Exception) {
            // If reflection fails, skip TTL tests gracefully
            fail("Could not expire entry via reflection: ${e.message}")
        }
    }
}
