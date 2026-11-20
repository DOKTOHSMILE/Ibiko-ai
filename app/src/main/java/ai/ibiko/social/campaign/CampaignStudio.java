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

    /** The studio's own model choice: a provider and one of its models (Social › Campaigns › the model at the top). */
    static final String PREF_PROVIDER = "studio_provider", PREF_MODEL = "studio_model";

    /** A model the studio can use: its provider, the model, and the name to show. */
    public static final class Choice {
        public final ai.ibiko.llm.Providers provider;
        public final String model;

        Choice(ai.ibiko.llm.Providers provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        public String shown() {
            return model;
        }
    }

    /** Every API model the studio could use: each provider set up, with its models. */
    public static List<Choice> choices(Context c) {
        ai.ibiko.llm.ProviderStore store = new ai.ibiko.llm.ProviderStore(c);
        List<Choice> out = new ArrayList<Choice>();
        for (ai.ibiko.llm.Providers p : store.order()) {
            if (store.brain(p) == null) {
                continue;
            }
            java.util.LinkedHashSet<String> models = new java.util.LinkedHashSet<String>();
            models.add(store.model(p));
            models.addAll(store.models(p));
            for (String m : models) {
                if (m != null && !m.trim().isEmpty()) {
                    out.add(new Choice(p, m.trim()));
                }
            }
        }
        return out;
    }

    /** The model the studio uses now: the one chosen on its screen, else the first API set up; null for none. */
    public static Choice chosen(Context c) {
        android.content.SharedPreferences prefs = c.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
        String p = prefs.getString(PREF_PROVIDER, ""), m = prefs.getString(PREF_MODEL, "");
        List<Choice> all = choices(c);
        for (Choice ch : all) {
            if (ch.provider.name().equals(p) && ch.model.equals(m)) {
                return ch;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    public static void choose(Context c, Choice ch) {
        c.getSharedPreferences("ibiko", Context.MODE_PRIVATE).edit().putString(PREF_PROVIDER, ch.provider.name())
                .putString(PREF_MODEL, ch.model).apply();
    }

    /**
     * The brain for campaign work: the studio's API model, asked directly (it works whichever screen is open; the
     * chat's API connection only counts as online while the chat is on screen), else the phone's own model when it is
     * loaded. Null when there is neither.
     */
    static CampaignPlanner.Brain brain(Context c) {
        final Choice ch = chosen(c);
        final ai.ibiko.llm.ProviderStore store = new ai.ibiko.llm.ProviderStore(c);
        final LocalModel model = ai.ibiko.llm.ModelHub.get(c).model;
        if (ch != null) {
            return (system, prompt, tokens) -> {
                ai.ibiko.llm.RemoteBrain b = store.brainWith(ch.provider, ch.model); // one a call: calls run side by side
                String got = null;
                if (b != null) {
                    List<String[]> turns = new ArrayList<String[]>();
                    turns.add(new String[]{"user", prompt});
                    got = b.chat(system, turns, tokens, 0.7f, null);
                }
                if ((got == null || got.trim().isEmpty()) && model != null && model.ready()) {
                    got = LocalModel.inBackground(() -> model.task(system, prompt, tokens, null));
                }
                return got;
            };
        }
        if (model == null || !(model.ready() || model.remoteOn())) {
            return null;
        }
        return background(model);
    }

    /** Whether an API model does the studio's work (calls can run side by side). */
    static boolean api(Context c) {
        return chosen(c) != null;
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
            final boolean api = api(app);
            final java.util.Set<String> shown = new java.util.HashSet<String>();
            final int[] told = {0};
            CampaignPlanner.Progress progress = new CampaignPlanner.Progress() {
                @Override
                public void at(String what) {
                    CampaignDraft.at(app, c.id, title, what);
                }

                @Override
                public void ready(Campaign plan, int ready, int total) {
                    // the posts written so far go on the phone at once, to read and edit while the rest are written
                    publish(app, plan, shown);
                    int quarter = Math.max(1, total / 4);
                    if (told[0] == 0 || ready - told[0] >= quarter) {
                        chat.add(CampaignChat.STUDIO, "text", (told[0] == 0 ? "The first posts are ready: " : "More posts are ready: ")
                                + ready + " of " + total + ". Tap “All posts” to read them and tell me what to change while I"
                                + " write the rest. Nothing is posted before you approve.", System.currentTimeMillis());
                        told[0] = ready;
                    }
                }
            };
            String why;
            try {
                why = CampaignPlanner.build(c, seats(app, c, true), brain, progress,
                        () -> CampaignDraft.isStopped(c.id), TimeZone.getDefault(), api ? CampaignPlanner.WORKERS : 1);
            } catch (RuntimeException e) {
                why = "something went wrong while planning (" + e.getClass().getSimpleName() + ")";
            }
            if (!shown.isEmpty()) {
                publish(app, c, shown); // the last posts in, on top of any edits made while planning
                Campaign kept = CampaignRunner.store(app).get(c.id);
                if (kept != null) {
                    c.items.clear();
                    c.items.addAll(kept.items);
                }
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
            if ((why != null || stopped) && !c.items.isEmpty()) {
                // stopped or cut short with posts already written: they are kept to review, never thrown away
                c.unwritten = Math.max(c.unwritten, 0);
                save(app, c);
                chat.add(CampaignChat.STUDIO, "plan", (stopped ? "Planning stopped. " : "Planning ended early (" + why + "). ")
                        + c.items.size() + (c.items.size() == 1 ? " post is" : " posts are") + " written and kept: tap “All posts”"
                        + " to review them. Say “add the missing posts” to write the rest. Nothing was posted.",
                        System.currentTimeMillis());
                CampaignDraft.show(app, c.id, "“" + title + "”: " + c.items.size() + " posts to review",
                        "Planning " + (stopped ? "stopped" : "ended early") + "; the posts written are kept. Nothing was posted.", false);
                return;
            }
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
                    + (c.unwritten > 0 ? " (" + c.unwritten + (c.unwritten == 1 ? " post" : " posts") + " couldn't be written:"
                    + " say \u201cadd the missing posts\u201d to try them again)" : "")
                    + ". Every post's words and picture are ready on the phone. Look through them, tell me what to change, and"
                    + " approve when it's right. Nothing is posted before you do.", System.currentTimeMillis());
            CampaignDraft.show(app, c.id, "“" + c.name + "” is ready to review", c.items.size() + " posts over "
                    + c.days + " days. Tap to read the plan and approve it. Nothing is posted before you do.", false);
        }, "ibiko-campaign-plan").start();
    }

    /**
     * A new picture for one post from the image generator: the studio's model designs it afresh; when it can't, the
     * formula draws a fresh one. Off the main thread. The picture's file, or null.
     */
    public static File newPicture(Context ctx, Campaign camp, Campaign.Item it) {
        Context app = ctx.getApplicationContext();
        CampaignPlanner.Brain brain = brain(app);
        if (brain != null) {
            String page = CampaignImage.page(brain.ask(ai.ibiko.design.HtmlDraw.system(CampaignImage.SIDE, CampaignImage.SIDE)
                    + " " + ai.ibiko.design.HtmlDraw.FOR_POSTS + " Make it different from the picture it had.",
                    CampaignPlanner.designBrief(camp, it), 5000));
            if (!page.isEmpty()) {
                return CampaignRunner.redraw(app, camp, it, page);
            }
        }
        return CampaignRunner.redraw(app, camp, it, null);
    }

    /**
     * Puts the posts written so far on the phone, keeping what the person did meanwhile: a post already shown stays as
     * they left it (edited, or taken out), and only posts not shown before are added. {@code shown} remembers which.
     */
    static synchronized void publish(Context app, Campaign plan, java.util.Set<String> shown) {
        Campaign stored = CampaignRunner.store(app).get(plan.id);
        Campaign out = stored != null ? stored : plan;
        if (stored != null) {
            stored.name = plan.name;
            stored.themes.clear();
            stored.themes.addAll(plan.themes);
            stored.searches.clear();
            stored.searches.addAll(plan.searches);
            stored.unwritten = plan.unwritten;
            for (Campaign.Item it : plan.items) {
                if (!shown.contains(it.id)) {
                    stored.items.add(it);
                }
            }
            stored.items.sort((x, y) -> Long.compare(x.at, y.at));
        }
        for (Campaign.Item it : plan.items) {
            shown.add(it.id);
        }
        save(app, out);
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
                if (d.changed()) {
                    save(app, now);
                    CampaignRunner.arm(app, 0);
                }
                for (Campaign.Item it : d.newItems) {
                    newPicture(app, now, it); // each added post's own picture, designed by the model
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
