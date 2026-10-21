package sa.urtv.screensaver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;

/** JavaScript interface exposed to the screensaver's WebView as `UrTvHost`.
 *  Forwards Quran play/stop requests to the launcher's QuranService.
 *
 *  A recitation started by this bridge is a "screensaver session" — it stops
 *  automatically the moment the Dream is dismissed (ScreenDream calls
 *  {@link #onScreensaverDismissed}). A recitation started manually from the
 *  launcher's dialog is independent and only stops when the user stops it. */
public class UrTvBridge {
    /** True while a scheduled recitation started by the screensaver is playing. */
    private static boolean activeFromSaver;

    private final Context ctx;

    public UrTvBridge(Context c) { this.ctx = c.getApplicationContext(); }

    @JavascriptInterface
    public boolean playQuran(String reciter, int surah) {
        try {
            Intent i = new Intent("sa.urtv.home.QURAN_PLAY");
            i.setComponent(new ComponentName("sa.urtv.home", "sa.urtv.home.QuranService"));
            i.putExtra("reciter", reciter);
            i.putExtra("surah", surah);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
            else ctx.startService(i);
            activeFromSaver = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean stopQuran() {
        activeFromSaver = false;
        return doStop();
    }

    /** Called by {@link ScreenDream#onDetachedFromWindow()}. Stops the recitation
     *  only if it was started by the screensaver's schedule — a manually started
     *  dialog session is left alone. */
    public static void onScreensaverDismissed(Context ctx) {
        if (!activeFromSaver) return;
        activeFromSaver = false;
        try {
            Intent i = new Intent("sa.urtv.home.QURAN_STOP");
            i.setComponent(new ComponentName("sa.urtv.home", "sa.urtv.home.QuranService"));
            ctx.getApplicationContext().startService(i);
        } catch (Exception ignored) {}
    }

    private boolean doStop() {
        try {
            Intent i = new Intent("sa.urtv.home.QURAN_STOP");
            i.setComponent(new ComponentName("sa.urtv.home", "sa.urtv.home.QuranService"));
            ctx.startService(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
