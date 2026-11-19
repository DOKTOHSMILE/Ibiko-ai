package ai.ibiko.social.campaign;

import android.content.Context;
import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialPoster;
import ai.ibiko.social.SocialWeb;
import java.io.File;
import java.io.IOException;

/**
 * Sends a lead's reply: as a comment under the post it answers, from the campaign's account (put in use for the
 * comment, the one in use before put back), within that account's comment limits. Call it off the main thread.
 */
public final class LeadSender {
    /** Pitches a campaign may send by itself in a day (only with "send without asking" on). */
    public static final int MOST_AUTO_PITCHES = 5;
    /** Answers to comments on the campaign's own posts in a day. */
    public static final int MOST_REPLIES = 15;

    private LeadSender() {
    }

    public static LeadStore store(Context c) {
        return new LeadStore(new File(c.getFilesDir(), "campaigns"));
    }

    /**
     * Reminds the person of pitches that went out and haven't moved on (still Contacted or Talking after
     * {@link Lead#FOLLOW_UP_MS}): one notification for all of them. Each lead is reminded twice at most.
     */
    public static synchronized int remind(Context c, long now) {
        LeadStore store = store(c);
        java.util.List<Lead> due = new java.util.ArrayList<Lead>();
        for (Lead l : store.all()) {
            if (l.dueFollowUp(now)) {
                due.add(l);
            }
        }
        if (due.isEmpty()) {
            return 0;
        }
        StringBuilder who = new StringBuilder();
        for (Lead l : due) {
            l.followUpAt = l.remindedAt > 0 ? 0 : now + Lead.FOLLOW_UP_MS; // a second reminder, then no more
            l.remindedAt = now;
            try {
                store.put(l);
            } catch (IOException ignored) {
                // reminded again next time
            }
            if (who.length() < 300) {
                String s = l.snippet.replaceAll("\\s+", " ").trim();
                who.append("• ").append(s.length() > 70 ? s.substring(0, 70) + "…" : s).append('\n');
            }
        }
        ai.ibiko.llm.ReplyNotice.show(c, "ibiko", due.size() + (due.size() == 1 ? " person" : " people")
                + " you pitched haven't moved on in 2 days. A short, friendly follow-up often wins the sale."
                + " Open Social accounts › Leads to follow up or mark how it went.\n" + who.toString().trim());
        return due.size();
    }

    /** Sends {@code lead} (its reply as it stands, edited or not) and keeps what happened. */
    public static synchronized SocialPoster.Result send(Context c, Lead lead) {
        SocialPoster.Result r = post(c, lead);
        lead.state = r.posted ? Lead.State.SENT : Lead.State.FAILED;
        lead.result = r.said;
        lead.sentAt = System.currentTimeMillis();
        if (r.posted && lead.kind == Lead.Kind.LEAD) {
            lead.moveTo(Lead.Stage.CONTACTED, lead.sentAt); // a reminder to follow up if nothing comes of it
        }
        try {
            store(c).put(lead);
        } catch (IOException ignored) {
            // the action log has it
        }
        return r;
    }

    private static SocialPoster.Result post(Context c, Lead lead) {
        SocialAccounts accounts = SocialWeb.accounts(c);
        SocialAccounts.Account a = accounts.get(lead.account);
        if (a == null || !a.signedIn) {
            return failed("the account that found it is " + (a == null ? "gone" : "signed out"));
        }
        if (a.platform != Platform.X && a.platform != Platform.FACEBOOK) {
            return failed("Ibiko comments itself only on Facebook and X");
        }
        if (!a.platform.owns(lead.url)) {
            return failed("the post isn't on " + a.platform.shown);
        }
        if (lead.reply == null || lead.reply.trim().isEmpty()) {
            return failed("there is no reply to send");
        }
        synchronized (SocialWeb.SWITCH) {
            return as(c, a, () -> SocialPoster.post(c, a.platform, lead.reply.trim(), null, lead.url),
                    failed("the sign-in for " + a.label + " wasn't kept"));
        }
    }

    /**
     * Does {@code work} with {@code a} in use on its network, then puts back the account in use before; {@code
     * notKept} when that account's sign-in couldn't be put in place. Holds {@link SocialWeb#SWITCH}.
     */
    public static <T> T as(Context c, SocialAccounts.Account a, java.util.function.Supplier<T> work, T notKept) {
        synchronized (SocialWeb.SWITCH) {
            SocialAccounts accounts = SocialWeb.accounts(c);
            SocialAccounts.Account before = accounts.inUse(a.platform);
            boolean switched = before == null || !before.id.equals(a.id);
            if (switched && !SocialWeb.switchTo(c, a.id)) {
                return notKept;
            }
            try {
                return work.get();
            } finally {
                if (switched && before != null) {
                    SocialWeb.switchTo(c, before.id);
                }
            }
        }
    }

    private static SocialPoster.Result failed(String why) {
        return SocialPoster.Result.failed("Not sent: " + why + ".");
    }
}
