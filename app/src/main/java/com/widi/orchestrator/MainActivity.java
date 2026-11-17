package com.widi.orchestrator;

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
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {

    private static final String PREFS = "orchestrator_prefs";
    private static final String KEY_URL = "orchestrator_url";
    private static final String DEFAULT_URL = "https://cloud.uipath.com/";
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);
        swipeRefresh = findViewById(R.id.swipe);

        setupWebView();
        swipeRefresh.setOnRefreshListener(() -> webView.reload());
        // Hanya izinkan tarik-untuk-refresh saat halaman di posisi paling atas
        webView.getViewTreeObserver().addOnScrollChangedListener(
                () -> swipeRefresh.setEnabled(webView.getScrollY() == 0));

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(getSavedUrl());
        }
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Hapus penanda "wv" agar halaman login tidak memperlakukan ini sebagai WebView terbatas
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    return false; // tetap di dalam aplikasi
                }
                // mailto:, tel:, intent:, dll. dibuka di aplikasi lain
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
                swipeRefresh.setRefreshing(false);
                CookieManager.getInstance().flush(); // simpan sesi login
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

        // Unduhan (mis. log atau paket .nupkg) lewat DownloadManager Android
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

    // ---------- Menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Beranda");
        menu.add(0, 2, 1, "Muat ulang");
        menu.add(0, 3, 2, "Ubah URL Orchestrator");
        menu.add(0, 4, 3, "Buka di browser");
        menu.add(0, 5, 4, "Keluar & hapus sesi");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: webView.loadUrl(getSavedUrl()); return true;
            case 2: webView.reload(); return true;
            case 3: showUrlDialog(); return true;
            case 4:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl())));
                return true;
            case 5: clearSession(); return true;
        }
        return super.onOptionsItemSelected(item);
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
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
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
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    webView.loadUrl(getSavedUrl());
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private String getSavedUrl() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getString(KEY_URL, DEFAULT_URL);
    }

    // ---------- Siklus hidup ----------

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
