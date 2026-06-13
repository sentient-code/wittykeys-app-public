package project.witty.keys.e2e.golden

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

/**
 * Golden Screenshot Manager — S1.1 (updated S2.5.6)
 *
 * Captures the full keyboard + SAB region from a device screenshot, crops from
 * the SAB top to the screen bottom, and saves as PNG. Used by both
 * GoldenCaptureTest (capture mode) and GoldenRegressionTest (compare mode).
 *
 * File I/O uses shell commands to write to /data/local/tmp/ and
 * UiAutomation.executeShellCommand() to read, avoiding Android 14
 * scoped storage EACCES issues with app external directories.
 *
 * Capture strategy:
 * 1. Element-based: finds SAB via UI Automator, uses SAB top → screen bottom (preferred)
 * 2. Fallback: uses hardcoded keyboard region for Motorola Razr 50 (1080x2640)
 *
 * Device: Motorola Razr 50 (1080×2640, ~2.75 density)
 */
class GoldenScreenshotManager(private val device: UiDevice) {

    companion object {
        private const val TAG = "GoldenScreenshot"
        private const val KEYBOARD_PKG = "project.witty.keys"

        // SAB root container resource ID (from smart_assistant_bar.xml)
        private const val RES_SAB_ROOT = "$KEYBOARD_PKG:id/smart_assistant_bar_root"

        // UnifiedAiView resource ID (from main_keyboard_frame.xml)
        private const val RES_AI_CHAT_VIEW = "$KEYBOARD_PKG:id/unified_ai_view"

        // MainKeyboardView resource ID (the actual QWERTY key rows)
        private const val RES_KEYBOARD_VIEW = "$KEYBOARD_PKG:id/keyboard_view"

        // Shell-accessible storage path (shell user has read/write access)
        const val GOLDEN_BASE_DIR = "/data/local/tmp/wittykeys_goldens"

        // Fallback crop region for Motorola Razr 50 (1080x2640)
        // Full keyboard + SAB area: from keyboard top to screen bottom.
        // Keyboard+SAB typically starts around Y=1580 on this device.
        private const val FALLBACK_KEYBOARD_TOP = 1580
        private const val SCREEN_WIDTH = 1080
        private const val SCREEN_HEIGHT = 2640

        // Navigation bar height to crop from bottom (48dp * ~2.75 density = ~132px)
        private const val NAV_BAR_HEIGHT_PX = 132
    }

    /**
     * Take a full device screenshot using UiAutomation.takeScreenshot().
     * Returns a Bitmap directly without file I/O, avoiding permission issues.
     */
    private fun captureScreenshot(): Bitmap? {
        return try {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val bitmap = automation.takeScreenshot()

            if (bitmap == null) {
                android.util.Log.e(TAG, "UiAutomation.takeScreenshot() returned null")
            } else {
                android.util.Log.d(TAG, "Screenshot captured: ${bitmap.width}x${bitmap.height}")
            }

            bitmap
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error capturing screenshot via UiAutomation", e)
            null
        }
    }

