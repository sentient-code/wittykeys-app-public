package project.witty.keys.evaluation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Performance Baseline Test Suite — Layer C.
 *
 * Measures TTFT (Time To Full Response, since proxy is non-streaming) and total
 * response time per tone mode via proxyClaudeHttp.
 *
 * Test matrix:
 *   4 tones (Savage, Professional, Casual, Empathetic)
 *   × 2 input lengths (short ~10 words, long ~60 words)
 *   × 5 measurement runs (first run = warmup, skipped from stats)
 *   = 40 measured API calls + 8 warmup calls
 *
 * Fallback detection:
 *   Production uses a 2s fast timeout → 60s fallback pattern.
 *   Any call > 2000ms is flagged as a "fallback" scenario.
 *
 * Output:
 *   - build/test-results/evaluation/performance_baseline.json
 *   - Stdout summary (avg/p95 per tone, fallback rates)
 *
 * Reference: CLAUDE_CODE_INSTRUCTIONS_B1-T2.md Step 4
 *
 * Requires: Firebase emulators running (proxyClaudeHttp endpoint)
 */
class PerformanceBaselineTest {

    companion object {
        private const val TAG = "PerformanceBaselineTest"
        private const val PROXY_URL = "http://127.0.0.1:5001/tapai-e33d2/us-central1/proxyClaudeHttp"
        private const val RUN_INTEGRATION_ENV = "WK_RUN_CLAUDE_INTEGRATION"
        private const val MAX_TOKENS = 1024

        /** Production fast-path timeout — calls exceeding this are "fallback" scenarios */
        private const val FAST_TIMEOUT_MS = 2000L

        /** Number of measured runs per tone×input combo (excluding warmup) */
        private const val RUNS_PER_COMBO = 5

        /** Output directory */
        private val RESULTS_DIR = File(
            System.getProperty("user.dir"),
            "build/test-results/evaluation"
        )

        /** Tones to benchmark */
        val TEST_TONES = listOf("Savage", "Professional", "Casual", "Empathetic")

        /** Short input (~10 words) */
        const val SHORT_INPUT = "Hey, I just got rejected from that job interview today"

        /** Long input (~60 words) */
        const val LONG_INPUT = "So basically what happened was my manager called me into " +
            "his office today and told me that they're restructuring the entire department " +
            "and my position is being eliminated effective next month, and I have no idea " +
            "what I'm going to do because I just signed a lease for a new apartment and I " +
            "have EMIs running on my car loan too"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val allResults = mutableListOf<TimedCallResult>()

    @Before
    fun setUp() {
        assumeTrue(
            "Set $RUN_INTEGRATION_ENV=true and start Firebase Functions emulator before running PerformanceBaselineTest.",
            System.getenv(RUN_INTEGRATION_ENV).equals("true", ignoreCase = true)
        )

        RESULTS_DIR.mkdirs()
        allResults.clear()
    }

    /**
     * Main test: Measure response time for each tone × input length combination.
     * 1 warmup call + 5 measured calls per combo.
     */
    @Test
    fun measureResponseTimePerTone() {
        println("[$TAG] === PERFORMANCE BASELINE TEST ===")
        println("[$TAG] Tones: ${TEST_TONES.joinToString(", ")}")
        println("[$TAG] Inputs: short (~10 words), long (~60 words)")
        println("[$TAG] Runs per combo: $RUNS_PER_COMBO (+ 1 warmup)")
        println("[$TAG] Fallback threshold: ${FAST_TIMEOUT_MS}ms")
        println()

        val inputs = mapOf("short" to SHORT_INPUT, "long" to LONG_INPUT)

        for (tone in TEST_TONES) {
            for ((inputLength, inputText) in inputs) {
                println("[$TAG] --- $tone × $inputLength ---")

                // Warmup call (not counted in stats)
                print("[$TAG]   Warmup... ")
                val warmupTime = timedReplyGeneration(tone, inputText)
                println("${warmupTime.totalTimeMs}ms (${warmupTime.replyCount} replies) [skipped]")

                // 5 measured runs
                repeat(RUNS_PER_COMBO) { run ->
                    val result = timedReplyGeneration(tone, inputText, inputLength)
                    allResults.add(result)

                    val fallbackMarker = if (result.isFallback) " [FALLBACK]" else ""
                    println("[$TAG]   Run ${run + 1}: ${result.totalTimeMs}ms (${result.replyCount} replies)$fallbackMarker")
                }
            }
            println()
        }

        // Assert at least some calls succeeded
        assertTrue(
            "Expected at least ${TEST_TONES.size * 2 * RUNS_PER_COMBO} measurements, got ${allResults.size}",
            allResults.size >= TEST_TONES.size * 2 * RUNS_PER_COMBO
        )
    }

    @After
    fun tearDown() {
        if (allResults.isEmpty()) return

        val report = buildReport()

        // Save JSON
        val outputFile = File(RESULTS_DIR, "performance_baseline.json")
        outputFile.writeText(reportToJson(report))
        println("[$TAG] Report saved: ${outputFile.absolutePath}")

        // Print summary
        printSummary(report)
    }

    // ==================== Timed API Call ====================

    /**
     * Generate 8 replies and measure total response time.
     * Since the proxy is non-streaming, TTFT ≈ total time.
     */
    private fun timedReplyGeneration(
        tone: String,
        message: String,
        inputLength: String = "warmup"
    ): TimedCallResult {
        val systemPrompt = buildSystemPrompt(tone)

        val requestJson = JSONObject().apply {
            val messagesArray = JSONArray()
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })
            put("messages", messagesArray)
            put("system", systemPrompt)
            put("max_tokens", MAX_TOKENS)
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(PROXY_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        val startTime = System.currentTimeMillis()
        val response = client.newCall(request).execute()
        val endTime = System.currentTimeMillis()
        val totalTimeMs = endTime - startTime

        val replyCount = if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            parseReplyCount(responseBody)
        } else {
            val errorBody = response.body?.string() ?: "Unknown"
            println("[$TAG]   WARNING: HTTP ${response.code} — ${errorBody.take(100)}")
            0
        }

        return TimedCallResult(
            tone = tone,
            inputLength = inputLength,
            totalTimeMs = totalTimeMs,
            replyCount = replyCount,
            isFallback = totalTimeMs > FAST_TIMEOUT_MS
        )
    }

