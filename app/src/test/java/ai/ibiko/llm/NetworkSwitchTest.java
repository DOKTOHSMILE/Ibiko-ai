package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** Leaving the app moved it from Wi-Fi to mobile data and cut the reply: it is asked again and carries on. */
public class NetworkSwitchTest {
    private static List<String[]> ask(String q) {
        List<String[]> t = new ArrayList<String[]>();
        t.add(new String[]{"user", q});
        return t;
    }

    private static String sse(String... pieces) {
        StringBuilder b = new StringBuilder();
        for (String p : pieces) {
            b.append("data: {\"choices\":[{\"delta\":{\"content\":\"").append(p).append("\"}}]}\n\n");
        }
        return b.toString();
    }

    /** A stream that sends its text, then the network changes and it ends with no [DONE]. */
    private static InputStream cutAfter(String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            int at;

            @Override
            public int read() throws IOException {
                if (at < bytes.length) {
                    return bytes[at++] & 0xff;
                }
                ai.ibiko.net.NetSwitch.switched(); // Wi-Fi to mobile data: the connection is gone
                return -1;
            }
        };
    }

    @Test
    public void aReplyCutByANetworkChangeCarriesOnFromWhereItStopped() {
        final List<String> bodies = new ArrayList<String>();
        RemoteBrain.Transport t = (url, h, body, ms) -> {
            bodies.add(new String(body, StandardCharsets.UTF_8));
            if (bodies.size() == 1) {
                return new RemoteBrain.Response(200, cutAfter(sse("The plan has ", "three steps.")));
            }
            return new RemoteBrain.Response(200, new ByteArrayInputStream((sse(" First, read the code.") + "data: [DONE]\n")
                    .getBytes(StandardCharsets.UTF_8)));
        };
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://gw.example.com/v1", "m", "k", "", t);
        String got = b.chat("Be brief.", ask("plan it"), 400, 0.7f, null);
        assertEquals(2, bodies.size());
        assertTrue("asked to carry on, with what had arrived: " + bodies.get(1), bodies.get(1).contains("three steps.")
                && bodies.get(1).contains("length limit") == RemoteBrain.CONTINUE.contains("length limit"));
        assertEquals("The plan has three steps. First, read the code.", got);
    }

    @Test
    public void aCutBeforeAnyWordIsSimplyAskedAgain() {
        final int[] calls = {0};
        RemoteBrain.Transport t = (url, h, body, ms) -> {
            calls[0]++;
            if (calls[0] == 1) {
                return new RemoteBrain.Response(200, cutAfter(""));
            }
            return new RemoteBrain.Response(200, new ByteArrayInputStream((sse("Hello.") + "data: [DONE]\n")
                    .getBytes(StandardCharsets.UTF_8)));
        };
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://gw.example.com/v1", "m", "k", "", t);
        assertEquals("Hello.", b.chat("Be brief.", ask("hi"), 400, 0.7f, null));
        assertEquals(2, calls[0]);
    }
}
