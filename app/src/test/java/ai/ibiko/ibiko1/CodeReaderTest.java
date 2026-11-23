package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.CodeReader;
import ai.ibiko.ibiko1.formulas.Readings;
import ai.ibiko.ibiko1.formulas.Recipes;

/** Code read and explained in plain words; and a message's meaning weighed with the conversation, not by its first word. */
public class CodeReaderTest {
    private static Recipes recipes() {
        return new Recipes(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/code/recipes.tsv"), null);
    }

    @Test
    public void codeIsFoundAndItsLanguageKnown() {
        CodeReader.Snippet py = CodeReader.find("What does this do?\n```\ndef add(a, b):\n    return a + b\n\nprint(add(2, 3))\n```");
        assertEquals("python", py.lang);
        assertEquals("What does this do?", py.words);
        assertEquals("javascript", CodeReader.find("const nums = [3, 1, 2];\nnums.sort((a, b) => a - b);\nconsole.log(nums);").lang);
        assertEquals("go", CodeReader.find("package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"hi\")\n}").lang);
        assertEquals("cpp", CodeReader.find("#include <iostream>\nint main() {\n    std::cout << \"hi\";\n}").lang);
        assertEquals("c", CodeReader.find("#include <stdio.h>\nint main(void) {\n    printf(\"hi\\n\");\n}").lang);
        assertEquals("sql", CodeReader.language("SELECT name, age FROM users WHERE age > 18 ORDER BY name;"));
        assertEquals("html", CodeReader.language("<div class=\"card\"><h1>Hi</h1></div>"));
        assertEquals("rust", CodeReader.language("fn main() {\n    let x = 5;\n    println!(\"{}\", x);\n}"));
        assertNull("prose with a bracket in it is not code", CodeReader.find("I went to the market (the big one) and bought rice."));
        assertNull(CodeReader.find("What is the capital of Nigeria?"));
    }

    @Test
    public void itIsExplainedInPlainWords() {
        String code = "def factorial(n):\n    result = 1\n    for i in range(2, n + 1):\n        result *= i\n    return result\n\nprint(factorial(5))";
        String said = CodeReader.explain(CodeReader.find(code), recipes());
        assertTrue(said, said.startsWith("This is Python code. It's the usual way to work out a factorial."));
        assertTrue(said, said.contains("It defines “factorial” (a reusable step called factorial), which takes in n."));
        assertTrue(said, said.contains("It repeats some steps (a loop)") && said.contains("Shows text on the screen"));
        assertTrue(said, said.contains("Line by line:") && said.contains("result *= i  →  multiplies result by i")
                && said.contains("return result  →  gives back result as the answer"));
        String web = CodeReader.explain(CodeReader.find("const res = await fetch(url);\nconst data = await res.json();\nconsole.log(data.name);"), null);
        assertTrue(web, web.contains("Talks to the internet"));
        String sql = CodeReader.explain(new CodeReader.Snippet("DELETE FROM orders;", "sql", ""), null);
        assertTrue(sql, sql.contains("deletes rows from the database (all of them: careful)"));
    }

    @Test
    public void aMistakeIsPointedOut() {
        String said = CodeReader.explain(CodeReader.find("function greet(name) {\n  console.log(\"Hi \" + name);\n\ngreet(\"Ada\");"), null);
        assertTrue(said, said.contains("One thing to look at: a curly bracket { is opened and never closed."));
    }

    @Test
    public void theConversationDecidesWhatAMessageMeans() {
        List<Readings.Kind> both = Arrays.asList(Readings.Kind.WORD, Readings.Kind.CODING);
        Readings.Context coding = new Readings.Context().said("code").said("code");
        assertEquals("after talk of code, it's about code", Readings.Kind.CODING, Readings.weigh("What does sort mean?", both, coding).get(0).kind);
        Readings.Context words = new Readings.Context().said("language");
        assertEquals("after talk of words, it's a word's meaning", Readings.Kind.WORD, Readings.weigh("What does sort mean?", both, words).get(0).kind);
        List<Readings.Reading> site = Readings.weigh("What does a restaurant website need?", Arrays.asList(Readings.Kind.WORD, Readings.Kind.SITE), new Readings.Context());
        assertEquals(Readings.Kind.SITE, site.get(0).kind);
        String step = Readings.said(site);
        assertTrue(step, step.startsWith("Weighing what you meant: a website (") && step.contains("also possible: a word's meaning"));
    }

    @Test
    public void reasoningReadingsAreWeighedToo() {
        List<Readings.Reading> r = Readings.weigh("What is the difference between a whale and a fish?",
                Arrays.asList(Readings.Kind.KINDQ, Readings.Kind.CONTRAST), new Readings.Context());
        assertEquals(Readings.Kind.CONTRAST, r.get(0).kind);
        assertTrue(r.get(0).why, r.get(0).why.contains("comparing"));
        Readings.Context causes = new Readings.Context().said("reasons");
        List<Readings.Reading> next = Readings.weigh("How come?", Arrays.asList(Readings.Kind.HOW, Readings.Kind.WHY), causes);
        assertEquals("after talk of causes, \"how come?\" asks a reason", Readings.Kind.WHY, next.get(0).kind);
    }
}
