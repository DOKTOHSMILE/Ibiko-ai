#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/code/errors.tsv.ibk: common programming errors, what they mean and how to fix them.

Every entry is tested before it is written: it carries a small program that really makes the error, the tool runs it with
the real interpreter or compiler (python3, node, java, go, rustc, gcc, php, git, bash), and the entry's pattern must match
what was printed. So each pattern fits the real wording, and the message kept as the sample is one a person would see.
An entry whose program doesn't give its error is not written.

One line an entry: id, language, pattern (a regular expression; its groups fill {1}, {2} in the words), what it means,
the usual causes, the fix, an example of the fix (new lines written as \\n), and the real message seen when it was tested.
Coded like the rest of the knowledge (tools/encode_text.py), read back by ai.ibiko.ibiko1.TextCode.

Usage: tools/build_code_errors.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "code", "errors.tsv")

RUN = {
    "python": ("main.py", ["python3", "-I", "main.py"]),
    "javascript": ("main.js", ["node", "main.js"]),
    "java": ("Main.java", ["java", "Main.java"]),
    "go": ("main.go", ["go", "run", "main.go"]),
    "rust": ("main.rs", ["sh", "-c", "rustc -o prog main.rs && ./prog"]),
    "c": ("main.c", ["sh", "-c", "gcc -o prog main.c && ./prog"]),
    "php": ("main.php", ["php", "main.php"]),
    "shell": ("main.sh", ["bash", "main.sh"]),
}

ERRORS = []


def e(id, lang, pattern, means, causes, fix, example, repro):
    ERRORS.append(dict(id=id, lang=lang, pattern=pattern, means=means, causes=causes, fix=fix, example=example, repro=repro))


# ------------------------------------------------------------------------------------------------- Python
e("py_module", "python", r"ModuleNotFoundError: No module named '([\w.]+)'",
  "Python can't find a module called {1}: it isn't installed, or the name is spelled differently",
  "the package was never installed; it's installed for another Python (a different version or virtual environment); "
  "the import name differs from the package name (import cv2 comes from opencv-python, import PIL from pillow)",
  "install it for the Python you run, then run again", "python3 -m pip install {1}",
  "import requestz_not_here\n")
e("py_name", "python", r"NameError: name '(\w+)' is not defined",
  "the code uses the name {1} before Python knows it: nothing called {1} was made (yet) where it's used",
  "a typo; the variable is made later, or inside another function; a missing import; a string without its quotes",
  "make {1} before you use it, fix the spelling, or import it", "{1} = 0  # set it first\nprint({1})",
  "print(total)\n")
e("py_indent", "python", r"IndentationError: (expected an indented block[^\n]*|unexpected indent|unindent does not match any outer indentation level)",
  "the spaces at the start of a line are wrong: Python reads the indentation as the structure of the code ({1})",
  "a line after if, for, def or class isn't indented; tabs mixed with spaces; one line indented more or less than its neighbours",
  "indent the lines inside a block by the same amount (4 spaces), and don't mix tabs and spaces",
  "if ready:\n    print(\"go\")  # 4 spaces in",
  "if True:\nprint('x')\n")
e("py_syntax", "python", r"SyntaxError: (invalid syntax|expected ':'|'\(' was never closed|unterminated string literal[^\n]*)",
  "Python can't read the line: {1}",
  "a missing colon after if, for, while, def or class; a bracket or quote not closed; a misspelled keyword",
  "look at the line named, and the line before it: close every bracket and quote, end if/for/def lines with a colon",
  "if x > 1:\n    print(x)",
  "if 1 > 0\n    print('x')\n")
e("py_type_concat", "python", r"TypeError: (?:can only concatenate str \(not \"(\w+)\"\) to str|unsupported operand type\(s\) for \+: '(\w+)' and '(\w+)')",
  "you joined text and a number with +, and Python won't guess which you meant",
  "adding a number to a string, often a value read with input(), which is always text",
  "turn the number into text with str(), or the text into a number with int() or float()",
  "age = 30\nprint(\"Age: \" + str(age))\n# or: print(f\"Age: {age}\")",
  "print('Age: ' + 30)\n")
e("py_none_attr", "python", r"AttributeError: 'NoneType' object has no attribute '(\w+)'",
  "the value is None (nothing), so it has no {1}",
  "a function that doesn't return anything (no return line); a lookup that found nothing (re.match, dict.get); "
  "using the result of list.sort() or list.append(), which give None",
  "find where the value came from and make sure it's set; check for None before using it",
  "m = re.match(r\"\\d+\", text)\nif m is not None:\n    print(m.group())",
  "x = None\nx.upper()\n")
