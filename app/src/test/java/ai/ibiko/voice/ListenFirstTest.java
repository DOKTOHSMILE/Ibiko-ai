package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;

/**
 * Listen never waits behind the voice made ahead for every reply on the screen: something to say now is taken first,
 * and the work it jumped keeps its place (before what was queued after it, and before its mark that lights Listen).
 */
public class ListenFirstTest {
    @Test
    public void somethingToSayNowComesBeforeWorkMadeAhead() throws Exception {
        NeuralVoice v = NeuralVoice.shared(Files.createTempDirectory("listen").toFile());
        v.shutdown(); // its own thread stops: the queue is read here, in order
        Thread.sleep(50);
        Field q = NeuralVoice.class.getDeclaredField("queue");
        q.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedBlockingQueue<Object> queue = (LinkedBlockingQueue<Object>) q.get(v);
        queue.clear();
        v.prepare("The first sentence of an old reply.", 0, 1f);
        v.prepare("The second sentence of an old reply.", 0, 1f);
        v.afterMade(() -> { });
        v.prepare("Another reply, made ahead too.", 0, 1f);
        NeuralVoice.Events none = new NeuralVoice.Events() {
            @Override
            public void onStart(String id) {
            }

            @Override
            public void onDone(String id) {
            }

            @Override
            public void onError(String id) {
            }
        };
        v.speak(none, "OBI-1", "Read this now.", 0, 1f, 1f, false);
        Method next = NeuralVoice.class.getDeclaredMethod("next");
        next.setAccessible(true);
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            Object it = next.invoke(v);
            Field id = it.getClass().getDeclaredField("id");
            id.setAccessible(true);
            Field text = it.getClass().getDeclaredField("text");
            text.setAccessible(true);
            order.append(id.get(it)).append(':').append(text.get(it)).append(" | ");
        }
        String o = order.toString();
        assertTrue(o, o.startsWith("OBI-1:Read this now."));
        assertEquals(o, "OBI-1:Read this now. | ahead:The first sentence of an old reply. | "
                + "ahead:The second sentence of an old reply. | mark: | ahead:Another reply, made ahead too. | ", o);
    }
}
