package project.witty.keys.keyboard.AssistantViews;


import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.JsonReader;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import project.witty.keys.R;
import project.witty.keys.app.AuthenticationActivity;
import project.witty.keys.app.SubscriptionListingActivity;
import project.witty.keys.app.entities.Subscription;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.utils.DailyUsageTracker;
import project.witty.keys.app.helpers.ThemeUtils;
import project.witty.keys.app.helpers.Trie;
import project.witty.keys.keyboard.KeyboardSwitcher;
import project.witty.keys.keyboard.Themeable;
import project.witty.keys.keyboard.shared.ErrorInfo;
import project.witty.keys.latin.LatinIME;
import project.witty.keys.latin.RichInputConnection;

import project.witty.keys.app.tutorial.TutorialManager;
import project.witty.keys.app.helpers.DebugConfig;

/**
 * Suggestion row for the Witty keyboard. Displays word predictions, emoji suggestions,
 * and inline AI responses (marquee chip).
 */
public class SuggestionRow extends FrameLayout implements Themeable {
    private final Context context;
    private LatinIME mLatinIme;
    private static final String TAG = "SuggestionRow";
    private TutorialManager tutorialManager;
    // --- Data sources ---
    private Trie wordTrie;
    private Map<String, String> emojiMap;

    // --- UI Views ---
    private LinearLayout rootLayout;
    private TextView firstSuggestion, secondSuggestion, thirdSuggestion;
    private View firstSeperator, secondSeperator;

    /** Voice mode keeps status/AI messages visible and suppresses live dictionary updates. */
    private boolean voiceMode = false;

    public enum SuggestionState {
        TEXT_SUGGESTIONS,
        AI_INLINE_RESPONSE,
        SUBSCRIPTION_GATE
    }

    private SuggestionState currentState = SuggestionState.TEXT_SUGGESTIONS;

    // Inline AI
    private String aiInlinePrompt = null, aiInlineResponse = null;

    // Next word table + safe defaults (to prevent NPE).
    private static final Map<String, List<String>> NEXT_WORDS = new HashMap<>();
    private static final List<String> FALLBACK_DEFAULTS =
            java.util.Arrays.asList("the", "to", "and");

    // Special containers
    private LinearLayout textSuggestionsContainer;

    // Subscription/login gate
    private LinearLayout paywallContainer;
    private TextView paywallMessageView;
    private TextView paywallCtaView;

    // Optional chaining state (kept robust & side-effect free)
    private int lastSuggestionCaret = -1;
    private long lastSuggestionTime = 0L;
    private static final long CHAIN_WINDOW_MS = 2000L; // 2s

