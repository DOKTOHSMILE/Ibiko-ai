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
    static final int MOST_HTML = 24_000;

    private static final String OUTLINE_SYSTEM = "You are a social media campaign strategist for a small business. You plan"
            + " honest, useful campaigns that attract real customers: value first, a clear offer, a call to action, no"
            + " spam, no fake claims, no fake reviews, no engagement bait. Reply with one JSON object only, no prose.";

    private static final String POST_SYSTEM = "You write one social media post for a small business campaign, and design"
            + " its picture as a single self-contained HTML document. Reply with one JSON object only, no prose.";

    private CampaignPlanner() {
    }

    /** The request for the outline. */
    static String outlinePrompt(Campaign.Brief brief, List<Seat> seats, int days, int perDay) {
        StringBuilder s = new StringBuilder();
        s.append(brief.words()).append("\n\nAccounts to post from (use their ids):\n");
        for (Seat seat : seats) {
            s.append("- ").append(seat.id).append(": ").append(seat.network).append(" (").append(seat.label).append(")\n");
        }
        s.append("\nThe campaign runs ").append(days).append(" days with ").append(perDay)
                .append(" post(s) a day on each account. Plan it as JSON:\n")
                .append("{\"name\":\"short campaign name\",\"themes\":[\"3 to 6 content themes\"],")
                .append("\"searches\":[\"3 to 8 short searches that find public posts by people who need this offer\"],")
                .append("\"slots\":[{\"day\":1,\"time\":\"09:30\",\"account\":\"<id>\",\"theme\":\"one of the themes\",")
                .append("\"angle\":\"what this post says, in one line\"}]}\n")
                .append("Give each account its own slots, at the hours its audience is active, varied (not every post the same")
                .append(" time). Mix value, proof and offer posts; no two posts say the same thing.");
        return s.toString();
    }

    /** The request for one post. */
    static String postPrompt(Campaign.Brief brief, String network, String theme, String angle) {
        int most = maxChars(network);
        return brief.words() + "\n\nWrite one " + network + " post. Theme: " + theme + ". Angle: " + angle + ".\n"
                + "At most " + most + " characters, in the brief's tone, with a clear call to action"
                + (brief.link.isEmpty() ? "" : " and the link") + "; at most 3 hashtags. No made-up facts, prices or reviews.\n"
                + "Design its picture: one HTML document, 1080 by 1080 pixels, its own <style> only (no script, no outside"
                + " images; Google Fonts may be linked). Bold, readable, the offer's key words large, the business name.\n"
                + "JSON: {\"text\":\"the post\",\"image\":\"<!doctype html>...\"}";
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
    }

    /** The outline as the brain wrote it, checked. */
    static final class Outline {
        String name = "";
        final List<String> themes = new ArrayList<String>(), searches = new ArrayList<String>();
        final List<Slot> slots = new ArrayList<Slot>();
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
        for (String t : MiniJson.strings(m.get("themes"))) {
            if (!t.trim().isEmpty() && o.themes.size() < 8) {
                o.themes.add(clip(t.trim(), 80));
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

    /** One post's words and picture, checked; null when the reply holds no words. */
    static String[] post(String reply, String network) {
        Map<String, Object> m = json(reply);
        if (m == null) {
            return null;
        }
        String text = MiniJson.str(m.get("text"), "").trim();
        if (text.isEmpty()) {
            return null;
        }
        int most = maxChars(network);
        if (text.length() > most) {
            int cut = text.lastIndexOf(' ', most - 1);
            text = text.substring(0, cut > most / 2 ? cut : most - 1).trim() + "\u2026";
        }
        return new String[]{text, picture(MiniJson.str(m.get("image"), ""))};
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
        if (seats.isEmpty()) {
            return "Choose at least one signed-in account.";
        }
        c.days = Math.max(1, Math.min(MOST_DAYS, c.days));
        c.perDay = Math.max(1, Math.min(MOST_PER_DAY, c.perDay));
        say(progress, "Planning the campaign");
        String reply = brain.ask(OUTLINE_SYSTEM, outlinePrompt(c.brief, seats, c.days, c.perDay), 4000);
        Outline o = outline(reply, seats, c.days, c.perDay);
        if (o == null) {
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
        int n = 0;
        for (Slot slot : o.slots) {
            if (stop != null && stop.getAsBoolean()) {
                return "Stopped.";
            }
            n++;
            say(progress, "Writing post " + n + " of " + o.slots.size());
            String net = network.get(slot.account);
            String[] got = post(brain.ask(POST_SYSTEM, postPrompt(c.brief, net, slot.theme, slot.angle), 3000), net);
            if (got == null) {
                continue; // one post the brain couldn't write: the rest of the plan stands
            }
            Campaign.Item it = new Campaign.Item();
            it.id = c.id + "-" + n;
            it.account = slot.account;
            it.network = net;
            it.theme = slot.theme;
            it.text = got[0];
            it.imageHtml = got[1];
            it.at = at(c.startAt, slot.day, slot.minute, zone);
            c.items.add(it);
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
