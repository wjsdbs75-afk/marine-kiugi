package io.github.wjsdbs75afk.marinekiugi;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

/**
 * 게임 본체는 GitHub Pages 에 있고, 이 앱은 그 페이지를 화면 가득 띄우는 껍데기다.
 *
 * 켤 때마다 하는 일:
 *   1. 사이트의 version.json 을 본다 (배포할 때마다 내용이 바뀌는 작은 파일).
 *   2. 지난번에 받은 것과 다르면 게임 페이지를 통째로 새로 받아 기기에 저장한다.
 *      다 받은 뒤에야 옛 사본과 바꾸므로, 받다가 끊겨도 옛 사본은 그대로 남는다.
 *   3. 저장해 둔 사본을 원래 주소(https://…github.io/…) 그대로 화면에 띄운다.
 *      주소가 같으니 게임 저장(localStorage)도 그대로 이어진다.
 *
 * 그래서 게임을 고쳐서 올린 뒤 앱을 껐다 켜면 새 버전이 뜨고, 인터넷이 없으면 마지막으로 받은 버전이 뜬다.
 * 뒤로 가기는 페이지의 window.__mkBack() 에 맡기고, 닫을 것이 없다고 하면(false) 앱을 끝낸다.
 */
public class MainActivity extends Activity {
    private static final String GAME_URL = BuildConfig.GAME_URL;
    private static final String PREF_SITE_VERSION = "siteVersion";

    private static final Object UPDATE_LOCK = new Object();   // 받기는 한 번에 하나만
    private static long lastRendererGoneAt;   // 웹 화면이 연달아 죽을 때 무한 재시작을 막는 데 쓴다

    private File gameFile;                // 기기에 저장해 둔 게임 페이지
    private FrameLayout root;
    private WebView web;
    private WebView loadWeb;              // 확인·받는 동안 띄우는 화면 (assets/loading.html, 게임 안의 로딩 화면과 같은 모양)
    private String loadText = "";         // 로딩 화면에 마지막으로 보낸 글자와 비율 (화면이 늦게 뜨면 다시 보낸다)
    private float loadFrac = 0f;
    private long loadSentAt;
    private LinearLayout errorView;
    private TextView errorDetail;

    private volatile int safeTopPx;
    private volatile int safeBottomPx;

