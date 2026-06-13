package project.witty.keys.app.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ToneData Unit Tests
 *
 * Tests the tone/emoji mapping and reply hierarchy data.
 * Pure data tests - no mocks needed.
 *
 * Run with:
 * ./gradlew test --tests "project.witty.keys.app.utils.ToneDataTest"
 */
class ToneDataTest {

    // ===========================================
    // Tone Emoji Map Tests
    // ===========================================

    @Test
    fun `toneEmojiMap is not empty`() {
        val toneMap = ToneData.getToneEmojiMap()

        assertNotNull(toneMap, "Tone map should not be null")
        assertTrue(toneMap.isNotEmpty(), "Tone map should not be empty")
        println("[PASS] Tone map has ${toneMap.size} entries")
    }

    @Test
    fun `toneEmojiMap has at least 20 tones`() {
        val toneMap = ToneData.getToneEmojiMap()

        assertTrue(
            toneMap.size >= 20,
            "Expected at least 20 tones, got ${toneMap.size}"
        )
        println("[PASS] Tone map has ${toneMap.size} tones (≥20)")
    }

    @Test
    fun `formal tone exists with emoji`() {
        val emoji = ToneData.getEmojiForTone("Formal")

        assertNotNull(emoji, "Formal tone should exist")
        assertTrue(emoji.isNotEmpty(), "Formal emoji should not be empty")
        println("[PASS] Formal tone emoji: $emoji")
    }

    @Test
    fun `casual tone exists with emoji`() {
        val emoji = ToneData.getEmojiForTone("Casual")

        assertNotNull(emoji, "Casual tone should exist")
        assertTrue(emoji.isNotEmpty(), "Casual emoji should not be empty")
        println("[PASS] Casual tone emoji: $emoji")
    }

    @Test
    fun `flirty tone exists with emoji`() {
        val emoji = ToneData.getEmojiForTone("Flirty")

        assertNotNull(emoji, "Flirty tone should exist")
        println("[PASS] Flirty tone emoji: $emoji")
    }

    @Test
    fun `professional tone exists with emoji`() {
        val emoji = ToneData.getEmojiForTone("Professional")

        assertNotNull(emoji, "Professional tone should exist")
        println("[PASS] Professional tone emoji: $emoji")
    }

    @Test
    fun `all core tones exist`() {
        val coreTones = listOf(
            "Flirty", "Formal", "Casual", "Professional", "Playful",
            "Savage", "Sarcastic", "Sassy", "Teasing", "Quirky",
            "Concise", "Romantic", "Descriptive", "Persuasive", "Witty",
            "Inspirational", "Technical", "Urgent", "Calm", "Curious", "Polite"
        )

        val toneMap = ToneData.getToneEmojiMap()

        coreTones.forEach { tone ->
            assertTrue(
                toneMap.containsKey(tone),
                "Tone '$tone' should exist in toneEmojiMap"
            )
        }

        println("[PASS] All ${coreTones.size} core tones exist")
    }

    @Test
    fun `all emoji values are non-empty strings`() {
        val toneMap = ToneData.getToneEmojiMap()

        toneMap.forEach { (tone, emoji) ->
            assertTrue(
                emoji.isNotEmpty(),
                "Emoji for '$tone' should not be empty"
            )
        }

        println("[PASS] All ${toneMap.size} tones have non-empty emojis")
    }

    // ===========================================
    // Reply Hierarchy Tests
    // ===========================================

    @Test
    fun `replyHierarchy is not empty`() {
        val hierarchy = ToneData.getReplyHierarchy()

        assertNotNull(hierarchy, "Reply hierarchy should not be null")
        assertTrue(hierarchy.isNotEmpty(), "Reply hierarchy should not be empty")
        println("[PASS] Reply hierarchy has ${hierarchy.size} Level 1 intents")
    }

