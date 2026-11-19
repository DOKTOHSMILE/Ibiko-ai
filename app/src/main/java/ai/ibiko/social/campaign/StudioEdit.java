package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * The campaign studio's brain turn: the person says what to change ("make the posts shorter", "add three posts about
 * the 12.5kg cylinder on Friday", "drop the ones about refills"), the brain answers with the changes as data, and they
 * are checked and made here: posts still to go out rewritten, added or taken out, the campaign paused or resumed. Posts
 * already out are never touched. Pure Java.
 */
public final class StudioEdit {
    /** The most posts one instruction may add. */
    static final int MOST_ADDED = 20;

    static final String SYSTEM = "You are the campaign studio: you manage one social media campaign for a small business and"
            + " carry out the owner's instructions on its plan. Change only what they ask. Keep every post honest, in the"
            + " brief's tone, within its network's length, with no made-up prices or facts. Reply with one JSON object only:"
            + " {\"reply\":\"one or two friendly sentences saying what you changed\",\"edit\":[{\"n\":3,\"text\":\"new words\"}],"
            + "\"add\":[{\"day\":2,\"time\":\"18:30\",\"account\":\"<id>\",\"theme\":\"...\",\"text\":\"...\",\"headline\":\"...\","
            + "\"line\":\"...\"}],\"remove\":[5],\"pause\":false,\"resume\":false}. Leave out any part you don't use. If they only"
            + " ask a question, answer it in reply and change nothing.";

    /** What an instruction did. */
    public static final class Done {
        public String reply = "";
        public int edited, added, removed;
        public boolean paused, resumed;
        public final List<Campaign.Item> newItems = new ArrayList<Campaign.Item>();

        public boolean changed() {
            return edited + added + removed > 0 || paused || resumed;
        }
    }

    private StudioEdit() {
    }

    /** The posts still to go out, numbered as the brain sees them. */
    static List<Campaign.Item> open(Campaign c) {
        List<Campaign.Item> out = new ArrayList<Campaign.Item>();
        for (Campaign.Item it : c.items) {
            if (it.state == Campaign.State.PLANNED || it.state == Campaign.State.FAILED) {
                out.add(it);
            }
        }
        return out;
    }