    public SuggestionRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init(context);
        loadDictionary();
        loadEmojiMap();
        seedNextWordTable();
        this.tutorialManager = TutorialManager.getInstance(context);
        if (DebugConfig.isDebugMode) {
            Log.d(TAG, "🎓 TutorialManager initialized: " + (tutorialManager != null ? "SUCCESS" : "FAILED"));
        }
    }

    private static List<String> asList(String... xs) {
        List<String> out = new ArrayList<>(xs.length);
        Collections.addAll(out, xs);
        return out;
    }

    public void setVoiceMode(boolean enabled) { this.voiceMode = enabled; }
    public boolean isVoiceMode() { return this.voiceMode; }
    public SuggestionState getCurrentState() { return this.currentState; }

    private void init(Context context) {
        // Root
        rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        rootLayout.setLayoutParams(params);
        rootLayout.setPadding(12, 10, 12, 10);
        rootLayout.setTag(R.id.theme_background_attr, R.attr.utilityRowBackground);
        addView(rootLayout);

        // Container for 3 standard chips
        textSuggestionsContainer = new LinearLayout(context);
        textSuggestionsContainer.setOrientation(LinearLayout.HORIZONTAL);
        textSuggestionsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Chips
        firstSuggestion = createSuggestionTextView("Hi");
        secondSuggestion = createSuggestionTextView("Hey");
        thirdSuggestion = createSuggestionTextView("👋");
        firstSeperator = createSeparator();
        secondSeperator = createSeparator();

        textSuggestionsContainer.addView(firstSuggestion);
        textSuggestionsContainer.addView(firstSeperator);
        textSuggestionsContainer.addView(secondSuggestion);
        textSuggestionsContainer.addView(secondSeperator);
        textSuggestionsContainer.addView(thirdSuggestion);

        // --- Subscription/Login gate (inline) ---
        paywallContainer = new LinearLayout(context);
        paywallContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pwParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paywallContainer.setLayoutParams(pwParams);
        paywallContainer.setPadding(dp(8), dp(0), dp(8), dp(0));
        paywallContainer.setVisibility(View.GONE);
        // Inherit row background color
        paywallContainer.setTag(R.id.theme_background_attr, R.attr.utilityRowBackground);

        paywallMessageView = new TextView(context);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        paywallMessageView.setLayoutParams(msgParams);
        paywallMessageView.setTextSize(14);
        paywallMessageView.setTypeface(null, Typeface.BOLD);
        // readable text color
        paywallMessageView.setTag(R.id.theme_text_color_attr, R.attr.productViewTitleColor);

        paywallCtaView = createSpecialButton("Open", "Open");
        LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics()));
        paywallCtaView.setLayoutParams(ctaParams);
        applySuggestionChipStyle(paywallCtaView);

        paywallContainer.addView(paywallMessageView);
        paywallContainer.addView(paywallCtaView);

        // Add to root
        rootLayout.addView(textSuggestionsContainer);
        rootLayout.addView(paywallContainer);

        updateState(SuggestionState.TEXT_SUGGESTIONS);
        onThemeChanged(this.context);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView createSpecialButton(String actionDescription, String label) {
        TextView button = new TextView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics())
        );
        button.setLayoutParams(params);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setClickable(true);
        button.setFocusable(true);
        button.setText(label);
        button.setContentDescription(actionDescription);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        // theme via chip style later
        button.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);
        return button;
    }

    private void updateState(SuggestionState newState) {
        if (currentState == newState) return;
        currentState = newState;

        textSuggestionsContainer.setVisibility(
                currentState == SuggestionState.TEXT_SUGGESTIONS || currentState == SuggestionState.AI_INLINE_RESPONSE
                        ? View.VISIBLE : View.GONE);
        paywallContainer.setVisibility(
                currentState == SuggestionState.SUBSCRIPTION_GATE ? View.VISIBLE : View.GONE);

        rootLayout.requestLayout();
    }

    @Override
    public void onThemeChanged(Context themedContext) {
        Log.d(TAG, "onThemeChanged called. Applying new theme via traversal.");
        applyThemeRecursively(this, themedContext);
    }

    private void applyThemeRecursively(View view, Context themedContext) {
        if (view == null) return;

        Object backgroundAttrTag = view.getTag(R.id.theme_background_attr);
        if (backgroundAttrTag instanceof Integer) {
            int attrId = (Integer) backgroundAttrTag;
            view.setBackgroundColor(ThemeUtils.getThemeColor(themedContext, attrId));
        }

        if (view instanceof TextView) {
            Object textColorAttrTag = view.getTag(R.id.theme_text_color_attr);
            if (textColorAttrTag instanceof Integer) {
                int attrId = (Integer) textColorAttrTag;
                ((TextView) view).setTextColor(ThemeUtils.getThemeColor(themedContext, attrId));
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyThemeRecursively(vg.getChildAt(i), themedContext);
            }
        }
    }

    public void resetToTextSuggestions() {
        updateState(SuggestionState.TEXT_SUGGESTIONS);
    }

    private View createSeparator() {
        View separator = new View(context);
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics())
        );
        separatorParams.setMargins(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                0
        );
        separatorParams.gravity = Gravity.CENTER;
        separator.setLayoutParams(separatorParams);
        separator.setTag(R.id.theme_background_attr, R.attr.utilityRowSeparatorColor);
        return separator;
    }

    private TextView createSuggestionTextView(String initialSuggestion) {
        TextView textView = new TextView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics()),
                1.0f
        );
        textView.setLayoutParams(params);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(8, 8, 8, 8);
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setText(initialSuggestion);
        textView.setTextSize(14);
        textView.setTypeface(null, Typeface.BOLD);

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        textView.setBackgroundResource(outValue.resourceId);

        textView.setTag(R.id.theme_text_color_attr, R.attr.themedButtonTextColor);

        textView.setOnClickListener(v -> commitSuggestionSmart(((TextView) v).getText().toString()));
        return textView;
    }

    // ---------------------------- Suggestion UI updates ----------------------------

    public void updateSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            firstSuggestion.setVisibility(View.GONE);
            secondSuggestion.setVisibility(View.GONE);
            thirdSuggestion.setVisibility(View.GONE);
            if (firstSeperator != null) firstSeperator.setVisibility(View.GONE);
            if (secondSeperator != null) secondSeperator.setVisibility(View.GONE);
            return;
        }

        if (suggestions.size() == 1) {
            String single = suggestions.get(0);
            firstSuggestion.setText(single);
            firstSuggestion.setVisibility(View.VISIBLE);
            secondSuggestion.setVisibility(View.GONE);
            thirdSuggestion.setVisibility(View.GONE);
            if (firstSeperator != null) firstSeperator.setVisibility(View.GONE);
            if (secondSeperator != null) secondSeperator.setVisibility(View.GONE);

            firstSuggestion.setSingleLine(true);
            firstSuggestion.setHorizontallyScrolling(true);
            firstSuggestion.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            firstSuggestion.setMarqueeRepeatLimit(-1);
            firstSuggestion.setSelected(true);
            applySuggestionChipStyle(firstSuggestion);

            firstSuggestion.setOnTouchListener(new OnTouchListener() {
                private float downX, downY;
                private int initialScrollX;
                private boolean moved;
                private int touchSlop = -1;

                @Override public boolean onTouch(View v, MotionEvent e) {
                    TextView tv = (TextView) v;
                    if (touchSlop < 0) touchSlop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = e.getX();
                            downY = e.getY();
                            moved = false;
                            initialScrollX = tv.getScrollX();
                            v.setPressed(true);
                            tv.getParent().requestDisallowInterceptTouchEvent(true);
                            return true;
                        case MotionEvent.ACTION_MOVE: {
                            float dx = downX - e.getX();
                            float dy = downY - e.getY();
                            if (!moved && (dx * dx + dy * dy) > (touchSlop * touchSlop)) moved = true;

                            int newScrollX = (int) (initialScrollX + dx);
                            int maxScroll = 0;
                            if (tv.getLayout() != null) {
                                maxScroll = Math.max(0, tv.getLayout().getWidth() - tv.getWidth());
                            }
                            newScrollX = Math.max(0, Math.min(maxScroll, newScrollX));
                            tv.scrollTo(newScrollX, 0);
                            return true;
                        }
                        case MotionEvent.ACTION_UP:
                            v.setPressed(false);
                            tv.getParent().requestDisallowInterceptTouchEvent(false);
                            if (!moved) v.performClick(); // trigger commit
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            v.setPressed(false);
                            tv.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                    }
                    return false;
                }
            });
            return;
        }

        // Multi-chip mode
        firstSuggestion.setSingleLine(false);
        firstSuggestion.setHorizontallyScrolling(false);
        firstSuggestion.setEllipsize(null);
        firstSuggestion.setMarqueeRepeatLimit(0);
        firstSuggestion.setSelected(false);
        firstSuggestion.setOnTouchListener(null);

        applySuggestionChipStyle(firstSuggestion);

        firstSuggestion.setText(suggestions.get(0));
        firstSuggestion.setVisibility(View.VISIBLE);

        if (suggestions.size() > 1) {
            secondSuggestion.setText(suggestions.get(1));
            secondSuggestion.setVisibility(View.VISIBLE);
            if (firstSeperator != null) firstSeperator.setVisibility(View.VISIBLE);
            applySuggestionChipStyle(secondSuggestion);
        } else {
            secondSuggestion.setVisibility(View.GONE);
            if (firstSeperator != null) firstSeperator.setVisibility(View.GONE);
        }

        if (suggestions.size() > 2) {
            thirdSuggestion.setText(suggestions.get(2));
            thirdSuggestion.setVisibility(View.VISIBLE);
            if (secondSeperator != null) secondSeperator.setVisibility(View.VISIBLE);
            applySuggestionChipStyle(thirdSuggestion);
        } else {
            thirdSuggestion.setVisibility(View.GONE);
            if (secondSeperator != null) secondSeperator.setVisibility(View.GONE);
        }
    }

    private void applySuggestionChipStyle(TextView tv) {
        if (tv == null) return;
        float cornerRadius = getResources().getDimension(R.dimen.button_corner_radius_lxx);
        Drawable background = ThemeUtils.createButtonBackground(
                context,
                R.attr.themedButtonBackgroundColor,
                R.attr.themedButtonPressedBackgroundColor,
                cornerRadius);
        tv.setBackground(background);
        tv.setTextColor(ThemeUtils.getThemeColor(context, R.attr.themedButtonTextColor));
    }

    // ---------------------------- Commit logic ----------------------------

    /** Legacy whole-field replace (kept for clipboard paste). */
    private void commitSuggestion(String suggestion) {
        if (suggestion == null || mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null) return;

        String currentText = ric.getCommitedText();
        int totalLength = currentText != null ? currentText.length() : 0;
        ric.replaceText(0, totalLength, suggestion);
        ric.setSelection(suggestion.length(), suggestion.length());
    }

    /** Smart: insert after space/start; otherwise replace current token. Keeps cursor after text, fixes caps, and triggers next-word predictions. */
    private void commitSuggestionSmart(String suggestion) {
        if (suggestion == null || mLatinIme == null) return;
        RichInputConnection ric = mLatinIme.getInputLogicInstance().mConnection;
        if (ric == null || !ric.isConnected()) return;

        CharSequence before1 = ric.getTextBeforeCursor(1, 0);
        boolean atWordBoundary = (before1 == null || before1.length() == 0
                || Character.isWhitespace(before1.charAt(before1.length() - 1)));

        if (atWordBoundary) {
            CharSequence before = ric.getTextBeforeCursor(256, 0);
            boolean sentenceStart = isSentenceBoundaryBefore(before);
            String toCommit = sentenceStart ? capitalizeFirstWord(suggestion) : suggestion;

            ric.commitText(toCommit + " ", 1);
            // chain metadata
            lastSuggestionCaret = ric.getExpectedSelectionEnd();
            lastSuggestionTime = android.os.SystemClock.uptimeMillis();

            // Predictions
            try {
                showNextWordPredictions(toCommit, sentenceStart);
            } catch (Throwable t) {
                Log.e(TAG, "Next-word prediction failed", t);
                updateState(SuggestionState.TEXT_SUGGESTIONS);
            }
            return;
        }

        // Replace current token to the left
        final int MAX_LOOKBACK = 64;
        CharSequence before = ric.getTextBeforeCursor(MAX_LOOKBACK, 0);
        if (before == null || before.length() == 0) {
            ric.commitText(suggestion + " ", 1);
            lastSuggestionCaret = ric.getExpectedSelectionEnd();
            lastSuggestionTime = android.os.SystemClock.uptimeMillis();
            try { showNextWordPredictions(suggestion, false); } catch (Throwable ignored) {}
            return;
        }

        // locate token start
        int i = before.length() - 1;
        while (i >= 0) {
            char c = before.charAt(i);
            boolean isWordChar = Character.isLetterOrDigit(c) || c == '_' || c == '\'';
            if (!isWordChar) break;
            i--;
        }
        int tokenStartInBefore = i + 1;
        int tokenLen = before.length() - tokenStartInBefore;
        String originalToken = tokenLen > 0 ? before.subSequence(tokenStartInBefore, before.length()).toString() : "";

        String adjusted = matchCase(suggestion, originalToken);

        int selStart = ric.getExpectedSelectionStart();
        int selEnd   = ric.getExpectedSelectionEnd();
        if (selStart < 0 || selEnd < 0) {
            ric.commitText(adjusted + " ", 1);
            lastSuggestionCaret = ric.getExpectedSelectionEnd();
            lastSuggestionTime = android.os.SystemClock.uptimeMillis();
            try { showNextWordPredictions(adjusted, false); } catch (Throwable ignored) {}
            return;
        }

        int absStart = Math.max(0, selStart - tokenLen);
        int absEnd   = selEnd;

        ric.beginBatchEdit();
        ric.setSelection(absStart, absEnd);    // refreshes cache too
        ric.commitText(adjusted + " ", 1);     // cursor ends after word + space
        ric.endBatchEdit();

        lastSuggestionCaret = ric.getExpectedSelectionEnd();
        lastSuggestionTime = android.os.SystemClock.uptimeMillis();

        try { showNextWordPredictions(adjusted, false); } catch (Throwable ignored) {}
    }

    public void setLatinIme(final LatinIME ime) {
        mLatinIme = ime;
    }

    // ---------------------------- Input-driven updates ----------------------------

    public void onUserInput(String input) {
        android.util.Log.d("WK_SUGGESTIONS", "[USER_INPUT] input='" + input + "'");

        // Hide gate if visible when user starts typing again
        if (currentState == SuggestionState.SUBSCRIPTION_GATE) {
            updateState(SuggestionState.TEXT_SUGGESTIONS);
        }

        // Reset chain if caret moved or time window expired.
        try {
            RichInputConnection ic = mLatinIme.getInputLogicInstance().mConnection;
            if (ic != null && ic.isConnected()) {
                if (ic.getExpectedSelectionEnd() != lastSuggestionCaret) {
                    lastSuggestionTime = 0L;
                }
            }
        } catch (Throwable ignored) {}
        if ((android.os.SystemClock.uptimeMillis() - lastSuggestionTime) > CHAIN_WINDOW_MS) {
            lastSuggestionTime = 0L;
        }

        if (voiceMode) return;
        if (currentState != SuggestionState.TEXT_SUGGESTIONS) updateState(SuggestionState.TEXT_SUGGESTIONS);

        String lastWord = getLastWord(input);
        List<String> suggestions = generateSuggestions(lastWord);

        if (suggestions.isEmpty()) {
            firstSuggestion.setText("Hi");
            secondSuggestion.setText("Hey");
            thirdSuggestion.setText("👋");
            firstSuggestion.setVisibility(View.VISIBLE);
            secondSuggestion.setVisibility(View.VISIBLE);
            thirdSuggestion.setVisibility(View.VISIBLE);
            if (firstSeperator != null) firstSeperator.setVisibility(View.VISIBLE);
            if (secondSeperator != null) secondSeperator.setVisibility(View.VISIBLE);

            firstSuggestion.setSingleLine(false);
            firstSuggestion.setHorizontallyScrolling(false);
            firstSuggestion.setEllipsize(null);
            firstSuggestion.setMarqueeRepeatLimit(0);
            firstSuggestion.setSelected(false);
            firstSuggestion.setOnTouchListener(null);

            applySuggestionChipStyle(firstSuggestion);
            applySuggestionChipStyle(secondSuggestion);
            applySuggestionChipStyle(thirdSuggestion);
            return;
        }

        // Apply context-aware casing to displayed suggestions
        String lastWordRaw = getLastWordRaw(input == null ? "" : input);
        boolean startOfSentence = isStartOfSentence(input == null ? "" : input);
        List<String> caseAdjusted = new ArrayList<>(suggestions.size());
        for (String s : suggestions) {
            caseAdjusted.add(adjustCaseForContext(s, lastWordRaw, startOfSentence));
        }

        updateSuggestions(caseAdjusted); // <-- use adjusted list (bugfix)
    }

    private String getLastWord(String input) {
        if (input == null || input.isEmpty()) return "";
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) return "";
        String[] words = trimmedInput.split("\\s+");
        return words[words.length - 1];
    }

    private void loadDictionary() {
        wordTrie = new Trie();
        try (InputStream is = context.getResources().openRawResource(R.raw.dictionary_google_10000);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                wordTrie.insert(line.trim().toLowerCase());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading dictionary", e);
        }
    }

    private void loadEmojiMap() {
        emojiMap = new HashMap<>();
        try (InputStream is = context.getResources().openRawResource(R.raw.emojis)) {
            JsonReader reader = new JsonReader(new InputStreamReader(is));
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                String emoji = null;
                List<String> aliases = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "emoji":
                            emoji = reader.nextString();
                            break;
                        case "aliases":
                            reader.beginArray();
                            while (reader.hasNext()) aliases.add(reader.nextString());
                            reader.endArray();
                            break;
                        case "tags":
                            reader.beginArray();
                            while (reader.hasNext()) tags.add(reader.nextString());
                            reader.endArray();
                            break;
                        default:
                            reader.skipValue();
                            break;
                    }
                }
                reader.endObject();
                if (emoji != null) {
                    for (String alias : aliases) emojiMap.put(alias, emoji);
                    for (String tag : tags) emojiMap.put(tag, emoji);
                }
            }
            reader.endArray();
        } catch (IOException e) {
            Log.e(TAG, "Error loading emoji map", e);
        }
    }

    /** Populate robust next-word predictions (single-word keys). */
    private void seedNextWordTable() {
        NEXT_WORDS.clear();

        // Always keep a default to avoid NPE and provide generic options
        NEXT_WORDS.put("__default__", FALLBACK_DEFAULTS); // ["the","to","and"]

        // Greetings / small talk
        NEXT_WORDS.put("hi", asList("there", "!", "how"));
        NEXT_WORDS.put("hey", asList("there", "!", "what's"));
        NEXT_WORDS.put("hello", asList("there", "!", "again"));
        NEXT_WORDS.put("yo", asList("what's", "hey", "bro"));
        NEXT_WORDS.put("sup", asList("!", "man", "dude"));
        NEXT_WORDS.put("morning", asList("!", "everyone", "team"));
        NEXT_WORDS.put("evening", asList("!", "everyone", "all"));

        // Gratitude / acknowledgements
        NEXT_WORDS.put("thanks", asList("!", "so", "for"));
        NEXT_WORDS.put("thank", asList("you", "you!", "you so much"));
        NEXT_WORDS.put("appreciate", asList("it", "the", "your"));
        NEXT_WORDS.put("cheers", asList("!", "for", "everyone"));
        NEXT_WORDS.put("ty", asList("!", "so", "for"));

        // Closings / sign-offs
        NEXT_WORDS.put("best", asList("regards", "wishes", "of"));
        NEXT_WORDS.put("kind", asList("regards", "thanks", "wishes"));
        NEXT_WORDS.put("warm", asList("regards", "wishes", "thanks"));
        NEXT_WORDS.put("regards", asList(",", "and", "to"));
        NEXT_WORDS.put("take", asList("care", "it", "a"));
        NEXT_WORDS.put("see", asList("you", "you soon", "you later"));
        NEXT_WORDS.put("talk", asList("soon", "to", "later"));

        // Pronouns
        NEXT_WORDS.put("i", asList("am", "will", "have"));
        NEXT_WORDS.put("you", asList("are", "can", "have"));
        NEXT_WORDS.put("he", asList("is", "was", "has"));
        NEXT_WORDS.put("she", asList("is", "was", "has"));
        NEXT_WORDS.put("it", asList("is", "was", "seems"));
        NEXT_WORDS.put("we", asList("are", "should", "will"));
        NEXT_WORDS.put("they", asList("are", "were", "have"));

        // Auxiliaries / modals
        NEXT_WORDS.put("can", asList("you", "we", "i"));
        NEXT_WORDS.put("could", asList("you", "we", "i"));
        NEXT_WORDS.put("should", asList("we", "i", "you"));
        NEXT_WORDS.put("would", asList("you", "we", "i"));
        NEXT_WORDS.put("will", asList("you", "we", "be"));
        NEXT_WORDS.put("shall", asList("we", "i", "proceed"));
        NEXT_WORDS.put("may", asList("i", "we", "be"));
        NEXT_WORDS.put("might", asList("be", "have", "also"));
        NEXT_WORDS.put("do", asList("you", "we", "it"));
        NEXT_WORDS.put("does", asList("it", "this", "he"));
        NEXT_WORDS.put("did", asList("you", "we", "they"));
        NEXT_WORDS.put("am", asList("i", "not", "going"));
        NEXT_WORDS.put("are", asList("you", "we", "they"));
        NEXT_WORDS.put("is", asList("it", "this", "that"));
        NEXT_WORDS.put("was", asList("it", "he", "she"));
        NEXT_WORDS.put("were", asList("you", "we", "they"));
        NEXT_WORDS.put("have", asList("you", "we", "been"));
        NEXT_WORDS.put("has", asList("it", "he", "she"));
        NEXT_WORDS.put("had", asList("been", "to", "already"));

        // Question words
        NEXT_WORDS.put("what", asList("is", "are", "do"));
        NEXT_WORDS.put("when", asList("is", "will", "are"));
        NEXT_WORDS.put("where", asList("is", "are", "can"));
        NEXT_WORDS.put("why", asList("is", "are", "did"));
        NEXT_WORDS.put("how", asList("are", "is", "do"));
        NEXT_WORDS.put("who", asList("is", "are", "did"));
        NEXT_WORDS.put("which", asList("is", "are", "one"));
        NEXT_WORDS.put("whats", asList("up", "the", "going"));
        NEXT_WORDS.put("what's", asList("up", "the", "going"));

        // Conjunctions / discourse markers
        NEXT_WORDS.put("and", asList("then", "also", "the"));
        NEXT_WORDS.put("but", asList("i", "we", "it's"));
        NEXT_WORDS.put("so", asList("i", "we", "that"));
        NEXT_WORDS.put("or", asList("not", "maybe", "just"));
        NEXT_WORDS.put("because", asList("i", "it", "there"));
        NEXT_WORDS.put("also", asList("note", "we", "it"));
        NEXT_WORDS.put("then", asList("we", "i", "let's"));
        NEXT_WORDS.put("therefore", asList("we", "it", "this"));

        // Prepositions / function words
        NEXT_WORDS.put("to", asList("the", "be", "do"));
        NEXT_WORDS.put("for", asList("the", "you", "now"));
        NEXT_WORDS.put("with", asList("you", "the", "me"));
        NEXT_WORDS.put("from", asList("the", "now", "here"));
        NEXT_WORDS.put("about", asList("the", "this", "that"));
        NEXT_WORDS.put("after", asList("the", "this", "that"));
        NEXT_WORDS.put("before", asList("we", "you", "i"));
        NEXT_WORDS.put("into", asList("the", "account", "details"));
        NEXT_WORDS.put("over", asList("the", "here", "there"));
        NEXT_WORDS.put("under", asList("the", "pressure", "control"));
        NEXT_WORDS.put("between", asList("the", "us", "them"));
        NEXT_WORDS.put("without", asList("you", "any", "the"));
        NEXT_WORDS.put("within", asList("the", "next", "24"));

        // Requests / actions
        NEXT_WORDS.put("please", asList("let", "share", "confirm"));
        NEXT_WORDS.put("let", asList("me", "us", "them"));
        NEXT_WORDS.put("make", asList("sure", "it", "a"));
        NEXT_WORDS.put("need", asList("to", "help", "a"));
        NEXT_WORDS.put("want", asList("to", "to make", "to go"));
        NEXT_WORDS.put("going", asList("to", "back", "home"));
        NEXT_WORDS.put("get", asList("back", "started", "ready"));
        NEXT_WORDS.put("give", asList("me", "us", "it"));
        NEXT_WORDS.put("send", asList("me", "it", "over"));
        NEXT_WORDS.put("share", asList("the", "with", "details"));
        NEXT_WORDS.put("check", asList("the", "this", "it"));
        NEXT_WORDS.put("open", asList("the", "this", "it"));
        NEXT_WORDS.put("close", asList("the", "this", "it"));
        NEXT_WORDS.put("create", asList("a", "the", "new"));
        NEXT_WORDS.put("add", asList("a", "the", "to"));
        NEXT_WORDS.put("remove", asList("the", "this", "it"));
        NEXT_WORDS.put("delete", asList("the", "this", "it"));
        NEXT_WORDS.put("update", asList("the", "this", "it"));

        // Affirmations / quick replies
        NEXT_WORDS.put("yes", asList("please", "this", "definitely"));
        NEXT_WORDS.put("no", asList("problem", "worries", "thanks"));
        NEXT_WORDS.put("ok", asList("thanks", "then", "cool"));
        NEXT_WORDS.put("okay", asList("then", "thanks", "cool"));
        NEXT_WORDS.put("sure", asList("thing", "i'll", "can"));
        NEXT_WORDS.put("fine", asList("by", "with", "then"));
        NEXT_WORDS.put("alright", asList("then", "let's", "thanks"));

        // Time words
        NEXT_WORDS.put("today", asList("i", "we", "is"));
        NEXT_WORDS.put("tomorrow", asList("i", "we", "let's"));
        NEXT_WORDS.put("tonight", asList("i", "we", "let's"));
        NEXT_WORDS.put("later", asList("today", "tonight", "on"));
        NEXT_WORDS.put("now", asList("i", "we", "let's"));
        NEXT_WORDS.put("soon", asList("as", "enough", "we"));

        // Common verbs
        NEXT_WORDS.put("see", asList("you", "if", "the"));
        NEXT_WORDS.put("check", asList("this", "the", "it"));
        NEXT_WORDS.put("look", asList("at", "into", "forward"));
        NEXT_WORDS.put("looking", asList("forward", "into", "for"));
        NEXT_WORDS.put("call", asList("you", "me", "back"));
        NEXT_WORDS.put("meet", asList("you", "at", "tomorrow"));
        NEXT_WORDS.put("set", asList("up", "the", "a"));
        NEXT_WORDS.put("follow", asList("up", "the", "this"));
        NEXT_WORDS.put("find", asList("the", "out", "a"));
        NEXT_WORDS.put("fix", asList("the", "this", "it"));
        NEXT_WORDS.put("help", asList("me", "you", "us"));

        // Email / work phrases
        NEXT_WORDS.put("attached", asList("is", "are", "the"));
        NEXT_WORDS.put("per", asList("our", "your", "the"));
        NEXT_WORDS.put("as", asList("soon", "discussed", "well"));
        NEXT_WORDS.put("action", asList("items", "required", "needed"));
        NEXT_WORDS.put("next", asList("steps", "week", "time"));
        NEXT_WORDS.put("high", asList("level", "priority", "quality"));
        NEXT_WORDS.put("followup", asList("on", "email", "call"));
        NEXT_WORDS.put("circling", asList("back", "around", "on"));
        NEXT_WORDS.put("looping", asList("in", "back", "you"));

        // Politeness / requests continued
        NEXT_WORDS.put("could", asList("you", "we", "please"));
        NEXT_WORDS.put("would", asList("you", "we", "it"));
        NEXT_WORDS.put("please", asList("share", "confirm", "review"));

        // Common adjectives / phrases
        NEXT_WORDS.put("happy", asList("birthday", "to", "friday"));
        NEXT_WORDS.put("good", asList("morning", "afternoon", "evening"));
        NEXT_WORDS.put("merry", asList("christmas", "christmas!", "xmas"));
        NEXT_WORDS.put("sorry", asList("for", "about", "i"));
        NEXT_WORDS.put("congrats", asList("!", "on", "🎉"));
        NEXT_WORDS.put("congratulations", asList("!", "on", "to"));

        // Casual / chatty
        NEXT_WORDS.put("lol", asList("that", "yeah", "nice"));
        NEXT_WORDS.put("lmao", asList("that", "yeah", "nice"));
        NEXT_WORDS.put("omg", asList("that", "was", "so"));
        NEXT_WORDS.put("haha", asList("yes", "that", "thanks"));
        NEXT_WORDS.put("btw", asList("the", "i", "we"));
        NEXT_WORDS.put("fyi", asList("the", "this", "we"));

        // Navigation / scheduling
        NEXT_WORDS.put("book", asList("a", "the", "an"));
        NEXT_WORDS.put("schedule", asList("a", "the", "it"));
        NEXT_WORDS.put("reschedule", asList("to", "for", "the"));
        NEXT_WORDS.put("cancel", asList("the", "this", "it"));

        // Misc common
        NEXT_WORDS.put("that's", asList("great", "fine", "okay"));
        NEXT_WORDS.put("this", asList("is", "was", "looks"));
        NEXT_WORDS.put("that", asList("is", "was", "sounds"));
        NEXT_WORDS.put("these", asList("are", "were", "look"));
        NEXT_WORDS.put("those", asList("are", "were", "look"));
    }

    private List<String> generateSuggestions(String lastWord) {
        List<String> suggestions = new ArrayList<>();
        if (lastWord == null || lastWord.isEmpty()) return suggestions;

        List<String> dictionaryMatches = wordTrie.searchPrefix(lastWord.toLowerCase());
        int count = 0;
        for (String match : dictionaryMatches) {
            suggestions.add(match);
            if (++count >= 3) break;
        }

        List<String> emojiMatches = generateEmojiSuggestions(lastWord.toLowerCase());
        suggestions.addAll(emojiMatches);
        return suggestions;
    }

    private Map<String, String> loadEmojiMapFromJson(InputStream inputStream) {
        Map<String, String> map = new HashMap<>();
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                String emoji = null;
                List<String> aliases = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "emoji":
                            emoji = reader.nextString();
                            break;
                        case "aliases":
                            reader.beginArray();
                            while (reader.hasNext()) aliases.add(reader.nextString());
                            reader.endArray();
                            break;
                        case "tags":
                            reader.beginArray();
                            while (reader.hasNext()) tags.add(reader.nextString());
                            reader.endArray();
                            break;
                        default:
                            reader.skipValue();
                            break;
                    }
                }
                reader.endObject();
                if (emoji != null) {
                    for (String alias : aliases) map.put(alias, emoji);
                    for (String tag : tags) map.put(tag, emoji);
                }
            }
            reader.endArray();
        } catch (IOException e) {
            Log.e(TAG, "Error reading emoji map", e);
        }
        return map;
    }

    private List<String> generateEmojiSuggestions(String query) {
        List<String> emojiSuggestions = new ArrayList<>();
        if (emojiMap.containsKey(query)) {
            emojiSuggestions.add(emojiMap.get(query));
        }
        return emojiSuggestions;
    }

    // ---------------------------- Inline AI response ----------------------------

    public void showAiInlineResponse(@androidx.annotation.Nullable final String prompt,
                                     @androidx.annotation.NonNull final String response) {
        aiInlinePrompt = prompt;
        aiInlineResponse = response;

        updateState(SuggestionState.AI_INLINE_RESPONSE);

        final String display = (prompt == null || prompt.trim().isEmpty())
                ? response
                : (prompt + " \u2192 " + response); // “prompt → response”

        firstSuggestion.setText(display);
        firstSuggestion.setVisibility(View.VISIBLE);
        if (firstSeperator != null) firstSeperator.setVisibility(View.GONE);
        secondSuggestion.setVisibility(View.GONE);
        if (secondSeperator != null) secondSeperator.setVisibility(View.GONE);
        thirdSuggestion.setVisibility(View.GONE);

        firstSuggestion.setSingleLine(true);
        firstSuggestion.setHorizontallyScrolling(true);
        firstSuggestion.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        firstSuggestion.setMarqueeRepeatLimit(-1);
        firstSuggestion.setSelected(true);
        applySuggestionChipStyle(firstSuggestion);

        firstSuggestion.setOnTouchListener(new OnTouchListener() {
            private float downX, downY;
            private int initialScrollX;
            private boolean moved;
            private int touchSlop = -1;

            @Override public boolean onTouch(View v, MotionEvent e) {
                TextView tv = (TextView) v;
                if (touchSlop < 0) touchSlop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        moved = false;
                        initialScrollX = tv.getScrollX();
                        tv.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = downX - e.getX();
                        float dy = downY - e.getY();
                        if (!moved && (dx * dx + dy * dy) > (touchSlop * touchSlop)) moved = true;
                        int newScrollX = (int) (initialScrollX + dx);
                        int maxScroll = 0;
                        if (tv.getLayout() != null) {
                            maxScroll = Math.max(0, tv.getLayout().getWidth() - tv.getWidth());
                        }
                        newScrollX = Math.max(0, Math.min(maxScroll, newScrollX));
                        tv.scrollTo(newScrollX, 0);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        tv.getParent().requestDisallowInterceptTouchEvent(false);
                        if (!moved) {
                            // GATE: only open AI chat if eligible; otherwise show inline message + CTA
                            requireSubscriptionThenRun(() -> {
                                if (aiInlinePrompt != null && !aiInlinePrompt.trim().isEmpty()) {
                                    KeyboardSwitcher.getInstance()
                                            .openChatWithSeedConversation(aiInlinePrompt, aiInlineResponse);
                                } else {
                                    KeyboardSwitcher.getInstance()
                                            .openChatWithSeedAssistantMessage(aiInlineResponse);
                                }
                            });
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public void showAiInlineResponse(final String text) { showAiInlineResponse(null, text); }

    // ---------------------------- SUBSCRIPTION / LOGIN GATE ----------------------------

    /** Call right before any AI action. Runs action if allowed; otherwise shows gate in-row. */
    public boolean requireSubscriptionThenRun(@androidx.annotation.NonNull Runnable action) {
        if (checkUserAndSubscription()) {
            try { action.run(); } catch (Throwable t) { Log.e(TAG, "AI action failed", t); }
            return true;
        }
        return false;
    }

    /** Mirrors ProductContainerView.checkUserAndSubscription; renders inline message + CTA if blocked. */
    public boolean checkUserAndSubscription() {

        // ========== TUTORIAL MODE BYPASS ==========
        if (tutorialManager != null && tutorialManager.isTutorialMode()) {
            if (DebugConfig.isDebugMode) {
                Log.d(TAG, "✅ Tutorial mode active - Bypassing subscription check");
            }
            hideSubscriptionGate();
            return true;
        }
        // ========== END TUTORIAL BYPASS ==========

        try { EncryptedPreferences.initialize(context); } catch (Throwable ignored) {}

        DailyUsageTracker tracker = DailyUsageTracker.getInstance(context);

        if (!tracker.canUseAI()) {
            showSubscriptionGate(ErrorInfo.ErrorType.DAILY_LIMIT_REACHED);
            return false;
        }

        hideSubscriptionGate();
        return true;
    }

    private void showSubscriptionGate(ErrorInfo.ErrorType errorType) {
        String message;
        String ctaText;
        View.OnClickListener cta;

        switch (errorType) {
            case NOT_LOGGED_IN:
                message = "Sign up to keep your daily AI credits synced.";
                ctaText = "Sign up";
                cta = v -> {
                    Intent i = new Intent(v.getContext(), AuthenticationActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(i);
                };
                break;

            case SUBSCRIPTION_EXPIRED:
                message = "Daily AI credits are used. Upgrade for more AI credits.";
                ctaText = "Subscribe";
                cta = v -> {
                    Intent i = new Intent(v.getContext(), SubscriptionListingActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(i);
                };
                break;

            case DAILY_LIMIT_REACHED:
                message = "Daily credits used. Upgrade for more AI credits \u2192";
                ctaText = "Subscribe";
                cta = v -> {
                    Intent i = new Intent(v.getContext(), SubscriptionListingActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(i);
                };
                break;

            case NO_ACTIVE_SUBSCRIPTION:
            default:
                message = "No active subscription. Subscribe to use AI features.";
                ctaText = "Subscribe";
                cta = v -> {
                    Intent i = new Intent(v.getContext(), SubscriptionListingActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    v.getContext().startActivity(i);
                };
                break;
        }

        paywallMessageView.setText(message);
        paywallCtaView.setText(ctaText);
        paywallCtaView.setOnClickListener(cta);
        // ensure styled as pill button each time (in case theme changed)
        applySuggestionChipStyle(paywallCtaView);

        updateState(SuggestionState.SUBSCRIPTION_GATE);
    }

    public void hideSubscriptionGate() {
        if (currentState == SuggestionState.SUBSCRIPTION_GATE) {
            updateState(SuggestionState.TEXT_SUGGESTIONS);
        }
    }

    // ---------------------------- Casing / context helpers ----------------------------

    /** True if at sentence start (last non-space char was a terminator or no text). */
    private boolean isStartOfSentence(CharSequence beforeCursor) {
        if (beforeCursor == null || beforeCursor.length() == 0) return true;
        int i = beforeCursor.length() - 1;
        while (i >= 0 && Character.isWhitespace(beforeCursor.charAt(i))) i--;
        if (i < 0) return true;
        char c = beforeCursor.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\n';
    }

    private String adjustCaseForContext(String suggestion, String typedTokenRaw, boolean startOfSentence) {
        if (suggestion == null || suggestion.isEmpty()) return suggestion;
        if (!Character.isLetter(suggestion.codePointAt(0))) return suggestion;

        boolean userForcedCap = typedTokenRaw != null && typedTokenRaw.length() > 0
                && Character.isUpperCase(typedTokenRaw.charAt(0));
        boolean cap = startOfSentence || userForcedCap;

        if (!cap) return suggestion;
        int cp = suggestion.codePointAt(0);
        int upper = Character.toUpperCase(cp);
        return new StringBuilder()
                .appendCodePoint(upper)
                .append(suggestion.substring(Character.charCount(cp)))
                .toString();
    }

    private String getLastWordRaw(String input) {
        if (input == null) return "";
        int i = input.length() - 1;
        while (i >= 0 && Character.isWhitespace(input.charAt(i))) i--;
        if (i < 0) return "";
        int end = i;
        while (i >= 0) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '\'' || c == '_') i--;
            else break;
        }
        return input.substring(i + 1, end + 1);
    }

    private void showNextWordPredictions(String prevWord, boolean sentenceStart) {
        try {
            String key = (prevWord == null ? "" : prevWord).toLowerCase();
            List<String> picks = (NEXT_WORDS != null) ? NEXT_WORDS.get(key) : null;
            if (picks == null || picks.isEmpty()) picks = (NEXT_WORDS != null) ? NEXT_WORDS.get("__default__") : null;
            if (picks == null || picks.isEmpty()) picks = FALLBACK_DEFAULTS;

            List<String> out = new ArrayList<>(3);
            for (String p : picks) {
                if (p == null || p.isEmpty()) continue;
                out.add("i".equals(p) ? "I" : p);
                if (out.size() == 3) break;
            }
            if (out.isEmpty()) {
                updateState(SuggestionState.TEXT_SUGGESTIONS);
                return;
            }

            updateState(SuggestionState.TEXT_SUGGESTIONS);

            firstSuggestion.setSingleLine(false);
            firstSuggestion.setHorizontallyScrolling(false);
            firstSuggestion.setEllipsize(null);
            firstSuggestion.setSelected(false);
            firstSuggestion.setOnTouchListener(null);

            firstSuggestion.setText(out.get(0));
            firstSuggestion.setVisibility(View.VISIBLE);
            applySuggestionChipStyle(firstSuggestion);

            if (out.size() > 1) {
                if (firstSeperator != null) firstSeperator.setVisibility(View.VISIBLE);
                secondSuggestion.setText(out.get(1));
                secondSuggestion.setVisibility(View.VISIBLE);
                applySuggestionChipStyle(secondSuggestion);
            } else {
                if (firstSeperator != null) firstSeperator.setVisibility(View.GONE);
                secondSuggestion.setVisibility(View.GONE);
            }

            if (out.size() > 2) {
                if (secondSeperator != null) secondSeperator.setVisibility(View.VISIBLE);
                thirdSuggestion.setText(out.get(2));
                thirdSuggestion.setVisibility(View.VISIBLE);
                applySuggestionChipStyle(thirdSuggestion);
            } else {
                if (secondSeperator != null) secondSeperator.setVisibility(View.GONE);
                thirdSuggestion.setVisibility(View.GONE);
            }
        } catch (Throwable t) {
            Log.e(TAG, "showNextWordPredictions failed", t);
            updateState(SuggestionState.TEXT_SUGGESTIONS);
        }
    }

    private static boolean isAllUpper(String s) {
        if (s == null || s.isEmpty()) return false;
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) { hasLetter = true; if (!Character.isUpperCase(c)) return false; }
        }
        return hasLetter;
    }
    private static boolean isAllLower(String s) {
        if (s == null || s.isEmpty()) return false;
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) { hasLetter = true; if (!Character.isLowerCase(c)) return false; }
        }
        return hasLetter;
    }
    private static boolean isTitleCaseWord(String s) {
        if (s == null || s.isEmpty()) return false;
        int i = 0;
        while (i < s.length() && !Character.isLetter(s.charAt(i))) i++;
        if (i >= s.length()) return false;
        if (!Character.isUpperCase(s.charAt(i))) return false;
        for (int j = i + 1; j < s.length(); j++) {
            char c = s.charAt(j);
            if (Character.isLetter(c) && !Character.isLowerCase(c)) return false;
        }
        return true;
    }
    private static String capitalizeFirstWord(String s) {
        if (s == null || s.isEmpty()) return s;
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            if (Character.isLetter(a[i])) {
                a[i] = Character.toUpperCase(a[i]);
                break;
            }
        }
        return new String(a);
    }
    private static String matchCase(String suggestion, String originalToken) {
        if (suggestion == null || suggestion.isEmpty() || originalToken == null || originalToken.isEmpty())
            return suggestion;
        if (isAllUpper(originalToken)) return suggestion.toUpperCase();
        if (isAllLower(originalToken)) return suggestion.toLowerCase();
        if (isTitleCaseWord(originalToken)) return capitalizeFirstWord(suggestion);
        return suggestion;
    }

    private static boolean isSentenceBoundaryBefore(CharSequence before) {
        if (before == null || before.length() == 0) return true;
        int i = before.length() - 1;
        while (i >= 0 && Character.isWhitespace(before.charAt(i))) i--;
        if (i < 0) return true;
        char c = before.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\n';
    }
}
