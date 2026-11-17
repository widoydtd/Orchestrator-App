package com.widi.orchestrator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String PREFS = "orchestrator_prefs";
    private static final String KEY_URL = "orchestrator_url";
    private static final String KEY_WIDTH = "view_width";     // 0 = mode mobile
    private static final String KEY_TEXT = "text_zoom";
    private static final String DEFAULT_URL = "https://cloud.uipath.com/";
    private static final int DEFAULT_WIDTH = 1024;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private static final int[] WIDTHS = {0, 1024, 1366};
    private static final String[] WIDTH_LABELS = {
            "Mobile (bawaan UiPath)", "Desktop – ringkas (1024 px)", "Desktop – lebar (1366 px)"};
    private static final int[] TEXT_ZOOMS = {85, 100, 115, 130};

    private WebView webView;
    private ProgressBar progressBar;
    private ImageButton menuButton;
    private ValueCallback<Uri[]> filePathCallback;
    private ScriptHandler viewportScript;
    private String defaultUserAgent;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        menuButton = findViewById(R.id.menu_button);

        setupWebView();
        applyViewSettings();
        setupMenuButton();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(getSavedUrl());
        }
    }

    // ---------- WebView ----------

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        defaultUserAgent = s.getUserAgentString().replace("; wv", "");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                // Cadangan untuk WebView lama yang tidak mendukung skrip awal dokumen
                if (viewportScript == null && getViewWidth() > 0) {
                    view.evaluateJavascript(buildViewportScript(getViewWidth()), null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                req.setMimeType(mimeType);
                req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                req.addRequestHeader("User-Agent", userAgent);
                req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this, "Mengunduh " + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
    }

    /** Terapkan mode tampilan (mobile/desktop) dan ukuran teks yang tersimpan. */
    private void applyViewSettings() {
        WebSettings s = webView.getSettings();
        int width = getViewWidth();

        s.setTextZoom(prefs.getInt(KEY_TEXT, 100));

        if (viewportScript != null) {
            viewportScript.remove();
            viewportScript = null;
        }

        if (width == 0) {
            s.setUserAgentString(defaultUserAgent);
        } else {
            s.setUserAgentString(desktopUserAgent());
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                viewportScript = WebViewCompat.addDocumentStartJavaScript(
                        webView, buildViewportScript(width), Collections.singleton("*"));
            }
        }
    }

    /** User agent desktop dengan versi Chrome yang sama seperti WebView perangkat. */
    private String desktopUserAgent() {
        String version = "120.0.0.0";
        Matcher m = Pattern.compile("Chrome/([\\d.]+)").matcher(defaultUserAgent);
        if (m.find()) version = m.group(1);
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + version + " Safari/537.36";
    }

    /**
     * Memaksa halaman ditata selebar layar PC (mis. 1024 px) lalu diperkecil agar pas di layar,
     * sehingga panel folder dan tabel tidak lagi terjepit. Tetap bisa dicubit untuk zoom.
     */
    private static String buildViewportScript(int width) {
        return "(function(){"
                + "var W=" + width + ";"
                + "function fix(){"
                + "var sw=Math.min(screen.width,screen.height);"
                + "if(window.matchMedia('(orientation: landscape)').matches)sw=Math.max(screen.width,screen.height);"
                + "var sc=Math.min(1,sw/W);"
                + "var c='width='+W+', initial-scale='+sc.toFixed(3)+', minimum-scale='+(sc*0.5).toFixed(3)+', maximum-scale=5, user-scalable=yes';"
                + "var ms=document.querySelectorAll('meta[name=\"viewport\"]');"
                + "if(!ms.length){var root=document.head||document.documentElement;if(!root)return;"
                + "var m=document.createElement('meta');m.name='viewport';m.content=c;root.appendChild(m);}"
                + "else{for(var i=0;i<ms.length;i++){if(ms[i].content!==c)ms[i].content=c;}}"
                + "}"
                + "fix();"
                + "var ob=new MutationObserver(fix);"
                + "ob.observe(document.documentElement||document,{childList:true,subtree:true,attributes:true,attributeFilter:['content']});"
                + "document.addEventListener('DOMContentLoaded',function(){fix();ob.disconnect();"
                + "if(document.head)ob.observe(document.head,{childList:true,subtree:true,attributes:true,attributeFilter:['content']});});"
                + "})();";
    }

    // ---------- Tombol menu mengambang (bisa digeser) ----------

    @SuppressLint("ClickableViewAccessibility")
    private void setupMenuButton() {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        menuButton.setOnClickListener(v -> showMenu());
        menuButton.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY, startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - e.getRawX();
                        dY = v.getY() - e.getRawY();
                        startX = e.getRawX();
                        startY = e.getRawY();
                        moved = false;
                        v.setAlpha(1f);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.hypot(e.getRawX() - startX, e.getRawY() - startY) > slop) moved = true;
                        if (moved) {
                            View p = (View) v.getParent();
                            v.setX(clamp(e.getRawX() + dX, 0, p.getWidth() - v.getWidth()));
                            v.setY(clamp(e.getRawY() + dY, 0, p.getHeight() - v.getHeight()));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.setAlpha(0.75f);
                        if (!moved) v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(0.75f);
                        return true;
                }
                return false;
            }
        });
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void showMenu() {
        PopupMenu popup = new PopupMenu(this, menuButton);
        Menu m = popup.getMenu();
        m.add(0, 1, 0, "Beranda");
        m.add(0, 2, 1, "Muat ulang");

        SubMenu view = m.addSubMenu(0, 10, 2, "Tampilan");
        int currentWidth = getViewWidth();
        for (int i = 0; i < WIDTHS.length; i++) {
            view.add(1, 100 + i, i, WIDTH_LABELS[i]).setChecked(WIDTHS[i] == currentWidth);
        }
        view.setGroupCheckable(1, true, true);

        SubMenu text = m.addSubMenu(0, 20, 3, "Ukuran teks");
        int currentText = prefs.getInt(KEY_TEXT, 100);
        for (int i = 0; i < TEXT_ZOOMS.length; i++) {
            text.add(2, 200 + i, i, TEXT_ZOOMS[i] + "%").setChecked(TEXT_ZOOMS[i] == currentText);
        }
        text.setGroupCheckable(2, true, true);

        m.add(0, 3, 4, "Ubah URL Orchestrator");
        m.add(0, 4, 5, "Buka di browser");
        m.add(0, 5, 6, "Keluar & hapus sesi");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id >= 100 && id < 100 + WIDTHS.length) {
                prefs.edit().putInt(KEY_WIDTH, WIDTHS[id - 100]).apply();
                applyViewSettings();
                webView.reload();
                return true;
            }
            if (id >= 200 && id < 200 + TEXT_ZOOMS.length) {
                prefs.edit().putInt(KEY_TEXT, TEXT_ZOOMS[id - 200]).apply();
                webView.getSettings().setTextZoom(TEXT_ZOOMS[id - 200]);
                return true;
            }
            switch (id) {
                case 1: webView.loadUrl(getSavedUrl()); return true;
                case 2: webView.reload(); return true;
                case 3: showUrlDialog(); return true;
                case 4:
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl())));
                    return true;
                case 5: clearSession(); return true;
            }
            return false;
        });
        popup.show();
    }

    private void showUrlDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(getSavedUrl());
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("URL Orchestrator")
                .setMessage("Contoh: https://cloud.uipath.com/namaorg/DefaultTenant/orchestrator_/ "
                        + "atau URL Orchestrator on-premise Anda")
                .setView(input)
                .setPositiveButton("Simpan", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("http")) url = "https://" + url;
                    prefs.edit().putString(KEY_URL, url).apply();
                    webView.loadUrl(url);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void clearSession() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar?")
                .setMessage("Semua cookie dan data login akan dihapus.")
                .setPositiveButton("Ya", (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    webView.clearCache(true);
                    WebStorage.getInstance().deleteAllData();
                    webView.loadUrl(getSavedUrl());
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private String getSavedUrl() {
        return prefs.getString(KEY_URL, DEFAULT_URL);
    }

    private int getViewWidth() {
        return prefs.getInt(KEY_WIDTH, DEFAULT_WIDTH);
    }

    // ---------- Siklus hidup ----------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            filePathCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePathCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }
}
