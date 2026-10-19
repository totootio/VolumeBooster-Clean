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
    /** 增益范围：-40% ~ +30%（负值为衰减） */
    public static final int MIN_BOOST = -40;
    public static final int MAX_BOOST = 30;
    private static final int[] PRESETS = {-40, -20, 0, 10, 20, 30};
    /** 首次启动的默认增益 */
    private static final int DEFAULT_BOOST = 10;

    private TextView tvGain;
    private TextView tvDb;
    private TextView tvStatus;
    private TextView tvPill;
    private android.widget.Button btnToggle;
    private android.widget.SeekBar seekBar;
    private LinearLayout presetsRow;
    private SharedPreferences prefs;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> runOnUiThread(this::refreshUi);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvGain = findViewById(R.id.tv_gain);
        tvDb = findViewById(R.id.tv_db);
        tvStatus = findViewById(R.id.tv_status);
        tvPill = findViewById(R.id.tv_pill);
        btnToggle = findViewById(R.id.btn_toggle);
        seekBar = findViewById(R.id.seek_bar);
        presetsRow = findViewById(R.id.presets);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // 首次启动给一个默认增益
        if (!prefs.contains(KEY_BOOST)) {
            prefs.edit().putInt(KEY_BOOST, DEFAULT_BOOST).apply();
        }

        // 请求通知权限（Android 13+），用于前台服务通知
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        findViewById(R.id.btn_minus).setOnClickListener(v -> adjust(-STEP));
        findViewById(R.id.btn_plus).setOnClickListener(v -> adjust(STEP));
        seekBar.setMax(MAX_BOOST - MIN_BOOST);
        seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) applyValue(progress + MIN_BOOST);
            }

            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) {}
        });
        btnToggle.setOnClickListener(v -> {
            if (BoosterService.running) {
                startService(new Intent(this, BoosterService.class).setAction(ACTION_STOP));
                refreshUi();
            } else {
                // 开启时若增益为 0，先给默认值
                if (prefs.getInt(KEY_BOOST, 0) == 0) {
                    prefs.edit().putInt(KEY_BOOST, DEFAULT_BOOST).apply();
                }
                startService(new Intent(this, BoosterService.class).setAction(ACTION_START));
                refreshUi();
                presetsRow.postDelayed(this::refreshUi, 600);
            }
        });

        buildPresets();

        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        // 启动默认开启：有非零增益值就自动启动服务（负增益同样需要服务运行）
        if (prefs.getInt(KEY_BOOST, 0) != 0 && !BoosterService.running) {
            startService(new Intent(this, BoosterService.class).setAction(ACTION_START));
            presetsRow.postDelayed(this::refreshUi, 600);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    // ---------- 交互 ----------

    private void adjust(int delta) {
        int cur = prefs.getInt(KEY_BOOST, 0);
        applyValue(Math.max(MIN_BOOST, Math.min(MAX_BOOST, cur + delta)));
    }

    private void applyValue(int percent) {
        percent = Math.max(MIN_BOOST, Math.min(MAX_BOOST, percent));
        if (percent > 20) {
            Toast.makeText(this, "增益较高可能失真，请注意保护听力", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putInt(KEY_BOOST, percent).apply();
        boolean starting = percent != 0 && !BoosterService.running;
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
            chip.setText((p > 0 ? "+" : "") + p + "%");
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
        int percent = prefs.getInt(KEY_BOOST, 0);

        tvGain.setText(percent == 0 ? "0%" : String.format("%+d%%", percent));
        tvDb.setText(String.format("增益 %+.2f dB", percent * 0.075f));
        tvPill.setText(String.format(percent >= 0 ? "音量增强 %d%% · %+.2f dB" : "音量衰减 %d%% · %+.2f dB",
                Math.abs(percent), percent * 0.075f));
        if (seekBar.getProgress() != percent - MIN_BOOST) {
            seekBar.setProgress(percent - MIN_BOOST);
        }

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
        btnToggle.setText(running ? "关闭增强" : "开启增强");
        btnToggle.setBackgroundResource(running ? R.drawable.bg_toggle_on : R.drawable.bg_toggle_off);
        tvStatus.setText(running
                ? BoosterService.status
                : (percent != 0 ? "已暂停（点开关开启）" : "未开启"));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
