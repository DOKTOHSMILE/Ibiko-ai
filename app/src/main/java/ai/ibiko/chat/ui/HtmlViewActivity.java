package ai.ibiko.chat.ui;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.ibiko.R;

/**
 * Renders one HTML page the brain already wrote. JavaScript can run inside the page, but the page can't reach the
 * network (nothing it holds is sent anywhere) and has no bridge back into the app. A tapped link opens in the browser.
 */
public final class HtmlViewActivity extends AppCompatActivity {
    public static final String EXTRA_HTML = "html";
    /** A page too big for an Intent, in a file (read here, off nothing else). */
    public static final String EXTRA_FILE = "file";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_view);
        Ui.fitInsets(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0)); // below the status bar, above the navigation bar
        web = findViewById(R.id.html_web);
        TextView back = findViewById(R.id.html_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        if (web == null) {
            return;
        }
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,
                    android.webkit.WebResourceRequest request) {
                // a model-made page is shown, never a door out: no script, image or form reaches the network
                String scheme = request.getUrl().getScheme();
                if ("data".equals(scheme) || "about".equals(scheme) || "blob".equals(scheme)) {
                    return null;
                }
                if (libraryRequest(request)) {
                    return null; // a script, style or font from a public library CDN: the page's own charts and styles
                }
                return new android.webkit.WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null,
                        new java.io.ByteArrayInputStream(new byte[0]));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // a link the person taps opens in their own browser, where they see where it goes; the page can't
                // navigate itself away by script
                android.net.Uri u = request.getUrl();
                if (request.hasGesture() && ("https".equals(u.getScheme()) || "http".equals(u.getScheme()))) {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, u));
                    } catch (android.content.ActivityNotFoundException ignored) {
                        // no browser: the link stays where it is
                    }
                }
                return true;
            }
        });
        String html = getIntent().getStringExtra(EXTRA_HTML);
        String path = getIntent().getStringExtra(EXTRA_FILE);
        if (html == null && path != null) {
            try {
                html = new String(java.nio.file.Files.readAllBytes(new java.io.File(path).toPath()), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException | OutOfMemoryError e) {
                html = "<p>The page couldn't be opened.</p>";
            }
        }
        web.loadDataWithBaseURL("https://ibiko.local/", page(html), "text/html", "utf-8", null);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** The public library CDNs a page may load scripts, styles and fonts from. Nothing else is reached. */
    static final java.util.Set<String> LIBRARIES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "cdnjs.cloudflare.com", "cdn.jsdelivr.net", "unpkg.com", "cdn.tailwindcss.com", "code.jquery.com",
            "fonts.googleapis.com", "fonts.gstatic.com"));

    /**
     * A plain GET over https to a library CDN: a page can use Chart.js or Tailwind, but nothing it holds can be sent
     * anywhere else (a form, a fetch, an image address carrying the page's text to another server).
     */
    public static boolean libraryRequest(android.webkit.WebResourceRequest r) {
        android.net.Uri u = r.getUrl();
        return "GET".equalsIgnoreCase(r.getMethod()) && "https".equals(u.getScheme()) && u.getHost() != null
                && LIBRARIES.contains(u.getHost().toLowerCase(java.util.Locale.US));
    }

    private static String page(String html) {
        if (html == null) {
            return "";
        }
        return html;
    }
}
