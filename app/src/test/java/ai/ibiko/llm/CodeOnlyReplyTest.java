package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** A reply that is only a command code ("\u00A7WK": start the agents) is an answer, not "the server sent no answer". */
public class CodeOnlyReplyTest {
    @Test
    public void aCodeOnlyReplyIsKept() {
        final int[] calls = {0};
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"\\u00A7WK\"}}]}\n\ndata: [DONE]\n";
        RemoteBrain.Transport t = (url, h, body, ms) -> {
            calls[0]++;
            return new RemoteBrain.Response(200, new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        };
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "", t);
        List<String[]> turns = new ArrayList<String[]>();
        turns.add(new String[]{"user", "Start"});
        assertEquals("\u00A7WK", b.chat("sys", turns, 700, 0.5f, null));
        assertEquals("asked once, not again", 1, calls[0]);
    }
}