    @Test
    fun `level1Intents returns expected categories`() {
        val intents = ToneData.getLevel1Intents()

        assertTrue(intents.isNotEmpty(), "Level 1 intents should not be empty")

        // Expected categories
        val expectedIntents = listOf("Initiate", "Respond", "React", "Rizz/Dating", "Advance Conv")

        expectedIntents.forEach { expected ->
            assertTrue(
                intents.contains(expected),
                "Level 1 intents should contain '$expected'"
            )
        }

        println("[PASS] Found ${intents.size} Level 1 intents: $intents")
    }

    @Test
    fun `respond category has expected actions`() {
        val respondActions = ToneData.getLevel2Actions("Respond")

        assertTrue(respondActions.isNotEmpty(), "Respond actions should not be empty")
        assertTrue(
            respondActions.containsKey("Agree"),
            "Respond should have 'Agree' action"
        )
        assertTrue(
            respondActions.containsKey("Disagree"),
            "Respond should have 'Disagree' action"
        )

        println("[PASS] Respond category has ${respondActions.size} actions")
    }

    @Test
    fun `react category has expected actions`() {
        val reactActions = ToneData.getLevel2Actions("React")

        assertTrue(reactActions.isNotEmpty(), "React actions should not be empty")
        assertTrue(
            reactActions.containsKey("Funny / Witty"),
            "React should have 'Funny / Witty' action"
        )
        assertTrue(
            reactActions.containsKey("Empathetic"),
            "React should have 'Empathetic' action"
        )

        println("[PASS] React category has ${reactActions.size} actions")
    }

    @Test
    fun `dating category has expected actions`() {
        val datingActions = ToneData.getLevel2Actions("Rizz/Dating")

        assertTrue(datingActions.isNotEmpty(), "Dating actions should not be empty")
        assertTrue(
            datingActions.containsKey("Romantic"),
            "Dating should have 'Romantic' action"
        )
        assertTrue(
            datingActions.containsKey("Playful Tease"),
            "Dating should have 'Playful Tease' action"
        )

        println("[PASS] Rizz/Dating category has ${datingActions.size} actions")
    }

    @Test
    fun `initiate category has opener actions`() {
        val initiateActions = ToneData.getLevel2Actions("Initiate")

        assertTrue(initiateActions.isNotEmpty(), "Initiate actions should not be empty")
        assertTrue(
            initiateActions.containsKey("Opener for Profile"),
            "Initiate should have 'Opener for Profile' action"
        )
        assertTrue(
            initiateActions.containsKey("Conversation Starter"),
            "Initiate should have 'Conversation Starter' action"
        )

        println("[PASS] Initiate category has ${initiateActions.size} actions")
    }

    @Test
    fun `unknown category returns empty map`() {
        val unknownActions = ToneData.getLevel2Actions("NonExistentCategory")

        assertTrue(
            unknownActions.isEmpty(),
            "Unknown category should return empty map"
        )

        println("[PASS] Unknown category returns empty map")
    }

    @Test
    fun `all level2 actions have emojis`() {
        val hierarchy = ToneData.getReplyHierarchy()

        hierarchy.forEach { (level1, actions) ->
            actions.forEach { (action, emoji) ->
                assertTrue(
                    emoji.isNotEmpty(),
                    "Action '$action' in '$level1' should have non-empty emoji"
                )
            }
        }

        val totalActions = hierarchy.values.sumOf { it.size }
        println("[PASS] All $totalActions Level 2 actions have emojis")
    }

    @Test
    fun `hierarchy is immutable`() {
        val hierarchy = ToneData.getReplyHierarchy()

        try {
            // This should throw UnsupportedOperationException
            @Suppress("UNCHECKED_CAST")
            (hierarchy as MutableMap<String, Any>).put("Test", HashMap<String, String>())
            // If we reach here, the map is mutable (bad!)
            assertTrue(false, "Hierarchy should be immutable")
        } catch (e: UnsupportedOperationException) {
            // Expected - hierarchy is properly immutable
            println("[PASS] Hierarchy is immutable")
        }
    }
}
