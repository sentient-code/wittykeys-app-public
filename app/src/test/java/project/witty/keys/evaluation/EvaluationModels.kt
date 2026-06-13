package project.witty.keys.evaluation

/**
 * Evaluation Models — Data classes for the LLM-as-Judge framework.
 *
 * These classes define:
 * - Golden test case structure (input + acceptance criteria + edge case flags)
 * - Evaluation rubric (7 dimensions with scoring guides)
 * - Evaluation results (per-dimension scores + pass/fail + reasoning)
 * - Pass thresholds (configurable quality gates)
 *
 * Reference: GOLDEN_TEST_DATASET_B1-T2.md, LLM_JUDGE_FRAMEWORK_B1-T2.md
 */

// ==================== Golden Test Dataset Models ====================

data class GoldenTestCase(
    val testCaseId: String,            // TC-01, TC-02, etc.
    val category: TestCategory,
    val input: TestInput,
    val acceptanceCriteria: AcceptanceCriteria,
    val edgeCaseFlags: EdgeCaseFlags = EdgeCaseFlags(),
    val rationale: String
) {
    override fun toString(): String = "$testCaseId (${category.name}): ${input.message.take(40)}..."
}

enum class TestCategory {
    TONE_ACCURACY,
    LANGUAGE,
    EDGE_CASE,
    CROSS_TONE,
    PERFORMANCE
}

data class TestInput(
    val message: String,
    val selectedTone: String,
    val languageContext: LanguageContext = LanguageContext.ENGLISH,
    val conversationHistory: String? = null
)

enum class LanguageContext {
    ENGLISH,
    HINGLISH,
    HINDI,
    MIXED
}

data class AcceptanceCriteria(
    val tone: ToneCriteria = ToneCriteria(),
    val language: LanguageCriteria = LanguageCriteria(),
    val length: LengthCriteria = LengthCriteria(),
    val contextRelevance: ContextRelevanceCriteria = ContextRelevanceCriteria(),
    val safety: SafetyCriteria = SafetyCriteria(),
    val creativity: CreativityCriteria = CreativityCriteria()
)

data class ToneCriteria(
    val mustMatch: String = "",
    val tonalCharacteristics: String = ""
)

data class LanguageCriteria(
    val required: String = "Match Input",
    val romanization: String = "Roman script only",
    val rationale: String = ""
)

data class LengthCriteria(
    val idealRange: String = "5-25 words",
    val rationale: String = "WhatsApp context — shorter is better"
)

data class ContextRelevanceCriteria(
    val mustReference: List<String> = emptyList(),
    val rationale: String = ""
)

data class SafetyCriteria(
    val constraints: List<String> = emptyList(),
    val rationale: String = ""
)

data class CreativityCriteria(
    val minLevel: String = "thoughtful",
    val rationale: String = ""
)

data class EdgeCaseFlags(
    val distressDetected: Boolean = false,
    val emptyOrImageOnly: Boolean = false,
    val offensiveInput: Boolean = false,
    val mixedLanguage: Boolean = false,
    val longMessage: Boolean = false,
    val ambiguousLanguage: Boolean = false
)

// ==================== Evaluation Rubric Models ====================

data class EvaluationRubric(
    val dimensions: Map<String, DimensionSpec>,
    val passThresholds: PassThresholds,
    val verbosityPenalties: VerbosityPenalties = VerbosityPenalties()
)

data class DimensionSpec(
    val name: String,
    val description: String,
    val scoringGuide: Map<Int, String>   // 1 → "Failed", 2 → "Weak", etc.
)

data class PassThresholds(
    val perDimensionMinimum: Double = 3.0,
    val overallMinimum: Double = 3.5,
    val safetyCompliance: Double = 5.0     // Zero tolerance
)

data class VerbosityPenalties(
    val casualToneMaxWords: Int = 30,      // > 30 words → max length score = 3/5
    val professionalToneMaxWords: Int = 50, // > 50 words → max length score = 4/5
    val absoluteMaxWords: Int = 100         // > 100 words → max length score = 2/5
)

// ==================== Evaluation Result Models ====================

data class EvaluationResult(
    val testCaseId: String,
    val scores: Map<String, Double>,       // dimension name → score (1.0-5.0)
    val overallScore: Double,
    val pass: Boolean,
    val reasoning: Map<String, String>,    // dimension name → reasoning text
    val flags: EvaluationFlags,
    val replies: List<String> = emptyList() // The 8 replies that were evaluated
)

data class EvaluationFlags(
    val verbosityPenaltyApplied: Boolean = false,
    val safetyIssue: Boolean = false,
    val distressOverrideTrigger: String? = null,
    val crossModelAgreement: Double? = null
)

// ==================== Aggregate Report Models ====================

data class EvaluationReport(
    val batchId: String,
    val totalTestCases: Int,
    val passCount: Int,
    val failCount: Int,
    val passRate: Double,
    val byCategory: Map<String, CategoryResult>,
    val byToneMode: Map<String, ToneModeResult>,
    val dimensionAverages: Map<String, Double>,
    val failedCases: List<String>,
    val results: List<EvaluationResult>
)

data class CategoryResult(
    val pass: Int,
    val fail: Int,
    val passRate: Double
)

data class ToneModeResult(
    val pass: Int,
    val fail: Int,
    val passRate: Double,
    val avgScore: Double
)

// ==================== Performance Models ====================

/**
 * Timing result from a single proxy call (reply generation).
 */
data class TimedCallResult(
    val tone: String,
    val inputLength: String,          // "short" or "long"
    val totalTimeMs: Long,
    val replyCount: Int,
    val isFallback: Boolean           // true if totalTimeMs > FAST_TIMEOUT_MS
)

/**
 * Statistics computed from a list of timed measurements.
 */
data class PerformanceStats(
    val avgMs: Double,
    val p95Ms: Long,
    val minMs: Long,
    val maxMs: Long,
    val medianMs: Long,
    val count: Int
)

/**
 * Per-tone performance summary (both input lengths aggregated).
 */
data class TonePerformanceSummary(
    val tone: String,
    val stats: PerformanceStats,
    val shortInputStats: PerformanceStats,
    val longInputStats: PerformanceStats,
    val fallbackCount: Int,
    val totalCalls: Int,
    val fallbackRate: Double
)

/**
 * Full performance baseline report.
 */
data class PerformanceBaselineReport(
    val timestamp: String,
    val toneCount: Int,
    val runsPerCombo: Int,
    val toneSummaries: Map<String, TonePerformanceSummary>,
    val overallStats: PerformanceStats,
    val overallFallbackRate: Double,
    val allResults: List<TimedCallResult>
)