    /** The request: the brief, the accounts, the posts still to go out (numbered), and the instruction. */
    public static String prompt(Campaign c, List<CampaignPlanner.Seat> seats, String said, TimeZone zone) {
        StringBuilder s = new StringBuilder(c.brief.words()).append("\n\nCampaign: ").append(c.name).append(", ").append(c.days)
                .append(" days from ").append(new java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.US).format(new java.util.Date(c.startAt)))
                .append(" (day 1). Status: ").append(c.status.name().toLowerCase(java.util.Locale.US)).append(".\nAccounts:\n");
        for (CampaignPlanner.Seat seat : seats) {
            s.append("- ").append(seat.id).append(": ").append(seat.network).append(" (").append(seat.label).append(")\n");
        }
        s.append("\nPosts still to go out:\n");
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.US);
        when.setTimeZone(zone);
        List<Campaign.Item> open = open(c);
        for (int i = 0; i < open.size() && i < 80; i++) {
            Campaign.Item it = open.get(i);
            s.append(i + 1).append(". ").append(when.format(new java.util.Date(it.at))).append(" · ").append(it.network).append(" · ")
                    .append(it.theme).append(" · ").append(it.text.replace('\n', ' ')).append('\n');
        }
        if (open.isEmpty()) {
            s.append("(none)\n");
        }
        return s.append("\nThe owner says: ").append(said == null ? "" : said.trim()).toString();
    }

    /**
     * Makes the brain's changes to {@code c}: edits and removals by number among the open posts, additions checked like
     * the planner's (a chosen account, a real day and time), and a pause or resume. Returns what was done; a reply that
     * isn't JSON becomes the reply alone.
     */
    public static Done apply(Campaign c, String brainReply, List<CampaignPlanner.Seat> seats, TimeZone zone) {
        Done d = new Done();
        Map<String, Object> m = CampaignPlanner.json(brainReply);
        if (m == null) {
            String r = brainReply == null ? "" : brainReply.replaceAll("(?s)<think>.*?(</think>|$)", "").trim();
            d.reply = r.isEmpty() ? "I couldn't work that out. Say it another way?" : (r.length() > 600 ? r.substring(0, 600) + "…" : r);
            return d;
        }
        d.reply = MiniJson.str(m.get("reply"), "").trim();
        List<Campaign.Item> open = open(c);
        for (Object o : MiniJson.list(m.get("edit"))) {
            Map<String, Object> e = MiniJson.obj(o);
            int n = (int) MiniJson.num(e.get("n"), -1);
            String text = MiniJson.str(e.get("text"), "").trim();
            if (n < 1 || n > open.size() || text.isEmpty()) {
                continue;
            }
            Campaign.Item it = open.get(n - 1);
            int most = CampaignPlanner.maxChars(it.network);
            it.text = WhatsApp.withLink(text.length() > most ? text.substring(0, most - 1).trim() + "…" : text, c.brief.chat(), most);
            d.edited++;
        }
        java.util.Set<String> drop = new java.util.HashSet<String>();
        for (Object o : MiniJson.list(m.get("remove"))) {
            int n = (int) MiniJson.num(o, -1);
            if (n >= 1 && n <= open.size()) {
                drop.add(open.get(n - 1).id);
            }
        }
        if (!drop.isEmpty()) {
            int before = c.items.size();
            c.items.removeIf(it -> drop.contains(it.id));
            d.removed = before - c.items.size();
        }
        java.util.Map<String, String> network = new java.util.HashMap<String, String>();
        for (CampaignPlanner.Seat s : seats) {
            network.put(s.id, s.network);
        }
        String brand = c.brief.business.replaceAll("[,.(].*$", "").trim();
        int serial = c.items.size();
        for (Object o : MiniJson.list(m.get("add"))) {
            if (d.added >= MOST_ADDED) {
                break;
            }
            Map<String, Object> a = MiniJson.obj(o);
            String account = MiniJson.str(a.get("account"), "");
            int day = (int) MiniJson.num(a.get("day"), -1);
            int minute = CampaignPlanner.minute(MiniJson.str(a.get("time"), ""));
            String text = MiniJson.str(a.get("text"), "").trim();
            if (!network.containsKey(account) || day < 1 || day > CampaignPlanner.MOST_DAYS || minute < 0 || text.isEmpty()) {
                continue;
            }
            Campaign.Item it = new Campaign.Item();
            it.id = c.id + "-s" + (++serial) + "-" + Long.toString(System.nanoTime() % 100000, 36);
            it.account = account;
            it.network = network.get(account);
            it.handoff = account.startsWith(CampaignPlanner.HANDOFF);
            it.theme = MiniJson.str(a.get("theme"), "");
            int most = CampaignPlanner.maxChars(it.network);
            it.text = WhatsApp.withLink(text.length() > most ? text.substring(0, most - 1).trim() + "…" : text, c.brief.chat(), most);
            String headline = MiniJson.str(a.get("headline"), "");
            it.imageHtml = CampaignPlanner.fill(null, headline.isEmpty() ? it.theme : headline, MiniJson.str(a.get("line"), ""), brand);
            it.at = Math.max(CampaignPlanner.at(c.startAt, day, minute, zone), System.currentTimeMillis() + 10 * 60_000L);
            c.items.add(it);
            d.newItems.add(it);
            d.added++;
        }
        c.items.sort((x, y) -> Long.compare(x.at, y.at));
        if (Boolean.TRUE.equals(m.get("pause")) && c.status == Campaign.Status.ACTIVE) {
            c.status = Campaign.Status.PAUSED;
            d.paused = true;
        } else if (Boolean.TRUE.equals(m.get("resume")) && c.status == Campaign.Status.PAUSED) {
            c.status = Campaign.Status.ACTIVE;
            d.resumed = true;
        }
        if (d.reply.isEmpty()) {
            d.reply = d.changed() ? "Done." : "Nothing needed changing.";
        }
        return d;
    }
}
