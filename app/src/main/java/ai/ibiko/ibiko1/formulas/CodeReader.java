package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading code, Ibiko v1's own: a snippet pasted in is recognised as code (not prose), its language worked out from
 * how it is written, and what it does said in plain words a person who doesn't program can follow: what it is for,
 * the parts it is made of (functions, classes, the libraries it brings in), what it does with the outside world
 * (shows text, reads input, files, the internet, a database), how it decides and repeats, and then a walk through it
 * line by line. A snippet that is one of its own tested recipes is named as such. Pure Java.
 *
 * <pre>
 * C1  finds     code in a message: a fenced block, or lines that read as code (brackets, ;, =, keywords, indentation)
 * C2  language  the one whose own marks it carries most ({@link Recipes#looks}, and the marks only one language has)
 * C3  parts     functions (and their inputs), classes, imports, loops, decisions, error handling, input and output
 * C4  says      the summary in plain words, then each line that does something, in order
 * </pre>
 */
public final class CodeReader {
    private CodeReader() {
    }

    /** Code found in a message: the code, its language, and the person's own words around it ("what does this do?"). */
    public static final class Snippet {
        public final String code, lang, words;

        public Snippet(String code, String lang, String words) {
            this.code = code;
            this.lang = lang;
            this.words = words;
        }
    }

    private static final Pattern FENCE = Pattern.compile("```\\s*([A-Za-z0-9+#.-]*)\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern CODE_LINE = Pattern.compile("[{};]\\s*$|^\\s*(?:def|class|function|fn|func|fun|public|private|static|import|from|"
            + "#include|package|using|const|let|var|return|if|for|while|else|elif|print|console\\.|System\\.out|echo|SELECT|<\\?php|<[a-z]+[ >]|"
            + "[A-Za-z_][\\w.]*\\s*(?:=|\\+=|-=|:=)\\s*\\S|[A-Za-z_][\\w.]*\\(.*\\)\\s*;?$)");

    /** C1: the code in a message, or null when it has none. Prose with a bracket in it is not code. */
    public static Snippet find(String message) {
        if (message == null) {
            return null;
        }
        Matcher f = FENCE.matcher(message);
        if (f.find()) {
            String code = f.group(2);
            String tag = f.group(1).toLowerCase(Locale.ROOT);
            String words = (message.substring(0, f.start()) + " " + message.substring(f.end())).trim();
            return new Snippet(code, tag.isEmpty() ? language(code) : tagged(tag, code), words);
        }
        String[] lines = message.split("\n");
        int codeLines = 0, first = -1, last = -1;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.trim().isEmpty()) {
                continue;
            }
            if (CODE_LINE.matcher(l).find() && !l.trim().matches("(?i)^(what|why|how|can|could|please|explain|tell|is|does)\\b.*\\?$")) {
                codeLines++;
                first = first < 0 ? i : first;
                last = i;
            }
        }
        // at least two lines of code, and most of the lines between the first and the last are code
        if (codeLines < 2 || codeLines < 0.6 * (last - first + 1)) {
            return null;
        }
        StringBuilder code = new StringBuilder(), words = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            (i >= first && i <= last ? code : words).append(lines[i]).append('\n');
        }
        String lang = language(code.toString());
        return lang == null ? null : new Snippet(code.toString(), lang, words.toString().trim());
    }

    private static String tagged(String tag, String code) {
        switch (tag) {
            case "py": case "python": case "python3": return "python";
            case "js": case "javascript": case "node": return "javascript";
            case "ts": case "typescript": return "typescript";
            case "c++": case "cpp": case "cc": return "cpp";
            case "cs": case "csharp": case "c#": return "csharp";
            case "sh": case "bash": case "shell": case "zsh": return "bash";
            case "golang": return "go";
            case "rb": return "ruby";
            case "kt": return "kotlin";
            case "htm": return "html";
            default: return tag.matches("[a-z]+") ? tag : language(code);
        }
    }

    // ------------------------------------------------------------------------------------------------- C2: language

    /** C2: the language a piece of code is written in, or null when it doesn't read as code. */
    public static String language(String code) {
        String c = code == null ? "" : code;
        // marks only one language has
        if (c.contains("<?php")) {
            return "php";
        }
        if (c.matches("(?s).*^\\s*package main\\b.*") || c.contains("fmt.Print")) {
            return "go";
        }
        if (c.matches("(?s).*\\bfn\\s+\\w+\\s*\\(.*") && (c.contains("let ") || c.contains("println!"))) {
            return "rust";
        }
        if (c.matches("(?s).*#include\\s*<.*")) {
            return c.contains("std::") || c.contains("cout") || c.contains("#include <iostream>") || c.contains("vector<") ? "cpp" : "c";
        }
        if (c.matches("(?s).*\\busing System\\b.*") || c.contains("Console.Write")) {
            return "csharp";
        }
        if (c.matches("(?s)^\\s*#!/(?:usr/)?bin/(?:env )?(?:ba)?sh.*")) {
            return "bash";
        }
        if (c.matches("(?is)^\\s*(?:<!doctype html|<html|<div|<body|<head|<section|<p>|<a |<span|<ul|<form).*")) {
            return "html";
        }
        if (c.matches("(?is)^\\s*(?:select|insert into|update \\w+ set|create table|delete from)\\b.*")) {
            return "sql";
        }
        if (c.matches("(?s)^\\s*[.#]?[a-zA-Z][\\w-]*(?:\\s*[,>+~ ]\\s*[.#]?[\\w-]+)*\\s*\\{\\s*[a-z-]+\\s*:[^;{}]+;.*")) {
            return "css";
        }
        String best = null;
        int bestScore = 0;
        for (String lang : new String[] {"python", "javascript", "typescript", "java", "kotlin", "ruby", "bash", "swift", "dart"}) {
            int s = Recipes.looks(c, lang);
            if (s > bestScore) {
                best = lang;
                bestScore = s;
            }
        }
        // TypeScript is JavaScript with types: only when it has them
        if ("typescript".equals(best) && !c.matches("(?s).*(?::\\s*(?:string|number|boolean|void|any)\\b|\\binterface\\s+\\w+|<\\w+>\\().*")) {
            best = "javascript";
        }
        if ("java".equals(best) && c.matches("(?s).*\\bfun\\s+\\w+.*")) {
            best = "kotlin";
        }
        return bestScore >= 1 ? best : null;
    }

    // ------------------------------------------------------------------------------------------------- C3: parts

    /** What a library a snippet brings in is for, in plain words. */
    static final Map<String, String> LIBS = new LinkedHashMap<String, String>();

    static {
        String[][] l = {{"requests", "fetching web pages and APIs"}, {"urllib", "fetching web pages"}, {"http", "talking to the web (HTTP)"},
            {"fetch", "talking to the web"}, {"axios", "calling web APIs"}, {"json", "reading and writing JSON data"},
            {"os", "files and the operating system"}, {"sys", "the program's own settings and arguments"}, {"re", "searching text with patterns"},
            {"regex", "searching text with patterns"}, {"math", "maths"}, {"random", "random numbers"}, {"datetime", "dates and times"},
            {"time", "time and waiting"}, {"fs", "reading and writing files"}, {"path", "file paths"}, {"express", "making a web server"},
            {"react", "building a user interface"}, {"flask", "making a web server"}, {"django", "making a website with a database"},
            {"numpy", "maths on lists of numbers"}, {"pandas", "tables of data"}, {"matplotlib", "drawing charts"}, {"sqlite3", "a small database"},
            {"mysql", "a MySQL database"}, {"pg", "a PostgreSQL database"}, {"mongoose", "a MongoDB database"}, {"stdio.h", "showing and reading text"},
            {"stdlib.h", "everyday tools (memory, numbers, random)"}, {"string.h", "working with text"}, {"iostream", "showing and reading text"},
            {"vector", "growable lists"}, {"java.util", "lists, maps and other everyday tools"}, {"java.io", "files and streams"},
            {"java.net", "the internet"}, {"java.nio", "files"}, {"java.time", "dates and times"}, {"fmt", "showing text"},
            {"net/http", "the web (HTTP)"}, {"encoding/json", "JSON data"}, {"strings", "working with text"}, {"strconv", "turning text into numbers"},
            {"std::collections", "lists and maps"}, {"std::fs", "files"}, {"std::io", "reading and writing"}, {"tkinter", "windows and buttons"},
            {"pygame", "making a game"}, {"socket", "network connections"}, {"threading", "doing several things at once"},
            {"asyncio", "waiting for things without blocking"}, {"csv", "spreadsheet (CSV) files"}, {"openai", "the OpenAI API"},
            {"anthropic", "the Claude API"}, {"firebase", "Firebase (Google's app backend)"}, {"bcrypt", "storing passwords safely"},
            {"jsonwebtoken", "logins with tokens (JWT)"}, {"dotenv", "settings kept in a .env file"}, {"cors", "letting other websites call the server"}};
        for (String[] x : l) {
            LIBS.put(x[0], x[1]);
        }
    }

    /** The parts a snippet is made of. */
    static final class Parts {
        final List<String[]> functions = new ArrayList<String[]>(); // name, inputs
        final Set<String> classes = new LinkedHashSet<String>(), imports = new LinkedHashSet<String>(), does = new LinkedHashSet<String>();
        boolean loops, decides, handlesErrors, recursive, returns;
    }

    private static final Pattern[] FUNCS = {
        Pattern.compile("(?m)^\\s*(?:async\\s+)?def\\s+(\\w+)\\s*\\(([^)]*)\\)"),
        Pattern.compile("(?m)\\bfunction\\s+(\\w+)\\s*\\(([^)]*)\\)"),
        Pattern.compile("(?m)\\b(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\(([^)]*)\\)\\s*=>"),
        Pattern.compile("(?m)\\bfn\\s+(\\w+)\\s*(?:<[^>]*>)?\\(([^)]*)\\)"),
        Pattern.compile("(?m)\\bfunc\\s+(?:\\([^)]*\\)\\s*)?(\\w+)\\s*\\(([^)]*)\\)"),
        Pattern.compile("(?m)\\bfun\\s+(\\w+)\\s*\\(([^)]*)\\)"),
        Pattern.compile("(?m)^\\s*(?:public|private|protected|static|final|synchronized|async|virtual|override|\\s)*(?:[\\w<>\\[\\],.?]+\\s+)+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:throws [\\w, .]+)?\\s*\\{"),
    };

    static Parts parts(String code, String lang) {
        Parts p = new Parts();
        Set<String> seen = new LinkedHashSet<String>();
        for (Pattern f : FUNCS) {
            Matcher m = f.matcher(code);
            while (m.find()) {
                String name = m.group(1);
                if (name.matches("if|for|while|switch|catch|return|new|else|main") && !name.equals("main") || !seen.add(name)) {
                    continue;
                }
                p.functions.add(new String[]{name, m.group(2).replaceAll("\\s+", " ").trim()});
            }
        }
        Matcher c = Pattern.compile("\\b(?:class|struct|interface|object|enum)\\s+(\\w+)").matcher(code);
        while (c.find()) {
            p.classes.add(c.group(1));
        }
        Matcher im = Pattern.compile("(?m)^\\s*(?:import\\s+([\\w.]+)|from\\s+([\\w.]+)\\s+import|#include\\s*[<\"]([\\w./]+)[>\"]|using\\s+([\\w.:]+);|use\\s+([\\w:]+)"
                + "|.*require\\(['\"]([\\w./@-]+)['\"]\\)|import\\s+.*?from\\s+['\"]([\\w./@-]+)['\"]|\\s*\"([\\w./]+)\"\\s*$)").matcher(code);
        while (im.find()) {
            for (int i = 1; i <= im.groupCount(); i++) {
                if (im.group(i) != null) {
                    p.imports.add(im.group(i));
                    break;
                }
            }
        }
        String low = code.toLowerCase(Locale.ROOT);
        p.loops = code.matches("(?s).*\\b(?:for|while|foreach|loop)\\b.*") || code.matches("(?s).*\\.(?:forEach|map|each|filter|reduce)\\b.*");
        p.decides = code.matches("(?s).*\\b(?:if|switch|match|case|elif|when)\\b.*") || code.contains(" ? ");
        p.handlesErrors = code.matches("(?s).*\\b(?:try|catch|except|rescue|finally|Result<|\\.catch\\(|if err != nil)\\b.*") || code.contains("if err != nil");
        p.returns = code.matches("(?s).*\\breturn\\b.*");
        for (String[] f : p.functions) {
            String body = code.substring(Math.min(code.length(), code.indexOf(f[0]) + f[0].length()));
            if (Pattern.compile("\\b" + Pattern.quote(f[0]) + "\\s*\\(").matcher(body).find()) {
                p.recursive |= body.indexOf(f[0] + "(") > 0 && countOf(code, f[0] + "(") > 2;
            }
        }
        if (code.matches("(?s).*\\b(?:print|println|printf|puts|echo|console\\.log|System\\.out|fmt\\.Print|cout|Console\\.Write)\\b.*")
                || code.contains("print(") || code.contains("println!")) {
            p.does.add("shows text on the screen");
        }
        if (code.matches("(?s).*\\b(?:input|Scanner|readline|readLine|fgets|gets|stdin|scanf|cin)\\b.*")) {
            p.does.add("reads what the user types");
        }
        if (low.matches("(?s).*\\b(?:open\\(|readfile|writefile|fopen|files\\.|file\\.read|file\\.write|fs\\.|os\\.writefile|os\\.readfile|ifstream|ofstream|read_to_string|file_put_contents|file_get_contents).*")) {
            p.does.add("reads or writes files");
        }
        if (low.matches("(?s).*(?:fetch\\(|requests\\.|urlopen|http\\.get|httpclient|axios|curl|file_get_contents\\(\"http|xmlhttprequest|net/http).*")) {
            p.does.add("talks to the internet");
        }
        if (low.matches("(?s).*\\b(?:select |insert into|update .* set|create table|cursor|execute\\(|query\\(|sqlite|mysql|mongodb|prisma).*")) {
            p.does.add("works with a database");
        }
        if (low.matches("(?s).*\\b(?:app\\.listen|createserver|@app\\.route|app\\.get\\(|listenandserve|express\\(\\)).*")) {
            p.does.add("runs a web server that answers requests");
        }
        if (low.matches("(?s).*\\b(?:addeventlistener|onclick|document\\.|getelementbyid|queryselector).*")) {
            p.does.add("changes a web page and reacts to clicks");
        }
        if (low.matches("(?s).*\\b(?:random|rand\\(|math\\.random).*")) {
            p.does.add("uses random numbers");
        }
        if (low.matches("(?s).*\\b(?:sort|sorted|qsort)\\b.*")) {
            p.does.add("sorts things into order");
        }
        if (low.matches("(?s).*\\b(?:password|token|api_key|apikey|secret)\\b.*")) {
            p.does.add("handles a password or key (keep those out of the code itself)");
        }
        return p;
    }

    private static int countOf(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------------- C4: saying it

    /** C4: the snippet explained in plain words: what it is, what it does, then line by line. */
    public static String explain(Snippet s, Recipes known) {
        String lang = s.lang;
        Parts p = parts(s.code, lang);
        StringBuilder b = new StringBuilder();
        b.append("This is ").append(name(lang)).append(" code.");
        String recipe = known == null ? null : known.match(s.code, lang);
        if (recipe != null) {
            b.append(" It's the usual way to ").append(recipe).append(".");
        }
        b.append("\n\nWhat it does, in plain words:\n");
        List<String> said = new ArrayList<String>();
        if (lang.equals("html")) {
            said.add("it lays out a web page: " + htmlParts(s.code));
        } else if (lang.equals("css")) {
            said.add("it styles a web page: it sets how things look (colours, sizes, spacing) for the parts it names");
        } else if (lang.equals("sql")) {
            said.add(sql(s.code));
        }
        for (String[] f : p.functions) {
            if (f[0].equals("main")) {
                said.add("its starting point is main, where the program begins");
                continue;
            }
            String inputs = f[1].isEmpty() ? "takes nothing in" : "takes in " + plainParams(f[1]);
            said.add("it defines " + human(f[0]) + " (a reusable step called " + f[0] + "), which " + inputs);
        }
        for (String c : p.classes) {
            said.add("it describes a kind of thing called " + c + " (a class: a blueprint for objects with their own data and actions)");
        }
        if (!p.imports.isEmpty()) {
            List<String> libs = new ArrayList<String>();
            for (String i : p.imports) {
                String why = null;
                for (Map.Entry<String, String> e : LIBS.entrySet()) {
                    if (i.equals(e.getKey()) || i.startsWith(e.getKey() + ".") || i.endsWith("/" + e.getKey()) || i.startsWith(e.getKey() + "::")) {
                        why = e.getValue();
                        break;
                    }
                }
                libs.add(i + (why == null ? "" : " (for " + why + ")"));
            }
            said.add("it brings in ready-made code: " + String.join(", ", libs));
        }
        said.addAll(p.does);
        if (p.loops) {
            said.add("it repeats some steps (a loop), going through items one by one");
        }
        if (p.decides) {
            said.add("it makes decisions: it checks a condition and does one thing or another");
        }
        if (p.recursive) {
            said.add("a function calls itself (recursion): it solves a big problem by solving a smaller copy of it");
        }
        if (p.handlesErrors) {
            said.add("it's ready for things going wrong: it catches errors instead of crashing");
        }
        if (said.isEmpty()) {
            said.add("it's a short piece that sets some values and works with them");
        }
        for (String x : said) {
            b.append("• ").append(Character.toUpperCase(x.charAt(0))).append(x.substring(1)).append(".\n");
        }
        List<String> walk = walk(s.code, lang);
        if (!walk.isEmpty()) {
            b.append("\nLine by line:\n");
            for (String w : walk) {
                b.append(w).append('\n');
            }
        }
        String check = check(s.code, lang);
        if (check != null) {
            b.append("\nOne thing to look at: ").append(check);
        }
        return b.toString().trim();
    }

    /** The plain-words name of a language. */
    public static String name(String lang) {
        switch (lang) {
            case "html": return "HTML (a web page's structure)";
            case "css": return "CSS (a web page's styling)";
            case "sql": return "SQL (database)";
            default: return Recipes.shown(lang);
        }
    }

    private static String human(String name) {
        String words = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        return "“" + words + "”";
    }

    private static String plainParams(String params) {
        List<String> out = new ArrayList<String>();
        for (String p : params.split(",")) {
            String t = p.trim().replaceAll("=.*$", "").replaceAll("^(?:const|final|mut|&|\\*)\\s*", "").trim();
            String[] w = t.split("[\\s:]+");
            String name = w.length == 0 ? t : (t.contains(":") ? w[0] : w[w.length - 1]);
            name = name.replaceAll("[^\\w$]", "");
            if (!name.isEmpty() && !name.equals("self") && !name.equals("this")) {
                out.add(name);
            }
        }
        return out.isEmpty() ? "nothing" : String.join(", ", out);
    }

    private static String htmlParts(String code) {
        List<String> parts = new ArrayList<String>();
        String low = code.toLowerCase(Locale.ROOT);
        String[][] tags = {{"<header", "a header"}, {"<nav", "navigation links"}, {"<h1", "a main heading"}, {"<form", "a form to fill in"},
            {"<img", "pictures"}, {"<table", "a table"}, {"<ul", "a list"}, {"<button", "buttons"}, {"<a ", "links"}, {"<footer", "a footer"},
            {"<script", "a script that makes it do things"}, {"<style", "its own styling"}};
        for (String[] t : tags) {
            if (low.contains(t[0])) {
                parts.add(t[1]);
            }
        }
        return parts.isEmpty() ? "some content" : String.join(", ", parts);
    }

    private static String sql(String code) {
        String low = code.toLowerCase(Locale.ROOT).trim();
        Matcher from = Pattern.compile("\\bfrom\\s+(\\w+)").matcher(low);
        String table = from.find() ? from.group(1) : "a table";
        if (low.startsWith("select")) {
            return "it asks the database for rows from " + table + (low.contains(" where ") ? ", only the ones that match a condition" : "")
                    + (low.contains("order by") ? ", in order" : "");
        }
        if (low.startsWith("insert")) {
            return "it adds a new row to a table in the database";
        }
        if (low.startsWith("update")) {
            return "it changes rows already in the database" + (low.contains(" where ") ? " (only those that match)" : " (all of them: careful)");
        }
        if (low.startsWith("delete")) {
            return "it deletes rows from the database" + (low.contains(" where ") ? " (only those that match)" : " (all of them: careful)");
        }
        if (low.startsWith("create table")) {
            return "it makes a new table in the database, with the columns it lists";
        }
        return "it works with a database";
    }

    /** Each line that does something, in plain words (at most 15). */
    static List<String> walk(String code, String lang) {
        List<String> out = new ArrayList<String>();
        if (lang.matches("html|css")) {
            return out;
        }
        for (String raw : code.split("\n")) {
            String l = raw.trim();
            if (l.isEmpty() || l.matches("[{}()\\[\\];,]+|end|fi|done|esac|\\}\\s*else\\s*\\{")) {
                continue;
            }
            String said = line(l, lang);
            if (said != null) {
                out.add("• " + (l.length() > 60 ? l.substring(0, 57) + "…" : l) + "  →  " + said);
            }
            if (out.size() >= 15) {
                out.add("• … and so on.");
                break;
            }
        }
        return out;
    }

    private static String line(String l, String lang) {
        Matcher m;
        if (l.matches("^(?://|#(?!include|!)|--|/\\*|\\*).*")) {
            String note = l.replaceFirst("^(?://+|#+|--|/\\*+|\\*+)\\s*", "").replaceFirst("\\*/\\s*$", "").trim();
            return note.isEmpty() ? null : "a note for people reading it: “" + note + "”";
        }
        if (l.matches("^(?:import|from|#include|using|use|require|package)\\b.*") || l.matches(".*=\\s*require\\(.*")) {
            return l.startsWith("package") ? "says which package (group of files) this belongs to" : "brings in ready-made code";
        }
        if ((m = Pattern.compile("^(?:async\\s+)?(?:def|function|fn|func|fun)\\s+(?:\\([^)]*\\)\\s*)?(\\w+)").matcher(l)).find()) {
            return "starts the definition of " + human(m.group(1));
        }
        if ((m = Pattern.compile("^(?:public |private |abstract |export )*(?:class|struct|interface|enum)\\s+(\\w+)").matcher(l)).find()) {
            return "starts describing " + m.group(1);
        }
        if (l.matches("^(?:public|private|protected|static)\\b.*\\(.*\\).*\\{?$")) {
            return "starts a method (a step that belongs to the class)";
        }
        if ((m = Pattern.compile("^(?:if|elif|else if|} else if)\\s*\\(?(.+?)\\)?\\s*(?:\\{|:|then)?$").matcher(l)).find()) {
            return "checks whether " + condition(m.group(1)) + ", and only then does what follows";
        }
        if (l.matches("^(?:else|}\\s*else)\\b.*")) {
            return "otherwise, does this instead";
        }
        if ((m = Pattern.compile("^for\\s*\\(?\\s*(?:const |let |var |int |auto |\\(?)?(\\w+)\\)?\\s*(?:in|of|:|=)\\s*(.+?)\\)?\\s*(?:\\{|:|do)?$").matcher(l)).find()) {
            return "repeats for each " + m.group(1) + " in " + m.group(2).replaceAll("[{:]\\s*$", "").trim();
        }
        if (l.matches("^(?:for|while|loop|until)\\b.*")) {
            return "repeats the steps inside while a condition holds";
        }
        if (l.matches("^(?:try|begin)\\b.*")) {
            return "tries the steps inside, ready for an error";
        }
        if (l.matches("^(?:catch|except|rescue|}\\s*catch)\\b.*")) {
            return "if an error happened above, handles it here instead of crashing";
        }
        if ((m = Pattern.compile("^return\\s+(.+?);?$").matcher(l)).find()) {
            return "gives back " + m.group(1) + " as the answer";
        }
        if (l.matches("^return;?$")) {
            return "stops here and goes back";
        }
        if ((m = Pattern.compile("(?:print|println|puts|echo|console\\.log|System\\.out\\.println|fmt\\.Println|printf|Console\\.WriteLine|println!)\\s*\\(?(.*?)\\)?;?$").matcher(l)).find()
                && l.matches("^(?:print|println|puts|echo|console\\.log|System\\.out|fmt\\.Print|printf|Console\\.Write|println!|std::cout).*")) {
            return "shows " + shorten(m.group(1)) + " on the screen";
        }
        if (l.startsWith("std::cout")) {
            return "shows text on the screen";
        }
        if ((m = Pattern.compile("^(?:const |let |var |val |final |auto |int |long |double |float |String |string |bool |boolean |char |mut |\\$)*\\s*([A-Za-z_][\\w.\\[\\]]*)\\s*(\\+=|-=|\\*=|/=|:=|=)\\s*(.+?);?$").matcher(l)).find()
                && !l.contains("==")) {
            String op = m.group(2);
            String v = m.group(3);
            if (op.equals("+=")) {
                return "adds " + shorten(v) + " to " + m.group(1);
            }
            if (op.equals("-=")) {
                return "takes " + shorten(v) + " away from " + m.group(1);
            }
            if (op.equals("*=")) {
                return "multiplies " + m.group(1) + " by " + shorten(v);
            }
            return "keeps " + valueWords(v) + " under the name " + m.group(1);
        }
        if ((m = Pattern.compile("^([\\w.]+)\\((.*)\\);?$").matcher(l)).find()) {
            return "runs " + m.group(1) + (m.group(2).isEmpty() ? "" : " with " + shorten(m.group(2)));
        }
        return null;
    }

    private static String condition(String c) {
        return c.replace("===", " is ").replace("==", " is ").replace("!==", " is not ").replace("!=", " is not ")
                .replace(">=", " is at least ").replace("<=", " is at most ").replace("&&", " and ").replace("||", " or ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String valueWords(String v) {
        String t = v.trim();
        if (t.matches("[\"'`].*[\"'`]")) {
            return "the text " + shorten(t);
        }
        if (t.matches("-?\\d+(?:\\.\\d+)?")) {
            return "the number " + t;
        }
        if (t.matches("\\[.*\\]|\\{.*\\}|(?:new )?(?:ArrayList|HashMap|List\\.of|vec!|\\[\\]).*")) {
            return "a collection " + shorten(t);
        }
        if (t.matches("(?:new\\s+)?[A-Z]\\w*\\(.*")) {
            return "a new " + t.replaceFirst("^new\\s+", "").replaceFirst("\\(.*$", "");
        }
        if (t.matches("[\\w.]+\\(.*\\)")) {
            return "what " + t.replaceFirst("\\(.*$", "") + " gives back";
        }
        return shorten(t);
    }

    private static String shorten(String s) {
        String t = s.trim();
        return t.length() > 40 ? t.substring(0, 37) + "…" : t;
    }

    /** Something plainly wrong: brackets that don't close, a Python line ending in ;, == in a C-style assignment. */
    static String check(String code, String lang) {
        int round = 0, square = 0, curly = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'' && !lang.matches("rust|lisp") || c == '`') {
                inString = true;
                quote = c;
                continue;
            }
            round += c == '(' ? 1 : c == ')' ? -1 : 0;
            square += c == '[' ? 1 : c == ']' ? -1 : 0;
            curly += c == '{' ? 1 : c == '}' ? -1 : 0;
        }
        if (round != 0) {
            return round > 0 ? "a round bracket ( is opened and never closed." : "there's a ) with no ( to match it.";
        }
        if (square != 0) {
            return "the square brackets [ ] don't pair up.";
        }
        if (curly != 0) {
            return curly > 0 ? "a curly bracket { is opened and never closed." : "there's a } with no { to match it.";
        }
        if (lang.equals("python") && code.matches("(?s).*^\\s*(?:if|for|while|def|class|elif|else)\\b[^:\\n]*$.*")
                && !code.matches("(?s).*^\\s*(?:if|for|while|def|class|elif|else)\\b[^\\n]*:\\s*$.*")) {
            return "in Python, a line that starts a block (if, for, def…) ends with a colon (:).";
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------- C5: follow-ups

    /** A question about code just read: convert it, one line of it, or whether it has a mistake. */
    public static final class Follow {
        public enum Is { CONVERT, LINE, CHECK }

        public final Is is;
        public final String lang;
        public final int line;

        Follow(Is is, String lang, int line) {
            this.is = is;
            this.lang = lang;
            this.line = line;
        }
    }

    private static final Pattern CONVERT = Pattern.compile("(?i)^\\s*(?:can you |please |now )?(?:convert|translate|rewrite|port|turn|write|show)\\s+"
            + "(?:it|this|that|the code|the same|this code|it all)?\\s*(?:in|into|to|as|for|using)\\s+(.{1,20}?)\\s*[?.!]*$|^\\s*(?:and |what about |how about )?in (.{1,20}?)\\s*[?.!]*$");
    private static final Pattern LINE = Pattern.compile("(?i)\\bline\\s+(\\d{1,3})\\b|\\b(first|second|third|fourth|fifth|last) line\\b");
    private static final Pattern CHECK = Pattern.compile("(?i)\\b(?:any|a|the) (?:mistakes?|bugs?|errors?|problems?)\\b|\\bis (?:it|this|that) (?:right|correct|ok|okay|wrong)\\b|"
            + "\\bwill (?:it|this) (?:work|run)\\b|\\bwhat'?s wrong\\b|\\bwhy (?:doesn'?t|won'?t) (?:it|this) (?:work|run)\\b");

    /** C5: a follow-up about the code just read, or null. */
    public static Follow follow(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        Matcher c = CONVERT.matcher(m);
        if (c.matches()) {
            String lang = Recipes.language("in " + (c.group(1) != null ? c.group(1) : c.group(2)));
            if (lang != null) {
                return new Follow(Follow.Is.CONVERT, lang, 0);
            }
        }
        Matcher l = LINE.matcher(m);
        if (l.find()) {
            int n = l.group(1) != null ? Integer.parseInt(l.group(1))
                    : Arrays.asList("first", "second", "third", "fourth", "fifth").indexOf(l.group(2).toLowerCase(Locale.ROOT)) + 1;
            return new Follow(Follow.Is.LINE, null, l.group(2) != null && l.group(2).equalsIgnoreCase("last") ? -1 : n);
        }
        if (CHECK.matcher(m).find()) {
            return new Follow(Follow.Is.CHECK, null, 0);
        }
        return null;
    }

    /** C5: one line of the code (1 is the first; -1 the last) in plain words; null when there's no such line. */
    public static String line(Snippet s, int n) {
        List<String> lines = new ArrayList<String>();
        for (String l : s.code.split("\n")) {
            if (!l.trim().isEmpty()) {
                lines.add(l);
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        int at = n < 0 ? lines.size() : n;
        if (at < 1 || at > lines.size()) {
            return "It has " + lines.size() + (lines.size() == 1 ? " line" : " lines") + " (blank ones aside), so there's no line " + n + ".";
        }
        String l = lines.get(at - 1).trim();
        String said = line(l, s.lang);
        return "Line " + at + ": " + l + "\n\n" + (said == null ? "It's part of the structure around it (it opens or closes a block, or carries on the line before)."
                : Character.toUpperCase(said.charAt(0)) + said.substring(1) + ".");
    }

    /** C5: whether the code has a mistake it can see, in words. */
    public static String checked(Snippet s) {
        String c = check(s.code, s.lang);
        return c == null ? "I can't see a mistake in it: the brackets pair up and the lines are formed right. That doesn't prove it does what you want: "
                + "run it to be sure (say \"run this\" with the code)." : "Yes, one thing: " + c;
    }
}
