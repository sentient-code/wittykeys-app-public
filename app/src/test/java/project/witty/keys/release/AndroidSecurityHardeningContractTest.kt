package project.witty.keys.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidSecurityHardeningContractTest {

    private fun read(path: String): String = File(path).takeIf { it.exists() }?.readText().orEmpty()

    private fun assertContainsAll(content: String, values: List<String>) {
        values.forEach { value ->
            assertTrue("Expected content to contain '$value'", content.contains(value))
        }
    }

    private fun assertContainsNone(content: String, values: List<String>) {
        values.forEach { value ->
            assertFalse("Expected content not to contain '$value'", content.contains(value))
        }
    }

    @Test
    fun `android build does not ship service account or hardcoded client api secrets`() {
        assertFalse(
            "Service account private keys must never be packaged in Android raw resources.",
            File("src/main/res/raw/service_account_key.json").exists()
        )

        val apiSource = read("src/main/java/project/witty/keys/api/GoogleCustomSearchApi.java")
        assertContainsNone(
            apiSource,
            listOf(
                "AIza",
                "customsearch/v1?key=",
                "GoogleCustomSearchApi"
            )
        )

        val riskyResourceText = File("src/main/res/raw")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("json", "xml", "txt") }
            .joinToString("\n") { it.readText() }
        assertContainsNone(
            riskyResourceText,
            listOf(
                "BEGIN PRIVATE KEY",
                "\"private_key\"",
                "\"client_email\"",
                "service_account"
            )
        )
    }

    @Test
    fun `encrypted preferences use encrypted storage and do not persist firebase id tokens`() {
        val encryptedPreferences = read("src/main/java/project/witty/keys/app/helpers/EncryptedPreferences.java")
        val user = read("src/main/java/project/witty/keys/app/entities/User.java")

        assertContainsAll(
            encryptedPreferences,
            listOf(
                "EncryptedSharedPreferences.create",
                "MasterKeys.getOrCreate",
                "remove(\"user_token\")"
            )
        )
        assertContainsNone(
            encryptedPreferences,
            listOf(
                "getApplicationContext().getSharedPreferences(PREFS_FILE_NAME",
                "KEY_USER_TOKEN",
                "getString(KEY_USER_TOKEN",
                "saveString(KEY_USER_TOKEN",
                "new User(name, id, token)"
            )
        )
        assertContainsNone(
            user,
            listOf(
                "getIdToken(false)",
                "String token = getTokenResult.getToken()",
                "new User(userName, userId, token",
                "saveUserInfoLocally(newUser);"
            )
        )
    }

    @Test
    fun `analytics helpers hash user identifiers before labels or firebase user ids`() {
        val eventHelpers = read("src/main/java/project/witty/keys/app/helpers/EventHelpers.java")

        assertContainsAll(
            eventHelpers,
            listOf(
                "safeAnalyticsId",
                "hashIdentifier",
                "attachSafeUser",
                "analytics_user_id"
            )
        )
        assertContainsNone(
            eventHelpers,
            listOf(
                "setUserId(user_id)",
                "setUserId(userId)",
                "setUserId(trackingId)",
                "bundle.putString(\"label\", user_id)",
                "bundle.putString(\"label\", userId)",
                "params.putString(\"user_id\", userId)",
                "analytics.setUserProperty(\"anonymous_device_id\", trackingId)"
            )
        )
    }

    @Test
    fun `nls accessibility screenshot and precompute logs never include raw user content`() {
        val sensitiveSurfaces = listOf(
            "src/main/java/project/witty/keys/app/helpers/WittyKeysNotificationListenerService.java",
            "src/main/java/project/witty/keys/app/helpers/ScreenReaderAccessibility.java",
            "src/main/java/project/witty/keys/app/helpers/ScreenCaptureService.java",
            "src/main/java/project/witty/keys/app/helpers/ScreenshotAnalyzer.java",
            "src/main/java/project/witty/keys/app/context/ReplyPrecomputeManager.java"
        ).joinToString("\n") { read(it) }

        assertContainsNone(
            sensitiveSurfaces,
            listOf(
                "dataIn.put(\"sender\", sender)",
                "dataIn.put(\"image_path\", imagePath)",
                "Log.d(TAG, \"[NLS] Buffered (not active app): \" + packageName + \" / \" + sender)",
                "Log.d(TAG, \"[NLS] MessagingStyle: \" + sender",
                "Log.d(TAG, \"[NLS] Extras: \" + sender",
                "\" -> \" + (messageText.length()",
                "\" → \" + (messageText.length()",
                "Log.d(TAG, \"[NLS] Notification tap detected: \" + contactName",
                "Log.d(TAG, \"[NLS] Filtering likely self-sent message from: \" + sender",
                "Log.d(TAG, \"Direct reply sent via RemoteInput for \" + conversationKey)",
                "Log.d(TAG, \"[J13] Typing indicator ignored: '\" + lastMessage",
                "lastMessage.substring",
                "Log.d(TAG, \"Captured text: \" + text)",
                "Log.d(TAG, \"Screenshot saved: \" + screenshotFile.getAbsolutePath())",
                "analysis.substring",
                "Log.e(TAG, \"Screenshot analysis error for overlay: \" + error)",
                "Log.d(TAG, \"[ANALYZE] Starting analysis for: \" + imagePath)",
                "postError(callback, \"Failed to decode image: \" + imagePath)",
                "Log.w(TAG, \"[VISION] Could not parse response: \" + responseStr.substring",
                "Log.e(TAG, \"[VISION] HTTP Error \" + response.code() + \": \" + errorBody)",
                "Log.e(TAG, \"[ANALYZE] All retries exhausted: \" + errorMsg)",
                "JourneyTracer.error(traceId, \"VISION_EXHAUSTED\", \"retries_failed\", errorMsg)",
                "Fresh cache exists for \" + conversationKey",
                "dropping request for \" + conversationKey",
                "\" replies for \" + conversationKey",
                "\" for \" + conversationKey + \": \" + error",
                "Cache invalidated for \" + conversationKey",
                "cache hit for \" + conversationKey",
                "no buffered messages for \" + conversationKey",
                "generating for \" + conversationKey",
                "exception for \" + conversationKey"
            )
        )
    }

    @Test
    fun `demo and journey tracing are redacted and release safe`() {
        val demoLogger = read("src/main/java/project/witty/keys/app/helpers/DemoLogger.java")
        val journeyTracer = read("src/main/java/project/witty/keys/app/helpers/JourneyTracer.java")

        assertContainsAll(
            demoLogger + journeyTracer,
            listOf(
                "private static boolean enabled = BuildConfig.DEBUG;",
                "sanitizeForPrivacy",
                "isSensitiveKey",
                "BuildConfig.DEBUG || DebugConfig.isDebugMode"
            )
        )
        assertContainsNone(
            journeyTracer,
            listOf(
                "event.put(\"data\", data)",
                "data.toString());",
                "Log.d(TAG, logLine);"
            )
        )
    }
}
