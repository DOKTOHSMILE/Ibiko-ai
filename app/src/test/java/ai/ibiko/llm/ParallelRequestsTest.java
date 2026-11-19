package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Test;

/** Workers asking one provider at once: each request has its own state, its own clock and its own stop. */
public class ParallelRequestsTest {
    private static final String ANSWER = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello there.\"}}]}\n\ndata: [DONE]\n";

    @After
    public void restore() {
        LocalModel.remoteIdleMs = LocalModel.REMOTE_IDLE_MS;
        LocalModel.remoteFirstByteMs = 300_000;
    }

    /** A connection that has died: nothing ever arrives, and a read waits until the connection is cut. */
    private static InputStream dead() {
        final java.util.concurrent.CountDownLatch cut = new java.util.concurrent.CountDownLatch(1);
        return new InputStream() {
            @Override
            public int read() throws java.io.IOException {
                try {
                    cut.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new java.io.IOException("connection cut");
            }

            @Override
            public void close() {
                cut.countDown();
            }
        };
    }

    /** A healthy server: keep-alive lines while it thinks for {@code ms}, then the answer. */
    private static InputStream aliveThen(final long ms, String answer) {
        final byte[] tail = answer.getBytes(StandardCharsets.UTF_8);
        final long until = System.currentTimeMillis() + ms;
        return new InputStream() {
            int at;

            @Override
            public int read() {
                if (System.currentTimeMillis() < until) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return -1;
                    }
                    return '\n';
                }
                return at < tail.length ? tail[at++] & 0xff : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                int c = read(); // like a socket: what has arrived, not a full buffer
                if (c < 0) {
                    return -1;
                }
                b[off] = (byte) c;
                return 1;
            }
        };
    }

    @Test(timeout = 30000)
    public void aStuckWorkerTimesOutAloneAndTheOthersAnswer() throws Exception {
        LocalModel.remoteIdleMs = 3000;
        LocalModel.remoteFirstByteMs = 3000;
        RemoteBrain.Transport t = (url, h, body, ms) -> {
            String b = new String(body, StandardCharsets.UTF_8);
            if (b.contains("stuck")) {
                return new RemoteBrain.Response(200, dead());
            }
            return new RemoteBrain.Response(200, aliveThen(5000, ANSWER)); // still going when the stuck one is stopped
        };
        LocalModel m = new LocalModel(java.nio.file.Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_1_7B, true);
        m.useRemote(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "", t), 400_000,
                () -> true);
        final String[] got = new String[2];
        final long[] took = new long[1];
        Thread stuck = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            got[0] = m.remoteTask("sys", "stuck", 400);
            took[0] = System.currentTimeMillis() - t0;
        });
        Thread fine = new Thread(() -> got[1] = m.remoteTask("sys", "fine", 400)); // started later: the newest call
        stuck.start();
        Thread.sleep(200);
        fine.start();
        stuck.join();
        fine.join();
        assertNull("the stuck request gives up", got[0]);
        assertTrue("on its own clock, not the socket's ten minutes: " + took[0], took[0] < 4_500);
        assertEquals("the other worker is not stopped with it", "Hello there.", got[1]);
    }

    @Test
    public void aCopyKeepsTheChangingKeyAndHasItsOwnState() {
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://gw.example.com/v1", "m", "", "", null)
                .keySource(() -> "session-key");
        final String[] sent = {null};
        RemoteBrain.Transport t = (url, h, body, ms) -> {
            sent[0] = h.get("Authorization");
            return new RemoteBrain.Response(200, new ByteArrayInputStream(ANSWER.getBytes(StandardCharsets.UTF_8)));
        };
        RemoteBrain withT = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://gw.example.com/v1", "m", "fixed", "", t)
                .keySource(() -> "session-key");
        java.util.List<String[]> turns = new java.util.ArrayList<String[]>();
        turns.add(new String[]{"user", "hi"});
        assertEquals("Hello there.", withT.withModel("other").chat("s", turns, 100, 0.5f, null));
        assertEquals("the copy asks for the account's current key", "Bearer session-key", sent[0]);
        RemoteBrain c = b.copy();
        b.cancel();
        assertTrue("a different object", c != b);
    }
}
