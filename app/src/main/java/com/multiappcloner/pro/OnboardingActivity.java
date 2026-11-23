package com.multiappcloner.pro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OnboardingActivity extends Activity {
    private static final String[] TITLES = {"Clone your apps", "Honest by design", "Network tools"};
    private static final String[] BODIES = {
        "Pick an installed app, name the clone and install it as a separate app with its own data.",
        "Cloning repackages the app under a new package name. System apps and bundles are refused, and apps that check their signature or use Google sign-in may not work. The app tells you why.",
        "Save proxy profiles with encrypted credentials, keep VPN provider references and check your real public IP with an actual network request."};
    private int page = 0;
    private TextView title, bodyText;
    private Button next;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.applyDarkBars(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 28), Ui.dp(this, 28), Ui.dp(this, 28));

        root.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        root.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 110), Ui.dp(this, 110)));
        title = Ui.text(this, "", 24, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(this, 24);
        root.addView(title, tp);
        bodyText = Ui.text(this, "", 14, Ui.TEXT2, false);
        bodyText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(this, 12);
        root.addView(bodyText, bp);
        root.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        next = Ui.button(this, "Next", true, new View.OnClickListener() {
            public void onClick(View v) {
                if (page < TITLES.length - 1) { page++; show(); } else finishOnboarding();
            }
        });
        root.addView(next, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        TextView skip = Ui.text(this, "Skip", 13, Ui.TEXT2, false);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        skip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finishOnboarding(); }
        });
        root.addView(skip);
        setContentView(root);
        show();
    }

    private void show() {
        title.setText(TITLES[page]);
        bodyText.setText(BODIES[page]);
        next.setText(page == TITLES.length - 1 ? "Get started" : "Next");
    }

    private void finishOnboarding() {
        AppPrefs.setOnboarded(this, true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
