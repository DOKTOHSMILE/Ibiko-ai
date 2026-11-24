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
    }
}
