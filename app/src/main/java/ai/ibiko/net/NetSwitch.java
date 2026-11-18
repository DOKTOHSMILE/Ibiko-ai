package ai.ibiko.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Watches the phone's default network for the whole app, on screen or not. Leaving the app often moves its traffic
 * from Wi-Fi to mobile data (or back), and a connection opened on the old network then dies without a word: a reply
 * waited out its full read timeout (ten minutes) and ended "the server sent no answer". Here a switch or a loss cuts
 * every open model connection at once ({@link #track}), so the request fails fast and is asked again on the new
 * network ({@link #awaitOnline}); RemoteBrain carries on from what had already arrived.
 */
public final class NetSwitch {
    private static final Set<java.net.HttpURLConnection> OPEN =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<java.net.HttpURLConnection, Boolean>()));
    private static final Object LOCK = new Object();
    private static volatile Network current;
    private static volatile boolean online = true;
    /** Bumped on every switch or loss: a request compares it to see whether its network changed under it. */
    private static volatile int switches;
    private static boolean started;

    private NetSwitch() {
    }

    /** Starts watching, once for the app (from IbikoApp, so it keeps watching while the app is off screen). */
    public static synchronized void start(Context context) {
        if (started) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        started = true;
        current = cm.getActiveNetwork();
        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Network was = current;
                current = network;
                if (was != null && !was.equals(network)) {
                    switched(); // Wi-Fi to mobile data or back: what was open on the old one is dead
                }
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                setOnline(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            }

            @Override
            public void onLost(Network network) {
                if (network.equals(current)) {
                    current = null;
                    setOnline(false);
                    switched();
                }
            }
        });
    }

    /** A model connection to cut if the network changes while it is open; {@link #done} when it ends. */
    public static void track(java.net.HttpURLConnection c) {
        OPEN.add(c);
    }

    public static void done(java.net.HttpURLConnection c) {
        OPEN.remove(c);
    }

    /** How many switches so far: a request whose count changed while it ran was cut by one. */
    public static int switches() {
        return switches;
    }

    /** Waits until the internet is back, for at most {@code ms}. True when it is. */
    public static boolean awaitOnline(long ms) {
        long until = System.currentTimeMillis() + ms;
        synchronized (LOCK) {
            while (!online) {
                long left = until - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                try {
                    LOCK.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    static void setOnline(boolean now) {
        synchronized (LOCK) {
            online = now;
            LOCK.notifyAll();
        }
    }

    /** Cuts every open model connection: each read then fails at once instead of waiting out its timeout. */
    /** Also called by tests, to act out a change of network. */
    public static void switched() {
        switches++;
        java.net.HttpURLConnection[] open;
        synchronized (OPEN) {
            open = OPEN.toArray(new java.net.HttpURLConnection[0]);
        }
        for (java.net.HttpURLConnection c : open) {
            try {
                c.disconnect();
            } catch (RuntimeException ignored) {
                // already closed
            }
        }
    }
}
