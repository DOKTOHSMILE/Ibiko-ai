package ai.ibiko.social.campaign;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The layout check a post's picture goes through before it is kept (see {@link CampaignImage#drawn}): the page measures
 * its own words and puts right what it can (words off the picture made smaller, shapes put behind the words, words that
 * collide made smaller apart); then the drawn picture is read under each piece of text, and words that don't stand out
 * from what is behind them are set in white or near-black. The contrast arithmetic is WCAG's, pure Java.
 */
final class Layout {
    private Layout() {
    }

    /** How long the page is given to draw again after a change. */
    static final long REDRAW_MS = 350;

    /** Large text (a headline) needs 3:1 against what is behind it (WCAG AA for large text). */
    static final double LEAST = 3.0;

    /**
     * Run in the page once it has drawn. Finds every piece of text (an element holding words of its own, not an inline
     * part of one), puts the shapes behind the words, shrinks words that run off the picture or collide, and returns
     * {"boxes":[{x,y,w,h,c}],"overlap":bool,"off":bool} with each piece's place and colour (c as "r,g,b").
     */
    static final String CHECK = "(function(){try{"
            + "var W=1080,H=1080;var de=document.documentElement;if(de.clientWidth)W=de.clientWidth;if(window.innerHeight)H=window.innerHeight;"
            + "var all=[].slice.call(document.body.querySelectorAll('*'));"
            + "function own(e){for(var n=e.firstChild;n;n=n.nextSibling){if(n.nodeType==3&&n.textContent.trim().length)return true;}return false;}"
            + "function shown(e){var s=getComputedStyle(e);return s.display!='none'&&s.visibility!='hidden'&&parseFloat(s.opacity||'1')>0.05;}"
            + "var skip={STYLE:1,SCRIPT:1,TITLE:1,HEAD:1,META:1,LINK:1};"
            + "var t=all.filter(function(e){return !skip[e.tagName]&&own(e)&&shown(e);});"
            // an inline part of a piece of text (the lit word in a headline) is that piece, not one of its own
            + "t=t.filter(function(e){if(getComputedStyle(e).display.indexOf('inline')!=0)return true;var p=e.parentElement;"
            + "while(p&&p!==document.body){if(t.indexOf(p)>=0)return false;p=p.parentElement;}return true;});"
            + "window.__ibkT=t;"
            // shapes: whatever holds no words and isn't a whole-picture backdrop goes behind the words
            + "all.forEach(function(e){if(skip[e.tagName]||e.textContent.trim().length)return;var r=e.getBoundingClientRect();"
            + "if(r.width*r.height>W*H*0.9)return;var s=getComputedStyle(e);if(s.position=='static')return;e.style.zIndex='0';});"
            + "t.forEach(function(e){var s=getComputedStyle(e);if(s.position=='static'){e.style.position='relative';}e.style.zIndex='5';"
            + "var p=e.parentElement;while(p&&p!==document.body){var ps=getComputedStyle(p);if(ps.position!='static'&&(ps.zIndex=='auto'||parseInt(ps.zIndex)<5))p.style.zIndex='5';p=p.parentElement;}"
            + "e.style.overflowWrap='break-word';});"
            + "function out(e){var r=e.getBoundingClientRect();return r.left<-2||r.top<-2||r.right>W+2||r.bottom>H+2||e.scrollWidth>e.clientWidth+4;}"
            + "function smaller(e){var f=parseFloat(getComputedStyle(e).fontSize)||16;e.style.fontSize=(f*0.9)+'px';}"
            + "var off=false;t.forEach(function(e){for(var i=0;i<9&&out(e);i++)smaller(e);if(out(e))off=true;});"
            + "function hit(a,b){var r=a.getBoundingClientRect(),q=b.getBoundingClientRect();"
            + "var x=Math.max(0,Math.min(r.right,q.right)-Math.max(r.left,q.left)),y=Math.max(0,Math.min(r.bottom,q.bottom)-Math.max(r.top,q.top));"
            + "var least=Math.min(r.width*r.height,q.width*q.height);return least>0&&x*y>least*0.06;}"
            + "var overlap=false;for(var i=0;i<t.length;i++)for(var j=i+1;j<t.length;j++){var a=t[i],b=t[j];"
            + "if(a.contains(b)||b.contains(a))continue;for(var k=0;k<6&&hit(a,b);k++){smaller(a);smaller(b);}if(hit(a,b))overlap=true;}"
            + "var boxes=t.map(function(e){var r=e.getBoundingClientRect();var c=getComputedStyle(e).color.match(/\\d+(\\.\\d+)?/g)||[0,0,0];"
            + "return{x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height),c:c[0]+','+c[1]+','+c[2]};});"
            + "return JSON.stringify({boxes:boxes,overlap:overlap,off:off});}catch(x){return JSON.stringify({boxes:[],overlap:false,off:false});}})()";

    /** What the page said about its words. */
    static final class Report {
        final List<int[]> boxes = new ArrayList<int[]>(); // x, y, w, h, colour (0xRRGGBB)
        boolean overlap, off;

