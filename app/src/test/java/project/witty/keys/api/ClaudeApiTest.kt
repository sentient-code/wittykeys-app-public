package project.witty.keys.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for ClaudeApi - HTTP client for Claude API via Firebase proxy.
 *
 * Uses MockWebServer to simulate API responses without network calls.
 */
class ClaudeApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var claudeApi: ClaudeApiTestable

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create testable version of ClaudeApi with custom URL
        claudeApi = ClaudeApiTestable(mockWebServer.url("/").toString())
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // ===========================
    // Success Cases
    // ===========================

    @Test
    fun `sendMessage with valid request returns response`() {
        // Arrange
        val mockResponse = """
            {
                "choices": [{
                    "message": {
                        "role": "assistant",
                        "content": "Sure! 🎉\nMaybe later\nNot today\nWhat time?"
                    },
                    "finish_reason": "end_turn"
                }],
                "usage": {
                    "input_tokens": 50,
                    "output_tokens": 20
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
        )

        val latch = CountDownLatch(1)
        var resultReplies: List<String>? = null
        var resultError: String? = null

        // Act
        claudeApi.generateReplies(
            systemPrompt = "Generate 4 replies",
            userMessage = "Hey, are you free tonight?",
            callback = object : ClaudeApiTestable.ReplyCallback {
                override fun onRepliesGenerated(replies: List<String>) {
                    resultReplies = replies
                    latch.countDown()
                }

                override fun onError(error: String) {
                    resultError = error
                    latch.countDown()
                }
            }
        )

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback should complete within 5 seconds")
        assertNotNull(resultReplies, "Replies should not be null")
        assertEquals(4, resultReplies!!.size, "Should return 4 replies")
        assertTrue(resultReplies!![0].contains("Sure"), "First reply should contain 'Sure'")
        assertEquals("Maybe later", resultReplies!![1])
        assertEquals("Not today", resultReplies!![2])
        assertEquals("What time?", resultReplies!![3])
    }

    @Test
    fun `parseResponse with valid JSON extracts content correctly`() {
        // Arrange
        val mockResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "Reply 1\nReply 2\nReply 3\nReply 4"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200)
        )

        val latch = CountDownLatch(1)
        var resultReplies: List<String>? = null

        // Act
        claudeApi.generateReplies("system", "user", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                resultReplies = replies
                latch.countDown()
            }

            override fun onError(error: String) {
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(4, resultReplies?.size)
        assertEquals("Reply 1", resultReplies?.get(0))
    }

    // ===========================
    // Error Cases
    // ===========================

    @Test
    fun `sendMessage with network error throws exception`() {
        // Arrange - Server error
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error": "Internal server error"}""")
        )

        val latch = CountDownLatch(1)
        var resultError: String? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                latch.countDown()
            }

            override fun onError(error: String) {
                resultError = error
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(resultError, "Error should be returned")
        assertTrue(resultError!!.contains("500"), "Error should contain status code")
    }

    @Test
    fun `sendMessage with timeout throws TimeoutException`() {
        // Arrange - No response (timeout)
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        val latch = CountDownLatch(1)
        var resultError: String? = null

        // Act - Use shorter timeout for test
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                latch.countDown()
            }

            override fun onError(error: String) {
                resultError = error
                latch.countDown()
            }
        })

        // Assert - Wait longer than the client timeout
        // Note: This test may take longer due to OkHttp's retry behavior
        assertTrue(latch.await(70, TimeUnit.SECONDS))
        assertNotNull(resultError, "Error should be returned on timeout")
        assertTrue(resultError!!.contains("Network error"), "Error should indicate network issue")
    }

    @Test
    fun `sendMessage with invalid API key throws 401 error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"type": "authentication_error", "message": "Invalid API Key"}}""")
        )

        val latch = CountDownLatch(1)
        var resultError: String? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                latch.countDown()
            }

            override fun onError(error: String) {
                resultError = error
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(resultError)
        assertTrue(resultError!!.contains("401"), "Error should contain 401 status")
    }

    @Test
    fun `parseResponse with malformed JSON throws parse exception`() {
        // Arrange - Invalid JSON response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("this is not valid JSON {{{")
        )

        val latch = CountDownLatch(1)
        var resultError: String? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                latch.countDown()
            }

            override fun onError(error: String) {
                resultError = error
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(resultError, "Error should be returned for malformed JSON")
        assertTrue(resultError!!.contains("Parse error"), "Error should indicate parse issue")
    }

    @Test
    fun `sendMessage with empty choices returns error`() {
        // Arrange - Empty choices array
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices": []}""")
        )

        val latch = CountDownLatch(1)
        var resultError: String? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                latch.countDown()
            }

            override fun onError(error: String) {
                resultError = error
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(resultError)
        assertTrue(resultError!!.contains("Invalid response format"))
    }

    // ===========================
    // Reply Parsing Tests
    // ===========================

    @Test
    fun `parseReplies handles newline separated replies`() {
        // Arrange
        val mockResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "Yes, I'm free!\nWhat time works?\nMaybe tomorrow?\nSounds good!"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse).setResponseCode(200))

        val latch = CountDownLatch(1)
        var resultReplies: List<String>? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                resultReplies = replies
                latch.countDown()
            }

            override fun onError(error: String) {
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(4, resultReplies?.size)
    }

    @Test
    fun `parseReplies handles numbered format`() {
        // Arrange - Claude sometimes returns numbered replies
        val mockResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "1. Yes, I'm free!\n2. What time works?\n3. Maybe tomorrow?\n4. Sounds good!"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse).setResponseCode(200))

        val latch = CountDownLatch(1)
        var resultReplies: List<String>? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                resultReplies = replies
                latch.countDown()
            }

            override fun onError(error: String) {
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(4, resultReplies?.size)
        // Numbers should be stripped
        assertEquals("Yes, I'm free!", resultReplies?.get(0))
    }

    @Test
    fun `parseReplies filters empty lines`() {
        // Arrange
        val mockResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "Reply 1\n\n\nReply 2\n\nReply 3\n\nReply 4"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse).setResponseCode(200))

        val latch = CountDownLatch(1)
        var resultReplies: List<String>? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                resultReplies = replies
                latch.countDown()
            }

            override fun onError(error: String) {
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(4, resultReplies?.size, "Empty lines should be filtered out")
        assertTrue(resultReplies?.none { it.isEmpty() } ?: false, "No empty strings in result")
    }

    @Test
    fun `parseReplies handles parenthesis numbered format`() {
        // Arrange
        val mockResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "1) Sure thing!\n2) Let me check\n3) Not available\n4) Thanks!"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse).setResponseCode(200))

        val latch = CountDownLatch(1)
        var resultReplies: List<String>? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                resultReplies = replies
                latch.countDown()
            }

            override fun onError(error: String) {
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals("Sure thing!", resultReplies?.get(0))
    }

    @Test
    fun `parseReplies handles colon numbered format`() {
        // Arrange
        val mockResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "1: Sure thing!\n2: Let me check\n3: Not available\n4: Thanks!"
                    }
                }]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(mockResponse).setResponseCode(200))

        val latch = CountDownLatch(1)
        var resultReplies: List<String>? = null

        // Act
        claudeApi.generateReplies("system", "message", object : ClaudeApiTestable.ReplyCallback {
            override fun onRepliesGenerated(replies: List<String>) {
                resultReplies = replies
                latch.countDown()
            }

            override fun onError(error: String) {
                latch.countDown()
            }
        })

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals("Sure thing!", resultReplies?.get(0))
    }

    @Test
    fun `real parseReplies extracts fenced quickReplies without markdown commentary`() {
        val content = """
            ```json
            {
              "detectedLanguage": "hi-en",
              "needsTranslation": false,
              "quickReplies": [
                "Haan yaar, pakka aaunga! Kaunsa time aur location bataa de bhai",
                "Arrey mast! Party kaunsi jagah hai? Definitely in for it"
              ]
            }
            ```

            **Breakdown:**
            - Reply 1 uses casual Hinglish calibration.
            - Reply 2 varies the tone.
        """.trimIndent()

        val replies = parseWithRealClaudeApi(content)

        assertEquals(
            listOf(
                "Haan yaar, pakka aaunga! Kaunsa time aur location bataa de bhai",
                "Arrey mast! Party kaunsi jagah hai? Definitely in for it"
            ),
            replies
        )
        assertTrue(
            replies.none { it.contains("```") || it.contains("Breakdown", ignoreCase = true) },
            "Replies should not include markdown fences or commentary: $replies"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseWithRealClaudeApi(content: String): List<String> {
        val method = ClaudeApi::class.java.getDeclaredMethod("parseReplies", String::class.java)
        method.isAccessible = true
        return method.invoke(ClaudeApi(), content) as List<String>
    }

    // ===========================
    // Request Validation Tests
    // ===========================

    @Test
    fun `request includes correct headers and body structure`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices": [{"message": {"content": "Reply"}}]}""")
        )

        val latch = CountDownLatch(1)

        // Act
        claudeApi.generateReplies(
            systemPrompt = "You are a helpful assistant",
            userMessage = "Hello there",
            callback = object : ClaudeApiTestable.ReplyCallback {
                override fun onRepliesGenerated(replies: List<String>) {
                    latch.countDown()
                }

                override fun onError(error: String) {
                    latch.countDown()
                }
            }
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))

        // Assert - Verify request
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.getHeader("Content-Type")?.contains("application/json") == true)

        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(requestBody.contains("messages"))
        assertTrue(requestBody.contains("system"))
        assertTrue(requestBody.contains("max_tokens"))
        assertTrue(requestBody.contains("Hello there"))
        assertTrue(requestBody.contains("You are a helpful assistant"))
    }

    @Test
    fun `getProxyUrl returns correct URL`() {
        // Note: Testing the static method from the original class
        // URL now uses BuildConfig.API_BASE_URL which varies by build type
        val url = ClaudeApi.getProxyUrl()
        assertNotNull(url)
        // In debug builds, URL points to emulator localhost; in release, to cloudfunctions.net
        // Both should end with the function name
        assertTrue(url.contains("proxyClaudeHttp"))
    }
}

