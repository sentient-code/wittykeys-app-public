package project.witty.keys.keyboard.internal;

/**
 * Interface for keyboard-internal views that receive key events instead of
 * routing them to the host app's editor via RichInputConnection.
 *
 * When an InternalInputTarget is active, LatinIME.onCodeInput() diverts
 * characters to this target and skips InputLogic entirely.
 *
 * Implementations:
 *  - InternalInputView  (custom tone/translate prompt input)
 *  - EmojiSearchView    (future: emoji search bar)
 *  - AiChatInputView    (future: AI chat inline input)
 */
public interface InternalInputTarget {

    /** Is this target currently active and accepting input? */
    boolean isActive();

    /** Receive a code point (printable character, space, enter, etc.) */
    void onCodeInput(int codePoint);

    /** Receive a backspace/delete event */
    void onDeleteInput();

    /** Get the current text content of the buffer */
    String getText();

    /** Clear all content from the buffer */
    void clear();

    /** Activate this input target (show cursor, start accepting input) */
    void activate();

    /** Deactivate this input target (hide cursor, stop accepting input, clear buffer) */
    void deactivate();

    /** Register a listener for text changes (e.g., for live search) */
    void setOnTextChangedListener(OnTextChangedListener listener);

    /** Callback for text content changes */
    interface OnTextChangedListener {
        void onTextChanged(String newText);
    }
}
