package project.witty.keys.keyboard.AssistantViews;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import project.witty.keys.BuildConfig;
import project.witty.keys.R;

/**
 * ReportHelper - Centralized mailto: report logic
 *
 * Build 6.3 - SmartAssistantBar Revamp - Phase 8
 *
 * Handles reporting of AI-generated content via email.
 * Used by:
 * - SmartAssistantBar (reply chips in Row 1)
 *
 * Opens email compose directly with pre-filled content (no confirmation dialog).
 *
 * Based on ChatAdapter.reportAiContent() pattern.
 */
public class ReportHelper {

    private static final String TAG = "ReportHelper";

    // Feature types for report context
    public static final String FEATURE_SMART_REPLY = "Smart Reply";
    public static final String FEATURE_CUSTOM_REPLY = "Custom Reply";
    public static final String FEATURE_AI_RESPONSE = "AI Response";

    /**
     * Report AI-generated content via mailto: intent.
     * Opens email compose directly with pre-filled content.
     *
     * @param context Application context
     * @param content The AI-generated content to report
     * @param featureType The feature type (e.g., FEATURE_SMART_REPLY)
     */
    public static void reportContent(Context context, String content, String featureType) {
        // Log journey
        SmartAssistantLogger.j11_reportFlagTapped(content);
        SmartAssistantLogger.j11_openingMailIntent();

        // Get email configuration from strings
        String reportEmail = context.getString(R.string.contact_mail);
        String subject = context.getString(R.string.report_email_subject);

        // Build timestamp and version info
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String timestamp = sdf.format(new Date());
        String appVersion = BuildConfig.VERSION_NAME;

        // Build email body
        String body = buildReportBody(context, content, featureType, appVersion, timestamp);

        // Build mailto URI
        String mailtoUri = "mailto:" + reportEmail +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body);

        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse(mailtoUri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            SmartAssistantLogger.logError("REPORT", "Failed to open email: " + e.getMessage());
        }
    }

    /**
     * Report AI-generated content with default feature type (Smart Reply).
     *
     * @param context Application context
     * @param content The AI-generated content to report
     */
    public static void reportContent(Context context, String content) {
        reportContent(context, content, FEATURE_SMART_REPLY);
    }

    /**
     * Build the email body for the report.
     *
     * @param context Application context
     * @param content The AI-generated content
     * @param featureType The feature type
     * @param appVersion The app version
     * @param timestamp The timestamp
     * @return Formatted email body
     */
    private static final String REPORT_TEMPLATE =
            "I would like to report the following AI-generated content:\n\n" +
            "---\n" +
            "Feature: %s\n" +
            "Content:\n" +
            "%s\n" +
            "---\n\n" +
            "Reason for report:\n" +
            "[Please describe why you're reporting this content]\n\n" +
            "---\n" +
            "App Version: %s\n" +
            "Timestamp: %s\n";

    private static String buildReportBody(Context context, String content,
                                          String featureType, String appVersion, String timestamp) {
        return String.format(REPORT_TEMPLATE, featureType, content, appVersion, timestamp);
    }
}
