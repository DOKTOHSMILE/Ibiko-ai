package ai.ibiko.social.campaign;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A campaign post's picture: the HTML the brain designed, drawn once in a hidden 1080 by 1080 page and kept as a JPEG.
 * The page reaches only public font and library CDNs (as model-made pages do in the viewer), never any other address,
 * and runs no script (the planner took it out). Call {@link #render} off the main thread.
 */
public final class CampaignImage {
    public static final int SIDE = 1080;
    /** How long a page may take to load and draw. */
    static final long WAIT_MS = 20_000;
    /** After the page says it loaded: a moment for web fonts to arrive and the layout to settle. */
    static final long SETTLE_MS = 900;

    private CampaignImage() {
    }

    /** Draws {@code html} into {@code out} (a JPEG); the file, or null when the page couldn't be drawn. */
    public static File render(Context context, String html, File out) {
        return render(context, html, out, SIDE, SIDE);
    }

    /** As above, {@code width} by {@code height} pixels (a story, a banner). */
    public static File render(Context context, final String html, File out, final int width, final int height) {
        return drawn(context, html, out, width, height).file;
    }

    /** A picture drawn, and what was still wrong with its layout after it was put right (null when nothing). */
    public static final class Drawn {
        public final File file;
        /** "words overlap", "words off the picture", "words hard to read"; null for a clean picture. */
        public final String problem;

        Drawn(File file, String problem) {
            this.file = file;
            this.problem = problem;
        }
    }

    /**
     * Strict about the words, whoever designed the page: once it has drawn, every piece of text is measured. Words running
     * off the picture are made smaller until they fit; shapes are put behind the words; words that collide are made
     * smaller apart; then the drawn picture itself is read under each piece of text, and words that don't stand out
     * from what is behind them (yellow on yellow, white on a pale sky) are set in white or near-black with a soft halo.
     * What still can't be put right is said in {@link Drawn#problem}, so a design that fails can be replaced.
     */
    public static Drawn drawn(Context context, final String html, File out, final int width, final int height) {
        if (html == null || html.isEmpty()) {
            return new Drawn(null, null);
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("drawing waits for the page: call it off the main thread");
        }
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final CountDownLatch done = new CountDownLatch(1);
        final Bitmap[] shot = {null};
        final String[] problem = {null};
        final WebView[] holder = {null};
        main.post(() -> {
            try {
                slowDraw();
                WebView w = new WebView(app);
                w.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null); // drawn off screen, into a bitmap
                holder[0] = w;
                WebSettings s = w.getSettings();
                // the page's own scripts were taken out (CampaignPlanner.picture); script is on only for the layout check
                s.setJavaScriptEnabled(true);
                s.setAllowFileAccess(false);
                s.setAllowContentAccess(false);
                s.setDomStorageEnabled(false);
                w.setVerticalScrollBarEnabled(false);
                w.setHorizontalScrollBarEnabled(false);
                w.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
                w.layout(0, 0, width, height);
                w.setInitialScale(100);
                w.setWebViewClient(new WebViewClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        String scheme = request.getUrl().getScheme();
                        if ("data".equals(scheme) || "about".equals(scheme)
                                || ai.ibiko.chat.ui.HtmlViewActivity.libraryRequest(request)) {
                            return null;
                        }
                        return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null,
                                new java.io.ByteArrayInputStream(new byte[0]));
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return true; // the picture never navigates
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        main.postDelayed(() -> view.evaluateJavascript(Layout.CHECK, found -> main.postDelayed(() -> {
                            Layout.Report rep = Layout.Report.of(found);
                            Bitmap b = capture(view, width, height);
                            if (b == null) {
                                done.countDown();
                                return;
                            }
                            String dull = Layout.dull(b, rep);
                            if (dull.isEmpty()) {
                                shot[0] = b;
                                problem[0] = rep.problem();
                                done.countDown();
                                return;
                            }
                            // words that don't stand out: set in white or near-black, then drawn again and read again
                            b.recycle();
                            view.evaluateJavascript(Layout.legible(dull), ignored -> main.postDelayed(() -> {
                                Bitmap again = capture(view, width, height);
                                if (again != null) {
                                    shot[0] = again;
                                    String still = Layout.dull(again, rep);
                                    problem[0] = rep.problem() != null ? rep.problem() : still.isEmpty() ? null : "words hard to read";
                                }
                                done.countDown();
                            }, Layout.REDRAW_MS));
                        }, Layout.REDRAW_MS)), SETTLE_MS);
                    }
                });
                w.loadDataWithBaseURL("https://ibiko.local/", fit(html, width, height), "text/html", "utf-8", null);
            } catch (RuntimeException e) {
                done.countDown();
            }
        });
        try {
            done.await(WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        main.post(() -> {
            if (holder[0] != null) {
                holder[0].stopLoading();
                holder[0].destroy();
            }
        });
        Bitmap b = shot[0];
        if (b == null) {
            return new Drawn(null, null);
        }
        try {
            File dir = out.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            File part = new File(out.getPath() + ".part");
            try (FileOutputStream o = new FileOutputStream(part)) {
                b.compress(Bitmap.CompressFormat.JPEG, 88, o); // small enough to attach, sharp enough for a feed
                o.getFD().sync();
            }
            if (!part.renameTo(out)) {
                part.delete();
                return new Drawn(null, null);
            }
            return new Drawn(out, problem[0]);
        } catch (IOException e) {
            return new Drawn(null, null);
        } finally {
            b.recycle();
        }
    }

    /**
     * A brain's reply made into a picture page: the HTML document taken out of any prose or code fence, without
     * scripts, frames, forms or event handlers. Empty when the reply holds no page.
     */
    public static String page(String reply) {
        String r = reply == null ? "" : reply.replaceAll("(?s)<think>.*?(</think>|$)", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)(<!doctype html.*?</html>|<html.*?</html>)").matcher(r);
        String h = m.find() ? m.group(1) : r.replaceAll("(?s)^.*?```(?:html)?\\s*", "").replaceAll("(?s)```.*$", "");
        return CampaignPlanner.picture(h);
    }

    /** The page as it is drawn now, or null when it drew nothing (a page that never painted, not a white square). */
    static Bitmap capture(WebView view, int width, int height) {
        try {
            Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(0xFFFFFFFF); // a page with no background draws on white, not black
            view.draw(c);
            if (blank(b)) {
                b.recycle();
                return null;
            }
            return b;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static boolean slow;

    /** WebView draws only what is on screen unless this is set before the first one is made. */
    private static synchronized void slowDraw() {
        if (!slow) {
            slow = true;
            try {
                WebView.enableSlowWholeDocumentDraw();
            } catch (RuntimeException ignored) {
                // a WebView was made already: off-screen drawing may then come out blank, which blank() catches
            }
        }
    }

    /** Whether the picture is one colour throughout (sampled on a grid): the page didn't draw. */
    static boolean blank(Bitmap b) {
        int first = b.getPixel(0, 0);
        int step = Math.max(1, b.getWidth() / 24);
        for (int y = 0; y < b.getHeight(); y += step) {
            for (int x = 0; x < b.getWidth(); x += step) {
                if (b.getPixel(x, y) != first) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The page held to the picture's square: a viewport of exactly its width, no margin or scrolling. */
    static String fit(String html) {
        return fit(html, SIDE, SIDE);
    }

    static String fit(String html, int width, int height) {
        String head = "<meta name=\"viewport\" content=\"width=" + width + ", initial-scale=1\">"
                + "<style>html,body{margin:0;width:" + width + "px;height:" + height + "px;overflow:hidden}</style>";
        int at = html.toLowerCase(java.util.Locale.US).indexOf("<head>");
        return at >= 0 ? html.substring(0, at + 6) + head + html.substring(at + 6) : head + html;
    }
}
