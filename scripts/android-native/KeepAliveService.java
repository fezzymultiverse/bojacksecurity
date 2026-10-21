package com.fezzy.vault;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Foreground service that keeps the app process (and CPU) alive while files are being secured in the background. */
public class KeepAliveService extends Service {
    static final String CHANNEL = "bojack_keepalive";
    static final int NOTE_ID = 4107;
    static final String EXTRA_TEXT = "text";
    private PowerManager.WakeLock cpuLock;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        if (text == null) text = "Securing your files";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Background securing", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setContentTitle("Bojack is securing your files")
         .setContentText(text)
         .setSmallIcon(android.R.drawable.stat_sys_upload)
         .setOngoing(true)
         .setOnlyAlertOnce(true);
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open != null) {
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
            b.setContentIntent(PendingIntent.getActivity(this, 0, open, fl));
        }
        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTE_ID, n);
        } catch (Exception e) {
            try { startForeground(NOTE_ID, n); } catch (Exception ignored) { }
            stopSelf();
            return START_NOT_STICKY;
        }
        if (cpuLock == null) {
            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bojack:securing");
                cpuLock.acquire(6L * 60L * 60L * 1000L); // safety timeout: 6 hours (V0.6.20, was 1 hour)
            } catch (Exception ignored) { cpuLock = null; }
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        try { if (cpuLock != null && cpuLock.isHeld()) cpuLock.release(); } catch (Exception ignored) { }
        cpuLock = null;
        super.onDestroy();
    }
}
