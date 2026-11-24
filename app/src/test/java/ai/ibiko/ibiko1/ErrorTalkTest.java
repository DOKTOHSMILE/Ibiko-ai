package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.ErrorTalk;

/** Pasted errors explained: which one, where, what it means and the fix; code that only names an error is left alone. */
public class ErrorTalkTest {
    private static ErrorTalk errors() {
        return new ErrorTalk(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/code/errors.tsv"));
    }

    @Test
    public void aPythonTracebackExplained() {
        String tb = "Traceback (most recent call last):\n  File \"/home/ada/shop.py\", line 3, in <module>\n    import requests\n"
                + "  File \"/home/ada/app.py\", line 12, in load\n    data = fetch()\nModuleNotFoundError: No module named 'requests'";
        ErrorTalk.Found f = errors().find(tb);
        assertEquals("py_module", f.known.id);
        assertEquals("/home/ada/app.py", f.file);
        assertEquals(12, f.at);
        String said = ErrorTalk.explain(f);
        assertTrue(said, said.startsWith("That's a Python error: ModuleNotFoundError: No module named 'requests'"));
        assertTrue(said.contains("What it means: Python can't find a module called requests"));
        assertTrue(said.contains("Where: /home/ada/app.py, line 12."));
        assertTrue(said.contains("```python\npython3 -m pip install requests\n```"));
    }

    @Test
    public void eachLanguagesOwnWording() {
        ErrorTalk e = errors();
        assertTrue(e.size() >= 30);
        assertEquals("js_undefined_prop", e.find("Uncaught TypeError: Cannot read properties of undefined (reading 'name')\n    at show (app.js:14:22)").known.id);
        ErrorTalk.Found js = e.find("TypeError: Cannot read properties of null (reading 'value')\n    at /srv/form.js:8:31");
        assertTrue(ErrorTalk.explain(js), ErrorTalk.explain(js).contains("const name = user?.value ?? \"\";"));
        assertEquals("/srv/form.js", js.file);
        assertEquals("java_npe", e.find("Exception in thread \"main\" java.lang.NullPointerException: Cannot invoke \"String.length()\"\n"
                + "\tat Main.main(Main.java:5)").known.id);
        assertEquals("java_index", e.find("java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 2").known.id);
        assertEquals("java_symbol", e.find("Main.java:3: error: cannot find symbol\n        System.out.println(total);").known.id);
        assertEquals("go_import", e.find("./main.go:2:8: \"fmt\" imported and not used").known.id);
        assertEquals("rust_moved", e.find("error[E0382]: borrow of moved value: `s`\n --> src/main.rs:4:20").known.id);
        assertEquals("src/main.rs", e.find("error[E0382]: borrow of moved value: `s`\n --> src/main.rs:4:20").file);
        assertEquals("php_undefined", e.find("PHP Warning:  Undefined variable $total in /var/www/index.php on line 7").known.id);
        assertEquals("sh_not_found", e.find("bash: pyhton3: command not found").known.id);
        assertEquals("git_not_repo", e.find("fatal: not a git repository (or any of the parent directories): .git").known.id);
        assertEquals("py_none_attr", e.find("AttributeError: 'NoneType' object has no attribute 'group'").known.id);
        assertTrue(ErrorTalk.explain(e.find("KeyError: 'price'")).contains("item.get(\"price\", 0)"));
    }

    @Test
    public void codeThatNamesAnErrorIsNotOne() {
        ErrorTalk e = errors();
        assertNull(e.find("try:\n    x = d['a']\nexcept KeyError: pass"));
        assertNull(e.find("throw new NullPointerException();"));
        assertNull(e.find("if (err instanceof TypeError) { console.log('TypeError: Cannot read properties of undefined (reading \\'x\\')'); }"));
        assertNull(e.find("What is a segmentation of the market?"));
        assertNull("talk about one, not one seen", e.find("Tell me about KeyError: what is it?"));
        assertEquals("said in words", "KeyError: 'b'", e.find("when I run it I get KeyError: 'b'").line);
    }

    @Test
    public void theLineOfYourCodeItIsAbout() {
        ErrorTalk e = errors();
        String code = "prices = {'tea': 2}\nqty = 3\nprint('Total: ' + qty)\nprint(prices['coffee'])";
        ErrorTalk.Found byLine = e.find("Traceback (most recent call last):\n  File \"shop.py\", line 3, in <module>\n"
                + "TypeError: can only concatenate str (not \"int\") to str");
        assertEquals("3", ErrorTalk.inCode(byLine, code)[0]);
        assertTrue(ErrorTalk.explain(byLine, code).contains("In your code: line 3: print('Total: ' + qty)"));
        ErrorTalk.Found byName = e.find("KeyError: 'coffee'");
        assertEquals("by the name it's about", "print(prices['coffee'])", ErrorTalk.inCode(byName, code)[1]);
        assertNull(ErrorTalk.inCode(byName, null));
    }

    @Test
    public void followUpsAboutTheErrorJustExplained() {
        ErrorTalk.Found f = errors().find("NameError: name 'total' is not defined");
        assertEquals(ErrorTalk.Ask.FIX, ErrorTalk.followUp("How do I fix it?"));
        assertEquals(ErrorTalk.Ask.FIX, ErrorTalk.followUp("so what should I do"));
        assertEquals(ErrorTalk.Ask.WHY, ErrorTalk.followUp("Why does this happen?"));
        assertEquals(ErrorTalk.Ask.EXAMPLE, ErrorTalk.followUp("Show me an example"));
        assertNull(ErrorTalk.followUp("How do I fix my bike?"));
        String fix = ErrorTalk.followed(f, ErrorTalk.Ask.FIX, "x = 1\nprint(total)");
        assertTrue(fix, fix.startsWith("To fix it: make total before you use it") && fix.contains("Start at line 2 of your code: print(total)")
                && fix.contains("```python\ntotal = 0"));
        assertTrue(ErrorTalk.followed(f, ErrorTalk.Ask.WHY, null).startsWith("It happens because the code uses the name total"));
    }

    @Test
    public void anErrorItDoesntKnowByHeart() throws Exception {
        ErrorTalk e = errors();
        assertNull("known ones aren't unknown", e.unknown("KeyError: 'b'"));
        ErrorTalk.Unknown u = e.unknown("Traceback (most recent call last):\n  File \"x.py\", line 2, in <module>\n"
                + "UnicodeDecodeError: 'utf-8' codec can't decode byte 0xe9 in position 41: invalid continuation byte");
        assertEquals("python", u.lang);
        assertTrue(u.line.startsWith("UnicodeDecodeError:"));
        assertEquals("python UnicodeDecodeError: 'utf-8' codec can't decode byte 0xe9 in position : invalid continuation byte fix", ErrorTalk.query(u));
        assertTrue("a question someone asked isn't a fix", ErrorTalk.fixFrom("I get UnicodeDecodeError, please help me fix it.", u) == null);
        assertEquals("the same error, other names and numbers", u.signature,
                e.unknown("UnicodeDecodeError: 'ascii' codec can't decode byte 0x92 in position 7: invalid continuation byte").signature);
        ErrorTalk.Unknown missing = e.unknown("ImportError: cannot import name 'soft_unicode' from 'markupsafe'");
        assertEquals("a module or package can't be found: not installed, or known by another name", ErrorTalk.kindSaid(missing));
        assertNull("chat isn't an error", e.unknown("Error: my phone won't charge"));
        assertNull(e.unknown("I think there's an error in my essay"));
        assertTrue(e.carries("panic: runtime error: invalid memory address or nil pointer dereference"));
    }

    @Test
    public void aFixFromWebTextKeptUntilConfirmed() throws Exception {
        java.io.File file = java.nio.file.Files.createTempFile("learned", ".tsv").toFile();
        file.delete();
        ErrorTalk e = new ErrorTalk(null, file);
        ErrorTalk.Unknown u = errors().unknown("UnicodeDecodeError: 'utf-8' codec can't decode byte 0xe9 in position 41: invalid continuation byte");
        String fix = ErrorTalk.fixFrom("Some intro about computers that has nothing to do with it at all.\nThe file isn't UTF-8, so the codec can't decode"
                + " it. Pass the real encoding when you open it.\n<pre><code>open(p, encoding=&quot;latin-1&quot;)</code></pre>", u);
        assertTrue(fix, fix.startsWith("The file isn't UTF-8, so the codec can't decode it. Pass the real encoding when you open it."));
        assertTrue("a bare error line names no language; the page's code, decoded: " + fix, fix.contains("```\nopen(p, encoding=\"latin-1\")\n```"));
        assertNull("nothing that says what to do", ErrorTalk.fixFrom("UnicodeDecodeError is a Python exception.", u));
        e.found(u, "Use the right encoding.\nLike this: \\n stays as written");
        assertEquals("0", e.learned(u)[0]);
        e.confirm(u.signature);
        ErrorTalk reloaded = new ErrorTalk(null, file);
        assertEquals("1", reloaded.learned(u)[0]);
        assertEquals("kept as written, line breaks and backslashes too", "Use the right encoding.\nLike this: \\n stays as written", reloaded.learned(u)[1]);
        reloaded.wrong(u.signature);
        assertNull(new ErrorTalk(null, file).learned(u));
        assertEquals(Boolean.TRUE, ErrorTalk.worked("That fixed it, thanks!"));
        assertEquals(Boolean.FALSE, ErrorTalk.worked("still the same error"));
        assertNull(ErrorTalk.worked("How do I fix it?"));
    }
}
