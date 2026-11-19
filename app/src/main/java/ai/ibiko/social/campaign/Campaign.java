package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One social media campaign: what the person asked for (the brief), the accounts it runs on, and the whole plan the
 * brain built from it (themes, a dated post calendar with each post's words and picture, and the searches for people
 * who may need the offer). Nothing runs until the person approves the plan; then due posts go out by themselves from
 * their own account. Pure Java; saved as JSON by {@link CampaignStore}.
 */
public final class Campaign {
    /** Where a campaign stands. Only an ACTIVE campaign posts. */
    public enum Status { DRAFT, ACTIVE, PAUSED, DONE }

    /** Where one planned post stands. */
    public enum State { PLANNED, POSTED, FAILED, SKIPPED }

    /** What the person asked for, in their words. */
    public static final class Brief {
        public String business = "", offer = "", audience = "", goal = "", tone = "", link = "";
        /** The business's WhatsApp: a number with its country code, or a wa.me link. */
        public String whatsapp = "";
        /** The catalog products this campaign features, as they were when it was planned. */
        public final List<Product> products = new ArrayList<Product>();
        /** What earlier campaigns showed works (themes, times), for the brain; set when planning. */
        public String lessons = "";

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("business", business);
            m.put("offer", offer);
            m.put("audience", audience);
            m.put("goal", goal);
            m.put("tone", tone);
            m.put("link", link);
            m.put("whatsapp", whatsapp);
            List<Object> ps = new ArrayList<Object>();
            for (Product p : products) {
                ps.add(p.json());
            }
            m.put("products", ps);
            m.put("lessons", lessons);
            return m;
        }

        static Brief of(Object o) {
            Map<String, Object> m = MiniJson.obj(o);
            Brief b = new Brief();
            b.business = MiniJson.str(m.get("business"), "");
            b.offer = MiniJson.str(m.get("offer"), "");
            b.audience = MiniJson.str(m.get("audience"), "");
            b.goal = MiniJson.str(m.get("goal"), "");
            b.tone = MiniJson.str(m.get("tone"), "");
            b.link = MiniJson.str(m.get("link"), "");
            b.whatsapp = MiniJson.str(m.get("whatsapp"), "");
            for (Object p : MiniJson.list(m.get("products"))) {
                b.products.add(Product.of(p));
            }
            b.lessons = MiniJson.str(m.get("lessons"), "");
            return b;
        }

        /** The brief as lines for the brain. */
        public String words() {
            StringBuilder s = new StringBuilder();
            line(s, "Business", business);
            line(s, "Offer", offer);
            line(s, "Audience", audience);
            line(s, "Goal", goal);
            line(s, "Tone", tone);
            line(s, "Link", link);
            line(s, "WhatsApp chat link", chat());
            if (!products.isEmpty()) {
                s.append("Products (use these exact names and prices; never make up a price):\n");
                for (int i = 0; i < products.size(); i++) {
                    s.append("P").append(i + 1).append(". ").append(products.get(i).line()).append('\n');
                }
            }
            line(s, "What worked in earlier campaigns", lessons);
            return s.toString().trim();
        }

        /** The click-to-chat link for the business's WhatsApp, or "" when none was given. */
        public String chat() {
            String l = WhatsApp.link(whatsapp, WhatsApp.greeting(this));
            return l == null ? "" : l;
        }

