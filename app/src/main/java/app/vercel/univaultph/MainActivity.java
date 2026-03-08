package app.vercel.univaultph;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.app.AlertDialog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
public class MainActivity extends Activity {
    private WebView webView;
    private static final String WEBSITE_URL = "https://univault-ph.vercel.app/";
    private static final String PREFS = "app.vercel.univaultph.prefs";
    private static final String TRACK_PREFS = "app.vercel.univaultph.tracking";
    private SwipeRefreshLayout swipeRefresh;
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private FrameLayout mFullscreenContainer;
    private int mOriginalOrientation;

    private void applyFullscreen() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        } catch (Exception e) {}
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        trackAppOpen();
        setContentView(R.layout.activity_main);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        webView = findViewById(R.id.webview);
        setupWebView();
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override public void onRefresh() { webView.reload(); new Handler().postDelayed(new Runnable() { @Override public void run() { swipeRefresh.setRefreshing(false); } }, 1500); }
        });
        promptForAdvancedPermissions();
        loadWebsite();
        applyFullscreen();
    }
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false); s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        webView.addJavascriptInterface(new CredentialBridge(), "AndroidCreds");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String pageUrl) {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                String em = p.getString("email", "");
                if (!em.isEmpty()) {
                    String js = "javascript:(function(){var ef=document.querySelectorAll('input[type=email],input[name*=email],input[id*=email]');var pf=document.querySelectorAll('input[type=password]');var em2=window.AndroidCreds.getEmail();var pw2=window.AndroidCreds.getPassword();if(ef.length>0&&em2)ef[0].value=em2;if(pf.length>0&&pw2)pf[0].value=pw2;document.querySelectorAll('form').forEach(function(f){f.addEventListener('submit',function(){var e=document.querySelector('input[type=email],input[name*=email]');var p=document.querySelector('input[type=password]');if(e&&p&&e.value&&p.value)window.AndroidCreds.save(e.value,p.value);});});})();";
                    view.loadUrl(js);
                }
            }
            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showOfflinePage(view);
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    showOfflinePage(view);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mCustomView != null) { callback.onCustomViewHidden(); return; }
                mCustomView = view;
                mCustomViewCallback = callback;
                mOriginalOrientation = getRequestedOrientation();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                mFullscreenContainer = new FrameLayout(MainActivity.this);
                mFullscreenContainer.setBackgroundColor(0xFF000000);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                mFullscreenContainer.addView(view, lp);
                ((FrameLayout)getWindow().getDecorView()).addView(mFullscreenContainer, lp);
                applyFullscreen();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            @Override public void onHideCustomView() {
                if (mCustomView == null) return;
                mCustomView.setVisibility(View.GONE);
                if (mFullscreenContainer != null) {
                    ((FrameLayout)getWindow().getDecorView()).removeView(mFullscreenContainer);
                    mFullscreenContainer = null;
                }
                mCustomView = null;
                if (mCustomViewCallback != null) { mCustomViewCallback.onCustomViewHidden(); mCustomViewCallback = null; }
                setRequestedOrientation(mOriginalOrientation);
                applyFullscreen();
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { callback.invoke(origin,true,false); }
            @Override public void onPermissionRequest(final PermissionRequest request) { runOnUiThread(new Runnable(){@Override public void run(){request.grant(request.getResources());}}); }
        });
    }
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.net.Network net = cm.getActiveNetwork();
            if (net == null) return false;
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            //noinspection deprecation
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }
    private String getIconBase64() {
        try {
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos);
            return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }
    private void showOfflinePage(final WebView view) {
        runOnUiThread(new Runnable() { @Override public void run() {
            String appName = getString(R.string.app_name);
            String iconB64 = getIconBase64();
            String iconHtml = iconB64.isEmpty()
                ? "<div class='emoji'>&#128241;</div>"
                : "<img src='data:image/png;base64," + iconB64 + "' class='appicon'/>";
            String html = "<!DOCTYPE html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "*{margin:0;padding:0;box-sizing:border-box}"
                + "body{background:#111;color:#eee;font-family:sans-serif;"
                + "display:flex;flex-direction:column;align-items:center;"
                + "justify-content:center;min-height:100vh;text-align:center;padding:28px}"
                + ".appicon{width:88px;height:88px;border-radius:20px;margin-bottom:22px;"
                + "box-shadow:0 4px 24px rgba(0,0,0,0.5)}"
                + ".emoji{font-size:72px;margin-bottom:22px}"
                + "h2{font-size:22px;font-weight:700;margin-bottom:10px}"
                + "p{color:#888;max-width:260px;line-height:1.7;font-size:14px}"
                + "button{margin-top:28px;padding:13px 40px;background:#fff;color:#111;"
                + "border:none;border-radius:12px;font-size:15px;font-weight:700;"
                + "cursor:pointer;transition:opacity 0.2s}"
                + "button:active{opacity:0.7}"
                + ".checking{margin-top:14px;font-size:12px;color:#555;min-height:18px}"
                + "</style></head><body>"
                + iconHtml
                + "<h2>" + appName + "</h2>"
                + "<p>Connect to WiFi or mobile data to access " + appName + ".</p>"
                + "<button onclick='OfflineBridge.retry()'>&#8635;&nbsp; Retry</button>"
                + "<div class='checking' id='msg'></div>"
                + "</body></html>";
            view.addJavascriptInterface(new OfflineBridge(view), "OfflineBridge");
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        }});
    }
    class OfflineBridge {
        private final WebView wv;
        OfflineBridge(WebView wv) { this.wv = wv; }
        @android.webkit.JavascriptInterface
        public void retry() {
            runOnUiThread(new Runnable() { @Override public void run() {
                wv.evaluateJavascript("document.getElementById('msg').textContent='Checking connection...';", null);
            }});
            new Handler().postDelayed(new Runnable() { @Override public void run() {
                if (isOnline()) {
                    runOnUiThread(new Runnable() { @Override public void run() {
                        wv.loadUrl(WEBSITE_URL);
                    }});
                } else {
                    runOnUiThread(new Runnable() { @Override public void run() {
                        wv.evaluateJavascript("document.getElementById('msg').textContent='No connection. Try again.';", null);
                        new Handler().postDelayed(new Runnable() { @Override public void run() {
                            wv.evaluateJavascript("document.getElementById('msg').textContent='';", null);
                        }}, 2500);
                    }});
                }
            }}, 600);
        }
    }
    private void promptForAdvancedPermissions() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app utilizes advanced features that require device permissions (like Location, Storage, Contacts, etc). Please enable them in your App Settings.")
            .setCancelable(false)
            .setPositiveButton("Allow / Open Settings", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            })
            .setNegativeButton("Later", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            })
            .show();
    }
    private void trackAppOpen(){
        SharedPreferences p=getSharedPreferences(TRACK_PREFS,MODE_PRIVATE);
        int total=p.getInt("total_opens",0)+1;
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());
        p.edit().putInt("total_opens",total).putString("last_open",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date())).putString("first_install",p.getString("first_install",today)).apply();
    }
    class CredentialBridge {
        @JavascriptInterface public void save(String email,String password){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("email",email).putString("password",android.util.Base64.encodeToString(password.getBytes(),android.util.Base64.NO_WRAP)).apply();}
        @JavascriptInterface public String getEmail(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString("email","");}
        @JavascriptInterface public String getPassword(){String enc=getSharedPreferences(PREFS,MODE_PRIVATE).getString("password","");if(enc.isEmpty())return "";try{return new String(android.util.Base64.decode(enc,android.util.Base64.NO_WRAP));}catch(Exception e){return enc;}}
    }
    private void loadWebsite(){
        if (isOnline()) { webView.loadUrl(WEBSITE_URL); } else { showOfflinePage(webView); }
    }
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen();
    }
    @Override protected void onResume() {
        super.onResume();
        applyFullscreen();
        if (webView != null) webView.onResume();
    }
    @Override protected void onPause(){super.onPause();if(webView!=null)webView.onPause();}
    @Override protected void onDestroy() {
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}