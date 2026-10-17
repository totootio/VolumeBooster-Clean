package com.clean.volumebooster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台服务：对全局音频会话（session 0）施加 LoudnessEnhancer 增益。
 * 仅此一个职责，无广告、无统计、无网络。
 */
public class BoosterService extends Service {

    public static boolean running = false;

    private static LoudnessEnhancer enhancer;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> {
                if (MainActivity.KEY_BOOST.equals(key)) {
                    setGain(prefs.getInt(key, 0));
                    updateNotification(prefs.getInt(key, 0));
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
        releaseEnhancer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- 增益控制 ----------

    /** 供 Activity 在服务未启动时也能即时刷新已有特效；无特效时静默忽略 */
    public static void applyBoost(Context ctx, int percent) {
        if (enhancer != null) setGain(percent);
    }

    private static void setGain(int percent) {
        try {
            if (enhancer == null) {
                // session 0 = 全局输出混音，作用于所有媒体音量的软件后级
                enhancer = new LoudnessEnhancer(0);
            }
            // 1% ≈ 75 mB，满量程 100% = +7.5 dB
            enhancer.setTargetGain(percent * 75);
            enhancer.setEnabled(percent > 0);
        } catch (RuntimeException e) {
            // 特效引擎不可用或被占用：增益功能降级关闭
            enhancer = null;
        }
    }

    private static void releaseEnhancer() {
        if (enhancer != null) {
            try {
                enhancer.setEnabled(false);
                enhancer.release();
            } catch (RuntimeException ignored) {
            }
            enhancer = null;
        }
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
                .setContentTitle("音量增强" + (percent > 0 ? " 运行中" : " 已就绪"))
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
