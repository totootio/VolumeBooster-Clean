package com.clean.volumebooster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台服务：对全局音频会话（session 0）施加音频增益。
 * 三级引擎回退：LoudnessEnhancer → Equalizer → DynamicsProcessing，
 * 覆盖不同 OEM 音频栈的支持差异。无广告、无统计、无网络。
 */
public class BoosterService extends Service {

    public static boolean running = false;
    /** 当前生效引擎描述 / 最近一次错误，供界面显示 */
    public static String status = "未开启";

    private static LoudnessEnhancer le;
    private static Equalizer eq;
    private static DynamicsProcessing dp;
    /** 1=LoudnessEnhancer 2=Equalizer 3=DynamicsProcessing 0=无 */
    private static int mode = 0;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> {
                if (MainActivity.KEY_BOOST.equals(key)) {
                    int p = prefs.getInt(key, 0);
                    setGain(p);
                    updateNotification(p);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createChannel(this);
        startForeground(1, buildNotification(0));
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && MainActivity.ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int boost = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getInt(MainActivity.KEY_BOOST, 0);
        setGain(boost);
        updateNotification(boost);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefListener);
        releaseAll();
        status = "未开启";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- 增益控制：三级引擎 ----------

    /** 供 Activity 调用；服务未运行时静默忽略 */
    public static void applyBoost(int percent) {
        if (running) setGain(percent);
    }

    private static void setGain(int percent) {
        StringBuilder err = new StringBuilder();

        // 1) LoudnessEnhancer（API 19+，首选）
        if (le == null) {
            try {
                le = new LoudnessEnhancer(0);
            } catch (RuntimeException e) {
                le = null;
                err.append("LE:").append(e.getClass().getSimpleName()).append("(").append(e.getMessage()).append(")").append("; ");
            }
        }
        if (le != null) {
            try {
                le.setTargetGain(percent * 75);   // 1% = 75 mB，满量程 +7.5 dB
                le.setEnabled(percent != 0);
                if (percent != 0 && !le.hasControl()) {
                    // 引擎被其他应用抢占：重建一次
                    le.release();
                    le = new LoudnessEnhancer(0);
                    le.setTargetGain(percent * 75);
                    le.setEnabled(true);
                }
                mode = 1;
                status = "运行中 · LoudnessEnhancer";
                return;
            } catch (RuntimeException e) {
                err.append("LE-set:").append(e.getClass().getSimpleName()).append("(").append(e.getMessage()).append(")").append("; ");
                le = null;
            }
        }

        // 2) Equalizer（老设备兜底，分频段提升）
        if (eq == null) {
            try {
                eq = new Equalizer(0, 0);
            } catch (RuntimeException e) {
                eq = null;
                err.append("EQ:").append(e.getClass().getSimpleName()).append("(").append(e.getMessage()).append(")").append("; ");
            }
        }
        if (eq != null) {
            try {
                short bands = eq.getNumberOfBands();
                short level = (short) (percent * 15);  // 满量程约 +15 dB（受硬件范围限制）
                for (short i = 0; i < bands; i++) {
                    eq.setBandLevel(i, level);
                }
                eq.setEnabled(percent != 0);
                mode = 2;
                status = "运行中 · Equalizer";
                return;
            } catch (RuntimeException e) {
                err.append("EQ-set:").append(e.getClass().getSimpleName()).append("(").append(e.getMessage()).append(")").append("; ");
                eq = null;
            }
        }

        // 3) DynamicsProcessing（API 28+，post-EQ 增益 + 限幅器）
        if (dp == null && Build.VERSION.SDK_INT >= 28) {
            try {
                DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2,      // 声道数
                        false, 0,   // preEq
                        false, 0,   // mbc
                        true, 3,    // postEq：3 段
                        true        // limiter：防削顶
                ).build();
                dp = new DynamicsProcessing(0, 0, cfg);
            } catch (Throwable e) {
                dp = null;
                err.append("DP:").append(e.getClass().getSimpleName()).append("(").append(e.getMessage()).append(")").append("; ");
            }
        }
        if (dp != null) {
            try {
                float gainDb = percent * 0.075f;   // 与 LE 标度一致：满量程 +7.5 dB
                dp.setPostEqBandAllChannelsTo(0,
                        new DynamicsProcessing.EqBand(true, gainDb, 0f));
                dp.setEnabled(percent != 0);
                mode = 3;
                status = "运行中 · DynamicsProcessing";
                return;
            } catch (Throwable e) {
                err.append("DP-set:").append(e.getClass().getSimpleName()).append("(").append(e.getMessage()).append(")").append("; ");
                dp = null;
            }
        }

        mode = 0;
        status = "音频特效创建失败（设备不支持或被占用）: " + err;
    }

    private static void releaseAll() {
        try { if (le != null) { le.setEnabled(false); le.release(); } } catch (RuntimeException ignored) {}
        try { if (eq != null) { eq.setEnabled(false); eq.release(); } } catch (RuntimeException ignored) {}
        try { if (dp != null) { dp.setEnabled(false); dp.release(); } } catch (RuntimeException ignored) {}
        le = null; eq = null; dp = null;
        mode = 0;
    }

    // ---------- 通知 ----------

    private static final String CHANNEL_ID = "vb_channel";

    private static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "音量增强", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(int percent) {
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("音量增强" + (percent != 0 ? " 运行中" : " 已就绪"))
                .setContentText(String.format("当前增益 %d%%（%+.2f dB）",
                        percent, percent * 0.075f))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setCategory(Notification.CATEGORY_SERVICE);
        }
        return b.build();
    }

    private void updateNotification(int percent) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1, buildNotification(percent));
    }
}
