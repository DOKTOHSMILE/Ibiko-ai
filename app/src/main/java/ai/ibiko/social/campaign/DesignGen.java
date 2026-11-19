package ai.ibiko.social.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Pictures made fresh by a formula, never a fixed template: from a seed it picks a colour harmony (a base hue and
 * its complementary, analogous or triadic partners, in a light or dark mood), a background (glowing mesh, gradient,
 * diagonal split, grid or dots), decoration (rings, blobs, sparkles, waves, bars, triangles, dot fields, each
 * placed, sized and turned at random), a layout (where the words sit: top, centre, bottom, a card, a split), and the
 * type (one of six web fonts, its weight and case, one word lit in the accent colour). Every post gets its own
 * seed, so no two pictures come out alike; the same seed gives the same picture. Text keeps its contrast whatever
 * the mood. Pure Java: an HTML page for {@link CampaignImage} to draw.
 */
public final class DesignGen {
    private DesignGen() {
    }

    /**
     * The model's design for one picture, from its reply: each part it names is used, each it leaves out (or names
     * wrongly) is chosen by the formula. {@link #of} reads it from the reply's "design" object.
     */
    public static final class Spec {
        String mood = "", background = "", layout = "", font = "", accent = "";
        final List<String> colors = new ArrayList<String>(), shapes = new ArrayList<String>();
        Boolean upper;

        /** Reads {"mood","colors":[..],"background","shapes":[..],"layout","font","accent","upper"}; empty for none. */
        public static Spec of(Object o) {
            Spec s = new Spec();
            java.util.Map<String, Object> m = ai.ibiko.brain.MiniJson.obj(o);
            s.mood = low(m.get("mood"));
            s.background = low(m.get("background"));
            s.layout = low(m.get("layout"));
            s.font = ai.ibiko.brain.MiniJson.str(m.get("font"), "").trim();
            s.accent = ai.ibiko.brain.MiniJson.str(m.get("accent"), "").trim();
            for (String c : ai.ibiko.brain.MiniJson.strings(m.get("colors"))) {
                if (c.trim().matches("#[0-9a-fA-F]{6}") && s.colors.size() < 3) {
                    s.colors.add(c.trim().toLowerCase(Locale.ROOT));
                }
            }
            for (String sh : ai.ibiko.brain.MiniJson.strings(m.get("shapes"))) {
                if (s.shapes.size() < 5) {
                    s.shapes.add(sh.trim().toLowerCase(Locale.ROOT));
                }
            }
            Object up = m.get("upper");
            s.upper = up instanceof Boolean ? (Boolean) up : null;
            return s;
        }

        private static String low(Object v) {
            return ai.ibiko.brain.MiniJson.str(v, "").trim().toLowerCase(Locale.ROOT);
        }

        /** As JSON, to keep with the post (a new picture keeps the model's design, with fresh details). */
        public String json() {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("mood", mood);
            m.put("colors", new ArrayList<Object>(colors));
            m.put("background", background);
            m.put("shapes", new ArrayList<Object>(shapes));
            m.put("layout", layout);
            m.put("font", font);
            m.put("accent", accent);
            if (upper != null) {
                m.put("upper", upper);
            }
            return ai.ibiko.brain.MiniJson.write(m);
        }
    }

    /** What the model is told it may choose for a picture (added to the request for posts). */
    public static final String HOW = "For each post also design its picture, as \"design\": {\"mood\":\"dark or light\","
            + "\"colors\":[\"#rrggbb main\",\"#rrggbb second\"],\"background\":\"mesh, gradient, split, grid or dots\","
            + "\"shapes\":[\"2 to 4 of: ring, blob, sparkle, wave, bars, triangle, dots, orb\"],\"layout\":\"top, center, bottom,"
            + " card or split\",\"font\":\"Poppins, Montserrat, Space Grotesk, Playfair Display, Bebas Neue or Outfit\","
            + "\"accent\":\"the one headline word to light up\",\"upper\":false}. Choose them to fit the post's feeling and the"
            + " brand: bold and dark for launches and tech, warm and light for food and family, elegant serif for premium;"
            + " vary the designs across posts so the feed looks rich, keeping the campaign's colours related.";

