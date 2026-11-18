package ai.ibiko.llm;

/**
 * How many workers run at once, set by how things are going rather than by a fixed number. A job starts at four.
 * While the provider answers smoothly it steps up one at a time, to at most eight. The moment the provider refuses a
 * request (too many requests, overloaded, busy) it halves, to no fewer than two, and stays down for two minutes. A
 * warm phone caps it at four and a hot one at two. The device's own model answers one call at a time, so it never
 * gets more than two. Running workers also pass through {@link #enter}, so a drop takes effect at once, not only on
 * the next batch. Pure Java: the clock is passed in.
 */
public final class WorkerPace {
    public static final int START = 4, MOST = 8, FEWEST = 2, DEVICE_MOST = 2, WARM_MOST = 4, HOT_MOST = 2;
    /** Smooth answers in a row before one more worker. */
    static final int SMOOTH_TO_RISE = 8;
    /** After a refusal, no rise for this long. */
    static final long QUIET_MS = 120_000L;
    /** A new job after this long with nothing asked starts again at {@link #START}. */
    static final long IDLE_MS = 600_000L;

    /** The phone's heat, as {@link DeviceGuard} reads it. */
    public enum Heat { COOL, WARM, HOT }

    /** The one every API call and every worker shares. */
    public static final WorkerPace SHARED = new WorkerPace();

    private int level = START;
    private int smooth;
    private long quietUntil, lastAsked = -1;
    private int active, limit = START;

    /** A request the provider answered. */
    public synchronized void answered(long now) {
        lastAsked = now;
        smooth++;
        if (smooth >= SMOOTH_TO_RISE && now >= quietUntil && level < MOST) {
            level++;
            smooth = 0;
        }
    }

    /** A request the provider refused because it was too busy or too many came at once. */
    public synchronized void refused(long now) {
        lastAsked = now;
        level = Math.max(FEWEST, level / 2);
        smooth = 0;
        quietUntil = now + QUIET_MS;
        limit = Math.min(limit, level);
    }

    /** The workers to run now. */
    public synchronized int workers(boolean api, Heat heat, long now) {
        if (lastAsked >= 0 && now - lastAsked > IDLE_MS) {
            level = START; // a new job: start from the middle again
            smooth = 0;
            quietUntil = 0;
            lastAsked = -1;
        }
        int n = api ? level : Math.min(DEVICE_MOST, level);
        if (heat == Heat.HOT) {
            n = Math.min(n, HOT_MOST);
        } else if (heat == Heat.WARM) {
            n = Math.min(n, WARM_MOST);
        }
        limit = Math.max(1, n);
        notifyAll();
        return limit;
    }

    /** A worker starts its call: it waits while as many as allowed are already calling. False when it was stopped. */
    public synchronized boolean enter(java.util.function.BooleanSupplier stopped) {
        while (active >= limit) {
            if (stopped != null && stopped.getAsBoolean()) {
                return false;
            }
            try {
                wait(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        active++;
        return true;
    }

    /** A worker's call ended. */
    public synchronized void exit() {
        active = Math.max(0, active - 1);
        notifyAll();
    }

    /** The heat from the guard's state. */
    public static Heat heat(DeviceGuard.State s) {
        if (s == null || s == DeviceGuard.State.NORMAL) {
            return Heat.COOL;
        }
        return s == DeviceGuard.State.WARM ? Heat.WARM : Heat.HOT;
    }

    /** For tests: back to a new job. */
    synchronized void reset() {
        level = START;
        smooth = 0;
        quietUntil = 0;
        lastAsked = -1;
        active = 0;
        limit = START;
    }
}
