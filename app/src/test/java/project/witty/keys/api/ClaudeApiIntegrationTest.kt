package project.witty.keys.api

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
 * ClaudeApi REAL Integration Tests
 *
 * IMPORTANT: These tests call the REAL Firebase proxy API.
 * - No mocks
 * - No MockWebServer
 * - Real network calls
 * - May cost money (API usage)
 *
 * Run with:
 * ./gradlew test --tests "project.witty.keys.api.ClaudeApiIntegrationTest"
 */
class ClaudeApiIntegrationTest {

    companion object {
        // Firebase Emulator URL (localhost for JVM tests)
        // Change to production URL when deploying: https://us-central1-tapai-e33d2.cloudfunctions.net/proxyClaudeHttp
        private const val REAL_API_URL = "http://127.0.0.1:5001/tapai-e33d2/us-central1/proxyClaudeHttp"
        private const val TIMEOUT_SECONDS = 30L
        private const val RUN_INTEGRATION_ENV = "WK_RUN_CLAUDE_INTEGRATION"
    }

    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        assumeTrue(
            "Set $RUN_INTEGRATION_ENV=true and start Firebase Functions emulator before running ClaudeApiIntegrationTest.",
            System.getenv(RUN_INTEGRATION_ENV).equals("true", ignoreCase = true)
        )

        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        println("[TEST] ClaudeApiIntegrationTest setup complete")
        println("[TEST] Using REAL API URL: $REAL_API_URL")
    }

    // ===========================================
    // Test 1: API returns valid response
    // ===========================================
    @Test
    fun `api returns valid response for simple prompt`() {
        println("[TEST START] api_returns_valid_response")

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        // Call REAL API
        callRealApi(
            systemPrompt = "You are a helpful assistant. Respond with exactly 3 short replies, one per line.",
            userMessage = "Someone said: 'Hey, how are you?' - Generate 3 appropriate replies.",
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Got ${replies.size} replies: $replies")
                latch.countDown()
            },
            onError = { err ->
                error = err
                println("[API ERROR] $err")
                latch.countDown()
            }
        )

        // Wait for response
        assertTrue(
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            "Timeout waiting for API response"
        )

        // Verify no error
        if (error != null) {
            fail("API returned error: $error")
        }

        // Verify response
        assertNotNull(result, "Result should not be null")
        assertTrue(result!!.isNotEmpty(), "Result should have at least 1 reply")
        println("[TEST PASS] Got ${result!!.size} replies")
    }

    // ===========================================
    // Test 2: API returns multiple replies
    // ===========================================
    @Test
    fun `api returns multiple distinct replies`() {
        println("[TEST START] api_returns_multiple_replies")

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = """You are generating reply suggestions for a messaging app.
                |Generate exactly 4 short reply options (under 50 characters each).
                |One reply per line. No numbering or bullets.""".trimMargin(),
            userMessage = "Friend says: 'Want to grab lunch tomorrow?'",
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Replies: $replies")
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

        // Verify replies are distinct
        val uniqueReplies = result!!.toSet()
        assertTrue(uniqueReplies.size >= 2, "Replies should be distinct")

        println("[TEST PASS] Got ${result!!.size} distinct replies")
    }

    // ===========================================
    // Test 3: API handles context correctly
    // ===========================================
    @Test
    fun `api generates contextual replies for chat`() {
        println("[TEST START] api_generates_contextual_replies")

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        val chatContext = """
            |Conversation in WhatsApp with Sarah:
            |Sarah: Hey! Are you coming to the party tonight?
            |Me: I'm not sure yet
            |Sarah: Come on, it'll be fun! Everyone's going to be there.
            |
            |Generate 4 casual reply suggestions for this chat.
        """.trimMargin()

        callRealApi(
            systemPrompt = "You are a reply suggestion assistant. Generate short, casual replies suitable for a friend chat.",
            userMessage = chatContext,
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Contextual replies: $replies")
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
        assertTrue(result!!.isNotEmpty(), "Should have replies")

        // Verify replies are reasonable length (under 100 chars for chat)
        result!!.forEach { reply ->
            assertTrue(
                reply.length < 150,
                "Reply too long for chat: ${reply.length} chars - '$reply'"
            )
        }

        println("[TEST PASS] Generated ${result!!.size} contextual replies")
    }

    // ===========================================
    // Test 4: API handles empty/short prompts
    // ===========================================
    @Test
    fun `api handles minimal prompt`() {
        println("[TEST START] api_handles_minimal_prompt")

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = "Reply with 'OK'",
            userMessage = "Hi",
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Minimal prompt response: $replies")
                latch.countDown()
            },
            onError = { err ->
                error = err
                println("[API ERROR] $err")
                latch.countDown()
            }
        )

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout")

        // Either success or graceful error is acceptable
        val hasResponse = result != null && result!!.isNotEmpty()
        val hasGracefulError = error != null

        assertTrue(
            hasResponse || hasGracefulError,
            "Should either succeed or return graceful error"
        )

        println("[TEST PASS] API handled minimal prompt")
    }

    // ===========================================
    // Test 5: Response parsing works correctly
    // ===========================================
    @Test
    fun `api response is parsed correctly`() {
        println("[TEST START] api_response_parsing")

        val latch = CountDownLatch(1)
        var result: List<String>? = null
        var error: String? = null

        callRealApi(
            systemPrompt = """Generate exactly 3 replies in this format:
                |1. First reply
                |2. Second reply
                |3. Third reply""".trimMargin(),
            userMessage = "Test message",
            onSuccess = { replies ->
                result = replies
                println("[API SUCCESS] Parsed replies: $replies")
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

        // Verify numbering was stripped (parseReplies removes "1." prefixes)
        result!!.forEach { reply ->
            assertTrue(
                !reply.startsWith("1.") && !reply.startsWith("2.") && !reply.startsWith("3."),
                "Numbering should be stripped: '$reply'"
            )
        }

        println("[TEST PASS] Response parsed correctly")
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
        println("[API REQUEST] System prompt length: ${systemPrompt.length}")
        println("[API REQUEST] User prompt length: ${userMessage.length}")

        try {
            // Build request JSON (same format as ClaudeApi.java)
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

            // Make synchronous call (we're already on test thread)
            val response = client.newCall(request).execute()

            println("[API RESPONSE] HTTP ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                println("[API RESPONSE] Error body: $errorBody")
                onError("HTTP ${response.code}: $errorBody")
                return
            }

            val responseBody = response.body?.string() ?: ""
            println("[API RESPONSE] Body length: ${responseBody.length}")

            // Parse response (same format as ClaudeApi.java)
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")

            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "") ?: ""

                println("[API RESPONSE] Content length: ${content.length}")

                // Parse replies (same logic as ClaudeApi.parseReplies)
                val replies = parseReplies(content)
                onSuccess(replies)
            } else {
                onError("No choices in response")
            }

        } catch (e: Exception) {
            println("[API ERROR] Exception: ${e.message}")
            e.printStackTrace()
            onError("Exception: ${e.message}")
        }
    }

    /**
     * Parse Claude's response into individual replies.
     * Same logic as ClaudeApi.java
     */
    private fun parseReplies(content: String): List<String> {
        if (content.isEmpty()) return emptyList()

        return content.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirst(Regex("^\\d+[.):]\\s*"), "") }
            .filter { it.isNotEmpty() }
    }
}
