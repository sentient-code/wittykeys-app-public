package project.witty.keys.evaluation

import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * AI Response Quality Test Suite — Layer B Execution.
 *
 * Parameterized test that iterates over the 60 golden test cases.
 * For each test case:
 *   1. Generate 8 reply suggestions via proxyClaudeHttp
 *   2. Evaluate replies with LLMAIJudge (7-dimension rubric)
 *   3. Assert pass/fail
 *   4. Save per-test-case results as JSON for aggregation
 *
 * Reference: CLAUDE_CODE_INSTRUCTIONS_B1-T2.md Step 3
 *
 * Requires: Firebase emulators running (proxyClaudeHttp endpoint)
 */
@RunWith(Parameterized::class)
class AIResponseQualityTest(private val testCase: GoldenTestCase) {

    companion object {
        private const val TAG = "AIResponseQualityTest"
        private const val RUN_INTEGRATION_ENV = "WK_RUN_CLAUDE_INTEGRATION"

        /** Output directory for per-test-case JSON results */
        private val RESULTS_DIR = File(
            System.getProperty("user.dir"),
            "build/test-results/evaluation"
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> = GOLDEN_TEST_DATASET.map { arrayOf(it) }
    }

    private lateinit var judge: LLMAIJudge
    private var result: EvaluationResult? = null

    @Before
    fun setUp() {
        assumeTrue(
            "Set $RUN_INTEGRATION_ENV=true and start Firebase Functions emulator before running AIResponseQualityTest.",
            System.getenv(RUN_INTEGRATION_ENV).equals("true", ignoreCase = true)
        )

        judge = LLMAIJudge()
        RESULTS_DIR.mkdirs()
    }

    @Test
    fun evaluateAIResponse() {
        println("[$TAG] === START: ${testCase.testCaseId} ===")
        println("[$TAG] Message: ${testCase.input.message.take(60)}")
        println("[$TAG] Tone: ${testCase.input.selectedTone}, Language: ${testCase.input.languageContext}")

        // Step 1: Generate 8 reply suggestions via proxyClaudeHttp
        val replies = judge.generateReplies(testCase)
        println("[$TAG] Generated ${replies.size} replies")
        replies.forEachIndexed { i, reply ->
            println("[$TAG]   Reply ${i + 1}: ${reply.take(80)}")
        }

        // Step 2: Evaluate replies with LLMAIJudge (7-dimension rubric scoring)
        val evaluationResult = judge.evaluateTestCase(testCase, replies)
        result = evaluationResult

        // Step 3: Log scores
        println("[$TAG] Scores:")
        evaluationResult.scores.forEach { (dim, score) ->
            println("[$TAG]   $dim: $score")
        }
        println("[$TAG] Overall: ${evaluationResult.overallScore}")
        println("[$TAG] Pass: ${evaluationResult.pass}")

        if (evaluationResult.flags.verbosityPenaltyApplied) {
            println("[$TAG] WARNING: Verbosity penalty was applied")
        }
        if (evaluationResult.flags.safetyIssue) {
            println("[$TAG] CRITICAL: Safety issue detected!")
        }

        // Step 4: Assert pass/fail
        val failureMessage = buildFailureMessage(evaluationResult)
        assertTrue(failureMessage, evaluationResult.pass)

        println("[$TAG] === PASSED: ${testCase.testCaseId} ===")
    }

    @After
    fun tearDown() {
        // Save per-test-case result as JSON for later aggregation
        val evalResult = result ?: return
        val outputFile = File(RESULTS_DIR, "result_${testCase.testCaseId}.json")

        val json = JSONObject().apply {
            put("testCaseId", evalResult.testCaseId)
            put("category", testCase.category.name)
            put("tone", testCase.input.selectedTone)
            put("language", testCase.input.languageContext.name)
            put("message", testCase.input.message)
            put("overallScore", evalResult.overallScore)
            put("pass", evalResult.pass)

            put("scores", JSONObject().apply {
                evalResult.scores.forEach { (dim, score) -> put(dim, score) }
            })

            put("reasoning", JSONObject().apply {
                evalResult.reasoning.forEach { (dim, reason) -> put(dim, reason) }
            })

            put("flags", JSONObject().apply {
                put("verbosityPenaltyApplied", evalResult.flags.verbosityPenaltyApplied)
                put("safetyIssue", evalResult.flags.safetyIssue)
                evalResult.flags.distressOverrideTrigger?.let { put("distressOverrideTrigger", it) }
                evalResult.flags.crossModelAgreement?.let { put("crossModelAgreement", it) }
            })

            put("replies", org.json.JSONArray(evalResult.replies))
        }

        outputFile.writeText(json.toString(2))
        println("[$TAG] Result saved: ${outputFile.absolutePath}")
    }

    // ==================== Helpers ====================

    /**
     * Build a detailed failure message showing which dimensions failed and why.
     */
    private fun buildFailureMessage(result: EvaluationResult): String {
        val sb = StringBuilder()
        sb.appendLine("Test case ${result.testCaseId} FAILED (overall=${result.overallScore})")
        sb.appendLine("Message: \"${testCase.input.message.take(60)}\"")
        sb.appendLine("Tone: ${testCase.input.selectedTone}")
        sb.appendLine()

        // Show failed dimensions
        val failedDimensions = result.scores
            .filter { (key, value) ->
                if (key == "safety_compliance") value < 5.0
                else value < EVALUATION_RUBRIC.passThresholds.perDimensionMinimum
            }

        if (failedDimensions.isNotEmpty()) {
            sb.appendLine("Failed dimensions:")
            failedDimensions.forEach { (dim, score) ->
                val threshold = if (dim == "safety_compliance") 5.0
                    else EVALUATION_RUBRIC.passThresholds.perDimensionMinimum
                sb.appendLine("  - $dim: $score (min: $threshold)")
                result.reasoning[dim]?.let { sb.appendLine("    Reason: $it") }
            }
        }

        if (result.overallScore < EVALUATION_RUBRIC.passThresholds.overallMinimum) {
            sb.appendLine("Overall score ${result.overallScore} < minimum ${EVALUATION_RUBRIC.passThresholds.overallMinimum}")
        }

        if (result.flags.safetyIssue) {
            sb.appendLine("SAFETY VIOLATION — entire test fails regardless of other scores")
        }

        // Show the replies that were evaluated
        sb.appendLine()
        sb.appendLine("Replies evaluated:")
        result.replies.forEachIndexed { i, reply ->
            sb.appendLine("  ${i + 1}. ${reply.take(80)}")
        }

        return sb.toString()
    }
}
