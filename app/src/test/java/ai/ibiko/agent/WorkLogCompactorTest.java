package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A growing work log keeps a byte-identical prefix until it is really too big, then elides once and stays elided. */
public class WorkLogCompactorTest {
    private static String result(String n, int chars) {
        StringBuilder b = new StringBuilder();
        b.append("<tool call=\"").append(n).append("\" />\n<result>\n");
        for (int i = 0; i < chars; i++) {
            b.append('x');
        }
        return b.append("\n</result>\n").toString();
    }

    @Test
    public void aSmallLogIsNotTouched() {
        String log = "Task: fix it\n" + result("a", 100);
        WorkLogCompactor c = new WorkLogCompactor();
        assertEquals(log, c.compact(log));
        assertEquals(log, c.compact(log));
    }

    @Test
    public void theSystemPrefixIsFrozenWhileTheWorkChanges() throws Exception {
        java.io.File ws = java.nio.file.Files.createTempDirectory("ibiko-si").toFile();
        final java.util.List<String> systems = new java.util.ArrayList<String>();
        CodeAgent.Brain brain = new CodeAgent.Brain() {
            @Override
            public String ask(String system, String transcript) {
                systems.add(system);
                return systems.size() == 1 ? "<tool name=\"list_files\" path=\".\"/>" : "Done.";
            }
        };
        CodeAgent.Host host = new CodeAgent.Host() {
            @Override public String steer() { return null; }
            @Override public void note(String text) { }
            @Override public boolean allowRun(String command) { return false; }
            @Override public String run(String command, java.io.File workspace) { return ""; }
            @Override public String fetch(String url) { return ""; }
            @Override public String connector(String call) { return ""; }
            @Override public void step(String line) { }
            @Override public boolean stopped() { return false; }
        };
        CodeAgent agent = new CodeAgent(ws, brain, host, "", 20_000).compact(true);
        agent.run("fix it", "");
        assertTrue("every step uses the same system", systems.size() >= 2);
        for (String system : systems) {
            assertEquals("the system prefix is byte-identical", systems.get(0), system);
        }
    }

    @Test
    public void aBigLogElidesOldResultsButKeepsTheRecentOnes() {
        StringBuilder log = new StringBuilder("Task: fix it\n");
        for (int i = 0; i < 24; i++) {
            log.append(result(String.valueOf(i), 6000));
        }
        WorkLogCompactor c = new WorkLogCompactor();
        String first = c.compact(log.toString(), 120_000, 60_000);
        assertTrue("old result gone", !first.contains(result("0", 6000)));
        assertTrue("recent result stays", first.contains(result("23", 6000)));
        assertTrue("elision is visible", first.contains("earlier tool result left out"));
        assertEquals("a second pass at the same size changes nothing", first, c.compact(first, 120_000, 60_000));
        assertFalse("elision is permanent", c.compact(first, 120_000, 60_000).contains(result("0", 6000)));

        log.append(result("24", 6000));
        String next = c.compact(log.toString(), 120_000, 60_000);
        assertTrue("the old prefix is unchanged", next.startsWith(first.substring(0, first.indexOf("earlier tool result"))));
        assertTrue("the new result stays", next.contains(result("24", 6000)));
        c.reset();
        assertEquals("a new task starts clean", log.toString(), c.compact(log.toString(), 120_000, 60_000));
    }
}
