package com.clean.volumebooster;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String PREFS = "vb_prefs";
    public static final String KEY_BOOST = "boost";
    public static final String ACTION_START = "com.clean.volumebooster.START";
    public static final String ACTION_STOP = "com.clean.volumebooster.STOP";
    /** 单次会话内已提示过高增益警告 */
    public static boolean warned = false;

    private SeekBar seekBar;
    private TextView tvGain;
    private TextView tvDb;
    private TextView tvStatus;
    private Button btnToggle;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> runOnUiThread(this::refreshUi);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        seekBar = findViewById(R.id.seek_bar);
        tvGain = findViewById(R.id.tv_gain);
        tvDb = findViewById(R.id.tv_db);
        tvStatus = findViewById(R.id.tv_status);
        btnToggle = findViewById(R.id.btn_toggle);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // 请求通知权限（Android 13+），用于前台服务通知
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (progress > 60 && !warned) {
                    warned = true;
                    Toast.makeText(MainActivity.this,
                            "增益过高可能失真，请注意保护听力", Toast.LENGTH_LONG).show();
                }
                prefs.edit().putInt(KEY_BOOST, progress).apply();
                // 拖动大于 0 时自动开启服务，无需再点按钮
                if (progress > 0 && !BoosterService.running) {
                    startService(new Intent(MainActivity.this, BoosterService.class)
                            .setAction(ACTION_START));
                }
                // 服务运行时通过监听器更新增益；未运行时先存值
                BoosterService.applyBoost(progress);
                refreshUi();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        btnToggle.setOnClickListener(v -> {
            if (BoosterService.running) {
                startService(new Intent(this, BoosterService.class).setAction(ACTION_STOP));
            } else {
                startService(new Intent(this, BoosterService.class).setAction(ACTION_START));
            }
            refreshUi();
        });

        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void refreshUi() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int boost = prefs.getInt(KEY_BOOST, 0);
        if (seekBar.getProgress() != boost) seekBar.setProgress(boost);
        tvGain.setText(boost + "%");
        tvDb.setText(String.format("增益 %.2f dB", boost * 0.075f));
        boolean running = BoosterService.running;
        btnToggle.setText(running ? "关闭增强" : "开启增强");
        tvStatus.setText(running
                ? BoosterService.status
                : (boost > 0 ? "未开启（拖动滑块或点按钮开启）" : "未开启"));
    }
}
