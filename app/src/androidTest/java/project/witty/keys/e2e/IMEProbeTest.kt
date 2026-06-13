package project.witty.keys.e2e

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class IMEProbeTest : BaseKeyboardE2ETest() {

    companion object {
        private const val TAG = "IMEProbe"
        private const val ACTION_PREFIX = "project.witty.keys.debug."
        private const val SETTLE = 1500L
    }

    /**
     * Probe 1: SAB — Can we find reply chip texts?
     */
    @Test
    fun probe_SAB_replyChipTexts() {
        Log.d(TAG, "=== PROBE: SAB Reply Chips ===")

        // Inject scenario and show replies
        loadTestScenario("FRUSTRATED_BOSS")
        SystemClock.sleep(SETTLE)

        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.sendBroadcast(Intent("${ACTION_PREFIX}SHOW_SMART_REPLIES").apply {
            setPackage(ctx.packageName)
        })
        SystemClock.sleep(SETTLE)

        // Probe: Can we find OV container?
        val ov = findSABElement("project.witty.keys:id/wk_original_view")
        Log.d(TAG, "PROBE wk_original_view: ${ov != null}")

        // Probe: Can we find reply scroll?
        val scroll = findSABElement("project.witty.keys:id/wk_ov_row2_chips")
        Log.d(TAG, "PROBE wk_ov_row2_chips: ${scroll != null}")

        // Probe: Can we get reply texts?
        val texts = getReplyChipTexts()
        Log.d(TAG, "PROBE reply chip count: ${texts.size}")
        texts.forEachIndexed { i, t -> Log.d(TAG, "PROBE chip[$i]: '$t'") }

        // Probe: Can we find specific buttons?
        val brain = findSABElement("project.witty.keys:id/wk_ov_brain")
        Log.d(TAG, "PROBE wk_ov_brain: ${brain != null}")

        val tone = findSABElement("project.witty.keys:id/wk_ov_tone_btn")
        Log.d(TAG, "PROBE wk_ov_tone_btn: ${tone != null}")

        val collapse = findSABElement("project.witty.keys:id/wk_ov_collapse_btn")
        Log.d(TAG, "PROBE wk_ov_collapse_btn: ${collapse != null}")

        // Probe: MV container
        val mv = findSABElement("project.witty.keys:id/wk_memory_view")
        Log.d(TAG, "PROBE wk_memory_view: ${mv != null}")

        // Probe: Loading shimmer
        val shimmer = findSABElement("project.witty.keys:id/wk_ov_row2_shimmer")
        Log.d(TAG, "PROBE wk_ov_row2_shimmer: ${shimmer != null}")

        Log.d(TAG, "=== PROBE SAB COMPLETE ===")
    }

    /**
     * Probe 2: AI Chat — Can we find message text in the IME window?
     */
    @Test
    fun probe_AIChat_messageVisibility() {
        Log.d(TAG, "=== PROBE: AI Chat Messages ===")

        val ctx: Context = ApplicationProvider.getApplicationContext()

        // Open AI Chat
        ctx.sendBroadcast(Intent("${ACTION_PREFIX}OPEN_AI_CHAT").apply {
            setPackage(ctx.packageName)
            putExtra("mode", "general")
        })
        SystemClock.sleep(SETTLE)

        // Add user message with unique probe text
        ctx.sendBroadcast(Intent("${ACTION_PREFIX}AI_CHAT_ADD_USER_MSG").apply {
            setPackage(ctx.packageName)
            putExtra("text", "PROBE_USER_MSG_XYZ123")
        })
        SystemClock.sleep(SETTLE)

        // Probe: Can UiAutomator find the user message text?
        val userMsg = device.findObject(By.textContains("PROBE_USER_MSG_XYZ123"))
        Log.d(TAG, "PROBE user msg findable: ${userMsg != null}")
        if (userMsg != null) {
            Log.d(TAG, "PROBE user msg class: ${userMsg.className}")
            Log.d(TAG, "PROBE user msg text: ${userMsg.text}")
        }

        // Add AI response
        ctx.sendBroadcast(Intent("${ACTION_PREFIX}AI_CHAT_ADD_AI_MSG").apply {
            setPackage(ctx.packageName)
            putExtra("text", "PROBE_AI_RESP_ABC789")
            putExtra("cta_type", "REPLY_COPY")
        })
        SystemClock.sleep(SETTLE)

        // Probe: Can we find AI response?
        val aiMsg = device.findObject(By.textContains("PROBE_AI_RESP_ABC789"))
        Log.d(TAG, "PROBE ai msg findable: ${aiMsg != null}")
        if (aiMsg != null) {
            Log.d(TAG, "PROBE ai msg class: ${aiMsg.className}")
            Log.d(TAG, "PROBE ai msg text: ${aiMsg.text}")
        }

        // Probe: unified_ai_view container
        val chatView = device.findObject(By.res("project.witty.keys:id/unified_ai_view"))
        Log.d(TAG, "PROBE unified_ai_view: ${chatView != null}")

        // Cleanup
        ctx.sendBroadcast(Intent("${ACTION_PREFIX}AI_CHAT_CLEAR").apply {
            setPackage(ctx.packageName)
        })
        SystemClock.sleep(500)

        Log.d(TAG, "=== PROBE AI CHAT COMPLETE ===")
    }

    /**
     * Probe 3: Emoji — Can we find emoji content in the IME window?
     */
    @Test
    fun probe_Emoji_contentVisibility() {
        Log.d(TAG, "=== PROBE: Emoji Keyboard ===")

        val ctx: Context = ApplicationProvider.getApplicationContext()

        // Open emoji keyboard
        ctx.sendBroadcast(Intent("${ACTION_PREFIX}SHOW_EMOJI_KEYBOARD").apply {
            setPackage(ctx.packageName)
        })
        SystemClock.sleep(SETTLE)

        // Probe: Dump all findable elements in keyboard package
        val allElements = device.findObjects(By.pkg("project.witty.keys"))
        Log.d(TAG, "PROBE total elements in pkg: ${allElements.size}")

        // Look for any RecyclerView or GridView (emoji grid)
        val recyclers = device.findObjects(By.clazz("androidx.recyclerview.widget.RecyclerView"))
        Log.d(TAG, "PROBE RecyclerViews found: ${recyclers.size}")
        recyclers.forEachIndexed { i, rv ->
            val children = rv.childCount
            Log.d(TAG, "PROBE RecyclerView[$i]: $children children, bounds=${rv.visibleBounds}")
        }

        // Look for any text content in emoji area
        val textViews = device.findObjects(By.clazz("android.widget.TextView").pkg("project.witty.keys"))
        Log.d(TAG, "PROBE TextViews in keyboard: ${textViews.size}")
        textViews.take(10).forEachIndexed { i, tv ->
            Log.d(TAG, "PROBE TextView[$i]: '${tv.text}' bounds=${tv.visibleBounds}")
        }

        // Close emoji
        ctx.sendBroadcast(Intent("${ACTION_PREFIX}CLOSE_EMOJI_KEYBOARD").apply {
            setPackage(ctx.packageName)
        })
        SystemClock.sleep(500)

        Log.d(TAG, "=== PROBE EMOJI COMPLETE ===")
    }
}
