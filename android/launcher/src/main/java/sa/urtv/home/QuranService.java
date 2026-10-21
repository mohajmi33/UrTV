package sa.urtv.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** Foreground service that owns the Quran MediaPlayer so playback keeps going after
 *  the screensaver dismisses or the dialog closes. Started by the launcher's dialog
 *  and by the screensaver's JS bridge via explicit intents. */
public class QuranService extends Service {
    public static final String ACTION_PLAY = "sa.urtv.home.QURAN_PLAY";
    public static final String ACTION_STOP = "sa.urtv.home.QURAN_STOP";
    public static final String EXTRA_RECITER = "reciter";
    public static final String EXTRA_SURAH = "surah";

    private static final String CHANNEL_ID = "urtv_quran";
    private static final int NOTIF_ID = 42;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            QuranPlayer.get().stop();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String reciter = intent != null ? intent.getStringExtra(EXTRA_RECITER) : null;
        int surah = intent != null ? intent.getIntExtra(EXTRA_SURAH, 2) : 2;
        if (reciter == null) reciter = "abdullah-qarafi";

        startForeground(NOTIF_ID, buildNotification(reciter, surah));
        // The callback now only fires on real transitions: started (OnPrepared) or
        // stopped (explicit stop or end of An-Nas). Mid-surah releases are silent,
        // so stopSelf() will not be called during an auto-advance.
        QuranPlayer.get().setOnStateChanged(() -> {
            QuranPlayer p = QuranPlayer.get();
            if (!p.isPlaying()) {
                stopForeground(true);
                stopSelf();
            } else {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIF_ID, buildNotification(p.reciterId(), p.surah()));
            }
        });
        QuranPlayer.get().play(getApplicationContext(), reciter, surah);
        return START_STICKY;
    }

    private Notification buildNotification(String reciterId, int surah) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "القرآن",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }

        String surahName = (surah >= 1 && surah <= QuranDialog.SURAH_AR.length)
                ? QuranDialog.SURAH_AR[surah - 1] : "";
        String reciterName = reciterId;
        for (String[] r : QuranDialog.RECITERS) if (r[0].equals(reciterId)) { reciterName = r[1]; break; }

        Intent stopIntent = new Intent(this, QuranService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("القرآن الكريم · " + surahName)
         .setContentText(reciterName)
         .setSmallIcon(R.drawable.ic_quran)
         .setOngoing(true)
         .setShowWhen(false)
         .addAction(new Notification.Action.Builder(null, "إيقاف", stopPi).build());
        return b.build();
    }

    public static void play(Context ctx, String reciter, int surah) {
        Intent i = new Intent(ctx, QuranService.class).setAction(ACTION_PLAY)
                .putExtra(EXTRA_RECITER, reciter)
                .putExtra(EXTRA_SURAH, surah);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, QuranService.class).setAction(ACTION_STOP);
        ctx.startService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