    private fun buildSystemPrompt(tone: String): String {
        return """You are an AI keyboard assistant for WittyKeys, a WhatsApp reply suggestion app.
Generate exactly 8 contextually appropriate reply suggestions for the incoming message.

Selected tone: $tone

Rules:
- All 8 replies MUST match the "$tone" tone
- Each reply should take a DIFFERENT approach (humorous, direct, question-back, empathetic, etc.)
- Keep replies WhatsApp-appropriate length (5-25 words for casual, up to 50 for professional)
- NEVER generate: "That's great!", "I understand", "Thank you for sharing"
- NEVER start replies with "I" for emotional messages
- Return one reply per line, no numbering, no quotes"""
    }

    /**
     * Parse the proxy response and count how many non-empty reply lines were generated.
     */
    private fun parseReplyCount(responseBody: String): Int {
        return try {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices") ?: return 0
            if (choices.length() == 0) return 0
            val content = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content", "") ?: ""

            content.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.replaceFirst(Regex("^\\d+[.):]\\s*"), "") }
                .filter { it.isNotEmpty() }
                .size
        } catch (e: Exception) {
            0
        }
    }

    // ==================== Stats Computation ====================

    private fun computeStats(times: List<Long>): PerformanceStats {
        if (times.isEmpty()) {
            return PerformanceStats(avgMs = 0.0, p95Ms = 0, minMs = 0, maxMs = 0, medianMs = 0, count = 0)
        }
        val sorted = times.sorted()
        val p95Index = ((sorted.size * 0.95).toInt()).coerceIn(0, sorted.size - 1)
        val medianIndex = sorted.size / 2

        return PerformanceStats(
            avgMs = sorted.average(),
            p95Ms = sorted[p95Index],
            minMs = sorted.first(),
            maxMs = sorted.last(),
            medianMs = sorted[medianIndex],
            count = sorted.size
        )
    }

    private fun buildReport(): PerformanceBaselineReport {
        val toneSummaries = TEST_TONES.associateWith { tone ->
            val toneResults = allResults.filter { it.tone == tone }
            val shortResults = toneResults.filter { it.inputLength == "short" }
            val longResults = toneResults.filter { it.inputLength == "long" }
            val fallbackCount = toneResults.count { it.isFallback }

            TonePerformanceSummary(
                tone = tone,
                stats = computeStats(toneResults.map { it.totalTimeMs }),
                shortInputStats = computeStats(shortResults.map { it.totalTimeMs }),
                longInputStats = computeStats(longResults.map { it.totalTimeMs }),
                fallbackCount = fallbackCount,
                totalCalls = toneResults.size,
                fallbackRate = if (toneResults.isNotEmpty()) {
                    fallbackCount.toDouble() / toneResults.size
                } else 0.0
            )
        }

        val totalFallbacks = allResults.count { it.isFallback }

        return PerformanceBaselineReport(
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            toneCount = TEST_TONES.size,
            runsPerCombo = RUNS_PER_COMBO,
            toneSummaries = toneSummaries,
            overallStats = computeStats(allResults.map { it.totalTimeMs }),
            overallFallbackRate = if (allResults.isNotEmpty()) {
                totalFallbacks.toDouble() / allResults.size
            } else 0.0,
            allResults = allResults.toList()
        )
    }

    // ==================== Output ====================

    private fun printSummary(report: PerformanceBaselineReport) {
        println()
        println("=".repeat(60))
        println("  PERFORMANCE BASELINE SUMMARY")
        println("=".repeat(60))
        println()
        println("  Timestamp: ${report.timestamp}")
        println("  Tones tested: ${report.toneCount}")
        println("  Runs per combo: ${report.runsPerCombo}")
        println("  Total measurements: ${report.allResults.size}")
        println()

        // Per-tone table
        println("  %-15s  %7s  %7s  %7s  %7s  %8s".format(
            "Tone", "Avg(ms)", "P95(ms)", "Min(ms)", "Max(ms)", "Fallback"
        ))
        println("  " + "-".repeat(55))

        report.toneSummaries.forEach { (tone, summary) ->
            println("  %-15s  %7.0f  %7d  %7d  %7d  %7.0f%%".format(
                tone,
                summary.stats.avgMs,
                summary.stats.p95Ms,
                summary.stats.minMs,
                summary.stats.maxMs,
                summary.fallbackRate * 100
            ))
        }
        println("  " + "-".repeat(55))
        println("  %-15s  %7.0f  %7d  %7d  %7d  %7.0f%%".format(
            "OVERALL",
            report.overallStats.avgMs,
            report.overallStats.p95Ms,
            report.overallStats.minMs,
            report.overallStats.maxMs,
            report.overallFallbackRate * 100
        ))
        println()

        // Short vs Long breakdown
        println("  Input Length Breakdown:")
        println("  %-15s  %10s  %10s".format("Tone", "Short(avg)", "Long(avg)"))
        println("  " + "-".repeat(37))
        report.toneSummaries.forEach { (tone, summary) ->
            println("  %-15s  %9.0fms  %9.0fms".format(
                tone,
                summary.shortInputStats.avgMs,
                summary.longInputStats.avgMs
            ))
        }
        println()

        // Fallback summary
        val totalFallbacks = report.toneSummaries.values.sumOf { it.fallbackCount }
        val totalCalls = report.allResults.size
        println("  Fallback Summary: $totalFallbacks / $totalCalls calls exceeded ${FAST_TIMEOUT_MS}ms")
        println("  Overall fallback rate: ${"%.1f".format(report.overallFallbackRate * 100)}%")
        println()
        println("=".repeat(60))
    }

    private fun reportToJson(report: PerformanceBaselineReport): String {
        val json = JSONObject().apply {
            put("timestamp", report.timestamp)
            put("config", JSONObject().apply {
                put("toneCount", report.toneCount)
                put("runsPerCombo", report.runsPerCombo)
                put("fastTimeoutMs", FAST_TIMEOUT_MS)
                put("proxyUrl", PROXY_URL)
                put("tones", JSONArray(TEST_TONES))
            })

            put("overall", JSONObject().apply {
                put("avgMs", report.overallStats.avgMs)
                put("p95Ms", report.overallStats.p95Ms)
                put("minMs", report.overallStats.minMs)
                put("maxMs", report.overallStats.maxMs)
                put("medianMs", report.overallStats.medianMs)
                put("totalMeasurements", report.overallStats.count)
                put("fallbackRate", report.overallFallbackRate)
            })

            put("byTone", JSONObject().apply {
                report.toneSummaries.forEach { (tone, summary) ->
                    put(tone, JSONObject().apply {
                        put("combined", statsToJson(summary.stats))
                        put("shortInput", statsToJson(summary.shortInputStats))
                        put("longInput", statsToJson(summary.longInputStats))
                        put("fallbackCount", summary.fallbackCount)
                        put("totalCalls", summary.totalCalls)
                        put("fallbackRate", summary.fallbackRate)
                    })
                }
            })

            put("rawResults", JSONArray().apply {
                report.allResults.forEach { r ->
                    put(JSONObject().apply {
                        put("tone", r.tone)
                        put("inputLength", r.inputLength)
                        put("totalTimeMs", r.totalTimeMs)
                        put("replyCount", r.replyCount)
                        put("isFallback", r.isFallback)
                    })
                }
            })
        }

        return json.toString(2)
    }

    private fun statsToJson(stats: PerformanceStats): JSONObject {
        return JSONObject().apply {
            put("avgMs", stats.avgMs)
            put("p95Ms", stats.p95Ms)
            put("minMs", stats.minMs)
            put("maxMs", stats.maxMs)
            put("medianMs", stats.medianMs)
            put("count", stats.count)
        }
    }
}
