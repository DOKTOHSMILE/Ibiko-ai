package ai.ibiko.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/** A reply nested thousands deep is refused as bad JSON; it used to overflow the stack and close the app. */
public class JsonDepthTest {
    private static String nested(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append('[');
        }
        for (int i = 0; i < n; i++) {
            b.append(']');
        }
        return b.toString();
    }

    @Test
    public void deepNestingIsAParseErrorNotAStackOverflow() {
        for (java.util.function.Function<String, Object> parse : java.util.Arrays.<java.util.function.Function<String, Object>>asList(
                MiniJson::parse, ai.ibiko.ibiko1.Json::parse)) {
            try {
                parse.apply(nested(100_000));
                fail("parsed");
            } catch (IllegalArgumentException expected) {
                // refused
            }
            assertEquals(1, ((java.util.List<?>) parse.apply(nested(200))).size()); // ordinary nesting still reads
        }
    }
}