        /** Reads the page's answer (a JSON string, itself quoted as the WebView returns it); empty when unreadable. */
        static Report of(String found) {
            Report r = new Report();
            if (found == null || found.isEmpty() || "null".equals(found)) {
                return r;
            }
            try {
                String json = found.startsWith("\"") ? new org.json.JSONTokener(found).nextValue().toString() : found;
                org.json.JSONObject o = new org.json.JSONObject(json);
                r.overlap = o.optBoolean("overlap");
                r.off = o.optBoolean("off");
                org.json.JSONArray a = o.optJSONArray("boxes");
                for (int i = 0; a != null && i < a.length(); i++) {
                    org.json.JSONObject b = a.getJSONObject(i);
                    String[] c = b.optString("c", "0,0,0").split(",");
                    int rgb = 0;
                    for (int k = 0; k < 3 && k < c.length; k++) {
                        rgb = rgb << 8 | Math.max(0, Math.min(255, (int) Double.parseDouble(c[k].trim())));
                    }
                    r.boxes.add(new int[]{b.optInt("x"), b.optInt("y"), b.optInt("w"), b.optInt("h"), rgb});
                }
            } catch (org.json.JSONException | RuntimeException e) {
                return new Report();
            }
            return r;
        }

        /** What is still wrong with the words' places, or null. */
        String problem() {
            return overlap ? "words overlap" : off ? "words off the picture" : null;
        }
    }

    /**
     * The pieces of text that don't stand out from the picture behind them, as "index:d" (on a dark ground: set light)
     * or "index:l" (on a light ground: set dark), comma-separated; empty when every piece reads.
     */
    static String dull(Bitmap b, Report rep) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rep.boxes.size(); i++) {
            int[] box = rep.boxes.get(i);
            int[] samples = sample(b, box[0], box[1], box[2], box[3]);
            if (samples.length == 0) {
                continue;
            }
            int ground = ground(samples, box[4]);
            if (contrast(box[4], ground) < LEAST) {
                out.append(out.length() == 0 ? "" : ",").append(i).append(':').append(luminance(ground) < 0.4 ? 'd' : 'l');
            }
        }
        return out.toString();
    }

    /** The picture's colours on a grid over a box (clipped to the picture). */
    static int[] sample(Bitmap b, int x, int y, int w, int h) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(b.getWidth() - 1, x + w), y1 = Math.min(b.getHeight() - 1, y + h);
        if (x1 <= x0 || y1 <= y0) {
            return new int[0];
        }
        int cols = 24, rows = 12;
        int[] out = new int[cols * rows];
        int n = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int px = x0 + (x1 - x0) * c / (cols - 1), py = y0 + (y1 - y0) * r / (rows - 1);
                out[n++] = b.getPixel(px, py) & 0xFFFFFF;
            }
        }
        return out;
    }

    /**
     * What is behind the words: of the colours sampled over a piece of text, those not close to the text's own colour
     * (the strokes of the letters), their middle by brightness. The text's colour itself when every sample is close
     * (words on a ground of their own colour: the very case this catches).
     */
    static int ground(int[] samples, int text) {
        List<Integer> back = new ArrayList<Integer>();
        for (int s : samples) {
            if (distance(s, text) > 60) {
                back.add(s);
            }
        }
        if (back.size() < samples.length / 5) {
            return text; // almost all of it is the text's colour: the words sit on their own colour
        }
        back.sort((p, q) -> Double.compare(luminance(p), luminance(q)));
        return back.get(back.size() / 2);
    }

    /** How far apart two colours are (0–441). */
    static double distance(int a, int b) {
        int dr = (a >> 16 & 255) - (b >> 16 & 255), dg = (a >> 8 & 255) - (b >> 8 & 255), db = (a & 255) - (b & 255);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /** WCAG relative luminance of an 0xRRGGBB colour (0–1). */
    static double luminance(int rgb) {
        double r = channel(rgb >> 16 & 255), g = channel(rgb >> 8 & 255), b = channel(rgb & 255);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** WCAG contrast ratio between two colours (1–21). */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** The page script that sets each dull piece (as {@link #dull} lists them) in white or near-black, with a soft halo. */
    static String legible(String dull) {
        StringBuilder js = new StringBuilder("(function(){var t=window.__ibkT||[];");
        for (String part : dull.split(",")) {
            String[] p = part.split(":");
            if (p.length != 2 || !p[0].matches("\\d+")) {
                continue;
            }
            boolean onDark = "d".equals(p[1]);
            String color = onDark ? "#ffffff" : "#111111";
            String halo = onDark ? "0 2px 18px rgba(0,0,0,.65),0 0 4px rgba(0,0,0,.5)" : "0 2px 16px rgba(255,255,255,.75),0 0 4px rgba(255,255,255,.6)";
            js.append(String.format(Locale.US, "(function(e){if(!e)return;[e].concat([].slice.call(e.querySelectorAll('*'))).forEach(function(x){"
                    + "x.style.setProperty('color','%s','important');x.style.setProperty('-webkit-text-fill-color','%s','important');"
                    + "x.style.setProperty('text-shadow','%s','important');});})(t[%s]);", color, color, halo, p[0]));
        }
        return js.append("return true;})()").toString();
    }
}
