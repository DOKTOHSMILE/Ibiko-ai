package ai.ibiko.social.campaign;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import ai.ibiko.llm.LocalModel;
import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;

/**
 * The campaign studio: campaigns are planned and managed here, on their own, apart from every chat. A brief starts
 * the plan in the background (the campaign's own conversation shows it going); the owner then talks to the studio in
 * that conversation ("make them shorter", "add two posts on Friday") and it changes the plan. Uses the app's one
 * brain ({@link ai.ibiko.llm.ModelHub}) as background work: the API when one is on, never holding up a chat.
 */
public final class CampaignStudio {
    /** Campaigns whose instruction the studio is working on now. */
    private static final Set<String> thinking = ConcurrentHashMap.newKeySet();

    private CampaignStudio() {
    }

    public static CampaignChat chat(Context c, String campaignId) {
        return new CampaignChat(new File(c.getFilesDir(), "campaigns"), campaignId);
    }

    /** True while the studio is answering an instruction for this campaign. */
    public static boolean thinking(String campaignId) {
        return thinking.contains(campaignId);
    }

    /** The brain for campaign work, or null when none is ready (no API on and no model on the phone). */
    static CampaignPlanner.Brain brain(Context c) {
        final LocalModel model = ai.ibiko.llm.ModelHub.get(c).model;
        if (model == null || !(model.ready() || model.remoteOn())) {
            return null;
        }
        return background(model);
    }

    /** {@code model} as campaign work: on the API when one is on, else the phone's model; background either way. */
    public static CampaignPlanner.Brain background(final LocalModel model) {
        return (system, prompt, tokens) -> LocalModel.inBackground(() -> {
            String got = model.remoteOn() ? model.remoteTask(system, prompt, tokens) : null;
            return got != null ? got : model.task(system, prompt, tokens, null);
        });
    }

    /**
     * The campaign's accounts as the planner sees them (signed-in Facebook and X accounts), and with {@code handoff}
     * the networks the person shares on from the app.
     */
    public static List<CampaignPlanner.Seat> seats(Context ctx, Campaign c, boolean handoff) {
        List<CampaignPlanner.Seat> out = new ArrayList<CampaignPlanner.Seat>();
        if (handoff) {
            for (String n : c.handoff) {
                out.add(new CampaignPlanner.Seat(CampaignPlanner.HANDOFF + n, n, CampaignRunner.shown(n) + " (you tap Share)"));
            }
        }
        SocialAccounts all = SocialWeb.accounts(ctx);
        for (String id : c.accounts) {
            SocialAccounts.Account acc = all.get(id);
            if (acc != null && acc.signedIn && (acc.platform == Platform.X || acc.platform == Platform.FACEBOOK)) {
                out.add(new CampaignPlanner.Seat(acc.id, acc.platform == Platform.X ? "x" : "facebook", acc.label));
            }
        }
        return out;
    }

    /**
     * The brief waiting from the form ({@link CampaignDraft#offer}): kept as a draft, its conversation started with the
     * brief, and planned in the background. Returns its id, or null when there was none.
     */
    public static String startWaiting(Context ctx) {
        final Campaign c = CampaignDraft.take();
        if (c == null) {
            return null;
        }
        final Context app = ctx.getApplicationContext();
        String title = c.brief.offer.isEmpty() ? c.brief.business : c.brief.offer;
        title = title.replaceAll("\\s+", " ").trim();
        c.name = title.length() > 50 ? title.substring(0, 50).trim() + "…" : title;
        try {
            CampaignRunner.store(app).put(c);
        } catch (java.io.IOException ignored) {
            // planned all the same; kept when it is done
        }
        CampaignChat chat = chat(app, c.id);
        chat.add(CampaignChat.YOU, "brief", CampaignBoard.brief(c, SocialWeb.accounts(app)), System.currentTimeMillis());
        plan(app, c);
        return c.id;
    }

