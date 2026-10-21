package sa.urtv.screensaver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** The WebView shared by the preview activity and the dream. */
final class ScreenView {
    private ScreenView() {}

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    static WebView create(final Context c) {
        WebView w = new WebView(c);
        w.setBackgroundColor(0xFF040E09);   // no white flash before the page paints
        w.setFocusable(false);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                WebResourceResponse res = PhotoCache.serve(c, r.getUrl().toString());
                return res != null ? res : super.shouldInterceptRequest(v, r);
            }
        });
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // lets the page call Yahoo and other APIs directly, without a CORS gateway
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        // Let the Quran scheduler play audio without a prior user tap (the screensaver
        // has no interaction — Chromium would otherwise block autoplay).
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);   // photos are cached by PhotoCache, data must be fresh
        if (android.os.Build.VERSION.SDK_INT >= 23) s.setOffscreenPreRaster(true);   // draw before it is shown
        // Bridge the scheduler to the launcher's native QuranService so the recitation
        // keeps running after the Dream is dismissed (user stops it from the pill, not
        // by touching the remote).
        w.addJavascriptInterface(new UrTvBridge(c), "UrTvHost");
        w.loadUrl(new Prefs(c).dashboardUrl(c));
        UpdateJob.schedule(c);
        return w;
    }
}