    /**
     * Capture the full keyboard + SAB region by finding the SAB element via UI Automator
     * and cropping from its top to the screen bottom (includes key grid).
     * Falls back to hardcoded region if element cannot be found.
     *
     * @param outputPath Shell-accessible path (e.g., /data/local/tmp/wittykeys_goldens/G01.png)
     * @return true if capture succeeded
     */
    fun captureSABScreenshot(outputPath: String): Boolean {
        return try {
            val fullBitmap = captureScreenshot()
            if (fullBitmap == null) {
                android.util.Log.e(TAG, "Failed to capture device screenshot")
                return false
            }

            // Determine keyboard top: use SAB element bounds, fall back to hardcoded value
            val sabBounds = getSABBounds()
            val keyboardTop = if (sabBounds != null) {
                android.util.Log.d(TAG, "SAB element bounds: $sabBounds, using top=${sabBounds.top}")
                sabBounds.top
            } else {
                android.util.Log.w(TAG, "SAB element not found, using fallback keyboardTop=$FALLBACK_KEYBOARD_TOP")
                FALLBACK_KEYBOARD_TOP
            }

            // Crop from keyboard top to screen bottom, full width
            val cropRect = Rect(0, keyboardTop, fullBitmap.width, fullBitmap.height)

            // Validate crop region
            val validRect = Rect(
                cropRect.left.coerceIn(0, fullBitmap.width),
                cropRect.top.coerceIn(0, fullBitmap.height),
                cropRect.right.coerceIn(0, fullBitmap.width),
                cropRect.bottom.coerceIn(0, fullBitmap.height)
            )

            if (validRect.width() <= 0 || validRect.height() <= 0) {
                android.util.Log.e(TAG, "Invalid crop region after validation: $validRect")
                fullBitmap.recycle()
                return false
            }

            // Crop to keyboard + SAB region
            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap,
                validRect.left,
                validRect.top,
                validRect.width(),
                validRect.height()
            )

            // Save cropped bitmap via shell
            val saved = saveBitmapViaShell(croppedBitmap, outputPath)

            // Cleanup
            if (croppedBitmap != fullBitmap) croppedBitmap.recycle()
            fullBitmap.recycle()

            if (saved) {
                android.util.Log.d(TAG, "Keyboard+SAB screenshot saved: $outputPath " +
                    "(${validRect.width()}x${validRect.height()}px, crop top=$keyboardTop)")
            }
            saved
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error capturing keyboard+SAB screenshot", e)
            false
        }
    }

    /**
     * Capture full device screenshot without cropping (for debug/calibration).
     *
     * @param outputPath Shell-accessible path
     * @return true if capture succeeded
     */
    fun captureFullScreenshot(outputPath: String): Boolean {
        return try {
            val fullBitmap = captureScreenshot()
            if (fullBitmap == null) {
                android.util.Log.e(TAG, "Failed to capture full screenshot")
                return false
            }

            val saved = saveBitmapViaShell(fullBitmap, outputPath)
            fullBitmap.recycle()
            saved
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error capturing full screenshot", e)
            false
        }
    }

    /**
     * Capture the AI Chat region by finding the UnifiedAiView element via UI Automator
     * and cropping from its top to the screen bottom.
     * Falls back to full-screen capture if element not found.
     */
    fun captureAiChatScreenshot(outputPath: String): Boolean {
        return try {
            val fullBitmap = captureScreenshot()
            if (fullBitmap == null) {
                android.util.Log.e(TAG, "Failed to capture device screenshot for AI Chat")
                return false
            }

            val aiChatBounds = getAiChatBounds()
            val cropTop = if (aiChatBounds != null) {
                android.util.Log.d(TAG, "DEBUG: AI Chat element bounds: $aiChatBounds, top=${aiChatBounds.top}")
                aiChatBounds.top
            } else {
                android.util.Log.w(TAG, "DEBUG: AI Chat element not found, using fallback keyboardTop=$FALLBACK_KEYBOARD_TOP")
                FALLBACK_KEYBOARD_TOP
            }

            // Crop from AI view top to screen bottom minus nav bar
            val cropBottom = (fullBitmap.height - NAV_BAR_HEIGHT_PX).coerceAtLeast(cropTop + 1)
            android.util.Log.d(TAG, "DEBUG: fullBitmap=${fullBitmap.width}x${fullBitmap.height}, cropTop=$cropTop, cropBottom=$cropBottom")
            val cropRect = Rect(0, cropTop, fullBitmap.width, cropBottom)
            val validRect = Rect(
                cropRect.left.coerceIn(0, fullBitmap.width),
                cropRect.top.coerceIn(0, fullBitmap.height),
                cropRect.right.coerceIn(0, fullBitmap.width),
                cropRect.bottom.coerceIn(0, fullBitmap.height)
            )

            if (validRect.width() <= 0 || validRect.height() <= 0) {
                android.util.Log.e(TAG, "Invalid AI Chat crop region: $validRect")
                fullBitmap.recycle()
                return false
            }

            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap, validRect.left, validRect.top,
                validRect.width(), validRect.height()
            )

            val saved = saveBitmapViaShell(croppedBitmap, outputPath)
            if (croppedBitmap != fullBitmap) croppedBitmap.recycle()
            fullBitmap.recycle()

            if (saved) {
                android.util.Log.d(TAG, "AI Chat screenshot saved: $outputPath " +
                    "(${validRect.width()}x${validRect.height()}px, crop top=$cropTop, bottom=$cropBottom)")
            }
            saved
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error capturing AI Chat screenshot", e)
            false
        }
    }

    /**
     * Capture Reply Mode composite: takes a pre-captured keyboard screenshot
     * and composites the AI header strip from the CURRENT screen on top of
     * the keyboard rows.
     *
     * IMPORTANT: kbViewTop must be captured BEFORE switching to reply mode,
     * since the keyboard position changes when the AI header is added.
     *
     * @param keyboardFullScreenshot Full screenshot captured while keyboard was visible
     * @param kbViewTop Top Y coordinate of MainKeyboardView in the keyboard screenshot
     * @param outputPath Where to save the composite
     * @return true if successful
     */
    fun captureReplyModeComposite(keyboardFullScreenshot: Bitmap, kbViewTop: Int, outputPath: String): Boolean {
        return try {
            // 1. Capture current screen (reply mode header should be showing)
            val headerFullScreenshot = captureScreenshot()
            if (headerFullScreenshot == null) {
                android.util.Log.e(TAG, "Failed to capture header screenshot for reply mode")
                return false
            }

            // 2. Get AI Chat header bounds from current screen
            val aiChatBounds = getAiChatBounds()
            val headerTop = aiChatBounds?.top ?: FALLBACK_KEYBOARD_TOP
            val density = android.content.res.Resources.getSystem().displayMetrics.density
            val headerHeight = aiChatBounds?.let {
                (it.bottom - it.top).coerceAtMost((130 * density).toInt())
            } ?: (120 * density).toInt()

            // 3. Keyboard rows from pre-captured screenshot using pre-captured bounds
            val kbBottom = (keyboardFullScreenshot.height - NAV_BAR_HEIGHT_PX)
                .coerceAtMost(keyboardFullScreenshot.height)
                .coerceAtLeast(kbViewTop + 1)

            android.util.Log.d(TAG, "Composite: headerTop=$headerTop, headerH=$headerHeight, " +
                "kbViewTop=$kbViewTop, kbBottom=$kbBottom")

            // 4. Create composite bitmap
            val kbRowsHeight = kbBottom - kbViewTop
            val compositeHeight = headerHeight + kbRowsHeight
            val composite = Bitmap.createBitmap(
                keyboardFullScreenshot.width, compositeHeight, Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(composite)

            // Draw AI header strip at top
            val headerSrcRect = Rect(0, headerTop, headerFullScreenshot.width,
                (headerTop + headerHeight).coerceAtMost(headerFullScreenshot.height))
            val headerDstRect = Rect(0, 0, composite.width, headerHeight)
            canvas.drawBitmap(headerFullScreenshot, headerSrcRect, headerDstRect, null)

            // Draw keyboard rows below header
            val kbSrcRect = Rect(0, kbViewTop, keyboardFullScreenshot.width, kbBottom)
            val kbDstRect = Rect(0, headerHeight, composite.width, compositeHeight)
            canvas.drawBitmap(keyboardFullScreenshot, kbSrcRect, kbDstRect, null)

            headerFullScreenshot.recycle()
            // Don't recycle keyboardFullScreenshot — caller owns it

            val saved = saveBitmapViaShell(composite, outputPath)
            composite.recycle()

            if (saved) {
                android.util.Log.d(TAG, "Reply mode composite saved: $outputPath " +
                    "(headerH=$headerHeight, kbRowsH=$kbRowsHeight)")
            }
            saved
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error capturing reply mode composite", e)
            false
        }
    }

    /**
     * Capture a full screenshot and return it as a Bitmap (for compositing).
     */
    fun captureFullBitmap(): Bitmap? = captureScreenshot()

    /**
     * Get the on-screen bounds of the UnifiedAiView element.
     */
    fun getAiChatBounds(): Rect? {
        return try {
            val element = device.findObject(By.res(RES_AI_CHAT_VIEW))
            if (element != null) {
                val bounds = element.visibleBounds
                Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error finding AI Chat bounds", e)
            null
        }
    }

    /**
     * Load a PNG from a shell-accessible path as a Bitmap.
     * Uses UiAutomation.executeShellCommand() to read binary data via ParcelFileDescriptor,
     * bypassing Android scoped storage restrictions.
     *
     * @param path Shell-accessible path (e.g., /data/local/tmp/wittykeys_goldens/G01.png)
     * @return Bitmap or null if file doesn't exist or can't be decoded
     */
    fun loadGolden(path: String): Bitmap? {
        return try {
            // Check file exists via stat
            val sizeStr = device.executeShellCommand("stat -c %s $path 2>/dev/null").trim()
            if ((sizeStr.toLongOrNull() ?: 0) <= 0) {
                android.util.Log.w(TAG, "Golden file not found: $path")
                return null
            }

            // Read binary data via UiAutomation shell command (returns ParcelFileDescriptor)
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val pfd = automation.executeShellCommand("cat $path")
            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                android.util.Log.e(TAG, "Failed to decode bitmap from: $path")
            }
            bitmap
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error loading golden from: $path", e)
            null
        }
    }

    /**
     * Check if a file exists at the given shell-accessible path.
     * Uses stat instead of test -f because UiDevice.executeShellCommand
     * doesn't reliably handle && and || shell operators.
     */
    fun fileExists(path: String): Boolean {
        val sizeStr = device.executeShellCommand("stat -c %s $path 2>/dev/null").trim()
        return (sizeStr.toLongOrNull() ?: 0) > 0
    }

    /**
     * Get the on-screen bounds of the MainKeyboardView (QWERTY key rows).
     * This skips the SAB and any banners above the actual keyboard.
     */
    fun getMainKeyboardViewBounds(): Rect? {
        return try {
            val element = device.findObject(By.res(RES_KEYBOARD_VIEW))
            if (element != null) {
                val bounds = element.visibleBounds
                Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error finding MainKeyboardView bounds", e)
            null
        }
    }

    /**
     * Get the on-screen bounds of the SAB element via UI Automator.
     *
     * @return Rect with SAB bounds, or null if element not found
     */
    fun getSABBounds(): Rect? {
        return try {
            val sabElement = device.findObject(By.res(RES_SAB_ROOT))
            if (sabElement != null) {
                val bounds = sabElement.visibleBounds
                Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error finding SAB bounds", e)
            null
        }
    }

    /**
     * Public API to save a bitmap to a shell-accessible path.
     * Used by regression tests to save diff/side-by-side images.
     */
    fun saveBitmap(bitmap: Bitmap, outputPath: String): Boolean {
        return saveBitmapViaShell(bitmap, outputPath)
    }

    /**
     * Save a bitmap as PNG to a shell-accessible path using UiAutomation's
     * executeShellCommandRwe() to pipe binary data directly to a file.
     * Uses "sh -c" to ensure shell redirection is properly interpreted.
     */
    private fun saveBitmapViaShell(bitmap: Bitmap, outputPath: String): Boolean {
        return try {
            // 1. Ensure parent directory exists
            val parentDir = outputPath.substringBeforeLast('/')
            device.executeShellCommand("mkdir -p $parentDir")
            device.executeShellCommand("rm -f $outputPath")

            // 2. Use dd to write stdin directly to file (no shell redirection needed)
            //    Returns: [0]=stdout(read), [1]=stdin(write), [2]=stderr(read)
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val rwe = automation.executeShellCommandRwe("dd of=$outputPath")

            // 3. Write PNG bytes to stdin pipe
            val stdinStream = ParcelFileDescriptor.AutoCloseOutputStream(rwe[1])
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stdinStream)
            stdinStream.flush()
            stdinStream.close()

            // 4. Wait for process to complete by reading stdout until EOF
            val stdoutStream = ParcelFileDescriptor.AutoCloseInputStream(rwe[0])
            stdoutStream.readBytes() // blocks until cat finishes
            stdoutStream.close()

            // 5. Read any stderr for debugging
            val stderrStream = ParcelFileDescriptor.AutoCloseInputStream(rwe[2])
            val stderr = String(stderrStream.readBytes()).trim()
            stderrStream.close()
            if (stderr.isNotEmpty()) {
                android.util.Log.w(TAG, "Shell stderr: $stderr")
            }

            // 6. Verify file was written
            val sizeStr = device.executeShellCommand("stat -c %s $outputPath 2>/dev/null").trim()
            val fileSize = sizeStr.toLongOrNull() ?: 0

            if (fileSize > 0) {
                android.util.Log.d(TAG, "Bitmap saved via shell: $outputPath ($fileSize bytes)")
                true
            } else {
                android.util.Log.e(TAG, "Shell write produced empty file: $outputPath")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error saving bitmap via shell to $outputPath", e)
            false
        }
    }
}