e("py_attr", "python", r"AttributeError: '(\w+)' object has no attribute '(\w+)'",
  "a {1} has nothing called {2}: the value isn't the kind you think, or the name is wrong",
  "a typo in {2}; the value is None (a function returned nothing); a list where you expected a string, or the reverse",
  "check what the value really is with print(type(x)), then use a name it has", "print(type(value), dir(value))",
  "x = 5\nx.append(1)\n")
e("py_key", "python", r"KeyError: '?([^'\n]+)'?",
  "the dictionary has no key {1}",
  "a typo or different capitals in the key; the key was never added; a JSON reply without that field",
  "use .get() with a default, or check with 'in' first",
  "price = item.get(\"{1}\", 0)\n# or: if \"{1}\" in item: ...",
  "d = {'a': 1}\nprint(d['b'])\n")
e("py_index", "python", r"IndexError: (list|string|tuple) index out of range",
  "you asked for a position the {1} doesn't have: it's shorter than you think",
  "counting from 1 instead of 0 (the last item is len(x) - 1); an empty {1}; a loop that goes one too far",
  "check the length first, or loop over the items themselves", "for item in items:\n    print(item)\n# or: if i < len(items): ...",
  "xs = [1, 2]\nprint(xs[5])\n")
e("py_value_int", "python", r"ValueError: invalid literal for int\(\) with base 10: '([^']*)'",
  "int() was given '{1}', which isn't a whole number",
  "text with spaces, a decimal point, or letters; an empty answer from input()",
  "strip spaces, use float() for decimals, and catch the error for bad input",
  "try:\n    n = int(text.strip())\nexcept ValueError:\n    print(\"Please type a whole number\")",
  "int('12a')\n")
e("py_zero", "python", r"ZeroDivisionError: (division by zero|integer division or modulo by zero|float division by zero)",
  "the code divided by zero, which has no answer",
  "a count or total that is 0 (an empty list's length, nothing entered yet)",
  "check the divisor first", "average = total / count if count else 0",
  "print(1 / 0)\n")
e("py_file", "python", r"FileNotFoundError: \[Errno 2\] No such file or directory: '([^']+)'",
  "there's no file at {1}, from where the program runs",
  "the program runs from another folder than the file's; a typo in the name or its extension; the file was never made",
  "use the full path, or a path built from the script's own folder",
  "from pathlib import Path\npath = Path(__file__).parent / \"{1}\"\ntext = path.read_text()",
  "open('no_such_file.txt')\n")
e("py_args", "python", r"TypeError: (\w+)\(\) takes (\d+) positional arguments? but (\d+) (?:was|were) given",
  "{1}() takes {2}, but was called with {3}",
  "a missing or extra argument; a method defined without self",
  "call it with the number it takes, or change what it takes; inside a class the first parameter is self",
  "class Shop:\n    def add(self, item):  # self first\n        ...",
  "def add(a, b):\n    return a + b\nadd(1, 2, 3)\n")
e("py_recursion", "python", r"RecursionError: maximum recursion depth exceeded",
  "a function called itself too many times without stopping",
  "no base case (the case that stops it), or a base case it never reaches",
  "give it a case that returns without calling itself, and make every call move toward it",
  "def count_down(n):\n    if n <= 0:  # the base case\n        return\n    count_down(n - 1)",
  "def f(n):\n    return f(n + 1)\nf(0)\n")

# ------------------------------------------------------------------------------------------------- JavaScript
e("js_undefined_prop", "javascript", r"TypeError: Cannot read propert(?:y|ies) of (undefined|null) \(reading '([^']+)'\)",
  "the code read {2} from something that is {1}: the object it expected isn't there",
  "data that hasn't arrived yet (a fetch still loading); a typo in a name earlier in the chain; "
  "document.getElementById found nothing (wrong id, or the script runs before the page is built)",
  "check the value first, or use optional chaining (?.) and a default",
  "const name = user?.{2} ?? \"\";\n// for the page: put <script> at the end of <body>, or use defer",
  "let user;\nconsole.log(user.name);\n")
e("js_not_defined", "javascript", r"ReferenceError: (\w+) is not defined",
  "the code uses {1}, but nothing called {1} exists where it's used",
  "a typo or different capitals; a variable declared inside another function or block; "
  "a browser thing (window, document) used in Node, or a library not loaded",
  "declare it with let or const before using it, or import it", "const {1} = 0;",
  "console.log(total);\n")
