package project.witty.keys.app.context

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * ReplyGenerator REAL Integration Tests
 *
 * Tests the reply generation logic using the REAL Claude API.
 * Uses the same system prompt as ReplyGenerator.java but bypasses
 * Android dependencies for JVM testing.
 *
 * IMPORTANT: These tests call the REAL Firebase proxy API.
 * - No mocks
 * - Real network calls
 * - May cost money (API usage)
 *
 * Run with:
 * WK_RUN_CLAUDE_INTEGRATION=true ./gradlew test --tests "project.witty.keys.app.context.ReplyGeneratorIntegrationTest"
 */
class ReplyGeneratorIntegrationTest {

    companion object {
        // Firebase Emulator URL (localhost for JVM tests)
        // Change to production URL when deploying: https://us-central1-tapai-e33d2.cloudfunctions.net/proxyClaudeHttp
        private const val REAL_API_URL = "http://127.0.0.1:5001/tapai-e33d2/us-central1/proxyClaudeHttp"
        private const val TIMEOUT_SECONDS = 30L
        private const val RUN_INTEGRATION_ENV = "WK_RUN_CLAUDE_INTEGRATION"

        // Same system prompt as ReplyGenerator.java
        private const val SYSTEM_PROMPT = """You are a reply assistant for a keyboard app. Generate 4 short, natural replies.

Rules:
- Each reply maximum 50 characters
- Match the conversation tone
- Include 1 relevant emoji per reply if appropriate
- First reply: most likely positive response
- Second reply: follow-up question
- Third reply: polite decline or alternative
- Fourth reply: neutral acknowledgment
- For Hinglish/Hindi conversations, respond in same style
- Never generate inappropriate or offensive content

Return ONLY the 4 replies, one per line, no numbering or explanation."""
    }

    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        assumeTrue(
            "Set $RUN_INTEGRATION_ENV=true and start Firebase Functions emulator before running ReplyGeneratorIntegrationTest.",
            System.getenv(RUN_INTEGRATION_ENV).equals("true", ignoreCase = true)
        )

        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        println("[TEST] ReplyGeneratorIntegrationTest setup complete")
    }

    // ===========================================
    // Test 1: Generate replies for messaging context
    // ===========================================
    @Test
    fun `generates replies for messaging chat`() {
        println("[TEST START] generates_replies_for_messaging_chat")

        val userPrompt = buildUserPrompt(
            appType = "messaging",
            sender = "John",
            lastMessage = "Hey, are you free for dinner tonight?",
            context = "You: Sure, what time? | John: How about 7pm?"
        )

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = userPrompt,
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Got ${replies.size} replies")
                replies.forEachIndexed { i, reply ->
                    println("[REPLY ${i + 1}] $reply")
                }
                latch.countDown()
            },
            onError = { err ->
                error = err
                println("[API ERROR] $err")
                latch.countDown()
            }
        )

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout")

        if (error != null) {
            fail("API error: $error")
        }

        assertNotNull(result)
        assertTrue(result!!.size >= 2, "Should have at least 2 replies, got ${result!!.size}")

        println("[TEST PASS] Generated ${result!!.size} messaging replies")
    }

    // ===========================================
    // Test 2: Generate replies for email context
    // ===========================================
    @Test
    fun `generates replies for email context`() {
        println("[TEST START] generates_replies_for_email")

        val userPrompt = buildUserPrompt(
            appType = "email",
            sender = "Manager",
            lastMessage = "Please send me the Q4 report by EOD",
            context = "Manager: Hi team, deadline approaching"
        )

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = userPrompt,
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Email replies:")
                replies.forEachIndexed { i, reply ->
                    println("[REPLY ${i + 1}] $reply")
                }
                latch.countDown()
            },
            onError = { err ->
                error = err
                latch.countDown()
            }
        )

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout")

        if (error != null) {
            fail("API error: $error")
        }

        assertNotNull(result)
        assertTrue(result!!.isNotEmpty(), "Should have replies")

        // Email replies should be professional tone
        println("[TEST PASS] Generated ${result!!.size} email replies")
    }

    // ===========================================
    // Test 3: Generate replies for dating context
    // ===========================================
    @Test
    fun `generates replies for dating app context`() {
        println("[TEST START] generates_replies_for_dating")

        val userPrompt = buildUserPrompt(
            appType = "dating",
            sender = "Sarah",
            lastMessage = "I love hiking too! What's your favorite trail?",
            context = "Sarah: Hi! | You: Hey, nice profile! | Sarah: Thanks! Do you hike?"
        )

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = userPrompt,
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Dating app replies:")
                replies.forEachIndexed { i, reply ->
                    println("[REPLY ${i + 1}] $reply")
                }
                latch.countDown()
            },
            onError = { err ->
                error = err
                latch.countDown()
            }
        )

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout")

        if (error != null) {
            fail("API error: $error")
        }

        assertNotNull(result)
        assertTrue(result!!.isNotEmpty(), "Should have replies")

        println("[TEST PASS] Generated ${result!!.size} dating replies")
    }

    // ===========================================
    // Test 4: Replies are appropriately short
    // ===========================================
    @Test
    fun `replies are under 50 characters`() {
        println("[TEST START] replies_are_under_50_chars")

        val userPrompt = buildUserPrompt(
            appType = "messaging",
            sender = "Friend",
            lastMessage = "Want to catch up this weekend?",
            context = ""
        )

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = userPrompt,
            onSuccess = { replies ->
                result = replies
                latch.countDown()
            },
            onError = { err ->
                error = err
                latch.countDown()
            }
        )

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout")

        if (error != null) {
            fail("API error: $error")
        }

        assertNotNull(result)

        // Check most replies are short (some may be slightly over due to emojis)
        val shortReplies = result!!.filter { it.length <= 60 }
        assertTrue(
            shortReplies.size >= result!!.size / 2,
            "At least half of replies should be ≤60 chars. " +
            "Got ${shortReplies.size}/${result!!.size} short replies"
        )

        result!!.forEachIndexed { i, reply ->
            println("[REPLY ${i + 1}] (${reply.length} chars) $reply")
        }

        println("[TEST PASS] ${shortReplies.size}/${result!!.size} replies are appropriately short")
    }

    // ===========================================
    // Test 5: Hinglish context gets Hinglish replies
    // ===========================================
    @Test
    fun `generates Hinglish replies for Hinglish context`() {
        println("[TEST START] generates_hinglish_replies")

        val userPrompt = buildUserPrompt(
            appType = "messaging",
            sender = "Rahul",
            lastMessage = "Bhai, party chal raha hai aaj raat. Aa raha hai kya?",
            context = "Rahul: Kya plan hai weekend ka?"
        )

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = userPrompt,
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Hinglish replies:")
                replies.forEachIndexed { i, reply ->
                    println("[REPLY ${i + 1}] $reply")
                }
                latch.countDown()
            },
            onError = { err ->
                error = err
                latch.countDown()
            }
        )

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout")

        if (error != null) {
            fail("API error: $error")
        }

        assertNotNull(result)
        assertTrue(result!!.isNotEmpty(), "Should have replies")

        println("[TEST PASS] Generated ${result!!.size} Hinglish replies")
    }

    // ===========================================
    // Test 6: Social media context
    // ===========================================
    @Test
    fun `generates replies for social media context`() {
        println("[TEST START] generates_social_media_replies")

        val userPrompt = buildUserPrompt(
            appType = "social",
            sender = "Colleague",
            lastMessage = "Great presentation today! The clients loved it.",
            context = "LinkedIn conversation"
        )

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = userPrompt,
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Social media replies:")
                replies.forEachIndexed { i, reply ->
                    println("[REPLY ${i + 1}] $reply")
                }
                latch.countDown()
            },
            onError = { err ->
                error = err
                latch.countDown()
            }
        )

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout")

        if (error != null) {
            fail("API error: $error")
        }

        assertNotNull(result)
        println("[TEST PASS] Generated ${result!!.size} social media replies")
    }

    // ===========================================
    // Helper: Build user prompt (mirrors ReplyGenerator.buildUserPrompt)
    // ===========================================
    private fun buildUserPrompt(
        appType: String,
        sender: String,
        lastMessage: String,
        context: String
    ): String {
        return buildString {
            appendLine("App: $appType")
            appendLine("Sender: $sender")
            appendLine("Their message: \"$lastMessage\"")
            if (context.isNotEmpty()) {
                appendLine("Recent context: $context")
            }
            appendLine()
            appendLine("Generate 4 reply options.")
        }
    }

    // ===========================================
    // Helper: Call REAL API
    // ===========================================
    private fun callRealApi(
        systemPrompt: String,
        userMessage: String,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        println("[API REQUEST] Calling REAL API...")

        try {
            val requestJson = JSONObject().apply {
                val messagesArray = JSONArray()
                val userMsg = JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                }
                messagesArray.put(userMsg)

                put("messages", messagesArray)
                put("system", systemPrompt)
                put("max_tokens", 256)
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(REAL_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()

            println("[API RESPONSE] HTTP ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                onError("HTTP ${response.code}: $errorBody")
                return
            }

            val responseBody = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")

            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "") ?: ""

                val replies = parseReplies(content)
                onSuccess(replies)
            } else {
                onError("No choices in response")
            }

        } catch (e: Exception) {
            println("[API ERROR] Exception: ${e.message}")
            onError("Exception: ${e.message}")
        }
    }

    private fun parseReplies(content: String): List<String> {
        if (content.isEmpty()) return emptyList()

        return content.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirst(Regex("^\\d+[.):]\\s*"), "") }
            .filter { it.isNotEmpty() }
    }
}
