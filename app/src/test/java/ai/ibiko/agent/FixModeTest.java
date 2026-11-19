package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

/** An error to fix goes straight to the code it names; reading in circles is called out. */
public class FixModeTest {
    @Test
    public void anErrorToFixIsFixMode() {
        assertTrue(Blueprint.pointed("Fix this crash:\njava.lang.NullPointerException\n    at com.shop.Cart.total(Cart.java:42)\n"));
        assertTrue(Blueprint.pointed("Traceback (most recent call last):\n  File \"app.py\", line 12, in <module>\n"
                + "    main()\nKeyError: 'price'"));
        assertTrue(Blueprint.pointed("my build fails with TypeError in checkout, please fix"));
        assertTrue("the old case still holds", Blueprint.pointed("src/Main.java:7: error: cannot find symbol Foo"));
        assertFalse("not an error to fix", Blueprint.pointed("Build me an app that handles IOException nicely"));
        assertFalse(Blueprint.pointed("Make a website for my bakery"));
        assertFalse("fix mode skips the plan and the survey", Blueprint.applies("Fix this crash:\njava.lang.NullPointerException\n"
                + "    at com.shop.Cart.total(Cart.java:42)\n"));
    }

    @Test
    public void theCodeTheErrorNamesIsReadForTheModel() throws Exception {
        File ws = Files.createTempDirectory("fix").toFile();
        File src = new File(ws, "shop/src/com/shop");
        src.mkdirs();
        StringBuilder code = new StringBuilder();
        for (int i = 1; i <= 80; i++) {
            code.append(i == 42 ? "        return items.get(0).price; // line 42\n" : "        // line " + i + "\n");
        }
        Files.write(new File(src, "Cart.java").toPath(), code.toString().getBytes("UTF-8"));
        CodeAgent a = new CodeAgent(ws, null, null, "", 20_000);
        String named = a.namedCode("src/com/shop/Cart.java:42: error: incompatible types: int cannot be converted to String");
        assertTrue(named, named.contains("shop/src/com/shop/Cart.java:42"));
        assertTrue("the failing line is in it", named.contains("return items.get(0).price"));
        assertFalse("only the lines around it", named.contains("// line 5\n"));
    }

    @Test
    public void readingInCirclesIsCalledOut() {
        CodeAgent a = new CodeAgent(new File("."), null, null, "", 20_000);
        CodeAgent.Call read = new CodeAgent.Call("read_file", new java.util.HashMap<String, String>(), "", "");
        String last = "";
        for (int i = 0; i < CodeAgent.WORK_LOOKS; i++) {
            last = a.noCircles(read, "the file");
        }
        assertTrue(last, last.contains("reads in a row with no change"));
        CodeAgent.Call edit = new CodeAgent.Call("edit_file", new java.util.HashMap<String, String>(), "", "");
        assertEquals("a change resets it", "done", a.noCircles(edit, "done"));
        assertEquals("the file", a.noCircles(read, "the file"));
    }
}
