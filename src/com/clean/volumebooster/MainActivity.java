package com.clean.volumebooster;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 底部控制条 UI（适合单手操作）：
 *  − / 胶囊进度 / +  一行，预设 chips 一行。
 *  增益逻辑在 BoosterService（三级音频引擎回退）。
 */
public class MainActivity extends Activity {

    public static final String PREFS = "vb_prefs";
    public static final String KEY_BOOST = "boost";
    public static final String ACTION_START = "com.clean.volumebooster.START";
    public static final String ACTION_STOP = "com.clean.volumebooster.STOP";

    private static final int STEP = 5;
    private static final int[] PRESETS = {0, 25, 50, 75, 100};

    private TextView tvGain;
    private TextView tvDb;
    private TextView tvStatus;
    private TextView pillText;
    private TextView btnClose;
    private View pillFill;
    private View pillRest;
    private LinearLayout presetsRow;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> runOnUiThread(this::refreshUi);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvGain = findViewById(R.id.tv_gain);
        tvDb = findViewById(R.id.tv_db);
        tvStatus = findViewById(R.id.tv_status);
        pillText = findViewById(R.id.pill_text);
        btnClose = findViewById(R.id.btn_close);
        pillFill = findViewById(R.id.pill_fill);
        pillRest = findViewById(R.id.pill_rest);
        presetsRow = findViewById(R.id.presets);

        // 请求通知权限（Android 13+），用于前台服务通知
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        findViewById(R.id.btn_minus).setOnClickListener(v -> adjust(-STEP));
        findViewById(R.id.btn_plus).setOnClickListener(v -> adjust(STEP));
        btnClose.setOnClickListener(v -> {
            startService(new Intent(this, BoosterService.class).setAction(ACTION_STOP));
            refreshUi();
        });

        buildPresets();

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    // ---------- 交互 ----------

    private void adjust(int delta) {
        int cur = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_BOOST, 0);
        applyValue(Math.max(0, Math.min(100, cur + delta)));
    }

    private void applyValue(int percent) {
        if (percent > 60) {
            Toast.makeText(this, "增益过高可能失真，请注意保护听力", Toast.LENGTH_SHORT).show();
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putInt(KEY_BOOST, percent).apply();
        boolean starting = percent > 0 && !BoosterService.running;
        if (starting) {
            startService(new Intent(this, BoosterService.class).setAction(ACTION_START));
        }
        BoosterService.applyBoost(percent);
        refreshUi();
        if (starting) {
            // 服务异步启动，稍后刷新一次拿到引擎状态
            presetsRow.postDelayed(this::refreshUi, 600);
        }
    }

    private void buildPresets() {
        int padH = dp(16), padV = dp(8);
        for (int p : PRESETS) {
            TextView chip = new TextView(this);
            chip.setText(p + "%");
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(padH, padV, padH, padV);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setTag(p);
            chip.setOnClickListener(v -> applyValue(p));
            presetsRow.addView(chip);
        }
    }

    // ---------- UI 刷新 ----------

    private void refreshUi() {
        int percent = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_BOOST, 0);

        tvGain.setText(percent + "%");
        tvDb.setText(String.format("增益 %+.2f dB", percent * 0.075f));
        pillText.setText("音量增强 " + percent + "%");

        // 胶囊进度填充（weight 0~100）
        LinearLayout.LayoutParams f = (LinearLayout.LayoutParams) pillFill.getLayoutParams();
        LinearLayout.LayoutParams r = (LinearLayout.LayoutParams) pillRest.getLayoutParams();
        f.weight = percent;
        r.weight = 100 - percent;
        pillFill.setLayoutParams(f);
        pillRest.setLayoutParams(r);

        // 预设 chips 选中态
        for (int i = 0; i < presetsRow.getChildCount(); i++) {
            TextView chip = (TextView) presetsRow.getChildAt(i);
            int p = (int) chip.getTag();
            boolean active = p == percent;
            chip.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
            chip.setTextColor(getResources().getColor(
                    active ? android.R.color.white : R.color.chip_text));
        }

        boolean running = BoosterService.running;
        btnClose.setVisibility(running ? View.VISIBLE : View.GONE);
        tvStatus.setText(running
                ? BoosterService.status
                : (percent > 0 ? "未开启（点 + 或预设开启）" : "未开启"));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