    private static final String[][] FONTS = {
            {"Poppins", "Poppins:wght@500;700;800;900", "800"},
            {"Montserrat", "Montserrat:wght@500;700;800;900", "800"},
            {"Space Grotesk", "Space+Grotesk:wght@500;700", "700"},
            {"Playfair Display", "Playfair+Display:wght@600;800;900", "800"},
            {"Bebas Neue", "Bebas+Neue", "400"},
            {"Outfit", "Outfit:wght@500;700;800;900", "800"},
    };

    /** A colour from hue (0–360), saturation and lightness (0–100), as #rrggbb. */
    static String hsl(double h, double s, double l) {
        h = ((h % 360) + 360) % 360;
        s = Math.max(0, Math.min(100, s)) / 100.0;
        l = Math.max(0, Math.min(100, l)) / 100.0;
        double c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
        double r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return String.format(Locale.US, "#%02x%02x%02x", (int) Math.round((r + m) * 255), (int) Math.round((g + m) * 255),
                (int) Math.round((b + m) * 255));
    }

    /** A colour with see-through {@code a} (0–1), as rgba(). */
    static String rgba(String hex, double a) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return String.format(Locale.US, "rgba(%d,%d,%d,%.2f)", (v >> 16) & 255, (v >> 8) & 255, v & 255, a);
    }

    /** The palette: background tones, two accents, and the text colours that read on it. */
    static final class Palette {
        boolean dark;
        String bg, bg2, a1, a2, a3, text, soft;
    }

    /** A colour's hue (0–360). */
    static double hue(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        double rr = ((v >> 16) & 255) / 255.0, gg = ((v >> 8) & 255) / 255.0, bb = (v & 255) / 255.0;
        double max = Math.max(rr, Math.max(gg, bb)), min = Math.min(rr, Math.min(gg, bb)), d = max - min;
        if (d == 0) {
            return 0;
        }
        double h = max == rr ? ((gg - bb) / d) % 6 : max == gg ? (bb - rr) / d + 2 : (rr - gg) / d + 4;
        return (h * 60 + 360) % 360;
    }

    static Palette palette(Random r) {
        return palette(r, new Spec());
    }

    static Palette palette(Random r, Spec spec) {
        Palette p = new Palette();
        double h = spec.colors.isEmpty() ? r.nextInt(360) : hue(spec.colors.get(0));
        int scheme = r.nextInt(3); // complementary, analogous, triadic
        double h2 = scheme == 0 ? h + 180 : scheme == 1 ? h + 35 + r.nextInt(25) : h + 120;
        double h3 = scheme == 2 ? h + 240 : h - 30 - r.nextInt(30);
        p.dark = "dark".equals(spec.mood) || !"light".equals(spec.mood) && r.nextInt(100) < 62;
        if (p.dark) {
            p.bg = hsl(h, 45 + r.nextInt(30), 7 + r.nextInt(8));
            p.bg2 = hsl(h + 20, 50 + r.nextInt(30), 14 + r.nextInt(10));
            p.text = "#ffffff";
            p.soft = "rgba(255,255,255,.82)";
        } else {
            p.bg = hsl(h, 60 + r.nextInt(30), 94 + r.nextInt(4));
            p.bg2 = hsl(h + 25, 70, 86 + r.nextInt(6));
            p.text = hsl(h, 40, 10);
            p.soft = rgba(hsl(h, 30, 20), .82);
        }
        p.a1 = hsl(h, 75 + r.nextInt(20), 52 + r.nextInt(10));
        p.a2 = hsl(h2, 75 + r.nextInt(20), 55 + r.nextInt(10));
        p.a3 = hsl(h3, 70 + r.nextInt(25), 58 + r.nextInt(12));
        if (!spec.colors.isEmpty()) {
            p.a1 = spec.colors.get(0); // the model's own colours, as it chose them
        }
        if (spec.colors.size() > 1) {
            p.a2 = spec.colors.get(1);
        }
        if (spec.colors.size() > 2) {
            p.a3 = spec.colors.get(2);
        }
        return p;
    }

    private static int between(Random r, int lo, int hi) {
        return lo + r.nextInt(Math.max(1, hi - lo + 1));
    }

    static final List<String> BACKGROUNDS = java.util.Arrays.asList("mesh", "gradient", "split", "grid", "dots");
    static final List<String> SHAPES = java.util.Arrays.asList("ring", "blob", "sparkle", "wave", "bars", "triangle", "dots", "orb");
    static final List<String> LAYOUTS = java.util.Arrays.asList("top", "center", "bottom", "card", "split");

    private static int pick(Random r, List<String> names, String named) {
        int at = names.indexOf(named);
        return at >= 0 ? at : r.nextInt(names.size());
    }

    private static String background(Random r, Palette p, int w, int h, String named) {
        switch (pick(r, BACKGROUNDS, named)) {
            case 0: // glowing mesh
                return "background:radial-gradient(circle at " + between(r, 5, 40) + "% " + between(r, 5, 40) + "%," + p.a1 + " 0,transparent "
                        + between(r, 35, 50) + "%),radial-gradient(circle at " + between(r, 60, 95) + "% " + between(r, 55, 95) + "%," + p.a2
                        + " 0,transparent " + between(r, 35, 50) + "%),radial-gradient(circle at " + between(r, 55, 95) + "% " + between(r, 5, 30)
                        + "%," + rgba(p.a3, .8) + " 0,transparent " + between(r, 22, 34) + "%)," + p.bg;
            case 1: // gradient
                return "background:linear-gradient(" + r.nextInt(360) + "deg," + p.bg + " 0%," + p.bg2 + " 55%,"
                        + rgba(p.a1, p.dark ? .55 : .35) + " 100%)";
            case 2: // diagonal split
                int y1 = between(r, 25, 55), y2 = between(r, 45, 80);
                return "background:linear-gradient(" + (r.nextBoolean() ? 165 : 195) + "deg," + p.a1 + " 0%," + p.a2 + " " + y1
                        + "%," + p.bg + " " + y1 + "%," + p.bg + " " + Math.max(y2, y1 + 1) + "%," + p.bg2 + " 100%)";
            case 3: // grid
                int g = between(r, 48, 80);
                return "background-color:" + p.bg + ";background-image:linear-gradient(" + rgba(p.text, .07) + " 1px,transparent 1px),"
                        + "linear-gradient(90deg," + rgba(p.text, .07) + " 1px,transparent 1px),radial-gradient(circle at "
                        + between(r, 20, 80) + "% " + between(r, 20, 80) + "%," + rgba(p.a1, .55) + " 0,transparent 55%);background-size:"
                        + g + "px " + g + "px," + g + "px " + g + "px,100% 100%";
            default: // dots
                int d = between(r, 26, 40);
                return "background-color:" + p.bg + ";background-image:radial-gradient(" + rgba(p.a2, .35) + " 2px,transparent 2px),"
                        + "linear-gradient(" + r.nextInt(360) + "deg," + p.bg + "," + p.bg2 + ");background-size:" + d + "px " + d + "px,100% 100%";
        }
    }

    /** One decoration, absolutely placed. */
    /** One decoration, placed clear of the words ({@code zone}: left, top, right, bottom), or partly off the edge. */
    private static String shape(Random r, Palette p, int w, int h, int[] zone, String named) {
        String color = new String[]{p.a1, p.a2, p.a3}[r.nextInt(3)];
        int size = between(r, Math.min(w, h) / 6, Math.min(w, h) * 2 / 5);
        int x = 0, y = 0;
        for (int tries = 0; tries < 24; tries++) {
            x = between(r, -size / 3, w - size * 2 / 3);
            y = between(r, -size / 3, h - size * 2 / 3);
            boolean clear = x + size < zone[0] || x > zone[2] || y + size < zone[1] || y > zone[3];
            if (clear) {
                break;
            }
        }
        String at = "position:absolute;left:" + x + "px;top:" + y + "px;width:" + size + "px;height:" + size + "px;";
        int rot = r.nextInt(360);
        switch (pick(r, SHAPES, named)) {
            case 0: // ring
                return "<div style=\"" + at + "border-radius:50%;border:" + between(r, 2, 14) + "px solid " + rgba(color, .35 + r.nextInt(40) / 100.0) + "\"></div>";
            case 1: // blob
                return "<div style=\"" + at + "border-radius:" + between(r, 30, 70) + "% " + between(r, 30, 70) + "% " + between(r, 30, 70) + "% "
                        + between(r, 30, 70) + "%/" + between(r, 30, 70) + "% " + between(r, 30, 70) + "% " + between(r, 30, 70) + "% "
                        + between(r, 30, 70) + "%;background:linear-gradient(" + rot + "deg," + color + "," + rgba(p.a2, .6) + ");opacity:"
                        + String.format(Locale.US, "%.2f", .55 + r.nextInt(40) / 100.0) + "\"></div>";
            case 2: // sparkle
                return "<svg style=\"" + at + "transform:rotate(" + rot + "deg)\" viewBox=\"0 0 100 100\"><path d=\"M50 0 C54 38 62 46 100 50 C62 54"
                        + " 54 62 50 100 C46 62 38 54 0 50 C38 46 46 38 50 0Z\" fill=\"" + rgba(color, .55 + r.nextInt(40) / 100.0) + "\"/></svg>";
            case 3: // wave
                int amp = between(r, 10, 30);
                int wy = zone[3] + 20 < h - 140 ? between(r, zone[3] + 20, h - 140) : between(r, -60, Math.max(-60, zone[1] - 200));
                return "<svg style=\"position:absolute;left:0;top:" + wy + "px;width:" + w + "px;height:200px\" viewBox=\"0 0 1000 200\""
                        + " preserveAspectRatio=\"none\"><path d=\"M0 " + (100 - amp) + " C250 " + (100 + amp * 3) + " 500 " + (100 - amp * 3) + " 750 "
                        + (100 + amp) + " S1000 " + (100 - amp) + " 1000 " + (100 - amp) + " V200 H0Z\" fill=\"" + rgba(color, .28 + r.nextInt(30) / 100.0) + "\"/></svg>";
            case 4: // bars
                StringBuilder b = new StringBuilder("<div style=\"" + at + "height:auto;transform:rotate(" + (rot % 90 - 45) + "deg)\">");
                for (int i = 0; i < between(r, 3, 6); i++) {
                    b.append("<div style=\"height:").append(between(r, 10, 22)).append("px;margin:").append(between(r, 10, 18))
                            .append("px 0;border-radius:20px;width:").append(between(r, 40, 100)).append("%;background:")
                            .append(rgba(color, .4 + r.nextInt(45) / 100.0)).append("\"></div>");
                }
                return b.append("</div>").toString();
            case 5: // triangle
                return "<svg style=\"" + at + "transform:rotate(" + rot + "deg)\" viewBox=\"0 0 100 100\"><polygon points=\"50,6 96,92 4,92\" fill=\"none\""
                        + " stroke=\"" + rgba(color, .55) + "\" stroke-width=\"" + between(r, 3, 8) + "\" stroke-linejoin=\"round\"/></svg>";
            case 6: // dot field
                return "<div style=\"" + at + "background-image:radial-gradient(" + rgba(color, .6) + " " + between(r, 2, 4) + "px,transparent "
                        + between(r, 4, 5) + "px);background-size:" + between(r, 18, 30) + "px " + between(r, 18, 30) + "px\"></div>";
            default: // orb
                return "<div style=\"" + at + "border-radius:50%;background:radial-gradient(circle at 30% 30%," + rgba("#ffffff", .7) + ","
                        + color + " 45%," + rgba(color, 0) + " 72%);opacity:.8\"></div>";
        }
    }

    static String esc(String s) {
        return CampaignPlanner.esc(s == null ? "" : s);
    }

    /** The headline with one word lit in the accent: the model's word when it named one, else a long word or the last. */
    private static String lit(Random r, String headline, String accent, boolean upper, String named) {
        String[] words = headline.trim().split("\\s+");
        int pick = -1;
        if (named != null && !named.isEmpty()) {
            for (int i = 0; i < words.length; i++) {
                if (words[i].replaceAll("[^\\p{L}\\p{N}]", "").equalsIgnoreCase(named.replaceAll("[^\\p{L}\\p{N}]", ""))) {
                    pick = i;
                }
            }
        }
        if (pick < 0) {
            if (words.length < 2 || r.nextInt(100) < 25) {
                return esc(upper ? headline.toUpperCase(Locale.ROOT) : headline);
            }
            pick = r.nextBoolean() ? words.length - 1 : 0;
            for (int i = 0; i < words.length; i++) {
                if (words[i].length() > words[pick].length() && r.nextBoolean()) {
                    pick = i;
                }
            }
        }
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = esc(upper ? words[i].toUpperCase(Locale.ROOT) : words[i]);
            s.append(i == 0 ? "" : " ").append(i == pick ? "<span style=\"color:" + accent + "\">" + w + "</span>" : w);
        }
        return s.toString();
    }

    /** As {@link #make(long, String, String, String, String, int, int, Spec)} with every choice the formula's. */
    public static String make(long seed, String headline, String line, String tag, String photo, int w, int h) {
        return make(seed, headline, line, tag, photo, w, h, new Spec());
    }

    /**
     * A fresh picture, {@code w} by {@code h}: the headline, the line under it and a small tag (the theme), designed
     * as {@code spec} says (the model's choices) with the rest made from {@code seed}. With {@code photo} (an image
     * address) the photo fills the picture under a coloured veil.
     */
    public static String make(long seed, String headline, String line, String tag, String photo, int w, int h, Spec spec) {
        Spec sp = spec == null ? new Spec() : spec;
        Random r = new Random(seed);
        Palette p = palette(r, sp);
        String[] font = FONTS[r.nextInt(FONTS.length)];
        for (String[] f : FONTS) {
            if (f[0].equalsIgnoreCase(sp.font)) {
                font = f;
            }
        }
        boolean display = font[0].equals("Bebas Neue");
        boolean upper = display || (sp.upper != null ? sp.upper : r.nextInt(100) < 22);
        boolean hasPhoto = photo != null && !photo.isEmpty();
        int layout = hasPhoto ? 2 : pick(r, LAYOUTS, sp.layout); // 0 top, 1 centre, 2 bottom, 3 card, 4 split
        String head = headline == null || headline.trim().isEmpty() ? "" : headline.trim();
        int len = Math.max(8, head.length());
        int size = (int) Math.max(68, Math.min(display ? 170 : 128, (display ? 1900 : 1450) / Math.sqrt(len) * (w / 1080.0)));
        String accent = p.dark || hasPhoto ? p.a2 : p.a1;
        int pad = (int) (w * (0.075 + r.nextInt(3) / 100.0));
        // where the words go, so the shapes keep clear of them
        double textW = layout == 4 ? w * 0.62 : w - 2.0 * pad;
        double perLine = Math.max(5, textW / (size * (display ? 0.42 : 0.58)));
        int pSize = Math.max(34, size * 2 / 5);
        double pLines = Math.ceil(Math.max(1, line == null ? 0 : line.length()) / Math.max(10, textW / (pSize * 0.5)));
        int textH = (int) (size * 1.08 * Math.ceil(len / perLine) + pSize * 1.4 * pLines + size + 90);
        int[] zone;
        switch (layout) {
            case 0:
                zone = new int[]{pad - 20, pad - 20, w - pad + 20, pad + textH};
                break;
            case 1:
                zone = new int[]{pad - 20, h / 2 - textH / 2 - 20, w - pad + 20, h / 2 + textH / 2 + 20};
                break;
            case 4:
                zone = new int[]{0, h / 2 - textH / 2 - 20, pad + 20 + w * 62 / 100, h / 2 + textH / 2 + 20};
                break;
            default:
                zone = new int[]{pad - 20, h - pad - textH - (layout == 3 ? pad : 0), w - pad + 20, h - pad + 20};
        }
        boolean cardTop = layout == 3 && r.nextBoolean();
        if (cardTop) {
            zone = new int[]{pad - 20, pad - 20, w - pad + 20, pad + textH + pad};
        }
        StringBuilder s = new StringBuilder("<!doctype html><html><head><link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=")
                .append(font[1]).append("&family=Inter:wght@400;500;600&display=swap\"><style>body{margin:0;width:").append(w)
                .append("px;height:").append(h).append("px;position:relative;overflow:hidden;font-family:'").append(font[0])
                .append("','Segoe UI',Roboto,sans-serif;color:").append(hasPhoto ? "#fff" : p.text).append(';')
                .append(hasPhoto ? "background:#111" : background(r, p, w, h, sp.background)).append("}")
                .append("h1{font-size:").append(size).append("px;line-height:").append(display ? "0.95" : "1.04").append(";margin:0 0 ")
                .append(size / 3).append("px;font-weight:").append(font[2]).append(";letter-spacing:").append(display ? "1px" : "-1.5px").append("}")
                .append("p{font-family:Inter,'Segoe UI',Roboto,sans-serif;font-size:").append(Math.max(34, size * 2 / 5)).append("px;line-height:1.35;margin:0;color:")
                .append(hasPhoto ? "rgba(255,255,255,.9)" : p.soft).append("}")
                .append(".t{display:inline-block;font-family:Inter,sans-serif;font-weight:600;font-size:26px;letter-spacing:4px;text-transform:uppercase;")
                .append("margin-bottom:").append(size / 3).append("px;");
        int tagStyle = r.nextInt(3);
        s.append(tagStyle == 0 ? "padding:12px 26px;border-radius:40px;background:" + rgba(accent, .18) + ";border:1px solid " + rgba(accent, .5) + ";color:" + (p.dark || hasPhoto ? "#fff" : p.text)
                : tagStyle == 1 ? "color:" + accent : "padding:10px 22px;border-radius:10px;background:" + accent + ";color:" + (p.dark ? p.bg : "#fff"));
        s.append("}.bar{width:").append(between(r, 100, 200)).append("px;height:").append(between(r, 8, 14)).append("px;border-radius:8px;background:linear-gradient(90deg,")
                .append(p.a1).append(',').append(p.a2).append(");margin:0 0 ").append(size / 3).append("px}</style></head><body>");
        if (hasPhoto) {
            s.append("<img src=\"").append(esc(photo)).append("\" style=\"position:absolute;inset:0;width:").append(w).append("px;height:").append(h)
                    .append("px;object-fit:cover\"><div style=\"position:absolute;inset:0;background:linear-gradient(").append(r.nextBoolean() ? 180 : 200)
                    .append("deg,transparent 30%,").append(rgba(hsl(hue(p.a1), 60, 10), .9)).append(" 80%)\"></div>");
        } else {
            int shapes = sp.shapes.isEmpty() ? between(r, 2, 4) : sp.shapes.size();
            for (int i = 0; i < shapes; i++) {
                s.append(shape(r, p, w, h, zone, i < sp.shapes.size() ? sp.shapes.get(i) : ""));
            }
        }
        String words = "<div class=\"t\">" + esc(tag) + "</div><h1>" + lit(r, head, accent, upper, sp.accent) + "</h1>"
                + (r.nextBoolean() ? "<div class=\"bar\"></div>" : "") + "<p>" + esc(line) + "</p>";
        String align = layout == 1 && r.nextBoolean() ? "text-align:center;" : "";
        switch (layout) {
            case 0:
                s.append("<div style=\"position:absolute;left:").append(pad).append("px;right:").append(pad).append("px;top:").append(pad).append("px\">").append(words).append("</div>");
                break;
            case 1:
                s.append("<div style=\"position:absolute;left:").append(pad).append("px;right:").append(pad).append("px;top:50%;transform:translateY(-50%);")
                        .append(align).append("\">").append(words).append("</div>");
                break;
            case 3:
                s.append("<div style=\"position:absolute;left:").append(pad).append("px;right:").append(pad).append("px;").append(cardTop ? "top" : "bottom")
                        .append(':').append(pad).append("px;padding:").append(pad * 3 / 4).append("px;border-radius:").append(between(r, 24, 48))
                        .append("px;background:").append(p.dark ? "rgba(0,0,0,.42)" : "rgba(255,255,255,.72)").append(";border:1px solid ")
                        .append(p.dark ? "rgba(255,255,255,.18)" : "rgba(0,0,0,.08)").append(";box-shadow:0 30px 80px rgba(0,0,0,.25)\">").append(words).append("</div>");
                break;
            case 4:
                s.append("<div style=\"position:absolute;left:0;top:0;bottom:0;width:").append(between(r, 8, 22)).append("px;background:").append(accent).append("\"></div>")
                        .append("<div style=\"position:absolute;left:").append(pad + 20).append("px;width:").append(w * 62 / 100).append("px;top:50%;transform:translateY(-50%)\">")
                        .append(words).append("</div>");
                break;
            default:
                s.append("<div style=\"position:absolute;left:").append(pad).append("px;right:").append(pad).append("px;bottom:").append(pad).append("px\">").append(words).append("</div>");
        }
        return s.append("</body></html>").toString();
    }

    /** A seed for a new picture: different every time it is asked for. */
    public static long freshSeed(String id) {
        return (id == null ? 0 : id.hashCode()) * 1_000_003L ^ System.nanoTime();
    }

    /** Several fresh designs, for a test or a choice. */
    static List<String> some(int n, String headline, String line, String tag) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            out.add(make(freshSeed("x" + i), headline, line, tag, null, 1080, 1080));
        }
        return out;
    }
}
