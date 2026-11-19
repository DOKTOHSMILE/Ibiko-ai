package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The brain builds the whole campaign from a short brief, in two steps: first the outline (a name, the themes, the
 * searches for people who may need the offer, and a calendar of slots: day, time, account, theme, angle), then each
 * post (its words and a picture as HTML). Everything the brain writes is checked: only the accounts chosen, days and
 * times that exist, words within each network's length, a picture that is HTML with no script, and at most
 * {@link #MOST_POSTS} posts. Pure Java: the brain is passed in.
 */
public final class CampaignPlanner {
    /** The brain the planner asks: a system prompt, the request, and how long the answer may be. */
    public interface Brain {
        String ask(String system, String prompt, int tokens);
    }

    /** A chosen account, as the brain sees it. */
    public static final class Seat {
        public final String id, network, label;

        public Seat(String id, String network, String label) {
            this.id = id;
            this.network = network;
            this.label = label;
        }
    }

    /** What the planner is doing, for the screen ("Writing post 3 of 14"). */
    public interface Progress {
        void at(String what);
    }

    public static final int MOST_POSTS = 120, MOST_DAYS = 90, MOST_PER_DAY = 6;
    /** A picture's HTML larger than this is dropped (the post goes out with words only). */
    static final int MOST_HTML = 48_000;

    private static final String OUTLINE_SYSTEM = "You are a social media campaign strategist for a small business. You plan"
            + " honest, useful campaigns that attract real customers: value first, a clear offer, a call to action, no"
            + " spam, no fake claims, no fake reviews, no engagement bait. Reply with one JSON object only, no prose.";

    private CampaignPlanner() {
    }

    /** The request for the outline. */
    static String outlinePrompt(Campaign.Brief brief, List<Seat> seats, int days, int perDay) {
        java.util.Set<String> nets = new java.util.LinkedHashSet<String>();
        for (Seat seat : seats) {
            nets.add(seat.network);
        }
        StringBuilder s = new StringBuilder();
        s.append(brief.words()).append("\n\nNetworks: ").append(String.join(", ", nets)).append(". The campaign runs ").append(days)
                .append(" days with ").append(perDay).append(" post(s) a day on each account (")
                .append(days * perDay * seats.size()).append(" posts). Plan its strategy as JSON (the calendar is made from it):\n")
                .append("{\"name\":\"short campaign name\",\"themes\":[{\"name\":\"a content theme\",\"angles\":[\"what one")
                .append(" post on it says, in one line\"]}],\"searches\":[\"3 to 8 short searches that find public posts by people who")
                .append(" need this offer\"],\"times\":{\"<network>\":[\"09:30\",\"19:00\"]}}\n")
                .append("3 to 6 themes mixing value, proof and offer, each with ").append(Math.max(3, Math.min(8, days * perDay * seats.size() / 4 + 1)))
                .append(" different angles; for each network the 2 to 4 times of day its audience is most active. Short lines only.")
                .append(brief.lessons.isEmpty() ? "" : " Lean on what worked in earlier campaigns.");
        return s.toString();
    }

    /** When a network's audience is usually about, if the brain gave no times for it. */
    static List<Integer> usualTimes(String network) {
        String[] t;
        switch (network == null ? "" : network) {
            case "x":
                t = new String[]{"08:30", "12:30", "18:00", "21:00"};
                break;
            case "instagram":
                t = new String[]{"11:00", "19:00", "21:00"};
                break;
            case "linkedin":
                t = new String[]{"08:00", "12:00", "17:30"};
                break;
            default:
                t = new String[]{"09:00", "13:00", "19:30"};
        }
        List<Integer> out = new ArrayList<Integer>();
        for (String x : t) {
            out.add(minute(x));
        }
        return out;
    }

    /**
     * The calendar, made from the strategy: each account its posts a day at its network's active times (spread over the
     * day and shifted a little day to day), the themes taken in turn, each theme's angles in turn, and the products
     * spread across about two posts in three.
     */
    static void schedule(Outline o, List<Seat> seats, int days, int perDay, int products) {
        if (o.themes.isEmpty()) {
            return;
        }
        int n = 0;
        java.util.Map<String, Integer> angleAt = new java.util.HashMap<String, Integer>();
        for (int day = 1; day <= days; day++) {
            for (int si = 0; si < seats.size(); si++) {
                Seat seat = seats.get(si);
                List<Integer> times = o.times.containsKey(seat.network) ? o.times.get(seat.network) : usualTimes(seat.network);
                for (int k = 0; k < perDay; k++) {
                    if (o.slots.size() >= MOST_POSTS) {
                        return;
                    }
                    Slot slot = new Slot();
                    slot.day = day;
                    int base = perDay <= times.size() ? times.get((k * times.size() / perDay + day + si) % times.size())
                            : 8 * 60 + k * (14 * 60 / perDay); // more posts than active times: spread 08:00 to 22:00
                    slot.minute = Math.max(0, Math.min(23 * 60 + 50, base + ((day * 7 + si * 11) % 21) - 10));
                    slot.account = seat.id;
                    String theme = o.themes.get((n + si) % o.themes.size());
                    slot.theme = theme;
                    List<String> angles = o.angles.containsKey(theme) ? o.angles.get(theme) : new ArrayList<String>();
                    int at = angleAt.getOrDefault(theme, 0);
                    angleAt.put(theme, at + 1);
                    String angle = angles.isEmpty() ? "a post on " + theme : angles.get(at % angles.size());
                    slot.angle = at >= Math.max(1, angles.size()) ? angle + " (a fresh take, unlike earlier posts on it)" : angle;
                    slot.product = products > 0 && n % 3 != 2 ? (n - n / 3) % products : -1;
                    o.slots.add(slot);
                    n++;
                }
            }
        }
        o.slots.sort((x, y) -> x.day != y.day ? Integer.compare(x.day, y.day) : Integer.compare(x.minute, y.minute));
    }

    /** Posts written in one call: fewer calls, each still short enough to come back whole. */
    static final int BATCH = 10;
    /** Calls at once on an API (the phone's own model takes one at a time). */
    public static final int WORKERS = 6;

    private static final String POSTS_SYSTEM = "You are a top social media copywriter for a small business. Every post"
            + " stops the scroll: a first line that hooks (a bold claim, a question, a surprising fact, a relatable"
            + " moment), then short lines with air between them, real value or a story, and one clear call to action."
            + " Vary the format across posts: a quick tip list, a question to the reader, a mini story, myth versus fact,"
            + " behind the scenes, a before and after, a straight offer. Sound like a person, warm and confident, never"
            + " corporate filler (no \"in today's fast-paced world\", no \"unlock\", no \"elevate\"). Fit the network:"
            + " X short and punchy (under 260 characters, at most one emoji, one or two hashtags); Facebook"
            + " conversational, 50 to 120 words, 1 to 3 fitting emojis, ends with a question or the call to action;"
            + " Instagram a caption of 60 to 130 words with emojis as bullets, the call to action, then 5 to 8 hashtags"
            + " on their own line; LinkedIn professional storytelling, 100 to 180 words, short paragraphs, an insight,"
            + " at most 2 emojis, 3 hashtags. Honest: no made-up facts, prices, numbers or reviews."
            + " Reply with one JSON object only, no prose.";

    private static final String TEMPLATE_SYSTEM = "You design one picture template for a campaign theme, as a single"
            + " self-contained HTML document. Reply with the HTML only.";

    /** The request for a batch of posts: each slot's network, theme and angle, numbered. */
    static String postsPrompt(Campaign.Brief brief, List<String[]> slots) {
        StringBuilder s = new StringBuilder(brief.words()).append("\n\nWrite these posts (number, network, theme, angle):\n");
        for (int i = 0; i < slots.size(); i++) {
            String[] sl = slots.get(i);
            s.append(i + 1).append(". ").append(sl[0]).append(" (at most ").append(maxChars(sl[0])).append(" characters)")
                    .append(" | ").append(sl[1]).append(" | ").append(sl[2]).append('\n');
        }
        s.append("\nEach post in the brief's tone, written for its network, with a clear call to action")
                .append(brief.link.isEmpty() ? "" : " and the link")
                .append(brief.chat().isEmpty() ? "" : "; where it fits, invite people to chat on WhatsApp with the WhatsApp chat link exactly as given")
                .append("; no two alike in opening or format.")
                .append(" For each also a picture headline (3 to 6 punchy words, no business name) and a short line under it"
                        + " (at most 9 words) that adds to the headline, not repeats it.\n")
                .append("JSON: {\"posts\":[{\"n\":1,\"text\":\"the post\",\"headline\":\"...\",\"line\":\"...\"}]}");
        return s.toString();
    }

    /** The request for one theme's picture template. */
    static String templatePrompt(Campaign.Brief brief, String theme) {
        String what = "the whole campaign".equals(theme) ? "the whole campaign (every post's picture uses it)"
                : "the theme \u201c" + theme + "\u201d";
        return brief.words() + "\n\nDesign the picture template for " + what + ": one HTML document,"
                + " 1080 by 1080 pixels, its own <style> only (no script, no outside images; Google Fonts may be linked)."
                + " Bold and readable, on-brand colours. Put these placeholders where the words go, exactly as written:"
                + " {{HEADLINE}} (large), {{LINE}} (smaller, under it) and {{BRAND}} (the business name, small)."
                + (hasPhotos(brief) ? " Also show the product photo large and clear, as <img src=\"{{PHOTO}}\"> with"
                + " object-fit:cover in a fixed-size box; the words must stay readable beside or over it." : "");
    }

    /** A seat whose id starts with this is a network the person posts on from its app (Instagram, LinkedIn). */
    public static final String HANDOFF = "handoff:";

    static boolean hasPhotos(Campaign.Brief b) {
        for (Product p : b.products) {
            if (!p.photo.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The most characters a post may have on a network (X's limit; the others kept readable). */
    static int maxChars(String network) {
        return "x".equalsIgnoreCase(network) ? 280 : 1200;
    }

    /** One slot of the outline, checked. */
    static final class Slot {
        int day;
        int minute;
        String account, theme, angle;
        /** The product it shows (index into the brief's products), or -1. */
        int product = -1;
    }

    /** The outline as the brain wrote it, checked. */
    static final class Outline {
        String name = "";
        final List<String> themes = new ArrayList<String>(), searches = new ArrayList<String>();
        final List<Slot> slots = new ArrayList<Slot>();
        /** Each theme's angles, and each network's active times (minutes past midnight), from the strategy. */
        final Map<String, List<String>> angles = new java.util.HashMap<String, List<String>>();
        final Map<String, List<Integer>> times = new java.util.HashMap<String, List<Integer>>();
    }

    /** The first JSON object in a reply (the brain may wrap it in a code block or a sentence), or null. */
    static Map<String, Object> json(String reply) {
        if (reply == null) {
            return null;
        }
        String r = reply.replaceAll("(?s)<think>.*?(</think>|$)", "");
        int a = r.indexOf('{'), b = r.lastIndexOf('}');
        if (a < 0 || b <= a) {
            return null;
        }
        try {
            Object o = MiniJson.parse(r.substring(a, b + 1));
            return o instanceof Map ? MiniJson.obj(o) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The outline, keeping only what checks out. Null when the reply holds no usable slot. */
    static Outline outline(String reply, List<Seat> seats, int days, int perDay) {
        return outline(reply, seats, days, perDay, 0);
    }

    /** As above, for a brief with {@code products} products (a slot may name one, P1 to Pn). */
    static Outline outline(String reply, List<Seat> seats, int days, int perDay, int products) {
        Map<String, Object> m = json(reply);
        if (m == null) {
            return null;
        }
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (Seat s : seats) {
            ids.add(s.id);
        }
        Outline o = new Outline();
        o.name = clip(MiniJson.str(m.get("name"), ""), 60);
        for (Object x : MiniJson.list(m.get("themes"))) {
            String t = x instanceof String ? (String) x : MiniJson.str(MiniJson.obj(x).get("name"), "");
            t = clip(t.trim(), 80);
            if (t.isEmpty() || o.themes.size() >= 8 || o.themes.contains(t)) {
                continue;
            }
            o.themes.add(t);
            if (!(x instanceof String)) {
                List<String> as = new ArrayList<String>();
                for (String a : MiniJson.strings(MiniJson.obj(x).get("angles"))) {
                    if (!a.trim().isEmpty() && as.size() < 12) {
                        as.add(clip(a.trim(), 200));
                    }
                }
                o.angles.put(t, as);
            }
        }
        for (Map.Entry<String, Object> e : MiniJson.obj(m.get("times")).entrySet()) {
            List<Integer> ts = new ArrayList<Integer>();
            for (String t : MiniJson.strings(e.getValue())) {
                int mm = minute(t);
                if (mm >= 0 && ts.size() < 6 && !ts.contains(mm)) {
                    ts.add(mm);
                }
            }
            if (!ts.isEmpty()) {
                o.times.put(e.getKey().trim().toLowerCase(Locale.ROOT), ts);
            }
        }
        for (String t : MiniJson.strings(m.get("searches"))) {
            if (!t.trim().isEmpty() && o.searches.size() < 10) {
                o.searches.add(clip(t.trim(), 100));
            }
        }
        java.util.Map<String, Integer> perAccountDay = new java.util.HashMap<String, Integer>();
        for (Object x : MiniJson.list(m.get("slots"))) {
            Map<String, Object> s = MiniJson.obj(x);
            Slot slot = new Slot();
            slot.day = (int) MiniJson.num(s.get("day"), -1);
            slot.minute = minute(MiniJson.str(s.get("time"), ""));
            slot.account = MiniJson.str(s.get("account"), "");
            slot.theme = clip(MiniJson.str(s.get("theme"), ""), 80);
            slot.angle = clip(MiniJson.str(s.get("angle"), ""), 200);
            java.util.regex.Matcher pm = java.util.regex.Pattern.compile("(?i)^\\s*p?(\\d{1,3})\\s*$")
                    .matcher(MiniJson.str(s.get("product"), String.valueOf((long) MiniJson.num(s.get("product"), 0))));
            slot.product = pm.matches() ? Integer.parseInt(pm.group(1)) - 1 : -1;
            if (slot.product >= products) {
                slot.product = -1; // a product that isn't in the brief
            }
            if (slot.day < 1 || slot.day > days || slot.minute < 0 || !ids.contains(slot.account) || slot.angle.isEmpty()) {
                continue; // a day past the end, a time that doesn't exist, an account not chosen
            }
            String key = slot.account + "@" + slot.day;
            int n = perAccountDay.getOrDefault(key, 0);
            if (n >= perDay) {
                continue; // no more than asked a day on one account
            }
            perAccountDay.put(key, n + 1);
            o.slots.add(slot);
            if (o.slots.size() >= MOST_POSTS) {
                break;
            }
        }
        if (o.slots.isEmpty() && !m.containsKey("slots")) {
            schedule(o, seats, days, perDay, products); // the strategy only: the calendar is made here, not written out
        }
        return o.slots.isEmpty() ? null : o;
    }

    /** "09:30" as minutes past midnight, or -1. */
    static int minute(String hhmm) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})\\s*$").matcher(hhmm == null ? "" : hhmm);
        if (!m.matches()) {
            return -1;
        }
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        return h < 24 && min < 60 ? h * 60 + min : -1;
    }

    /** A batch's posts, checked: index in the batch to {text, headline, line}; a missing or empty post is left out. */
    static java.util.Map<Integer, String[]> posts(String reply, List<String[]> slots) {
        java.util.Map<Integer, String[]> out = new java.util.HashMap<Integer, String[]>();
        Map<String, Object> m = json(reply);
        if (m == null) {
            return out;
        }
        for (Object o : MiniJson.list(m.get("posts"))) {
            Map<String, Object> p = MiniJson.obj(o);
            int n = (int) MiniJson.num(p.get("n"), -1);
            if (n < 1 || n > slots.size() || out.containsKey(n - 1)) {
                continue;
            }
            String text = MiniJson.str(p.get("text"), "").trim();
            if (text.isEmpty()) {
                continue;
            }
            int most = maxChars(slots.get(n - 1)[0]);
            if (text.length() > most) {
                int cut = text.lastIndexOf(' ', most - 1);
                text = text.substring(0, cut > most / 2 ? cut : most - 1).trim() + "\u2026";
            }
            out.put(n - 1, new String[]{text, clip(MiniJson.str(p.get("headline"), ""), 60), clip(MiniJson.str(p.get("line"), ""), 90)});
        }
        return out;
    }

    /** A theme's template, made safe; the built-in card when the brain's has no headline slot. */
    static String template(String reply) {
        String h = picture(reply == null ? "" : reply.replaceAll("(?s)^.*?(<!doctype|<html)", "$1").replaceAll("(?s)```.*$", ""));
        return h.contains("{{HEADLINE}}") ? h : "";
    }

    /** The card used when a theme has no template of its own: plain, bold, readable. */
    static final String CARD = "<!doctype html><html><head><style>body{margin:0;width:1080px;height:1080px;display:flex;"
            + "flex-direction:column;justify-content:center;padding:96px;box-sizing:border-box;background:linear-gradient(135deg,"
            + "#3b1f8f,#8b5cf6);color:#fff;font-family:sans-serif}h1{font-size:96px;line-height:1.05;margin:0 0 32px}"
            + "p{font-size:44px;margin:0;opacity:.9}</style></head>"
            + "<body><h1>{{HEADLINE}}</h1><p>{{LINE}}</p></body></html>";

    /** Designed picture layouts, one a theme in turn: bold type, depth and shape; the theme as a small tag, no name. */
    static final String[] DESIGNS = {
            "<!doctype html><html><head><link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Pop"
                    + "pins:wght@400;600;700;800;900&display=swap\"><style>body{margin:0;width:1080px;height:1080px;position"
                    + ":relative;overflow:hidden;font-family:'Poppins','Segoe UI',Roboto,sans-serif;background:radial-gradi"
                    + "ent(circle at 18% 20%,#7c3aed 0,transparent 42%),radial-gradient(circle at 85% 80%,#06b6d4 0,transpa"
                    + "rent 45%),radial-gradient(circle at 80% 10%,#f472b6 0,transparent 30%),#0b1026;color:#fff}.ring{posi"
                    + "tion:absolute;border-radius:50%;border:2px solid rgba(255,255,255,.18)}.r1{width:620px;height:620px;"
                    + "right:-180px;top:-160px}.r2{width:420px;height:420px;right:-80px;top:-60px;border-color:rgba(255,255"
                    + ",255,.28)}.dots{position:absolute;left:80px;bottom:80px;width:260px;height:120px;background-image:ra"
                    + "dial-gradient(rgba(255,255,255,.35) 2px,transparent 2px);background-size:22px 22px}.c{position:absol"
                    + "ute;left:96px;right:96px;top:50%;transform:translateY(-50%)}.tag{display:inline-block;padding:12px 2"
                    + "6px;border-radius:40px;background:rgba(255,255,255,.12);border:1px solid rgba(255,255,255,.25);font-"
                    + "size:28px;letter-spacing:3px;text-transform:uppercase;margin-bottom:40px}h1{font-size:104px;line-hei"
                    + "ght:1.02;margin:0 0 36px;font-weight:800;letter-spacing:-2px}.bar{width:140px;height:10px;border-rad"
                    + "ius:6px;background:linear-gradient(90deg,#f472b6,#06b6d4);margin-bottom:36px}p{font-size:44px;line-h"
                    + "eight:1.3;margin:0;color:rgba(255,255,255,.85)}</style></head><body><div class=\"ring r1\"></div><div "
                    + "class=\"ring r2\"></div><div class=\"dots\"></div><div class=\"c\"><div class=\"tag\">{{THEME}}</div><h1>{{H"
                    + "EADLINE}}</h1><div class=\"bar\"></div><p>{{LINE}}</p></div></body></html>",
            "<!doctype html><html><head><link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Pop"
                    + "pins:wght@400;600;700;800;900&display=swap\"><style>body{margin:0;width:1080px;height:1080px;position"
                    + ":relative;overflow:hidden;font-family:'Poppins','Segoe UI',Roboto,sans-serif;background:#fff7ed;colo"
                    + "r:#1c1917}.blob{position:absolute;right:-140px;bottom:-140px;width:760px;height:760px;border-radius:"
                    + "46% 54% 42% 58%/55% 45% 55% 45%;background:linear-gradient(135deg,#fb923c,#e11d48)}.blob2{position:a"
                    + "bsolute;right:120px;bottom:140px;width:300px;height:300px;border-radius:50%;background:rgba(255,255,"
                    + "255,.22)}svg{position:absolute;right:150px;bottom:170px;width:240px;height:240px}.c{position:absolut"
                    + "e;left:90px;top:120px;width:640px}.k{font-size:30px;font-weight:700;color:#e11d48;letter-spacing:4px"
                    + ";text-transform:uppercase;margin-bottom:28px}h1{font-size:96px;line-height:1.02;margin:0 0 32px;font"
                    + "-weight:800;letter-spacing:-2px}p{font-size:42px;line-height:1.35;margin:0;color:#44403c}</style></h"
                    + "ead><body><div class=\"blob\"></div><div class=\"blob2\"></div><svg viewBox=\"0 0 100 100\"><path d=\"M50 8"
                    + " L61 38 L93 39 L67 58 L77 90 L50 71 L23 90 L33 58 L7 39 L39 38 Z\" fill=\"#fff\"/></svg><div class=\"c\">"
                    + "<div class=\"k\">{{THEME}}</div><h1>{{HEADLINE}}</h1><p>{{LINE}}</p></div></body></html>",
            "<!doctype html><html><head><link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Pop"
                    + "pins:wght@400;600;700;800;900&display=swap\"><style>body{margin:0;width:1080px;height:1080px;position"
                    + ":relative;overflow:hidden;font-family:'Poppins','Segoe UI',Roboto,sans-serif;background:linear-gradi"
                    + "ent(160deg,#0f766e 0%,#14b8a6 45%,#a7f3d0 100%);color:#fff}.grid{position:absolute;inset:0;backgroun"
                    + "d-image:linear-gradient(rgba(255,255,255,.08) 1px,transparent 1px),linear-gradient(90deg,rgba(255,25"
                    + "5,255,.08) 1px,transparent 1px);background-size:60px 60px}.card{position:absolute;left:90px;right:90"
                    + "px;bottom:110px;padding:70px 64px;border-radius:44px;background:rgba(6,40,38,.55);border:1px solid r"
                    + "gba(255,255,255,.25);box-shadow:0 30px 80px rgba(0,0,0,.25)}.num{position:absolute;right:90px;top:70"
                    + "px;font-size:300px;font-weight:900;color:rgba(255,255,255,.14);line-height:1}.k{font-size:28px;lette"
                    + "r-spacing:4px;text-transform:uppercase;color:#a7f3d0;margin-bottom:22px;font-weight:700}h1{font-size"
                    + ":88px;line-height:1.04;margin:0 0 26px;font-weight:800;letter-spacing:-1.5px}p{font-size:40px;line-h"
                    + "eight:1.35;margin:0;color:rgba(255,255,255,.88)}</style></head><body><div class=\"grid\"></div><div cl"
                    + "ass=\"num\">&#10022;</div><div class=\"card\"><div class=\"k\">{{THEME}}</div><h1>{{HEADLINE}}</h1><p>{{LI"
                    + "NE}}</p></div></body></html>",
            "<!doctype html><html><head><link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Pop"
                    + "pins:wght@400;600;700;800;900&display=swap\"><style>body{margin:0;width:1080px;height:1080px;position"
                    + ":relative;overflow:hidden;font-family:'Poppins','Segoe UI',Roboto,sans-serif;background:#111827;colo"
                    + "r:#f9fafb}.half{position:absolute;left:0;top:0;width:1080px;height:1080px;background:linear-gradient"
                    + "(135deg,#facc15,#f97316);clip-path:polygon(0 0,100% 0,100% 38%,0 62%)}svg{position:absolute;right:80"
                    + "px;top:70px;width:300px;height:300px}.c{position:absolute;left:90px;right:90px;bottom:120px}h1{font-"
                    + "size:110px;line-height:1;margin:0 0 30px;font-weight:900;text-transform:uppercase;letter-spacing:-2p"
                    + "x}h1 span{color:#facc15}p{font-size:42px;line-height:1.35;margin:0;color:#d1d5db}.k{position:absolut"
                    + "e;left:90px;top:110px;font-size:34px;font-weight:800;color:#111827;letter-spacing:3px;text-transform"
                    + ":uppercase;max-width:560px}</style></head><body><div class=\"half\"></div><div class=\"k\">{{THEME}}</di"
                    + "v><svg viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"46\" fill=\"none\" stroke=\"#111827\" stroke-widt"
                    + "h=\"5\"/><circle cx=\"50\" cy=\"50\" r=\"30\" fill=\"none\" stroke=\"#111827\" stroke-width=\"5\"/><circle cx=\"50\""
                    + " cy=\"50\" r=\"12\" fill=\"#111827\"/></svg><div class=\"c\"><h1>{{HEADLINE}}</h1><p>{{LINE}}</p></div></bod"
                    + "y></html>"
    };


    /** The card for a post that shows a product photo, when the theme's template has no place for one. */
    static final String CARD_PHOTO = "<!doctype html><html><head><style>body{margin:0;width:1080px;height:1080px;"
            + "position:relative;overflow:hidden;background:#140b33;color:#fff;font-family:sans-serif}"
            + "img{position:absolute;inset:0;width:1080px;height:1080px;object-fit:cover}"
            + ".s{position:absolute;left:0;right:0;bottom:0;padding:72px 80px 64px;background:linear-gradient(transparent,"
            + "rgba(20,11,51,.92) 38%)}h1{font-size:84px;line-height:1.05;margin:0 0 20px}p{font-size:40px;margin:0 0 28px;"
            + "opacity:.92}b{font-size:32px;opacity:.85}</style></head><body><img src=\"{{PHOTO}}\"><div class=\"s\">"
            + "<h1>{{HEADLINE}}</h1><p>{{LINE}}</p></div></body></html>";

    /** The template with this post's words in it (as text: nothing the brain wrote becomes markup). */
    static String fill(String template, String headline, String line, String brand) {
        return fill(template, headline, line, brand, "");
    }

    /**
     * As above, with the photo of product {@code photoId} ("" for none) in the template's {@code {{PHOTO}}} place, or
     * on the photo card when the template has none. The photo itself goes in when the picture is drawn
     * ({@link Catalog#inline}).
     */
    static String fill(String template, String headline, String line, String brand, String photoId) {
        return fill(template, headline, line, brand, photoId, "");
    }

    /** As above, with the theme shown as the picture's small tag. */
    static String fill(String template, String headline, String line, String brand, String photoId, String theme) {
        String t = template == null || template.isEmpty() ? CARD : template;
        boolean photo = photoId != null && !photoId.isEmpty();
        if (photo && !t.contains("{{PHOTO}}")) {
            t = CARD_PHOTO;
        }
        String src = photo ? Catalog.PHOTO + Catalog.safe(photoId) : Catalog.BLANK;
        // the business's name is not written on the picture: the post comes from its own account already
        return t.replace("{{HEADLINE}}", esc(headline)).replace("{{LINE}}", esc(line)).replace("{{BRAND}}", "")
                .replace("{{THEME}}", esc(theme == null ? "" : theme)).replace("{{PHOTO}}", src);
    }

    static String esc(String s) {
        return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * The picture's HTML, made safe to draw: scripts, frames, forms and event handlers are taken out (it is drawn once
     * into an image, and the page only ever reaches public font and library CDNs). Empty when it isn't HTML or is too big.
     */
    static String picture(String html) {
        String h = html == null ? "" : html.trim();
        if (h.length() > MOST_HTML || !h.toLowerCase(Locale.US).contains("<")) {
            return "";
        }
        h = h.replaceAll("(?is)<script\\b.*?(</script\\s*>|$)", "")
                .replaceAll("(?is)<(iframe|object|embed|form)\\b.*?(</\\1\\s*>|$)", "")
                .replaceAll("(?i)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "")
                .replaceAll("(?i)javascript:", "");
        return h.toLowerCase(Locale.US).contains("<html") || h.toLowerCase(Locale.US).contains("<body") || h.contains("<div") ? h : "";
    }

    /**
     * Builds the whole plan into {@code c} (a DRAFT): the outline, then each post. Returns null when it worked, or why
     * it couldn't. {@code stop} is checked between posts.
     */
    public static String build(Campaign c, List<Seat> seats, Brain brain, Progress progress, java.util.function.BooleanSupplier stop,
            TimeZone zone) {
        return build(c, seats, brain, progress, stop, zone, 1);
    }

    /** As above, with {@code workers} calls at once (an API can take several; the phone's model one). */
    public static String build(Campaign c, List<Seat> seats, Brain brain, Progress progress, java.util.function.BooleanSupplier stop,
            TimeZone zone, int workers) {
        if (seats.isEmpty()) {
            return "Choose at least one signed-in account.";
        }
        c.days = Math.max(1, Math.min(MOST_DAYS, c.days));
        c.perDay = Math.max(1, Math.min(MOST_PER_DAY, c.perDay));
        say(progress, "Planning the campaign");
        final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, workers));
        // on an API the campaign's picture design is made while the strategy is (it needs only the brief); the phone's
        // own model, one call at a time, uses the built-in cards instead of spending a long call on a design
        // the pictures use Ibiko's designed layouts (one a theme in turn): more polished than a design written in one
        // call, and no call spent on it
        final java.util.concurrent.Future<String> design = null;
        String reply;
        try {
            reply = brain.ask(OUTLINE_SYSTEM, outlinePrompt(c.brief, seats, c.days, c.perDay), 1500);
        } catch (RuntimeException e) {
            pool.shutdownNow();
            throw e;
        }
        Outline o = outline(reply, seats, c.days, c.perDay, c.brief.products.size());
        if (o == null) {
            pool.shutdownNow();
            return reply == null ? "The brain didn't answer." : "The brain's plan couldn't be read. Try again.";
        }
        c.name = o.name.isEmpty() ? clip(c.brief.offer.isEmpty() ? c.brief.business : c.brief.offer, 40) : o.name;
        c.themes.clear();
        c.themes.addAll(o.themes);
        c.searches.clear();
        c.searches.addAll(o.searches);
        c.items.clear();
        java.util.Map<String, String> network = new java.util.HashMap<String, String>();
        for (Seat s : seats) {
            network.put(s.id, s.network);
        }
        final String brand = clip(c.brief.business.replaceAll("[,.(].*$", ""), 40);
        try {
            // one picture design for the campaign, made alongside the strategy (not one a theme, nor one a post)
            java.util.Map<String, java.util.concurrent.Future<String>> templates = new java.util.HashMap<String, java.util.concurrent.Future<String>>();
            for (Slot slot : o.slots) {
                if (design != null) {
                    templates.put(slot.theme, design);
                }
            }
            List<java.util.concurrent.Future<java.util.Map<Integer, String[]>>> batches = new ArrayList<java.util.concurrent.Future<java.util.Map<Integer, String[]>>>();
            List<List<String[]>> asked = new ArrayList<List<String[]>>();
            for (int at = 0; at < o.slots.size(); at += BATCH) {
                final List<String[]> part = new ArrayList<String[]>();
                for (Slot slot : o.slots.subList(at, Math.min(o.slots.size(), at + BATCH))) {
                    part.add(new String[]{network.get(slot.account), slot.theme, slot.angle
                            + (slot.product >= 0 ? " (shows P" + (slot.product + 1) + ": " + c.brief.products.get(slot.product).name + ")" : "")});
                }
                asked.add(part);
                batches.add(pool.submit(() -> posts(brain.ask(POSTS_SYSTEM, postsPrompt(c.brief, part), 4000), part)));
            }
            int n = 0;
            for (int b = 0; b < batches.size(); b++) {
                if (stop != null && stop.getAsBoolean()) {
                    return "Stopped.";
                }
                int from = b * BATCH;
                say(progress, "Writing posts " + (from + 1) + "\u2013" + Math.min(o.slots.size(), from + BATCH) + " of " + o.slots.size());
                java.util.Map<Integer, String[]> got;
                try {
                    got = batches.get(b).get(10, java.util.concurrent.TimeUnit.MINUTES);
                } catch (Exception e) {
                    got = new java.util.HashMap<Integer, String[]>();
                }
                // posts the brain left out or couldn't write (a busy server, a cut-off reply): asked for once more
                List<Integer> missing = new ArrayList<Integer>();
                for (int i = 0; i < asked.get(b).size(); i++) {
                    if (!got.containsKey(i)) {
                        missing.add(i);
                    }
                }
                if (!missing.isEmpty() && (stop == null || !stop.getAsBoolean())) {
                    List<String[]> again = new ArrayList<String[]>();
                    for (int i : missing) {
                        again.add(asked.get(b).get(i));
                    }
                    say(progress, "Writing posts again that didn't come back (" + missing.size() + ")");
                    java.util.Map<Integer, String[]> second;
                    try {
                        second = posts(brain.ask(POSTS_SYSTEM, postsPrompt(c.brief, again), 4000), again);
                    } catch (RuntimeException e) {
                        second = new java.util.HashMap<Integer, String[]>();
                    }
                    for (int k = 0; k < missing.size(); k++) {
                        if (second.containsKey(k)) {
                            got.put(missing.get(k), second.get(k));
                        } else {
                            c.unwritten++;
                        }
                    }
                }
                for (int i = 0; i < asked.get(b).size(); i++) {
                    String[] p = got.get(i);
                    if (p == null) {
                        continue;
                    }
                    Slot slot = o.slots.get(from + i);
                    String tpl;
                    try {
                        java.util.concurrent.Future<String> f = templates.get(slot.theme);
                        tpl = f == null ? "" : f.get(5, java.util.concurrent.TimeUnit.MINUTES);
                    } catch (Exception e) {
                        tpl = "";
                    }
                    if (tpl.isEmpty()) {
                        int t = Math.max(0, o.themes.indexOf(slot.theme));
                        tpl = DESIGNS[(t + (from + i) / Math.max(1, o.themes.size())) % DESIGNS.length];
                    }
                    n++;
                    Campaign.Item it = new Campaign.Item();
                    it.id = c.id + "-" + n;
                    it.account = slot.account;
                    it.network = network.get(slot.account);
                    it.theme = slot.theme;
                    it.text = WhatsApp.withLink(p[0], c.brief.chat(), maxChars(it.network));
                    Product shown = slot.product >= 0 ? c.brief.products.get(slot.product) : null;
                    it.product = shown == null ? "" : shown.id;
                    it.handoff = slot.account.startsWith(HANDOFF);
                    it.imageHtml = fill(tpl, p[1].isEmpty() ? slot.theme : p[1], p[2], brand,
                            shown == null || shown.photo.isEmpty() ? "" : shown.id, slot.theme);
                    it.at = at(c.startAt, slot.day, slot.minute, zone);
                    c.items.add(it);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        c.items.sort((a, b) -> Long.compare(a.at, b.at));
        return c.items.isEmpty() ? "No post could be written. Try again." : null;
    }

    /** The moment of day {@code day} (1 is the start day) at {@code minute}, in the phone's time zone. */
    static long at(long start, int day, int minute, TimeZone zone) {
        Calendar cal = Calendar.getInstance(zone);
        cal.setTimeInMillis(start);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, day - 1);
        cal.add(Calendar.MINUTE, minute);
        long t = cal.getTimeInMillis();
        return t < start ? start + 15 * 60_000L : t; // a slot already past on day 1 goes out soon instead (the runner spaces them)
    }

    private static void say(Progress p, String what) {
        if (p != null) {
            p.at(what);
        }
    }

    static String clip(String s, int most) {
        String t = s == null ? "" : s.trim();
        return t.length() <= most ? t : t.substring(0, most).trim();
    }
}
