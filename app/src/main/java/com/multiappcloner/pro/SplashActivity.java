package com.multiappcloner.pro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.applyDarkBars(this);
        setContentView(R.layout.activity_splash);

        SpannableString s = new SpannableString("Multi App Cloner Pro");
        s.setSpan(new ForegroundColorSpan(Color.parseColor("#00D9FF")), 10, 20, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.splashTitle)).setText(s);

        findViewById(R.id.btnStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SplashActivity.this, AppPrefs.onboarded(SplashActivity.this) ? MainActivity.class : OnboardingActivity.class));
                finish();
            }
        });
    }
}