        private static void line(StringBuilder s, String name, String v) {
            if (v != null && !v.trim().isEmpty()) {
                s.append(name).append(": ").append(v.trim()).append('\n');
            }
        }
    }

    /** One planned post: when, from which account, the words, and the picture (HTML, then the rendered file). */
    public static final class Item {
        public String id = "", account = "", network = "", text = "", imageHtml = "", imagePath = "", result = "", theme = "";
        public long at;
        public State state = State.PLANNED;
        /** The picture's words, the model's design for it, and the seed its fresh design is drawn from. */
        public String headline = "", line = "", design = "";
        public long seed;
        /** The model designed this picture itself (the image generator), rather than the formula. */
        public boolean designed;
        /** The catalog product it shows ({@link Product#id}), or "". */
        public String product = "";
        /** A network Ibiko can't post on itself (Instagram, LinkedIn): offered to the person to share when due. */
        public boolean handoff;
        /** Whether the post was seen on the network after posting: 1 seen, -1 looked for and not there, 0 not checked yet. */
        public int checked;
        /** The post's own address on the network, when the check found it. */
        public String url = "";

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id);
            m.put("at", (double) at);
            m.put("account", account);
            m.put("network", network);
            m.put("theme", theme);
            m.put("text", text);
            m.put("imageHtml", imageHtml);
            m.put("imagePath", imagePath);
            m.put("state", state.name());
            m.put("result", result);
            m.put("product", product);
            m.put("headline", headline);
            m.put("line", line);
            m.put("design", design);
            m.put("seed", String.valueOf(seed));
            m.put("designed", designed);
            m.put("handoff", handoff);
            m.put("checked", (double) checked);
            m.put("url", url);
            return m;
        }

        static Item of(Object o) {
            Map<String, Object> m = MiniJson.obj(o);
            Item i = new Item();
            i.id = MiniJson.str(m.get("id"), "");
            i.at = (long) MiniJson.num(m.get("at"), 0);
            i.account = MiniJson.str(m.get("account"), "");
            i.network = MiniJson.str(m.get("network"), "");
            i.theme = MiniJson.str(m.get("theme"), "");
            i.text = MiniJson.str(m.get("text"), "");
            i.imageHtml = MiniJson.str(m.get("imageHtml"), "");
            i.imagePath = MiniJson.str(m.get("imagePath"), "");
            i.state = state(MiniJson.str(m.get("state"), "PLANNED"));
            i.result = MiniJson.str(m.get("result"), "");
            i.product = MiniJson.str(m.get("product"), "");
            i.headline = MiniJson.str(m.get("headline"), "");
            i.line = MiniJson.str(m.get("line"), "");
            i.design = MiniJson.str(m.get("design"), "");
            i.designed = Boolean.TRUE.equals(m.get("designed"));
            try {
                i.seed = Long.parseLong(MiniJson.str(m.get("seed"), "0"));
            } catch (NumberFormatException e) {
                i.seed = 0;
            }
            i.handoff = Boolean.TRUE.equals(m.get("handoff"));
            i.checked = (int) MiniJson.num(m.get("checked"), 0);
            i.url = MiniJson.str(m.get("url"), "");
            return i;
        }

        private static State state(String s) {
            try {
                return State.valueOf(s);
            } catch (IllegalArgumentException e) {
                return State.PLANNED;
            }
        }
    }

    public String id = "", name = "";
    public Brief brief = new Brief();
    /** The accounts this campaign posts from (their ids in {@code SocialAccounts}). */
    public final List<String> accounts = new ArrayList<String>();
    public long createdAt, startAt;
    public int days = 7, perDay = 1;
    public Status status = Status.DRAFT;
    /** Reply to comments on the campaign's own posts. */
    public boolean engage = true;
    /** Look for people who may need the offer, and draft a pitch for each. */
    public boolean prospect = true;
    /** Send drafted pitches without asking (off: each waits in Leads for a tap). */
    public boolean autoPitch = false;
    /** When each post goes out, a notification offers it to share to WhatsApp Status or a group in one tap. */
    public boolean whatsappShare = false;
    /** Networks Ibiko prepares posts for and the person shares from the app (Instagram, LinkedIn). */
    public final List<String> handoff = new ArrayList<String>();
    /** Posts the planner couldn't write in this planning (not kept: said once when the plan is ready). */
    public transient int unwritten;
    /** The chat that is this campaign's own: its brief, its planning and its plan (a chat id), or "". */
    public String chat = "";
    public final List<String> themes = new ArrayList<String>();
    /** What to search for: posts by people who may need the offer ("need a logo designer in Lagos"). */
    public final List<String> searches = new ArrayList<String>();
    public final List<Item> items = new ArrayList<Item>();

    /** The posts still to go out, earliest first. */
    public List<Item> planned() {
        List<Item> out = new ArrayList<Item>();
        for (Item i : items) {
            if (i.state == State.PLANNED) {
                out.add(i);
            }
        }
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    /** How many posts are in each state, for the screen ("3 posted · 11 planned · 1 failed"). */
    public String progress() {
        int posted = 0, planned = 0, failed = 0;
        for (Item i : items) {
            if (i.state == State.POSTED) {
                posted++;
            } else if (i.state == State.PLANNED) {
                planned++;
            } else if (i.state == State.FAILED) {
                failed++;
            }
        }
        return posted + " posted · " + planned + " planned" + (failed > 0 ? " · " + failed + " failed" : "");
    }

    Map<String, Object> json() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("name", name);
        m.put("brief", brief.json());
        m.put("accounts", new ArrayList<Object>(accounts));
        m.put("createdAt", (double) createdAt);
        m.put("startAt", (double) startAt);
        m.put("days", (double) days);
        m.put("perDay", (double) perDay);
        m.put("status", status.name());
        m.put("engage", engage);
        m.put("prospect", prospect);
        m.put("autoPitch", autoPitch);
        m.put("whatsappShare", whatsappShare);
        m.put("handoff", new ArrayList<Object>(handoff));
        m.put("chat", chat);
        m.put("themes", new ArrayList<Object>(themes));
        m.put("searches", new ArrayList<Object>(searches));
        List<Object> its = new ArrayList<Object>();
        for (Item i : items) {
            its.add(i.json());
        }
        m.put("items", its);
        return m;
    }

    static Campaign of(Object o) {
        Map<String, Object> m = MiniJson.obj(o);
        Campaign c = new Campaign();
        c.id = MiniJson.str(m.get("id"), "");
        c.name = MiniJson.str(m.get("name"), "");
        c.brief = Brief.of(m.get("brief"));
        c.accounts.addAll(MiniJson.strings(m.get("accounts")));
        c.createdAt = (long) MiniJson.num(m.get("createdAt"), 0);
        c.startAt = (long) MiniJson.num(m.get("startAt"), 0);
        c.days = (int) MiniJson.num(m.get("days"), 7);
        c.perDay = (int) MiniJson.num(m.get("perDay"), 1);
        try {
            c.status = Status.valueOf(MiniJson.str(m.get("status"), "DRAFT"));
        } catch (IllegalArgumentException e) {
            c.status = Status.PAUSED; // a value this build doesn't know: nothing runs until the person looks
        }
        c.engage = !Boolean.FALSE.equals(m.get("engage"));
        c.prospect = !Boolean.FALSE.equals(m.get("prospect"));
        c.autoPitch = Boolean.TRUE.equals(m.get("autoPitch"));
        c.whatsappShare = Boolean.TRUE.equals(m.get("whatsappShare"));
        c.handoff.addAll(MiniJson.strings(m.get("handoff")));
        c.chat = MiniJson.str(m.get("chat"), "");
        c.themes.addAll(MiniJson.strings(m.get("themes")));
        c.searches.addAll(MiniJson.strings(m.get("searches")));
        for (Object i : MiniJson.list(m.get("items"))) {
            c.items.add(Item.of(i));
        }
        return c;
    }
}
