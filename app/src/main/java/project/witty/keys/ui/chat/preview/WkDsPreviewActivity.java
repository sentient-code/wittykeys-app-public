package project.witty.keys.ui.chat.preview;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import project.witty.keys.R;
import project.witty.keys.ui.chat.Surface;
import project.witty.keys.ui.chat.WkAiBubble;
import project.witty.keys.ui.chat.WkChatHeader;
import project.witty.keys.ui.chat.WkDualCtaRow;
import project.witty.keys.ui.chat.WkEmptyState;
import project.witty.keys.ui.chat.WkInputBar;
import project.witty.keys.ui.chat.WkSessionCard;
import project.witty.keys.ui.chat.WkSourceTag;
import project.witty.keys.ui.chat.WkStripMiniHeader;
import project.witty.keys.ui.chat.WkTypingBubble;
import project.witty.keys.ui.chat.WkUserBubble;

public class WkDsPreviewActivity extends AppCompatActivity {

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wk_ds_preview_activity);
        root = findViewById(R.id.wkDsPreviewRoot);

        section("Surfaces · Source tags");
        WkSourceTag t1 = new WkSourceTag(this); t1.setSurface(Surface.KEYBOARD); add(t1);
        WkSourceTag t2 = new WkSourceTag(this); t2.setSurface(Surface.OVERLAY); add(t2);
        WkSourceTag t3 = new WkSourceTag(this); t3.setSurface(Surface.FULLSCREEN); add(t3);

        section("Headers");
        WkChatHeader h = new WkChatHeader(this); h.setTitle("AI Chat"); add(h);
        WkStripMiniHeader mh = new WkStripMiniHeader(this); mh.setTitle("New chat"); add(mh);

        section("Bubbles");
        WkUserBubble ub = new WkUserBubble(this); ub.bind("Draft a calm reply."); add(ub);
        WkAiBubble ai1 = new WkAiBubble(this); ai1.bindNormal("Landing at 5:30 — let me know if earlier."); add(ai1);
        WkAiBubble ai2 = new WkAiBubble(this); ai2.bindWithReplyBadge("Inbox tak 5:30 ke andar!"); add(ai2);
        WkAiBubble ai3 = new WkAiBubble(this); ai3.bindError(() -> {}); add(ai3);
        WkTypingBubble tb = new WkTypingBubble(this); tb.start(); add(tb);

        section("Input bars");
        WkInputBar b1 = new WkInputBar(this); add(b1);
        WkInputBar b2 = new WkInputBar(this); b2.setCaptureEnabled(false); add(b2);
        WkInputBar b3 = new WkInputBar(this); b3.setDisabled(true); add(b3);

        section("Empty states");
        WkEmptyState e1 = new WkEmptyState(this); e1.bind("No chats yet", "Start a chat or share a screenshot."); add(e1);
        WkEmptyState e2 = new WkEmptyState(this);
        e2.bind("No chats yet", "Start a chat or share a screenshot.");
        e2.showCta("New chat", () -> {}, "Open overlay", () -> {});
        add(e2);

        section("Dual CTA row");
        WkDualCtaRow cta = new WkDualCtaRow(this);
        cta.setPrimary("New chat", () -> {});
        cta.setGhost("Open keyboard", () -> {});
        add(cta);

        section("Session cards");
        WkSessionCard c1 = new WkSessionCard(this);
        c1.bind("Reply to Ananya", "On it — 6pm", "2m", Surface.KEYBOARD, true);
        add(c1);
        WkSessionCard c2 = new WkSessionCard(this);
        c2.bind("Screenshot help", "Inbox tak 5:30 ke andar", "1h", Surface.OVERLAY, false);
        add(c2);
    }

    private void section(String name) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        lp.bottomMargin = 8;
        tv.setLayoutParams(lp);
        root.addView(tv);
    }

    private void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 8;
        v.setLayoutParams(lp);
        root.addView(v);
    }
}