e("js_not_function", "javascript", r"TypeError: ([\w.$]+) is not a function",
  "the code calls {1}, but {1} isn't a function",
  "a typo in the name; a method that doesn't exist on that kind of value (map on an object, not an array); "
  "an import that brought in something else",
  "check what it really is with console.log(typeof x), then call a function it has",
  "console.log(typeof value, value);",
  "const items = {};\nitems.map(x => x);\n")
e("js_const", "javascript", r"TypeError: Assignment to constant variable",
  "a variable declared with const was given a new value",
  "const for a value that changes later (a counter, a total)",
  "declare it with let instead", "let count = 0;\ncount = count + 1;",
  "const n = 1;\nn = 2;\n")
e("js_json", "javascript", r"SyntaxError: (Unexpected token .{1,12} in JSON at position \d+|Unexpected token '?(.)'?,? .{0,40}is not valid JSON|Unexpected end of JSON input)",
  "JSON.parse was given text that isn't JSON",
  "the server sent an error page (HTML starts with <) instead of JSON; an empty reply; single quotes or a trailing comma",
  "look at the raw text first, and check the reply's status before parsing",
  "const res = await fetch(url);\nif (!res.ok) throw new Error(\"Server said \" + res.status);\nconst data = await res.json();",
  "JSON.parse('<html>');\n")
e("js_module", "javascript", r"Cannot find module '([^']+)'",
  "Node can't find the module {1}",
  "it isn't installed (no node_modules, or not in package.json); a wrong relative path (./ is missing); the wrong file extension",
  "install it in the project folder, or fix the path", "npm install {1}",
  "require('leftpadz-not-here');\n")

# ------------------------------------------------------------------------------------------------- Java
e("java_npe", "java", r"java\.lang\.NullPointerException",
  "the code used something that is null (it points to no object)",
  "a field or variable never given a value; a method that returned null (a map lookup that found nothing)",
  "give it a value before using it, or check for null", "if (name != null) {\n    System.out.println(name.length());\n}",
  "public class Main { public static void main(String[] a) { String s = null; System.out.println(s.length()); } }\n")
e("java_index", "java", r"(?:Array)?IndexOutOfBoundsException: Index (-?\d+) out of bounds for length (\d+)",
  "the code asked for position {1}, but there are only {2} (positions 0 to one less than {2})",
  "counting from 1 instead of 0; a loop with <= where it should be <",
  "loop while i < length", "for (int i = 0; i < items.length; i++) { ... }",
  "public class Main { public static void main(String[] a) { int[] x = new int[2]; System.out.println(x[5]); } }\n")
e("java_number", "java", r"NumberFormatException: For input string: \"([^\"]*)\"",
  "Integer.parseInt (or a similar method) was given \"{1}\", which isn't a number it can read",
  "spaces, letters or a decimal point in the text; empty input",
  "trim the text, and catch the exception for bad input",
  "try {\n    int n = Integer.parseInt(text.trim());\n} catch (NumberFormatException e) {\n    System.out.println(\"Not a number\");\n}",
  "public class Main { public static void main(String[] a) { Integer.parseInt(\"12a\"); } }\n")
e("java_symbol", "java", r"error: cannot find symbol",
  "the compiler doesn't know a name used in the code",
  "a typo or different capitals; a missing import; a variable declared in another block; a method that doesn't exist",
  "check the spelling and the import, and declare it where it's used",
  "import java.util.List;\nimport java.util.ArrayList;",
  "public class Main { public static void main(String[] a) { System.out.println(total); } }\n")
e("java_types", "java", r"error: incompatible types: (.+?) cannot be converted to (\w+)",
  "a {1} was used where a {2} is needed",
  "assigning text to a number variable, or the reverse",
  "convert it, or change the variable's type", "int n = Integer.parseInt(text);\nString s = String.valueOf(n);",
  "public class Main { public static void main(String[] a) { int n = \"5\"; } }\n")

# ------------------------------------------------------------------------------------------------- Go, Rust, C, PHP
e("go_unused", "go", r"declared and not used: (\w+)|(\w+) declared (?:and|but) not used",
  "Go won't compile a variable that is made but never used",
  "a leftover variable; a value computed and forgotten",
  "use it, delete it, or assign it to _", "_ = result",
  "package main\nfunc main() { x := 1 }\n")
