package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Recipes;

/** Ibiko v1's coding: tested recipes in 11 languages, read from the request; what it finds is kept once confirmed. */
public class RecipesTest {
    private static Recipes book(File learned) throws Exception {
        return new Recipes(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/code/recipes.tsv"), learned);
    }

    @Test
    public void readsTheTaskAndTheLanguage() {
        Recipes.Ask a = Recipes.read("How do I reverse a string in Go?");
        assertEquals("reverse_string", a.task);
        assertEquals("go", a.lang);
        assertEquals("javascript", Recipes.read("write a function to sort an array in JavaScript").lang);
        assertEquals("java", Recipes.read("How can I read a file in Java?").lang);
        assertEquals("cpp", Recipes.read("show me code to parse JSON in C++").lang);
        assertEquals("c", Recipes.read("how do I check if a number is prime in C?").lang);
        assertEquals("json_parse", Recipes.read("How do I parse JSON in Python?").task);
        assertEquals("http_get", Recipes.read("how to make an HTTP GET request in PHP").task);
        assertEquals("no language named: the task alone", "factorial", Recipes.read("write a factorial function").task);
        assertNull("not a coding request", Recipes.read("What is the capital of Nigeria?"));
        assertNull(Recipes.read("I went to the market in Lagos"));
        assertEquals("rust", Recipes.followUp("And in Rust?"));
        assertEquals("python", Recipes.followUp("what about python"));
        assertNull(Recipes.followUp("Tell me about the history of the Roman Empire"));
    }

    @Test
    public void everyRecipeShippedIsThereAndSaysItWasTested() throws Exception {
        Recipes b = book(null);
        assertTrue("over 200 tested recipes: " + b.size(), b.size() >= 200);
        Recipes.Recipe go = b.get("reverse_string", "go");
        assertNotNull(go);
        assertTrue(go.tested);
        assertTrue(go.code.contains("package main") && go.code.contains("func reverse"));
        String said = go.said();
        assertTrue(said, said.startsWith("Here's how to reverse a string in Go:\n\n```go\n"));
        assertTrue(said, said.endsWith("Tested: it compiles and runs as it is."));
        assertTrue(b.languages("hello").size() >= 11);
        assertNull("not shipped in Kotlin", b.get("hello", "kotlin"));
    }

    @Test
    public void whatItFindsIsKeptOnlyOnceConfirmed() throws Exception {
        File learned = new File(Files.createTempDirectory("recipes").toFile(), "learned.tsv");
        Recipes b = book(learned);
        String kotlin = "fun main() {\n    println(\"Hello, world!\")\n}";
        b.found("hello", "kotlin", kotlin);
        Recipes.Recipe r = b.get("hello", "kotlin");
        assertFalse("found, not yet confirmed", r.tested);
        assertTrue(r.said().contains("not tested yet"));
        assertFalse("not offered as a language it knows yet", b.languages("hello").contains("kotlin"));
        assertTrue(b.confirm("hello", "kotlin"));
        assertTrue(b.get("hello", "kotlin").tested);
        assertTrue(book(learned).get("hello", "kotlin").tested); // kept on the device
        b.found("factorial", "kotlin", "fun factorial(n: Int): Int = if (n < 2) 1 else n * factorial(n - 1)");
        b.wrong("factorial", "kotlin");
        assertNull("wrong: dropped for good", book(learned).get("factorial", "kotlin"));
    }

    @Test
    public void codeIsFoundInAPageByHowItReads() {
        String page = "<p>Use this:</p><pre><code>fun main() {\n    val name = \"Ada\"\n    println(&quot;Hello, $name&quot;)\n}</code></pre>"
                + "<pre>npm install something</pre>";
        String code = Recipes.code(page, "kotlin");
        assertNotNull(code);
        assertTrue(code, code.contains("println(\"Hello, $name\")"));
        assertNull("no block reads as Swift", Recipes.code(page, "swift"));
        assertNotNull(Recipes.code("Here:\n```python\ndef f(x):\n    print(x)\n```", "python"));
        String reddit = "You can do it like this:\n\n    fun main() {\n        val nums = listOf(3, 1, 2).sorted()\n        println(nums)\n    }\n\nHope that helps!";
        String posted = Recipes.code(reddit, "kotlin");
        assertNotNull("code in a post, indented four spaces as people share it", posted);
        assertTrue(posted, posted.startsWith("fun main() {") && posted.contains("    val nums"));
    }
}
