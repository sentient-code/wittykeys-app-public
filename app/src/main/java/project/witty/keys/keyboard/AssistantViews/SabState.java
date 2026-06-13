package project.witty.keys.keyboard.AssistantViews;

public enum SabState {
    // OriginalView primary states
    OV_EXPANDED,
    OV_COLLAPSED,
    OV_CUSTOM,
    OV_NO_CONTEXT,
    OV_ACCESSIBILITY,
    OV_ROW2_LOADING,
    OV_ERROR,

    // CTA interaction states
    CTA_TONE_SELECT,
    CTA_TONE_ACTIVE,
    CTA_TRANSLATE,
    CTA_TONE_CUSTOM,
    CTA_TRANSLATE_CUSTOM,
    CTA_TRANSLATE_ACTIVE,
    CTA_GRAMMAR,

    // Tone variants (same as CTA_TONE_ACTIVE but different tone)
    TONE_PROFESSIONAL,
    TONE_CASUAL,
    TONE_SAVAGE,
    TONE_SARCASTIC,
    TONE_CALM,

    // Special states
    OV_MILESTONE,
    OV_DATING,
    OV_BRAIN_BLINK,

    // Contact picker (Build 7.0 — confidence < 80%)
    OV_CONTACT_PICKER,

    // Bottom sheets
    BS_ACC_CONSENT
}
