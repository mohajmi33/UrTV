package sa.urtv.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;

/** Singleton Quran player. Native MediaPlayer with audio focus + wake lock, so long
 *  recitations don't drop out when the TV tries to doze. */
public final class QuranPlayer {
    private static QuranPlayer instance;
    private MediaPlayer mp;
    private String currentReciter = "";
    private int currentSurah = 0;
    private Runnable onState;
    private Context appCtx;
    private AudioManager am;
    private AudioFocusRequest focusReq;
    private int retries;
    private static final int MAX_RETRIES = 2;

    public static synchronized QuranPlayer get() {
        if (instance == null) instance = new QuranPlayer();
        return instance;
    }
    private QuranPlayer() {}

    public void setOnStateChanged(Runnable r) { this.onState = r; }

    public boolean isPlaying() { return mp != null; }
    public String reciterId() { return currentReciter; }
    public int surah() { return currentSurah; }

    public static String urlFor(String reciterId, int surah) {
        return "https://cdn.mp3quran.net/audio/" + reciterId + "/r1/"
                + String.format("%03d", surah) + ".mp3";
    }

    public void play(Context ctx, String reciterId, int surah) {
        appCtx = ctx.getApplicationContext();
        releaseMp();  // silent cleanup of any previous player — do NOT fire onState
        currentReciter = reciterId;
        currentSurah = surah;
        retries = 0;
        doPlay();
    }

    /** Release the MediaPlayer without touching focus or firing onState. Used for
     *  intermediate transitions (surah → next surah, retry) that should not look
     *  like a stop to listeners. */
    private void releaseMp() {
        if (mp != null) {
            try { if (mp.isPlaying()) mp.stop(); } catch (Exception ignored) {}
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
    }

    private void doPlay() {
        if (!requestFocus()) { stop(); return; }
        try {
            mp = new MediaPlayer();
            // Keep the CPU awake while buffering + playing a long surah.
            mp.setWakeMode(appCtx, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(urlFor(currentReciter, currentSurah));
            mp.setOnPreparedListener(p -> { p.start(); retries = 0; if (onState != null) onState.run(); });
            mp.setOnErrorListener((p, what, extra) -> {
                // Transient stream error (buffer underrun, DNS blip): rebuild and resume
                if (retries++ < MAX_RETRIES) { restart(); return true; }
                stop();
                return true;
            });
            mp.setOnCompletionListener(p -> {
                // Auto-advance: Al-Fatiha → Al-Baqara → … → An-Nas, then stop.
                if (currentSurah < 114) {
                    currentSurah += 1;
                    retries = 0;
                    releaseMp();  // silent — do not fire onState for the transition
                    doPlay();
                } else {
                    stop();
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            if (retries++ < MAX_RETRIES) restart(); else stop();
        }
    }

    private void restart() {
        releaseMp();  // silent — a retry is not a user-visible stop
        // small pause so the CDN edge recovers
        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        doPlay();
    }

    public void stop() {
        releaseMp();
        releaseFocus();
        if (onState != null) onState.run();
    }

    private boolean requestFocus() {
        if (appCtx == null) return true;
        am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return true;
        // Abandon any previous focus request first. Otherwise the system fires
        // AUDIOFOCUS_LOSS on the old listener (which calls stop() and abandons
        // the brand-new request), killing playback the moment it starts.
        releaseFocus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS) stop();
                        else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                              || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            if (mp != null) { try { mp.pause(); } catch (Exception ignored) {} }
                        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                            if (mp != null) { try { mp.start(); } catch (Exception ignored) {} }
                        }
                    })
                    .build();
            return am.requestAudioFocus(focusReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            int r = am.requestAudioFocus(change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS) stop();
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    private void releaseFocus() {
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusReq != null) { am.abandonAudioFocusRequest(focusReq); focusReq = null; }
        }
    }
}