    /** Plans {@code c} in the background; its conversation and the planning notification follow it. */
    static void plan(final Context app, final Campaign c) {
        final CampaignChat chat = chat(app, c.id);
        final CampaignPlanner.Brain brain = brain(app);
        if (brain == null) {
            chat.add(CampaignChat.STUDIO, "text", "Planning needs a brain: choose an API model, or download one for the phone,"
                    + " in Ibiko's chat (the model name at the top). Then say “plan it” here.", System.currentTimeMillis());
            return;
        }
        final String title = c.name;
        CampaignDraft.started(c.id);
        CampaignDraft.at(app, c.id, title, "Planning the campaign");
        chat.add(CampaignChat.STUDIO, "note", "Planning: the strategy, every post with its words and picture, and the searches"
                + " for people who need it. You can leave this screen; it carries on.", System.currentTimeMillis());
        new Thread(() -> {
            try {
                c.brief.lessons = CampaignRunner.results(app).lessons(CampaignRunner.store(app).all(), TimeZone.getDefault());
            } catch (RuntimeException e) {
                c.brief.lessons = "";
            }
            final boolean api = ai.ibiko.llm.ModelHub.get(app).model.remoteOn();
            String why;
            try {
                why = CampaignPlanner.build(c, seats(app, c, true), brain, line -> CampaignDraft.at(app, c.id, title, line),
                        () -> CampaignDraft.isStopped(c.id), TimeZone.getDefault(), api ? CampaignPlanner.WORKERS : 1);
            } catch (RuntimeException e) {
                why = "something went wrong while planning (" + e.getClass().getSimpleName() + ")";
            }
            boolean stopped = CampaignDraft.isStopped(c.id);
            if (why == null && !stopped) {
                // every picture drawn and saved now: the plan is whole on the phone, and posting makes nothing later
                for (int i = 0; i < c.items.size() && !CampaignDraft.isStopped(c.id); i++) {
                    Campaign.Item it = c.items.get(i);
                    if (!it.imageHtml.isEmpty()) {
                        if (i % 3 == 0) {
                            CampaignDraft.at(app, c.id, title, "Drawing pictures " + (i + 1) + " of " + c.items.size());
                        }
                        CampaignRunner.pictureOf(app, c, it);
                    }
                }
                stopped = CampaignDraft.isStopped(c.id);
            }
            CampaignDraft.finished(app, c.id);
            if (why != null || stopped) {
                c.items.clear(); // a draft with no plan: shown as "Planning stopped", to plan again or delete
                save(app, c);
                chat.add(CampaignChat.STUDIO, "text", stopped ? "Planning stopped. Nothing was posted. Say “plan it” to start again."
                        : "The campaign wasn't planned: " + why + " Nothing was posted. Say “plan it” to try again.",
                        System.currentTimeMillis());
                CampaignDraft.show(app, c.id, stopped ? "Campaign planning stopped" : "The campaign wasn't planned",
                        stopped ? "“" + title + "” was stopped. Nothing was posted." : why, false);
                return;
            }
            save(app, c);
            chat.add(CampaignChat.STUDIO, "plan", "Your campaign “" + c.name + "” is planned: " + c.items.size()
                    + " posts over " + c.days + " days" + (c.themes.isEmpty() ? "" : ", themes: " + String.join(", ", c.themes))
                    + ". Every post's words and picture are ready on the phone. Look through them, tell me what to change, and"
                    + " approve when it's right. Nothing is posted before you do.", System.currentTimeMillis());
            CampaignDraft.show(app, c.id, "“" + c.name + "” is ready to review", c.items.size() + " posts over "
                    + c.days + " days. Tap to read the plan and approve it. Nothing is posted before you do.", false);
        }, "ibiko-campaign-plan").start();
    }

    private static void save(Context app, Campaign c) {
        try {
            CampaignRunner.store(app).put(c);
        } catch (java.io.IOException ignored) {
            // shown as it was
        }
    }

    /**
     * An instruction from the owner in the campaign's conversation: the studio changes the plan and says what it did.
     * "Plan it" on a campaign with no plan plans it again. {@code done} runs (on the calling thread's choosing: the
     * caller posts it) when the answer is in.
     */
    public static void instruct(final Context ctx, final String campaignId, final String said, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        final CampaignChat chat = chat(app, campaignId);
        chat.add(CampaignChat.YOU, "text", said, System.currentTimeMillis());
        final Campaign first = CampaignRunner.store(app).get(campaignId);
        if (first == null) {
            return;
        }
        if (first.items.isEmpty() && first.status == Campaign.Status.DRAFT && CampaignDraft.planning(campaignId) == null) {
            plan(app, first); // nothing planned yet: any word plans it
            if (done != null) {
                done.run();
            }
            return;
        }
        final CampaignPlanner.Brain brain = brain(app);
        if (brain == null) {
            chat.add(CampaignChat.STUDIO, "text", "I need a brain for that: choose an API model or download one for the phone in"
                    + " Ibiko's chat.", System.currentTimeMillis());
            if (done != null) {
                done.run();
            }
            return;
        }
        thinking.add(campaignId);
        new Thread(() -> {
            try {
                TimeZone zone = TimeZone.getDefault();
                Campaign c = CampaignRunner.store(app).get(campaignId);
                if (c == null) {
                    return;
                }
                List<CampaignPlanner.Seat> seats = seats(app, c, true);
                String before = chat.recent(8);
                String prompt = (before.isEmpty() ? "" : "Earlier in this conversation:\n" + before + "\n\n")
                        + StudioEdit.prompt(c, seats, said, zone);
                String reply = brain.ask(StudioEdit.SYSTEM, prompt, 3500);
                // made on the campaign as it is now (a post may have gone out while the brain thought)
                Campaign now = CampaignRunner.store(app).get(campaignId);
                if (now == null) {
                    return;
                }
                StudioEdit.Done d = StudioEdit.apply(now, reply, seats, zone);
                for (Campaign.Item it : d.newItems) {
                    CampaignRunner.pictureOf(app, now, it);
                }
                if (d.changed()) {
                    save(app, now);
                    CampaignRunner.arm(app, 0);
                }
                StringBuilder what = new StringBuilder(d.reply);
                if (d.changed()) {
                    what.append("\n\n").append(summary(d));
                }
                chat.add(CampaignChat.STUDIO, "text", what.toString(), System.currentTimeMillis());
            } catch (RuntimeException e) {
                chat.add(CampaignChat.STUDIO, "text", "Something went wrong with that (" + e.getClass().getSimpleName() + "). Try again?",
                        System.currentTimeMillis());
            } finally {
                thinking.remove(campaignId);
                if (done != null) {
                    done.run();
                }
            }
        }, "ibiko-campaign-studio").start();
    }

    /** What changed, in a line: "2 posts rewritten · 3 added · 1 taken out · paused". */
    static String summary(StudioEdit.Done d) {
        List<String> parts = new ArrayList<String>();
        if (d.edited > 0) {
            parts.add(d.edited + (d.edited == 1 ? " post rewritten" : " posts rewritten"));
        }
        if (d.added > 0) {
            parts.add(d.added + " added");
        }
        if (d.removed > 0) {
            parts.add(d.removed + " taken out");
        }
        if (d.paused) {
            parts.add("paused");
        }
        if (d.resumed) {
            parts.add("running again");
        }
        return String.join(" · ", parts);
    }
}
