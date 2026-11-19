package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Someone a campaign could help, found in a public post, or a comment on the campaign's own posts that deserves an
 * answer: the post, why it fits, and the reply the brain drafted. A pitch waits for the person's tap unless the
 * campaign was told to send pitches itself; a reply to a comment on our own posts goes out within the campaign's
 * daily cap. Pure Java.
 */
public final class Lead {
    /** A person who may need the offer, or a comment on the business's own post. */
    public enum Kind { LEAD, REPLY }

    public enum State { NEW, SENT, DISMISSED, FAILED }

    /** After a pitch went out: where things stand with the person (the person sets it; a reminder asks). */
    public enum Stage { CONTACTED, TALKING, BOUGHT, NOT_INTERESTED }

    /** A contacted lead not heard from in this long gets a reminder to follow up. */
    public static final long FOLLOW_UP_MS = 2 * 24 * 3_600_000L;

    public String id = "", campaign = "", account = "", network = "", url = "", snippet = "", why = "", reply = "", result = "";
    public double fit;
    public long at, sentAt;
    public Kind kind = Kind.LEAD;
    public State state = State.NEW;
    public Stage stage = Stage.CONTACTED;
    /** When to remind the person to follow up (0: no reminder), and when they last were. */
    public long followUpAt, remindedAt;
    /** The person's own note ("asked for the price in blue"). */
    public String note = "";

    Map<String, Object> json() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("campaign", campaign);
        m.put("account", account);
        m.put("network", network);
        m.put("url", url);
        m.put("snippet", snippet);
        m.put("why", why);
        m.put("reply", reply);
        m.put("result", result);
        m.put("fit", fit);
        m.put("at", (double) at);
        m.put("sentAt", (double) sentAt);
        m.put("kind", kind.name());
        m.put("state", state.name());
        m.put("stage", stage.name());
        m.put("followUpAt", (double) followUpAt);
        m.put("remindedAt", (double) remindedAt);
        m.put("note", note);
        return m;
    }

    static Lead of(Object o) {
        Map<String, Object> m = MiniJson.obj(o);
        Lead l = new Lead();
        l.id = MiniJson.str(m.get("id"), "");
        l.campaign = MiniJson.str(m.get("campaign"), "");
        l.account = MiniJson.str(m.get("account"), "");
        l.network = MiniJson.str(m.get("network"), "");
        l.url = MiniJson.str(m.get("url"), "");
        l.snippet = MiniJson.str(m.get("snippet"), "");
        l.why = MiniJson.str(m.get("why"), "");
        l.reply = MiniJson.str(m.get("reply"), "");
        l.result = MiniJson.str(m.get("result"), "");
        l.fit = MiniJson.num(m.get("fit"), 0);
        l.at = (long) MiniJson.num(m.get("at"), 0);
        l.sentAt = (long) MiniJson.num(m.get("sentAt"), 0);
        l.followUpAt = (long) MiniJson.num(m.get("followUpAt"), 0);
        l.remindedAt = (long) MiniJson.num(m.get("remindedAt"), 0);
        l.note = MiniJson.str(m.get("note"), "");
        try {
            l.stage = Stage.valueOf(MiniJson.str(m.get("stage"), "CONTACTED"));
        } catch (IllegalArgumentException e) {
            l.stage = Stage.CONTACTED;
        }
        try {
            l.kind = Kind.valueOf(MiniJson.str(m.get("kind"), "LEAD"));
            l.state = State.valueOf(MiniJson.str(m.get("state"), "NEW"));
        } catch (IllegalArgumentException e) {
            l.state = State.DISMISSED; // a value this build doesn't know: never sent by itself
        }
        return l;
    }

    /** Where the person moved this lead; a reminder is set again only while they are still waiting to hear. */
    public void moveTo(Stage s, long now) {
        stage = s;
        followUpAt = s == Stage.CONTACTED || s == Stage.TALKING ? now + FOLLOW_UP_MS : 0;
    }

    /** True when a sent pitch is due a follow-up reminder. */
    public boolean dueFollowUp(long now) {
        return kind == Kind.LEAD && state == State.SENT && followUpAt > 0 && followUpAt <= now
                && (stage == Stage.CONTACTED || stage == Stage.TALKING);
    }

    /** The stage in words for the screen. */
    public String stageShown() {
        switch (stage) {
            case TALKING:
                return "Talking";
            case BOUGHT:
                return "Bought";
            case NOT_INTERESTED:
                return "Not interested";
            default:
                return "Contacted";
        }
    }
}
