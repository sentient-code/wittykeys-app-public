package project.witty.keys.e2e.golden

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Pixel Diff Comparator — S1.2
 *
 * Compares two bitmaps pixel-by-pixel. Calculates the percentage of pixels that
 * differ beyond a per-channel tolerance. Generates a visual diff image highlighting
 * changed pixels in red.
 *
 * Thresholds:
 * - pixelTolerance: Per-channel RGB distance (0-255). Pixels with any channel
 *   differing by more than this are flagged as "changed".
 * - areaThreshold: Max percentage of changed pixels for a PASS result (0.0-100.0).
 *   Default 0.5% — if more than 0.5% of pixels changed, the comparison fails.
 */
data class DiffResult(
    val diffPercentage: Double,     // 0.0 to 100.0
    val diffBitmap: Bitmap?,        // Visual diff image (null if identical)
    val passed: Boolean,            // diffPercentage <= areaThreshold
    val message: String,            // Human-readable result
    val changedPixels: Int,         // Number of changed pixels
    val totalPixels: Int            // Total pixel count
)

class PixelDiffComparator(
    private val pixelTolerance: Int = 30,       // Per-channel RGB tolerance (0-255)
    private val areaThreshold: Double = 0.5     // Max % of changed pixels for PASS
) {

    companion object {
        private const val TAG = "PixelDiffComparator"

        // Diff visualization colors
        private const val DIFF_COLOR = Color.RED              // Changed pixels
        private const val UNCHANGED_ALPHA = 80                // Dimmed unchanged pixels (0-255)
        private const val DIFF_MARKER_ALPHA = 255             // Full opacity for changed pixels
    }

    /**
     * Compare a golden (reference) bitmap against a current (test) screenshot.
     *
     * @param golden The approved golden screenshot
     * @param current The newly captured screenshot
     * @return DiffResult with percentage, visual diff, and pass/fail
     */
    fun compare(golden: Bitmap, current: Bitmap): DiffResult {
        // 1. Verify dimensions match
        if (golden.width != current.width || golden.height != current.height) {
            return DiffResult(
                diffPercentage = 100.0,
                diffBitmap = null,
                passed = false,
                message = "FAIL: Dimension mismatch — golden(${golden.width}x${golden.height}) " +
                    "vs current(${current.width}x${current.height})",
                changedPixels = golden.width * golden.height,
                totalPixels = golden.width * golden.height
            )
        }

        val width = golden.width
        val height = golden.height
        val totalPixels = width * height

        // 2. Extract pixel arrays for efficient comparison
        val goldenPixels = IntArray(totalPixels)
        val currentPixels = IntArray(totalPixels)
        golden.getPixels(goldenPixels, 0, width, 0, 0, width, height)
        current.getPixels(currentPixels, 0, width, 0, 0, width, height)

        // 3. Compare pixels and build diff bitmap
        val diffPixels = IntArray(totalPixels)
        var changedCount = 0

        for (i in 0 until totalPixels) {
            val gPixel = goldenPixels[i]
            val cPixel = currentPixels[i]

            val rDiff = abs(Color.red(gPixel) - Color.red(cPixel))
            val gDiff = abs(Color.green(gPixel) - Color.green(cPixel))
            val bDiff = abs(Color.blue(gPixel) - Color.blue(cPixel))

            val isChanged = rDiff > pixelTolerance ||
                gDiff > pixelTolerance ||
                bDiff > pixelTolerance

            if (isChanged) {
                changedCount++
                // Mark changed pixel in RED with full opacity
                diffPixels[i] = Color.argb(DIFF_MARKER_ALPHA, 255, 0, 0)
            } else {
                // Show unchanged pixel dimmed (low alpha original)
                diffPixels[i] = Color.argb(
                    UNCHANGED_ALPHA,
                    Color.red(gPixel),
                    Color.green(gPixel),
                    Color.blue(gPixel)
                )
            }
        }

        // 4. Calculate diff percentage
        val diffPercentage = if (totalPixels > 0) {
            (changedCount.toDouble() / totalPixels) * 100.0
        } else {
            0.0
        }

        // 5. Build diff bitmap
        val diffBitmap = if (changedCount > 0) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.setPixels(diffPixels, 0, width, 0, 0, width, height)
            }
        } else {
            null
        }

        // 6. Determine pass/fail
        val passed = diffPercentage <= areaThreshold

        val message = if (passed) {
            "PASS: %.3f%% pixels changed (%d/%d) — within %.1f%% threshold".format(
                diffPercentage, changedCount, totalPixels, areaThreshold
            )
        } else {
            "FAIL: %.3f%% pixels changed (%d/%d) — exceeds %.1f%% threshold".format(
                diffPercentage, changedCount, totalPixels, areaThreshold
            )
        }

        android.util.Log.d(TAG, message)

        return DiffResult(
            diffPercentage = diffPercentage,
            diffBitmap = diffBitmap,
            passed = passed,
            message = message,
            changedPixels = changedCount,
            totalPixels = totalPixels
        )
    }

    /**
     * Save the visual diff image to a file for evidence/debugging.
     *
     * @param result DiffResult from compare()
     * @param outputFile Where to save the diff PNG
     * @return true if saved successfully, false if no diff or save failed
     */
    fun saveDiffImage(result: DiffResult, outputFile: File): Boolean {
        val bitmap = result.diffBitmap ?: return false
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            android.util.Log.d(TAG, "Diff image saved: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error saving diff image", e)
            false
        }
    }

    /**
     * Generate a side-by-side comparison image: golden | current | diff
     * Useful for human review of failures.
     *
     * @param golden The golden bitmap
     * @param current The current bitmap
     * @param diff The diff bitmap (from DiffResult)
     * @return Combined side-by-side bitmap, or null if inputs are invalid
     */
    fun generateSideBySide(golden: Bitmap, current: Bitmap, diff: Bitmap?): Bitmap? {
        if (golden.width != current.width || golden.height != current.height) return null

        val panelWidth = golden.width
        val panelHeight = golden.height
        val gap = 4 // 4px gap between panels
        val totalWidth = panelWidth * 3 + gap * 2

        val combined = Bitmap.createBitmap(totalWidth, panelHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(combined)

        // Fill background with dark gray (for gap visibility)
        canvas.drawColor(Color.DKGRAY)

        // Draw golden on left
        canvas.drawBitmap(golden, 0f, 0f, null)

        // Draw current in center
        canvas.drawBitmap(current, (panelWidth + gap).toFloat(), 0f, null)

        // Draw diff on right (or blank if no diff)
        if (diff != null) {
            canvas.drawBitmap(diff, (panelWidth * 2 + gap * 2).toFloat(), 0f, null)
        }

        return combined
    }
}
