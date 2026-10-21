package sa.urtv.home;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/** Dialog with reciter + surah pickers and play/stop buttons. Uses QuranPlayer (native MediaPlayer). */
final class QuranDialog {

    static final String[][] RECITERS = {
        {"abdullah-qarafi",     "عبدالله القرافي"},
        {"muhammad-ayyub",      "محمد أيوب"},
        {"abdullah-juhani",     "عبدالله الجهني"},
        {"abdulrahman-sudais",  "عبدالرحمن السديس"},
        {"saud-shuraim",        "سعود الشريم"},
        {"mishary-alafasy",     "مشاري العفاسي"},
        {"saad-ghamdi",         "سعد الغامدي"},
        {"khalid-ghamdi",       "خالد الغامدي"},
    };

    static final String[] SURAH_AR = {
        "الفاتحة","البقرة","آل عمران","النساء","المائدة","الأنعام","الأعراف","الأنفال","التوبة","يونس",
        "هود","يوسف","الرعد","إبراهيم","الحجر","النحل","الإسراء","الكهف","مريم","طه",
        "الأنبياء","الحج","المؤمنون","النور","الفرقان","الشعراء","النمل","القصص","العنكبوت","الروم",
        "لقمان","السجدة","الأحزاب","سبأ","فاطر","يس","الصافات","ص","الزمر","غافر",
        "فصلت","الشورى","الزخرف","الدخان","الجاثية","الأحقاف","محمد","الفتح","الحجرات","ق",
        "الذاريات","الطور","النجم","القمر","الرحمن","الواقعة","الحديد","المجادلة","الحشر","الممتحنة",
        "الصف","الجمعة","المنافقون","التغابن","الطلاق","التحريم","الملك","القلم","الحاقة","المعارج",
        "نوح","الجن","المزمل","المدثر","القيامة","الإنسان","المرسلات","النبأ","النازعات","عبس",
        "التكوير","الانفطار","المطففين","الانشقاق","البروج","الطارق","الأعلى","الغاشية","الفجر","البلد",
        "الشمس","الليل","الضحى","الشرح","التين","العلق","القدر","البينة","الزلزلة","العاديات",
        "القارعة","التكاثر","العصر","الهمزة","الفيل","قريش","الماعون","الكوثر","الكافرون","النصر",
        "المسد","الإخلاص","الفلق","الناس"
    };

    static Dialog build(Context ctx) {
        int pad = dp(ctx, 24);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2141414);
        bg.setCornerRadius(dp(ctx, 18));
        bg.setStroke(dp(ctx, 1), 0x33FFFFFF);
        root.setBackground(bg);

