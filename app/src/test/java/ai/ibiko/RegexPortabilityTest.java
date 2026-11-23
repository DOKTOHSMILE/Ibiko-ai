package ai.ibiko;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Android compiles regular expressions with ICU, which is stricter than the desktop Java the tests run on: a pattern
 * that compiles here can throw on the phone. A word boundary or anchor given a quantifier ("\\b?", "\\b*") is one such
 * pattern: Java accepts it, ICU rejects it, and a pattern in a static field then takes its whole class down
 * (Calculus did, crashing every chat message). This checks the source for those patterns.
 */
public class RegexPortabilityTest {
    /** In Java source: a backslash-escaped \b, \B, \A, \z, \Z or \G followed by ?, *, + or {. */
    private static final Pattern QUANTIFIED_ASSERTION = Pattern.compile("\\\\\\\\[bBAzZG][?*+{]");

    @Test
    public void noQuantifiedAssertionsAndroidCantCompile() throws IOException {
        Path src = Paths.get("src/main/java");
        if (!Files.isDirectory(src)) {
            src = Paths.get("app/src/main/java");
        }
        List<String> found = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(src)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = QUANTIFIED_ASSERTION.matcher(lines.get(i));
                    if (m.find()) {
                        found.add(f + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue("Android's regex engine can't compile a quantified \\b (or \\B, \\A, \\z, \\G):\n" + String.join("\n", found),
                found.isEmpty());
    }
}
