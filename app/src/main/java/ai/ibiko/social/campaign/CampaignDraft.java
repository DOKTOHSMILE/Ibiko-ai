package ai.ibiko.social.campaign;

/**
 * A new campaign's brief on its way from the Social accounts screen to the chat, where the brain plans it (the
 * screen has no brain of its own). Taken once.
 */
public final class CampaignDraft {
    private static Campaign waiting;

    private CampaignDraft() {
    }

    public static synchronized void offer(Campaign c) {
        waiting = c;
    }

    public static synchronized Campaign take() {
        Campaign c = waiting;
        waiting = null;
        return c;
    }
}