e("go_import", "go", r"\"(\w+)\" imported and not used",
  "Go won't compile an import that isn't used: {1}",
  "an import left behind after the code that used it was removed",
  "remove the import (gofmt and goimports do this for you)", "goimports -w main.go",
  "package main\nimport \"fmt\"\nfunc main() {}\n")
e("rust_moved", "rust", r"borrow of moved value: `(\w+)`",
  "{1} was moved (given away) and then used again: in Rust a value has one owner",
  "passing a String or Vec to a function, or into another variable, then using the original",
  "lend it with & instead of moving it, or clone it if you need two",
  "fn show(s: &String) { println!(\"{}\", s); }\nshow(&{1});\nprintln!(\"{}\", {1});",
  "fn main() { let s = String::from(\"a\"); let t = s; println!(\"{}\", s); let _ = t; }\n")
e("c_segfault", "c", r"Segmentation fault",
  "the program touched memory it doesn't own, and the system stopped it",
  "using a NULL or uninitialised pointer; reading past the end of an array; using memory after free()",
  "check pointers before using them, keep indexes inside the array, and run it under a checker",
  "gcc -g -fsanitize=address main.c && ./a.out  # shows the line",
  "#include <stdio.h>\nint main(void) { int *p = 0; printf(\"%d\\n\", *p); return 0; }\n")
e("php_undefined", "php", r"Undefined variable \$(\w+)",
  "the code uses ${1}, which was never given a value",
  "a typo; a variable made inside a function or an if that didn't run; a form field that wasn't sent",
  "give it a value first, or a default", "${1} = ${1} ?? \"\";",
  "<?php\necho $total;\n")

# ------------------------------------------------------------------------------------------------- the shell and git
e("sh_not_found", "shell", r"(?:bash: )?(?:line \d+: )?([\w.-]+): command not found",
  "the shell can't find a program called {1}",
  "it isn't installed; a typo; it's installed somewhere not on the PATH",
  "install it, or check the name; 'which {1}' says where it is", "which {1}",
  "pyhton3 --version\n")
e("sh_permission", "shell", r"([\w./-]+): Permission denied",
  "you don't have permission to run or open {1}",
  "a script without the run (x) permission; a file owned by another user",
  "give the script permission to run, or run it with its interpreter", "chmod +x {1}",
  "printf 'echo hi\\n' > s.sh\n./s.sh\n")
e("git_not_repo", "shell", r"fatal: not a git repository",
  "git was run outside a git project",
  "the terminal is in the wrong folder; the project was never set up with git",
  "go into the project folder, or start one with git init", "cd my-project\ngit status",
  "cd \"$(mktemp -d)\" && git status\n")


def run(entry, d):
    name, cmd = RUN[entry["lang"]]
    work = os.path.join(d, entry["id"])
    os.makedirs(work)
    with open(os.path.join(work, name), "w") as f:
        f.write(entry["repro"])
    env = dict(os.environ, GOCACHE=os.path.join(d, "gocache"), GOPATH=os.path.join(d, "gopath"), GO111MODULE="off", LC_ALL="C")
    p = subprocess.run(cmd, cwd=work, capture_output=True, text=True, timeout=120, env=env)
    return p.stdout + p.stderr


def main():
    lines = ["# Programming errors Ibiko v1 explains, each made for real and matched when built (tools/build_code_errors.py): "
             "id, language, pattern, means, causes, fix, example, seen"]
    with tempfile.TemporaryDirectory() as d:
        failed = []
        for x in ERRORS:
            out = run(x, d)
            m = re.search(x["pattern"], out)
            if not m:
                failed.append("%s: its program didn't give its error; it printed:\n%s" % (x["id"], out[-600:]))
                continue
            seen = m.group(0).strip()
            for field in ("means", "causes", "fix", "example"):
                assert "\t" not in x[field] and "\\n" not in x[field], x["id"] + " " + field
            row = [x["id"], x["lang"], x["pattern"], x["means"], x["causes"], x["fix"], x["example"].replace("\n", "\\n"), seen]
            assert all("\t" not in c and "\n" not in c for c in row), x["id"]
            lines.append("\t".join(row))
            print("ok   %-18s %s" % (x["id"], seen))
        if failed:
            print("\n".join("FAIL " + f for f in failed))
            sys.exit(1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d errors, each made for real and matched" % len(ERRORS))


if __name__ == "__main__":
    main()