/**
 * Testable version of ClaudeApi that allows custom base URL for MockWebServer.
 */
class ClaudeApiTestable(private val baseUrl: String) {

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun generateReplies(systemPrompt: String, userMessage: String, callback: ReplyCallback) {
        try {
            val requestJson = org.json.JSONObject()
            val messagesArray = org.json.JSONArray()
            val userMsg = org.json.JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", userMessage)
            messagesArray.put(userMsg)

            requestJson.put("messages", messagesArray)
            requestJson.put("system", systemPrompt)
            requestJson.put("max_tokens", 256)

            val body = okhttp3.RequestBody.create(
                "application/json".toMediaType(),
                requestJson.toString()
            )

            val request = okhttp3.Request.Builder()
                .url(baseUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    callback.onError("Network error: ${e.message}")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!response.isSuccessful) {
                        callback.onError("API error: ${response.code}")
                        return
                    }

                    try {
                        val responseBody = response.body?.string() ?: ""
                        val jsonResponse = org.json.JSONObject(responseBody)
                        val choices = jsonResponse.optJSONArray("choices")

                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val message = firstChoice.optJSONObject("message")
                            val content = message?.optString("content", "") ?: ""

                            val replies = parseReplies(content)
                            callback.onRepliesGenerated(replies)
                        } else {
                            callback.onError("Invalid response format")
                        }
                    } catch (e: org.json.JSONException) {
                        callback.onError("Parse error: ${e.message}")
                    }
                }
            })
        } catch (e: org.json.JSONException) {
            callback.onError("JSON error: ${e.message}")
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

    interface ReplyCallback {
        fun onRepliesGenerated(replies: List<String>)
        fun onError(error: String)
    }
}
