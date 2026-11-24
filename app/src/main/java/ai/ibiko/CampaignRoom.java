package ai.ibiko;

import java.util.List;
import java.util.Locale;

import ai.ibiko.chat.ChatBook;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignChat;
import ai.ibiko.social.campaign.CampaignDraft;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.CampaignStudio;

/**
 * A campaign's studio conversation as a chat like any other: a page of the one chat screen, with one purpose. What the
 * owner sends takes the same way as every message ({@link BrainGate}): it shows as theirs, the working steps show while
 * the studio changes the plan, and the studio's answer comes back as a reply (the plan with buttons to review it, to see
 * the posts, to ask how it's doing). Planning that goes on in the background shows as steps on the chat too. The studio
 * keeps its own record of the talk ({@link CampaignChat}), which it reads to follow what was asked before; this page
 * shows it.
 */
public final class CampaignRoom {
    /** The extra on a chat-screen intent that opens a campaign's conversation. */
    public static final String EXTRA = "open_campaign";
    private static final String PAGE = "campaign_page_", SEEN = "campaign_seen_", PURPOSE = "chat_campaign_";

    /** An intent that opens the campaign's conversation on the chat screen. */
    public static android.content.Intent intent(android.content.Context c, String campaignId) {
        return new android.content.Intent(c, MainActivity.class).putExtra(EXTRA, campaignId)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    private final MainActivity a;
    /** The campaign the studio is working on for a message sent here, or null. */
    private volatile String working;
    private String planningShown;
    private boolean ticking;

    CampaignRoom(MainActivity a) {
        this.a = a;
    }

    /** Whether the studio is working on a message sent here: Stop and the steps show, as for any reply. */
    boolean working() {
        return working != null;
    }

    /** The campaign whose conversation is on screen, or null. */
    String onScreen() {
        if (a.prefs == null || a.chats == null || a.chats.current() == null) {
            return null;
        }
        return a.prefs.getString(PURPOSE + a.chats.current().id, null);
    }

    /** The chat screen was opened for a campaign ({@link #intent}): its conversation is shown. */
    void fromIntent(android.content.Intent i) {
        String id = i == null ? null : i.getStringExtra(EXTRA);
        if (id == null) {
            return;
        }
        i.removeExtra(EXTRA);
        open(id);
    }

    /** Opens a campaign's conversation as the chat on screen, made the first time from the studio's record. */
    void open(String campaignId) {
        Campaign c = CampaignRunner.store(a).get(campaignId);
        if (c == null || a.chats == null) {
            return;
        }
        String page = a.prefs.getString(PAGE + campaignId, null);
        if (section(page) == null) {
            ChatBook.Section s = a.chats.openOwn(c.name == null || c.name.trim().isEmpty() ? "Campaign" : c.name.trim(), "ibiko", false);
            page = s.id;
            a.prefs.edit().putString(PAGE + campaignId, page).putString(PURPOSE + page, campaignId).putInt(SEEN + campaignId, 0).apply();
        }
        if (a.chatPane != null) {
            a.chatPane.openChatById(page);
        } else {
            a.chats.use(page);
        }
        sync();
        if (!ticking) {
            ticking = true;
            a.handler.postDelayed(tick, 1500);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (a.isFinishing()) {
                ticking = false;
                return;
            }
            try {
                sync();
            } catch (RuntimeException e) {
                // shown at the next look
            }
            a.handler.postDelayed(this, 2000);
        }
    };

    /**
     * What the studio said that this page doesn't show yet (planning in the background, a plan that's ready), and the
     * planning under way as working steps.
     */
    void sync() {
        String id = onScreen();
        if (id == null) {
            return;
        }
        Campaign c = CampaignRunner.store(a).get(id);
        if (c == null) {
            return;
        }
        String planning = CampaignDraft.planning(id);
        if (working == null && a.chatPane != null) {
            if (planning != null && !planning.equals(planningShown)) {
                a.chatPane.activityStep(planning); // the plan being made, step by step, as any work shows
                planningShown = planning;
            } else if (planning == null && planningShown != null) {
                planningShown = null;
                a.chatPane.activityEnd();
            }
        }
        if (working == null) {
            showNew(id, c);
        }
    }

    /** The studio's messages this page hasn't shown, each as the chat shows a message of its kind. */
    private void showNew(String id, Campaign c) {
        String page = a.prefs.getString(PAGE + id, null);
        if (page == null) {
            return;
        }
        List<CampaignChat.Entry> all = CampaignStudio.chat(a, id).all();
        int seen = a.prefs.getInt(SEEN + id, 0);
        if (seen >= all.size()) {
            return;
        }
        for (int i = seen; i < all.size(); i++) {
            CampaignChat.Entry e = all.get(i);
            if (CampaignChat.YOU.equals(e.who)) {
                a.chats.addTo(page, "user", "text", "brief".equals(e.kind) ? "Campaign brief\n\n" + e.text.replaceFirst("^New social media campaign\\s*", "")
                        : e.text, "ibiko");
            } else {
                a.chats.addTo(page, "assistant", "text", shown(c, e), "ibiko");
            }
        }
        a.prefs.edit().putInt(SEEN + id, all.size()).apply();
        if (a.chatPane != null) {
            a.chatPane.show();
        }
    }

    /** A studio message as a reply: a ready plan comes with buttons to review it, see the posts, ask how it's doing. */
    private static String shown(Campaign c, CampaignChat.Entry e) {
        if ("plan".equals(e.kind)) {
            boolean review = c.status == Campaign.Status.DRAFT && !c.items.isEmpty();
            return "The plan is ready. " + e.text + "\n[buttons: " + (review ? "Review and approve | " : "") + "Show the posts | How is it doing?]";
        }
        return e.text;
    }

    /**
     * A message sent in a campaign's conversation, after it is shown as the owner's (the usual way): done here, true;
     * false when the chat on screen isn't a campaign's.
     */
    boolean handle(final String text) {
        final String id = onScreen();
        if (id == null || text == null) {
            return false;
        }
        final Campaign c = CampaignRunner.store(a).get(id);
        if (c == null) {
            return false;
        }
        String low = text.trim().toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "");
        final String agent = a.stage == null ? "ibiko" : a.stage.agent();
        if (low.matches("review(?: and approve)?(?: the plan)?|approve(?: the plan)?|review the posts")) {
            // the review sheet: every post, to change and approve
            ai.ibiko.chat.ui.CampaignReview.show(a, c, () -> {
                seenUpTo(id);
                a.play(ai.ibiko.turn.LocalTurn.speakAs(agent, state(CampaignRunner.store(a).get(id))), null);
            });
            a.play(ai.ibiko.turn.LocalTurn.speakAs(agent, "Here's the plan to review: change any post, then approve it to start."), null);
            return true;
        }
        if (low.matches("(?:show(?: me)?|list|see)(?: the| all the| all)? posts|the posts|posts")) {
            a.play(ai.ibiko.turn.LocalTurn.speakAs(agent, posts(c)), null);
            return true;
        }
        if (low.matches("(?:open|show)(?: the)? (?:campaign|dashboard|campaign dashboard|campaign settings|settings)|campaign settings|dashboard")) {
            a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.CampaignActivity.class).putExtra(ai.ibiko.chat.ui.CampaignActivity.EXTRA_ID, id));
            a.play(ai.ibiko.turn.LocalTurn.speakAs(agent, "Opening the campaign: its posts, pictures, settings and pause."), null);
            return true;
        }
        // an instruction for the studio: its steps show while it works, and its answer comes back as the reply
        seenUpTo(id);
        working = id;
        a.refreshSend();
        if (a.chatPane != null) {
            a.chatPane.activityStep("Reading your instruction for " + (c.name == null ? "the campaign" : c.name));
            a.chatPane.activityStep("Changing the plan with " + CampaignStudio.chosen(a).shown());
        }
        final int before = CampaignStudio.chat(a, id).all().size() + 1; // with the owner's message, added by the studio
        CampaignStudio.instruct(a, id, text.trim(), () -> a.handler.post(() -> {
            working = null;
            if (a.chatPane != null) {
                a.chatPane.activityEnd();
            }
            List<CampaignChat.Entry> all = CampaignStudio.chat(a, id).all();
            Campaign now = CampaignRunner.store(a).get(id);
            StringBuilder said = new StringBuilder();
            for (int i = Math.min(before, all.size()); i < all.size(); i++) {
                if (!CampaignChat.YOU.equals(all.get(i).who)) {
                    said.append(said.length() == 0 ? "" : "\n\n").append(now == null ? all.get(i).text : shown(now, all.get(i)));
                }
            }
            a.prefs.edit().putInt(SEEN + id, all.size()).apply();
            a.refreshSend();
            if (said.length() > 0) {
                a.play(ai.ibiko.turn.LocalTurn.speakAs(agent, said.toString()), null); // the reply, as every reply is shown
            }
        }));
        return true;
    }

    /** The studio's record up to now counts as shown (the owner's message is on the page already). */
    private void seenUpTo(String id) {
        a.prefs.edit().putInt(SEEN + id, CampaignStudio.chat(a, id).all().size() + 1).apply();
    }

    /** Where the campaign stands, in a line. */
    private static String state(Campaign c) {
        if (c == null) {
            return "The campaign is gone.";
        }
        int posted = 0;
        for (Campaign.Item it : c.items) {
            posted += it.state == Campaign.State.POSTED ? 1 : 0;
        }
        return (c.name == null ? "The campaign" : c.name) + " is " + c.status.name().toLowerCase(Locale.ROOT) + ": " + posted + " of " + c.items.size()
                + " posts out.";
    }

    /** The campaign's posts, newest plans last: when, where, what, and the link of each that went out. */
    static String posts(Campaign c) {
        if (c.items.isEmpty()) {
            return "No posts yet: the plan isn't made. Tell me about the campaign and I'll plan it.";
        }
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("EEE d MMM, h:mm a", Locale.US);
        StringBuilder b = new StringBuilder(state(c)).append("\n");
        long now = System.currentTimeMillis();
        boolean comingShown = false;
        for (Campaign.Item it : c.items) {
            if (!comingShown && it.state != Campaign.State.POSTED && it.at >= now) {
                b.append("\nComing up:");
                comingShown = true;
            }
            String text = it.text == null ? "" : it.text.replaceAll("\\s+", " ").trim();
            b.append("\n• ").append(when.format(new java.util.Date(it.at))).append(" · ").append(it.network).append(" · ")
                    .append(it.state.name().toLowerCase(Locale.ROOT)).append(": ").append(text.length() > 140 ? text.substring(0, 140) + "…" : text);
            if (it.url != null && !it.url.isEmpty()) {
                b.append("\n  [Open the post on ").append(network(it.network)).append("](").append(it.url).append(")");
            }
        }
        return b.append("\n[buttons: How is it doing? | Open the campaign]").toString();
    }

    private static String network(String n) {
        return n == null || n.isEmpty() ? "the site" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private ChatBook.Section section(String id) {
        if (id == null || a.chats == null) {
            return null;
        }
        for (ChatBook.Section s : a.chats.sections()) {
            if (s.id.equals(id) && !s.archived) {
                return s;
            }
        }
        return null;
    }
}
