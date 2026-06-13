package project.witty.keys.keyboard.AssistantViews;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.Button;

import project.witty.keys.R;

public class AssistantContainer extends FrameLayout {
    private Button backButton;

    public AssistantContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));
        setBackgroundColor(getResources().getColor(R.color.background_lxx_light));

        // Initialize back button
//        backButton = new Button(context);
//        LayoutParams backButtonLayoutParams = new LayoutParams(
//                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, getResources().getDisplayMetrics()),
//                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, getResources().getDisplayMetrics())
//        );
//
//        backButtonLayoutParams.setMargins(16, 16, 16, 16);
//        backButton.setLayoutParams(backButtonLayoutParams);
//        backButton.setBackgroundResource(R.drawable.sym_keyboard_back_lxx_light);
//        backButton.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                setVisibility(View.GONE);
//            }
//        });
//        addView(backButton);
    }
}