        // Title
        TextView title = new TextView(ctx);
        title.setText("القرآن الكريم");
        title.setTextColor(0xFFF6EFE0);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.RIGHT);
        root.addView(title, lp(ctx, -1, -2, 0, 0, 0, dp(ctx, 16)));

        // Reciter label
        root.addView(label(ctx, "القارئ"));
        Spinner reciterSel = new Spinner(ctx);
        String[] reciterNames = new String[RECITERS.length];
        for (int i = 0; i < RECITERS.length; i++) reciterNames[i] = RECITERS[i][1];
        ArrayAdapter<String> ra = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, reciterNames);
        ra.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reciterSel.setAdapter(ra);
        // restore last choice
        String savedReciter = Dock.prefs(ctx).getString("quran_reciter", RECITERS[0][0]);
        for (int i = 0; i < RECITERS.length; i++)
            if (RECITERS[i][0].equals(savedReciter)) { reciterSel.setSelection(i); break; }
        root.addView(wrapSpinner(ctx, reciterSel), lp(ctx, -1, -2, 0, dp(ctx, 4), 0, dp(ctx, 14)));

        // Surah label
        root.addView(label(ctx, "السورة"));
        Spinner surahSel = new Spinner(ctx);
        String[] surahNames = new String[SURAH_AR.length];
        for (int i = 0; i < SURAH_AR.length; i++) surahNames[i] = (i + 1) + ". " + SURAH_AR[i];
        ArrayAdapter<String> sa = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, surahNames);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        surahSel.setAdapter(sa);
        int savedSurah = Dock.prefs(ctx).getInt("quran_surah", 2);  // Al-Baqara
        surahSel.setSelection(Math.max(0, Math.min(113, savedSurah - 1)));
        root.addView(wrapSpinner(ctx, surahSel), lp(ctx, -1, -2, 0, dp(ctx, 4), 0, dp(ctx, 18)));

        // Status
        TextView status = new TextView(ctx);
        status.setTextColor(0xFFDCB86F);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        updateStatus(status);
        root.addView(status, lp(ctx, -1, -2, 0, 0, 0, dp(ctx, 14)));

        // Buttons row
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        TextView playBtn = pillBtn(ctx, "▶  تشغيل", 0xFFDCB86F, 0xFF111111);
        TextView stopBtn = pillBtn(ctx, "■  إيقاف", 0x66000000, 0xE6FFFFFF);

        btnRow.addView(playBtn, lp(ctx, 0, dp(ctx, 46), 1f, 0, 0, 0));
        btnRow.addView(new View(ctx), lp(ctx, dp(ctx, 12), dp(ctx, 1), 0, 0, 0, 0));
        btnRow.addView(stopBtn, lp(ctx, 0, dp(ctx, 46), 1f, 0, 0, 0));
        root.addView(btnRow, lp(ctx, -1, -2, 0, 0, 0, 0));

        AlertDialog.Builder b = new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        AlertDialog dlg = b.setView(root).create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        QuranPlayer.get().setOnStateChanged(() -> {
            root.post(() -> updateStatus(status));
        });

        playBtn.setOnClickListener(v -> {
            String rid = RECITERS[reciterSel.getSelectedItemPosition()][0];
            int surah = surahSel.getSelectedItemPosition() + 1;
            Dock.prefs(ctx).edit()
                    .putString("quran_reciter", rid)
                    .putInt("quran_surah", surah)
                    .apply();
            QuranService.play(ctx, rid, surah);
            status.setText("جاري التحميل…");
        });
        stopBtn.setOnClickListener(v -> {
            QuranService.stop(ctx);
        });

        playBtn.setFocusable(true);
        stopBtn.setFocusable(true);
        reciterSel.setFocusable(true);
        surahSel.setFocusable(true);

        return dlg;
    }

    private static TextView label(Context ctx, String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(0xFFC3B8A2);
        t.setTextSize(13);
        t.setGravity(Gravity.RIGHT);
        return t;
    }

    private static TextView pillBtn(Context ctx, String text, int bgColor, int fgColor) {
        final int bgRest = bgColor;
        final int fgRest = fgColor;
        final boolean isPrimary = bgColor == 0xFFDCB86F;
        // Focused look (same gold-on-dark highlight as the main pills)
        final int bgFocus = isPrimary ? 0xFFFFFCF2 : 0xF2FFFFFF;
        final int fgFocus = 0xFF111111;
        final int strokeRest = 0x33FFFFFF;
        final int strokeFocus = 0xFFDCB86F;
        final int strokeRestWidth = dp(ctx, 1);
        final int strokeFocusWidth = dp(ctx, 3);

        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextColor(fgRest);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgRest);
        bg.setCornerRadius(dp(ctx, 15));
        bg.setStroke(strokeRestWidth, strokeRest);
        b.setBackground(bg);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setClickable(true);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? bgFocus : bgRest);
            bg.setStroke(has ? strokeFocusWidth : strokeRestWidth, has ? strokeFocus : strokeRest);
            ((TextView) v).setTextColor(has ? fgFocus : fgRest);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(220).start();
        });
        return b;
    }

    /** Wrap a Spinner with a focus-highlighted container (Spinners don't show focus well on their own). */
    private static LinearLayout wrapSpinner(Context ctx, Spinner sp) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 2));
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(ctx, 10));
        bg.setStroke(dp(ctx, 1), 0x33FFFFFF);
        wrap.setBackground(bg);
        wrap.addView(sp, new LinearLayout.LayoutParams(-1, -2));

        // Mirror spinner focus to the wrapper
        sp.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x44DCB86F : 0x33FFFFFF);
            bg.setStroke(dp(ctx, has ? 3 : 1), has ? 0xFFDCB86F : 0x33FFFFFF);
            wrap.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(220).start();
        });
        return wrap;
    }

    private static void updateStatus(TextView status) {
        QuranPlayer p = QuranPlayer.get();
        if (p.isPlaying()) {
            int s = p.surah();
            String sn = (s >= 1 && s <= SURAH_AR.length) ? SURAH_AR[s - 1] : "";
            status.setText("يبث · " + sn);
        } else {
            status.setText("");
        }
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
    private static LinearLayout.LayoutParams lp(Context c, int w, int h, float weight,
                                                int top, int bottom, int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.topMargin = top;
        p.bottomMargin = bottomMargin > 0 ? bottomMargin : bottom;
        return p;
    }
}
