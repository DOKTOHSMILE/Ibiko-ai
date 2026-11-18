package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Workers at once: four to start, up while the provider answers smoothly, down on a refusal or a warm phone. */
public class WorkerPaceTest {
    private static final WorkerPace.Heat COOL = WorkerPace.Heat.COOL;

    @Test
    public void startsAtFourAndRisesToEightWhileItGoesSmoothly() {
        WorkerPace p = new WorkerPace();
        assertEquals(4, p.workers(true, COOL, 0));
        long t = 0;
        for (int i = 0; i < 200; i++) {
            p.answered(t += 1000);
        }
        assertEquals("never more than eight", 8, p.workers(true, COOL, t));
    }

    @Test
    public void aRefusalHalvesItAndItWaitsBeforeRisingAgain() {
        WorkerPace p = new WorkerPace();
        long t = 1000;
        for (int i = 0; i < 40; i++) {
            p.answered(t += 1000);
        }
        assertEquals(8, p.workers(true, COOL, t));
        p.refused(t += 1000);
        assertEquals(4, p.workers(true, COOL, t));
        p.refused(t += 1000);
        p.refused(t += 1000);
        assertEquals("two at least", 2, p.workers(true, COOL, t));
        for (int i = 0; i < 30; i++) {
            p.answered(t += 1000); // all inside the two quiet minutes
        }
        assertEquals("no rise right after a refusal", 2, p.workers(true, COOL, t));
        t += WorkerPace.QUIET_MS;
        for (int i = 0; i < WorkerPace.SMOOTH_TO_RISE; i++) {
            p.answered(t += 1000);
        }
        assertEquals(3, p.workers(true, COOL, t));
    }

    @Test
    public void heatAndTheDevicesOwnModelKeepItLow() {
        WorkerPace p = new WorkerPace();
        long t = 0;
        for (int i = 0; i < 40; i++) {
            p.answered(t += 1000);
        }
        assertEquals("a warm phone: four", 4, p.workers(true, WorkerPace.Heat.WARM, t));
        assertEquals("a hot phone: two", 2, p.workers(true, WorkerPace.Heat.HOT, t));
        assertEquals("the device's own model: two", 2, p.workers(false, COOL, t));
        assertEquals(WorkerPace.Heat.WARM, WorkerPace.heat(DeviceGuard.State.WARM));
        assertEquals(WorkerPace.Heat.HOT, WorkerPace.heat(DeviceGuard.State.COOLING_DOWN));
        assertEquals(COOL, WorkerPace.heat(null));
    }

    @Test
    public void aNewJobAfterAQuietSpellStartsAtFourAgain() {
        WorkerPace p = new WorkerPace();
        p.refused(1000);
        assertEquals(2, p.workers(true, COOL, 2000));
        assertEquals(4, p.workers(true, COOL, 2000 + WorkerPace.IDLE_MS + 1));
    }

    @Test
    public void runningWorkersWaitWhenTheLimitDrops() throws Exception {
        WorkerPace p = new WorkerPace();
        p.workers(true, COOL, 0);
        for (int i = 0; i < 4; i++) {
            assertTrue(p.enter(() -> false));
        }
        p.refused(1000); // down to two while four are calling
        final boolean[] got = {false};
        Thread waiting = new Thread(() -> got[0] = p.enter(() -> false));
        waiting.start();
        p.exit();
        p.exit();
        waiting.join(300);
        assertFalse("still two calling: the fifth waits", got[0]);
        p.exit();
        waiting.join(2000);
        assertTrue("one fewer than the limit: it goes", got[0]);
        WorkerPace full = new WorkerPace();
        full.workers(true, COOL, 0);
        for (int i = 0; i < 4; i++) {
            full.enter(() -> false);
        }
        assertFalse("a stopped job does not wait forever", full.enter(() -> true));
    }
}
