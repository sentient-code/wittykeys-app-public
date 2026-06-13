package project.witty.keys.keyboard.EmojiKeyboard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import project.witty.keys.R;
import project.witty.keys.app.helpers.ThemeUtils;

public class EmojisView extends RecyclerView {
    private EmojiAdapter adapter;
    private OnEmojiLongPressListener longPressListener;
    private boolean horizontalMode = false;
    private int fixedCellSize = 0;

    /** Callback for long-press on an emoji (used for skin tone popup). */
    public interface OnEmojiLongPressListener {
        void onEmojiLongPress(View anchorView, String emoji);
    }

    public EmojisView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        int columns = calculateColumns(context);
        setLayoutManager(new GridLayoutManager(context, columns));
        adapter = new EmojiAdapter();
        setAdapter(adapter);
    }

    private int calculateColumns(Context context) {
        int minColumns = context.getResources().getInteger(R.integer.wk_emoji_grid_columns_min);
        int targetCellSize = context.getResources().getDimensionPixelSize(R.dimen.wk_emoji_cell_target_size);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int calculated = screenWidth / targetCellSize;
        return Math.max(minColumns, calculated);
    }

    /**
     * Switch between vertical grid (normal) and horizontal single-row (search mode).
     * In horizontal mode, enforce a fixed height so the row doesn't get clipped
     * when sharing space with the QWERTY keyboard.
     */
    public void setHorizontalMode(boolean horizontal) {
        if (this.horizontalMode == horizontal) return;
        this.horizontalMode = horizontal;
        if (horizontal) {
            setLayoutManager(new LinearLayoutManager(getContext(),
                    LinearLayoutManager.HORIZONTAL, false));
            // Use cell target size for fixed-width items; let row height fill
            // whatever space the parent provides (avoids clipping when the
            // available area is smaller than a hard-coded value)
            int cellSize = getContext().getResources().getDimensionPixelSize(
                    R.dimen.wk_emoji_cell_target_size);
            fixedCellSize = cellSize;
            adapter.setFixedCellSize(cellSize);
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                setLayoutParams(lp);
            }
        } else {
            fixedCellSize = 0;
            adapter.setFixedCellSize(0);
            setLayoutManager(new GridLayoutManager(getContext(), calculateColumns(getContext())));
            // Restore full-height grid
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                setLayoutParams(lp);
            }
        }
    }

    public void onThemeChanged(Context themedContext) {
        adapter.setThemedContext(themedContext);
    }

    public void setEmojis(String[] emojis, OnClickListener listener) {
        adapter.setEmojis(emojis, listener, longPressListener);
    }

    public void setOnEmojiLongPressListener(OnEmojiLongPressListener listener) {
        this.longPressListener = listener;
    }

    private static class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {
        private String[] emojis;
        private OnClickListener listener;
        private OnEmojiLongPressListener longPressListener;
        private Context themedContext;
        private int fixedCellSize = 0;

        public void setFixedCellSize(int size) {
            this.fixedCellSize = size;
        }

        public void setEmojis(String[] emojis, OnClickListener listener,
                              OnEmojiLongPressListener longPressListener) {
            this.emojis = emojis;
            this.listener = listener;
            this.longPressListener = longPressListener;
            notifyDataSetChanged();
        }

        public void setThemedContext(Context themedContext) {
            this.themedContext = themedContext;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new EmojiViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
            holder.bind(emojis[position], listener, longPressListener, themedContext, fixedCellSize);
        }

        @Override
        public int getItemCount() {
            return emojis != null ? emojis.length : 0;
        }

        static class EmojiViewHolder extends RecyclerView.ViewHolder {
            private final TextView button;

            public EmojiViewHolder(@NonNull View itemView) {
                super(itemView);
                button = (TextView) itemView;
            }

            public void bind(String emoji, OnClickListener listener,
                             OnEmojiLongPressListener longPressListener,
                             Context themedContext, int fixedCellSize) {
                if (fixedCellSize > 0) {
                    // Fixed width for uniform spacing; MATCH_PARENT height to fill row
                    button.setLayoutParams(new ViewGroup.LayoutParams(
                            fixedCellSize, ViewGroup.LayoutParams.MATCH_PARENT));
                } else {
                    button.setLayoutParams(new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                button.setMinWidth(0);
                button.setMinHeight(0);
                button.setMinimumWidth(0);
                button.setMinimumHeight(0);
                button.setGravity(android.view.Gravity.CENTER);
                button.setText(emoji);
                float emojiTextSize = button.getResources().getDimension(R.dimen.wk_emoji_text_size);
                button.setTextSize(TypedValue.COMPLEX_UNIT_PX, emojiTextSize);
                button.setClickable(true);
                button.setFocusable(true);

                if (themedContext != null) {
                    TypedValue outValue = new TypedValue();
                    boolean found = themedContext.getTheme().resolveAttribute(
                            android.R.attr.selectableItemBackgroundBorderless, outValue, true);
                    if (!found) {
                        themedContext.getTheme().resolveAttribute(
                                android.R.attr.selectableItemBackground, outValue, true);
                    }
                    button.setBackgroundResource(outValue.resourceId);
                    button.setTextColor(ThemeUtils.getThemeColor(
                            themedContext, R.attr.themedButtonTextColor));
                }
                int paddingH = button.getResources().getDimensionPixelSize(
                        R.dimen.wk_emoji_cell_padding_h);
                if (fixedCellSize > 0) {
                    // Horizontal search mode: zero vertical padding + no font padding
                    // to maximize emoji size in the tight available space
                    button.setPadding(paddingH, 0, paddingH, 0);
                    button.setIncludeFontPadding(false);
                } else {
                    int paddingV = button.getResources().getDimensionPixelSize(
                            R.dimen.wk_emoji_cell_padding_v);
                    button.setPadding(paddingH, paddingV, paddingH, paddingV);
                    button.setIncludeFontPadding(true);
                }

                // D1: Spring-like scale + alpha tap animation with haptic
                button.setOnClickListener(v -> {
                    // Press: scale down + fade
                    AnimatorSet press = new AnimatorSet();
                    press.playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.82f),
                            ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.82f),
                            ObjectAnimator.ofFloat(v, "alpha", 1f, 0.7f)
                    );
                    press.setDuration(100);
                    press.setInterpolator(new AccelerateInterpolator());

                    // Release: spring bounce back
                    AnimatorSet release = new AnimatorSet();
                    release.playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 0.82f, 1f),
                            ObjectAnimator.ofFloat(v, "scaleY", 0.82f, 1f),
                            ObjectAnimator.ofFloat(v, "alpha", 0.7f, 1f)
                    );
                    release.setDuration(250);
                    release.setInterpolator(new OvershootInterpolator(2.0f));

                    AnimatorSet combined = new AnimatorSet();
                    combined.playSequentially(press, release);
                    combined.start();

                    // Haptic + callback on press start (immediate feel)
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (listener != null) {
                        listener.onClick(v);
                    }
                });

                // D2: Long-press for skin tone popup
                if (longPressListener != null) {
                    button.setOnLongClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        longPressListener.onEmojiLongPress(v, emoji);
                        return true;
                    });
                } else {
                    button.setOnLongClickListener(null);
                }
            }
        }
    }
}