    private int startSeq;                 // 진행 중인 시작 시도 번호 (늦게 도착한 응답을 버리는 데 쓴다)
    private boolean mainFrameFailed;
    private boolean pageReady;            // 게임 페이지가 떠서 __mkBack 을 부를 수 있는 상태

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameFile = new File(getNoBackupFilesDir(), "game.html");
        setUpWindow();
        buildViews();
        setContentView(root);
        hideSystemBars();   // 화면(DecorView)이 생긴 뒤에 불러야 한다. 그 전에 부르면 안드로이드 11 이상에서 앱이 죽는다
        if (Build.VERSION.SDK_INT >= 33) {
            Api33.registerBack(this, this::handleBack);
        }
        start();
    }

    // ------------------------------------------------------------------ 창과 화면

    /**
     * 게임 화면을 상태 바와 내비게이션 바 뒤까지 깔고, 꺼지지 않게 한다.
     * 상태 바(시계·배터리)와 하단 내비게이션 바(뒤로·홈·최근)는 몰입 모드로 숨겨 게임이 화면 전체를 덮는다.
     * 위·아래에서 쓸어 넘기면 잠깐 나타났다 사라진다. 카메라 구멍(노치) 높이만큼은 게임이 UI 를 민다.
     */
    private void setUpWindow() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 상태 바를 처음부터 숨긴다 (옛 방식. 안드로이드 11 이상은 hideSystemBars 의 InsetsController 가 숨긴다)
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        }
    }

    /** 상태 바와 내비게이션 바를 숨긴다. 다이얼로그·키보드·다른 앱에 다녀오면 시스템이 다시 보이게 할 수 있어 돌아올 때마다 부른다. */
    private void hideSystemBars() {
        try {
            hideSystemBarsUnchecked();
        } catch (RuntimeException e) {
            // 바를 못 숨겨도 게임은 그대로 뜨게 한다
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBarsUnchecked() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            Api30.hideSystemBars(w);
        } else {
            // LAYOUT_STABLE 은 넣지 않는다. 넣으면 숨긴 바의 높이까지 여백으로 잡혀 게임이 맨 아래까지 내려오지 않는다
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private void buildViews() {
        int night = getColor(R.color.night);
        FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        root = new FrameLayout(this);
        root.setBackgroundColor(night);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                int[] tb = Api30.topBottom(insets);
                top = tb[0];
                bottom = tb[1];
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {   // 상태 바를 숨겨도 카메라 구멍은 피한다
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                    bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            if (top != safeTopPx || bottom != safeBottomPx) {
                safeTopPx = top;
                safeBottomPx = bottom;
                pushInsets();
            }
            return insets;
        });

        web = new WebView(this);
        web.setBackgroundColor(night);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(v -> true);   // 길게 눌러도 글자 선택 메뉴가 뜨지 않게

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);            // 게임 저장(localStorage)
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);                      // 기기의 글자 크기 설정이 게임 배치를 흐트러뜨리지 않게
        s.setSupportZoom(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.addJavascriptInterface(new Shell(), "MKShell");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new Client());
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        root.addView(web, fill);

        loadWeb = new WebView(this);
        loadWeb.setBackgroundColor(night);
        loadWeb.setOverScrollMode(View.OVER_SCROLL_NEVER);
        loadWeb.setVerticalScrollBarEnabled(false);
        loadWeb.setHorizontalScrollBarEnabled(false);
        loadWeb.setOnLongClickListener(v -> true);
        WebSettings ls = loadWeb.getSettings();
        ls.setJavaScriptEnabled(true);
        ls.setTextZoom(100);
        ls.setSupportZoom(false);
        ls.setAllowContentAccess(false);
        loadWeb.addJavascriptInterface(new Shell(), "MKShell");
        loadWeb.setWebViewClient(new LoadClient());
        loadWeb.loadUrl("file:///android_asset/loading.html");
        root.addView(loadWeb, fill);

        errorView = column(true);
        errorView.setVisibility(View.GONE);
        TextView title = label(R.color.fg, 20);
        title.setText(R.string.error_title);
        errorView.addView(title, wrapParams(0));
        errorDetail = label(R.color.fg_dim, 14);
        errorView.addView(errorDetail, wrapParams(dp(10)));
        Button retry = new Button(this);
        retry.setText(R.string.error_retry);
        retry.setOnClickListener(v -> start());
        errorView.addView(retry, wrapParams(dp(24)));
        root.addView(errorView, fill);
    }

    /** 가운데 정렬 세로 묶음. opaque 면 뒤의 화면을 가리고 터치도 막는다. */
    private LinearLayout column(boolean opaque) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(32), dp(32), dp(32), dp(32));
        if (opaque) {
            l.setBackgroundColor(getColor(R.color.night));
            l.setClickable(true);
        }
        return l;
    }

    private TextView label(int colorRes, int sp) {
        TextView t = new TextView(this);
        t.setTextColor(getColor(colorRes));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout.LayoutParams wrapParams(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private int dp(int v) {
        return Math.round(v * density());
    }

    // ------------------------------------------------------------------ 새 버전 확인과 불러오기

    /** 새 배포가 있으면 받아 두고, 기기에 있는 사본을 띄운다. */
    private void start() {
        final int seq = ++startSeq;
        pageReady = false;
        mainFrameFailed = false;
        errorView.setVisibility(View.GONE);
        showLoading(getString(R.string.loading_check), 0f);
        if (web != null) {
            web.setVisibility(View.INVISIBLE);   // 다 뜬 뒤에 보여 준다
        }
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        final File file = gameFile;
        new Thread(() -> {
            String problem = null;
            synchronized (UPDATE_LOCK) {
                String site = fetchSiteVersion();   // null: 서버에 닿지 못함, "": version.json 이 없음
                if (site == null) {
                    problem = getString(R.string.error_offline);
                } else if (!file.isFile() || site.isEmpty()
                        || !site.equals(prefs.getString(PREF_SITE_VERSION, null))) {
                    final String label = getString(file.isFile() ? R.string.loading_update : R.string.loading_first);
                    final long size = siteSize(site);   // 받을 페이지 크기 (모르면 0)
                    runOnUiThread(() -> {
                        if (seq == startSeq) {
                            showLoading(label, 0f);
                        }
                    });
                    problem = download(file, got -> {
                        if (size <= 0) {
                            return;
                        }
                        long now = SystemClock.elapsedRealtime();
                        if (now - loadSentAt < 80 && got < size) {
                            return;
                        }
                        loadSentAt = now;
                        final float f = Math.min(1f, got / (float) size);
                        runOnUiThread(() -> {
                            if (seq == startSeq) {
                                showLoading(label, f);
                            }
                        });
                    });
                    if (problem == null) {
                        prefs.edit().putString(PREF_SITE_VERSION, site).commit();
                    }
                }
            }
            final String detail = problem;
            runOnUiThread(() -> {
                if (seq != startSeq || web == null || isFinishing() || isDestroyed()) {
                    return;
                }
                if (file.isFile()) {
                    web.loadUrl(GAME_URL);   // Client.shouldInterceptRequest 가 저장해 둔 사본을 내준다
                } else {
                    showError(detail);
                }
            });
        }, "site-update").start();
    }

    /** 배포할 때마다 바뀌는 작은 파일(version.json)의 내용. 파일이 없으면 "", 서버에 닿지 못했으면 null. */
    private static String fetchSiteVersion() {
        HttpURLConnection c = null;
        try {
            c = open(new URL(new URL(GAME_URL), "version.json?t=" + System.currentTimeMillis()), 4000, 4000);
            if (c.getResponseCode() != 200) {
                return "";
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 4096) {
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    /** version.json 의 size (게임 페이지 바이트 수). 없거나 읽지 못하면 0. */
    private static long siteSize(String site) {
        try {
            return new JSONObject(site).optLong("size", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 받는 중에 지금까지 받은 바이트 수를 알려 준다 (받는 스레드에서 불린다). */
    private interface Progress {
        void got(long bytes);
    }

    /**
     * 게임 페이지를 받아 dest 에 저장한다. 임시 파일에 다 받은 뒤 이름을 바꾸므로 실패해도 기존 dest 는 그대로다.
     * 성공하면 null, 실패하면 화면에 보여 줄 이유.
     */
    private static String download(File dest, Progress progress) {
        File tmp = new File(dest.getPath() + ".tmp");
        HttpURLConnection c = null;
        try {
            c = open(new URL(GAME_URL + "?t=" + System.currentTimeMillis()), 8000, 20000);
            int code = c.getResponseCode();
            if (code != 200) {
                return GAME_URL + "\nHTTP " + code;
            }
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                    progress.got(total);
                }
            }
            if (!looksComplete(tmp)) {
                return "받은 페이지가 온전하지 않습니다.";
            }
            if (!tmp.renameTo(dest)) {
                return "받은 페이지를 저장하지 못했습니다.";
            }
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        } finally {
            if (c != null) {
                c.disconnect();
            }
            if (tmp.exists()) {
                tmp.delete();
            }
        }
    }

    private static HttpURLConnection open(URL url, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        return c;
    }

    /** 끝까지 받은 HTML 인지: 내용이 있고 닫는 태그로 끝나야 한다. */
    private static boolean looksComplete(File f) {
        long len = f.length();
        if (len < 1024) {
            return false;
        }
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] tail = new byte[64];
            r.seek(len - tail.length);
            r.readFully(tail);
            return new String(tail, StandardCharsets.ISO_8859_1).toLowerCase(Locale.US).contains("</html>");
        } catch (Exception e) {
            return false;
        }
    }

    /** 로딩 화면을 보이고 글자와 막대를 바꾼다. */
    private void showLoading(String text, float frac) {
        loadText = text;
        loadFrac = frac;
        if (loadWeb == null) {
            return;
        }
        loadWeb.animate().cancel();
        loadWeb.setAlpha(1f);
        loadWeb.setVisibility(View.VISIBLE);
        sendLoading();
    }

    private void sendLoading() {
        if (loadWeb != null) {
            loadWeb.evaluateJavascript(String.format(Locale.US, "window.mkLoad&&mkLoad(%s,%.4f)",
                    JSONObject.quote(loadText), loadFrac), null);
        }
    }

    /** 게임이 떴다: 로딩 화면을 걷어 낸다. 밑에서 게임의 로딩 화면이 같은 모양으로 이어진다. */
    private void hideLoading() {
        if (loadWeb != null && loadWeb.getVisibility() == View.VISIBLE) {
            loadWeb.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                if (loadWeb != null) {
                    loadWeb.setVisibility(View.GONE);
                }
            });
        }
    }

    /** 로딩 화면 자체의 웹 화면. 다 뜨면 마지막 상태를 다시 보낸다. */
    private final class LoadClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            pushInsets();
            sendLoading();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true;   // 로딩 화면에서는 어디로도 가지 않는다
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // 로딩 화면만 없애고 게임은 그대로 진행한다 (게임 화면 쪽은 Client 가 처리한다)
            if (loadWeb != null) {
                root.removeView(loadWeb);
                loadWeb.destroy();
                loadWeb = null;
            }
            return true;
        }
    }

    private void showError(String detail) {
        pageReady = false;
        if (loadWeb != null) {
            loadWeb.setVisibility(View.GONE);
        }
        if (web != null) {
            web.setVisibility(View.INVISIBLE);
        }
        errorDetail.setText(detail == null ? "" : detail);
        errorView.setVisibility(View.VISIBLE);
    }

    private final class Client extends WebViewClient {
        private final Uri game = Uri.parse(GAME_URL);

        /** 게임 페이지 자체를 요청하면 네트워크 대신 기기에 저장해 둔 사본을 내준다. */
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (!request.isForMainFrame() || !"GET".equalsIgnoreCase(request.getMethod())
                    || !sameHost(u) || !samePath(u.getPath(), game.getPath())) {
                return null;
            }
            try {
                Map<String, String> headers = new HashMap<>();
                headers.put("Cache-Control", "no-store");
                return new WebResourceResponse("text/html", "utf-8", 200, "OK", headers, new FileInputStream(gameFile));
            } catch (Exception e) {
                return null;   // 사본이 없으면 평소처럼 네트워크에서 받는다
            }
        }

        private boolean sameHost(Uri u) {
            return "https".equalsIgnoreCase(u.getScheme())
                    && u.getHost() != null && u.getHost().equalsIgnoreCase(game.getHost());
        }

        private boolean samePath(String a, String b) {
            return strip(a).equals(strip(b));
        }

        private String strip(String path) {
            String p = path == null ? "" : path;
            if (p.endsWith("index.html")) {
                p = p.substring(0, p.length() - "index.html".length());
            }
            while (p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            return p;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (sameHost(request.getUrl())) {
                return false;
            }
            try {   // 게임 밖으로 나가는 링크는 브라우저로 넘긴다
                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
            } catch (Exception ignored) {
                // 열 수 있는 앱이 없으면 아무것도 하지 않는다
            }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                mainFrameFailed = true;
                showError(String.valueOf(error.getDescription()));
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame()) {
                mainFrameFailed = true;
                showError(GAME_URL + "\nHTTP " + response.getStatusCode());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            Uri u = Uri.parse(String.valueOf(error.getUrl()));
            if (sameHost(u) && samePath(u.getPath(), game.getPath())) {
                mainFrameFailed = true;
                showError(GAME_URL + "\n보안 연결(TLS) 오류");
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (mainFrameFailed || web == null) {
                return;
            }
            final int seq = startSeq;
            pushInsets();
            view.evaluateJavascript("typeof window.__mkBack === 'function'", value -> {
                if (seq != startSeq || web == null || mainFrameFailed) {
                    return;
                }
                web.setVisibility(View.VISIBLE);
                hideLoading();
                pageReady = "true".equals(value);
                if (!pageReady) {
                    // 게임이 아닌 것이 떴다. 다음에 켤 때 다시 받도록 "받았다"는 표시를 지운다
                    getPreferences(MODE_PRIVATE).edit().remove(PREF_SITE_VERSION).apply();
                }
            });
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // 메모리가 모자라 웹 화면이 죽은 경우: 앱이 같이 죽지 않게 화면을 새로 만든다
            if (web != null) {
                root.removeView(web);
                web.destroy();
                web = null;
            }
            pageReady = false;
            long now = SystemClock.elapsedRealtime();
            boolean again = lastRendererGoneAt != 0 && now - lastRendererGoneAt < 15000;
            lastRendererGoneAt = now;
            if (again) {
                finish();      // 켜자마자 또 죽었다. 계속 되살리지 않고 끝낸다
            } else {
                recreate();
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ 시스템 바 높이 전달

    /** 페이지가 시작될 때 읽어 가는 값 (index.html 머리의 스크립트가 부른다). 단위는 CSS px. */
    public final class Shell {
        /** 설치된 앱(껍데기) 버전. 게임 설정 화면에 함께 보여 준다. */
        @JavascriptInterface
        public String appVersion() {
            return "안드로이드 " + BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public float safeTop() {
            return safeTopPx / density();
        }

        @JavascriptInterface
        public float safeBottom() {
            return safeBottomPx / density();
        }
    }

    /** 값이 나중에 정해지거나 바뀐 경우 페이지에 다시 알려 준다. 게임은 resize 때 이 값을 다시 읽는다. */
    private void pushInsets() {
        String js = String.format(Locale.US,
                "(function(){var r=document.documentElement;if(!r)return;"
                        + "r.style.setProperty('--safe-top','%.2fpx');"
                        + "r.style.setProperty('--safe-bottom','%.2fpx');"
                        + "window.dispatchEvent(new Event('resize'));})()",
                safeTopPx / density(), safeBottomPx / density());
        if (web != null) {
            web.evaluateJavascript(js, null);
        }
        if (loadWeb != null) {
            loadWeb.evaluateJavascript(js, null);
        }
    }

    // ------------------------------------------------------------------ 뒤로 가기

    private void handleBack() {
        if (web == null || !pageReady || errorView.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }
        web.evaluateJavascript(
                "(function(){try{return typeof window.__mkBack==='function'&&!!window.__mkBack()}catch(e){return false}})()",
                value -> {
                    if (!"true".equals(value)) {
                        finish();
                    }
                });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {   // 안드로이드 12 이하
        handleBack();
    }

    /** 새 기기 전용 API 는 옛 기기에서 읽히지 않도록 따로 떼어 둔다. */
    private static final class Api30 {
        static int[] topBottom(WindowInsets insets) {
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[] {i.top, i.bottom};
        }

        /** 숨긴 상태 바·내비게이션 바는 쓸어 넘길 때만 잠깐 화면 위에 겹쳐 나온다 (게임 배치는 움직이지 않는다). */
        static void hideSystemBars(Window w) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) {
                c = w.getDecorView().getWindowInsetsController();
            }
            if (c == null) {
                return;
            }
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsets.Type.systemBars());
        }
    }

    /** 안드로이드 13 이상의 뒤로 가기 등록. */
    private static final class Api33 {
        static void registerBack(Activity activity, Runnable onBack) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBack::run);
        }
    }

    // ------------------------------------------------------------------ 생명 주기

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (web != null) {
            web.onPause();   // 페이지가 "숨겨짐"이 되어 게임이 스스로 일시정지하고 음악을 멈춘다
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        startSeq++;
        if (loadWeb != null) {
            root.removeView(loadWeb);
            loadWeb.destroy();
            loadWeb = null;
        }
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
