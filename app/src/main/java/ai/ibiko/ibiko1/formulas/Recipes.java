package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coding, Ibiko v1's own: "how do I reverse a string in Go?" is answered with a whole program, offline, from recipes
 * every one of which was compiled, run and its output checked before it was shipped ({@code tools/build_code_recipes.py},
 * 11 languages). What it hasn't got is found on the web and given as found, said to be untested; it is kept as a recipe
 * of its own only once it is confirmed to work (the person marks it right, or it runs without failing), and from then on
 * answered offline like the rest. Pure Java.
 *
 * <pre>
 * R1  reads    a coding request: a task (reverse a string, read a file, parse JSON…) and a language (Python, Go…)
 * R2  answers  the recipe for both, or the task in the languages it has when none is named
 * R3  learns   code found for a task and language: kept as untested (1), known once confirmed (2), dropped if wrong
 * R4  finds    code in a web page or text: the block that reads as the language asked
 * </pre>
 */
public final class Recipes {
    /** One task: its name as said, and the ways people ask for it. */
    static final class Task {
        final String id, title;
        final Pattern asks;

        Task(String id, String title, String asks) {
            this.id = id;
            this.title = title;
            this.asks = Pattern.compile("(?i)\\b(?:" + asks + ")\\b");
        }
    }

    static final List<Task> TASKS = new ArrayList<Task>();

    static {
        t("hello", "print Hello, world", "hello,? world");
        t("reverse_string", "reverse a string", "reverse (?:a |the )?(?:string|text|word)|string revers(?:e|al)");
        t("sort_list", "sort a list", "sort (?:a |an |the )?(?:list|array|numbers|vector|slice)|sorting (?:a |an )?(?:list|array)");
        t("sum_list", "add up a list of numbers", "sum (?:of )?(?:a |an |the )?(?:list|array|numbers)|add up (?:a |the )?(?:list|numbers)|total of (?:a |the )?(?:list|numbers)");
        t("max_list", "find the largest number in a list", "(?:largest|biggest|maximum|max|highest) (?:number|value|element|item)|max of (?:a |an )?(?:list|array)");
        t("remove_duplicates", "remove duplicates from a list", "remove (?:the )?duplicates|unique (?:values|elements|items)|dedupe|deduplicate");
        t("factorial", "work out a factorial", "factorial");
        t("fibonacci", "print the Fibonacci numbers", "fibonacci");
        t("is_prime", "check if a number is prime", "prime(?: number)?");
        t("is_even", "check if a number is even", "(?:is|are|if|whether) (?:a |the |an )?(?:number|integer|int|value|it|n|x)?\\s*(?:is )?even\\b|even or odd|odd or even|even numbers?");
        t("uppercase", "make a string upper case", "upper ?case|uppercase|capital letters|to upper");
        t("split_string", "split a string", "split (?:a |the )?(?:string|text|line)|split (?:it )?(?:by|on) (?:a )?comma");
        t("string_to_int", "turn a string into a number", "(?:convert|turn|parse|change|cast) (?:a |the )?string (?:to|into) (?:an? )?(?:int|integer|number)|string to (?:int|integer|number)|parse (?:an? )?(?:int|integer)");
        t("count_words", "count the words in a string", "count (?:the )?words|word count|number of words");
        t("dictionary", "use a dictionary (key to value)", "dictionary|dictionaries|hash ?maps?|hash ?tables?|associative arrays?|key[- ]value");
        t("read_write_file", "write a file and read it back", "(?:read|write|save|open|create) (?:a |an |to a |from a |the )?(?:text )?file|file (?:input|output|io|i/o)");
        t("try_catch", "catch an error", "try[ /-]?catch|try[ /-]?except|handle (?:an? )?(?:errors?|exceptions?)|exception handling|error handling|catch (?:an? )?(?:errors?|exceptions?)");
        t("loop_range", "loop from 1 to 5", "for[- ]?loops?|loop (?:from|through|over)|iterate|count from 1|range of numbers|write a loop");
        t("class_object", "make a class and an object", "classes|class|objects?|oop|struct(?:s|ure)? with (?:a )?method");
        t("json_parse", "parse JSON", "json");
        t("regex_find", "find numbers with a regular expression", "regex|regexp|regular expressions?|find (?:all )?(?:the )?(?:numbers|digits) in");
        t("random_number", "make a random number", "random (?:number|integer|int|value)");
        t("read_input", "read what the user types", "(?:read|get|take) (?:the )?(?:user'?s? )?input|input from (?:the )?(?:user|keyboard)|keyboard input|ask the user");
        t("current_date", "get today's date", "(?:current|today'?s?) date|date today|today'?s date|current time|the date");
        t("http_get", "fetch a web page (HTTP GET)", "http(?: get)?(?: request)?|web request|api call|call an api|fetch (?:a )?(?:url|web ?page|page|api)|get request|download a (?:web)?page");
    }

    private static void t(String id, String title, String asks) {
        TASKS.add(new Task(id, title, asks));
    }

    /** The languages: their name in a request, the name shown, and the tag for a code block. */
    static final Map<String, String[]> LANGS = new LinkedHashMap<String, String[]>();

    static {
        LANGS.put("typescript", new String[]{"typescript|\\bts\\b", "TypeScript", "typescript"});
        LANGS.put("javascript", new String[]{"javascript|\\bjs\\b|node(?:\\.?js)?", "JavaScript", "javascript"});
        LANGS.put("python", new String[]{"python|\\bpy\\b", "Python", "python"});
        LANGS.put("java", new String[]{"\\bjava\\b(?!\\s*script)", "Java", "java"});
        LANGS.put("kotlin", new String[]{"kotlin", "Kotlin", "kotlin"});
        LANGS.put("cpp", new String[]{"c\\+\\+|\\bcpp\\b", "C++", "cpp"});
        LANGS.put("csharp", new String[]{"c#|c sharp|csharp|\\.net\\b", "C#", "csharp"});
        LANGS.put("c", new String[]{"\\bin c\\b|\\bc language\\b|\\bc program\\b|\\bc code\\b|^c\\b|\\busing c\\b", "C", "c"});
        LANGS.put("go", new String[]{"golang|\\bin go\\b|\\bgo (?:code|program|language)\\b|\\busing go\\b", "Go", "go"});
        LANGS.put("rust", new String[]{"\\brust\\b", "Rust", "rust"});
        LANGS.put("php", new String[]{"\\bphp\\b", "PHP", "php"});
        LANGS.put("ruby", new String[]{"\\bruby\\b", "Ruby", "ruby"});
        LANGS.put("bash", new String[]{"\\bbash\\b|shell script|\\bshell\\b|\\bsh\\b|terminal command", "Bash", "bash"});
        LANGS.put("swift", new String[]{"\\bswift\\b", "Swift", "swift"});
        LANGS.put("dart", new String[]{"\\bdart\\b|flutter", "Dart", "dart"});
        LANGS.put("sql", new String[]{"\\bsql\\b|mysql|postgres|sqlite", "SQL", "sql"});
    }

    /** What a message asks for: the task (or null), the language (or null). */
    public static final class Ask {
        public final String task, lang;

        Ask(String task, String lang) {
            this.task = task;
            this.lang = lang;
        }
    }

    private static final Pattern CODING = Pattern.compile("(?i)\\b(?:how (?:do|can|would|to|should) (?:i|you|we|one)?\\s*|"
            + "write (?:me )?(?:a |an |the |some )?(?:[\\w-]+ ){0,3}?(?:function|program|code|script|snippet|method|class|loop)|"
            + "(?:give|show) me (?:the |some |an? )?(?:code|example|snippet|program)|"
            + "code (?:to|for|that)|example (?:of|for)|snippet|in code|programm?ing|syntax for|implement)");

    /** A request for a task in a language, as already understood ("and in Rust?" after a recipe in Go). */
    public static Ask ask(String task, String lang) {
        return new Ask(task, lang);
    }

    /** A short follow-up naming only a language ("and in Rust?", "what about Java?", "in C++ please"): that language. */
    public static String followUp(String message) {
        String m = message == null ? "" : message.trim();
        if (m.split("\\s+").length > 6 || !m.matches("(?is)^(?:and |what about |how about |now |also |ok,? |okay,? )?(?:in |using |with )?.{1,20}?(?: please| too| instead| as well)?[?.!]*$")) {
            return null;
        }
        return language(m.matches("(?is).*\\b(?:in|using|with)\\b.*") ? m : "in " + m);
    }

    /** R1: a coding request read, or null when the message isn't one. */
    public static Ask read(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        String lang = language(m);
        boolean coding = CODING.matcher(m).find() || lang != null && m.matches("(?is).*\\b(?:in|using|with)\\b.*");
        if (!coding) {
            return null;
        }
        String task = null;
        int at = Integer.MAX_VALUE;
        for (Task t : TASKS) {
            Matcher x = t.asks.matcher(m);
            if (x.find() && x.start() < at) { // the first task named is the one asked
                task = t.id;
                at = x.start();
            }
        }
        if (task == null && lang == null) {
            return null;
        }
        if (task == null && !CODING.matcher(m).find()) {
            return null;
        }
        return new Ask(task, lang);
    }

    /** The words that may stand around a task named on its own ("sort a list in Go please") without making it something else. */
    private static final java.util.Set<String> FILLER = new java.util.HashSet<String>(Arrays.asList("a", "an", "the", "me", "please", "for",
            "from", "of", "in", "into", "with", "using", "to", "code", "function", "program", "method", "script", "snippet", "how", "i", "do",
            "can", "you", "show", "write", "give", "make", "some", "example", "and", "list", "array", "string", "numbers", "number", "parse", "find",
            "check", "get", "read", "convert", "print", "handle", "catch", "use", "create", "generate", "build", "implement", "if", "is", "it"));

    /**
     * A looser reading, when a message carries a task's words but not the usual way of asking for code ("Sort a list",
     * "Reverse a string in Go"): the task named on its own, with nothing around it but filler and a language. Data in the
     * message ("Sort these: 5 3 1", "Reverse the word hello") makes it a thing to do, not code to write: null.
     */
    private static final List<String> DOING = Arrays.asList("sort", "reverse", "sum", "add", "find", "remove", "check", "make", "convert",
            "turn", "parse", "split", "count", "read", "write", "use", "get", "fetch", "print", "handle", "catch", "loop", "iterate",
            "generate", "create", "build", "implement", "show", "how", "code", "program", "dedupe", "deduplicate", "open", "save", "call");

    public static Ask loose(String message) {
        if (message == null || message.matches("(?s).*(?:[:\"“”]|\\d.*\\d).*")) {
            return null;
        }
        String m = message.trim().replaceAll("[?.!]+$", "");
        if (!m.matches("(?i)^(?:please\\s+)?(?:" + String.join("|", DOING) + ")\\b.*")) {
            return null; // asked for, not mentioned: "Sort a list", not "The prime number"
        }
        String lang = language(m);
        for (Task t : TASKS) {
            Matcher x = t.asks.matcher(m);
            if (!x.find()) {
                continue;
            }
            String rest = (m.substring(0, x.start()) + " " + m.substring(x.end())).toLowerCase(Locale.ROOT);
            if (lang != null) {
                rest = rest.replaceAll(LANGS.get(lang)[0], " ");
            }
            boolean bare = true;
            for (String w : rest.split("[^\\p{L}+#']+")) {
                if (!w.isEmpty() && !FILLER.contains(w)) {
                    bare = false;
                    break;
                }
            }
            return bare ? new Ask(t.id, lang) : null;
        }
        return null;
    }

    /** The language a message names, or null. */
    public static String language(String message) {
        String low = message.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> e : LANGS.entrySet()) {
            if (Pattern.compile(e.getValue()[0], Pattern.CASE_INSENSITIVE).matcher(low).find()) {
                return e.getKey();
            }
        }
        return null;
    }

    public static String shown(String lang) {
        String[] l = LANGS.get(lang);
        return l == null ? lang : l[1];
    }

    static String title(String task) {
        for (Task t : TASKS) {
            if (t.id.equals(task)) {
                return t.title;
            }
        }
        return task.replace('_', ' ');
    }

    // ------------------------------------------------------------------------------------------------- the recipes

    /** task → language → code, as shipped (tested before it was built). */
    private final Map<String, Map<String, String>> shipped = new LinkedHashMap<String, Map<String, String>>();
    /** task|language → {strength, code}: what it has learned (1 untested, 2 or more confirmed). */
    private final Map<String, String[]> learned = new LinkedHashMap<String, String[]>();
    private final File learnedFile;

    public Recipes(InputStream shippedTsv, File learnedFile) {
        this.learnedFile = learnedFile;
        if (shippedTsv != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(shippedTsv, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t", 3);
                    if (line.startsWith("#") || p.length < 3) {
                        continue;
                    }
                    shipped.computeIfAbsent(p[0], k -> new LinkedHashMap<String, String>()).put(p[1], unescape(p[2]));
                }
            } catch (IOException ignored) {
                // none shipped: what it learns still answers
            }
        }
        load();
    }

    public int size() {
        int n = 0;
        for (Map<String, String> m : shipped.values()) {
            n += m.size();
        }
        return n;
    }

    /** The answer: the code, whether it was tested, and how to say it. */
    public static final class Recipe {
        public final String task, lang, code;
        /** Shipped (tested before it shipped) or confirmed since; false: found, not yet confirmed. */
        public final boolean tested;

        Recipe(String task, String lang, String code, boolean tested) {
            this.task = task;
            this.lang = lang;
            this.code = code;
            this.tested = tested;
        }

        /** "Here's how to reverse a string in Go: ```go …``` It compiles and runs (tested)." */
        public String said() {
            return "Here's how to " + title(task) + " in " + shown(lang) + ":\n\n```" + LANGS.getOrDefault(lang, new String[]{"", "", lang})[2]
                    + "\n" + code.trim() + "\n```\n\n" + (tested ? "Tested: it compiles and runs as it is."
                    : "Found, not tested yet: tell me if it works (thumbs up), and I'll keep it.");
        }
    }

    /** R2: the recipe for a task in a language: what it learned and confirmed, else what shipped, else what it found. */
    public synchronized Recipe get(String task, String lang) {
        String[] l = learned.get(task + "|" + lang);
        if (l != null && Integer.parseInt(l[0]) >= 2) {
            return new Recipe(task, lang, l[1], true);
        }
        Map<String, String> m = shipped.get(task);
        if (m != null && m.containsKey(lang)) {
            return new Recipe(task, lang, m.get(lang), true);
        }
        return l == null ? null : new Recipe(task, lang, l[1], false);
    }

    /** The languages a task is known in (shipped or confirmed). */
    public synchronized List<String> languages(String task) {
        List<String> out = new ArrayList<String>();
        Map<String, String> m = shipped.get(task);
        if (m != null) {
            out.addAll(m.keySet());
        }
        for (Map.Entry<String, String[]> e : learned.entrySet()) {
            String[] k = e.getKey().split("\\|");
            if (k[0].equals(task) && Integer.parseInt(e.getValue()[0]) >= 2 && !out.contains(k[1])) {
                out.add(k[1]);
            }
        }
        return out;
    }

    /** R3: code found for a task and language, kept untested (it isn't answered as known until confirmed). */
    public synchronized void found(String task, String lang, String code) {
        if (task == null || lang == null || code == null || code.trim().isEmpty() || shipped.containsKey(task) && shipped.get(task).containsKey(lang)) {
            return;
        }
        String key = task + "|" + lang;
        String[] had = learned.get(key);
        if (had == null || !had[1].equals(code.trim())) {
            learned.put(key, new String[]{"1", code.trim()});
            save();
        }
    }

    /** R3: it worked (marked right, or ran without failing): known from now on. False when there was nothing to confirm. */
    public synchronized boolean confirm(String task, String lang) {
        String[] had = learned.get(task + "|" + lang);
        if (had == null) {
            return false;
        }
        had[0] = String.valueOf(Math.max(2, Integer.parseInt(had[0]) + 1));
        save();
        return true;
    }

    /** R3: it didn't work: dropped, never given again. */
    public synchronized void wrong(String task, String lang) {
        if (learned.remove(task + "|" + lang) != null) {
            save();
        }
    }

    /**
     * The task a piece of code does, when it is (near enough) one of the recipes in that language: most of its words are
     * the recipe's. Null when it isn't one.
     */
    public synchronized String match(String code, String lang) {
        java.util.Set<String> words = tokens(code);
        if (words.size() < 3) {
            return null;
        }
        String best = null;
        double bestScore = 0;
        for (Map.Entry<String, Map<String, String>> e : shipped.entrySet()) {
            String r = e.getValue().get(lang);
            if (r == null) {
                continue;
            }
            java.util.Set<String> rw = tokens(r);
            java.util.Set<String> both = new java.util.HashSet<String>(rw);
            both.retainAll(words);
            java.util.Set<String> any = new java.util.HashSet<String>(rw);
            any.addAll(words);
            double j = any.isEmpty() ? 0 : both.size() / (double) any.size();
            if (j > bestScore) {
                bestScore = j;
                best = e.getKey();
            }
        }
        return bestScore >= 0.6 ? title(best) : null;
    }

    /** The task id a piece of code does (as {@link #match}), or null. */
    public synchronized String matchTask(String code, String lang) {
        String title = match(code, lang);
        if (title == null) {
            return null;
        }
        for (Task t : TASKS) {
            if (t.title.equals(title)) {
                return t.id;
            }
        }
        return null;
    }

    /** A task's name as said ("reverse a string"). */
    public static String titleOf(String task) {
        return title(task);
    }

    private static java.util.Set<String> tokens(String code) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (String w : code.split("[^A-Za-z0-9_]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------- R4: finding code

    private static final Pattern FENCED = Pattern.compile("```\\s*([A-Za-z0-9+#.-]*)\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern PRE = Pattern.compile("(?is)<pre[^>]*>(?:\\s*<code[^>]*>)?(.*?)(?:</code>\\s*)?</pre>");

    /** R4: the code block in a page or text that reads as {@code lang}, or null. */
    public static String code(String page, String lang) {
        if (page == null) {
            return null;
        }
        List<String> blocks = new ArrayList<String>();
        Matcher f = FENCED.matcher(page);
        while (f.find()) {
            blocks.add(f.group(2));
        }
        Matcher p = PRE.matcher(page);
        while (p.find()) {
            blocks.add(p.group(1).replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .replace("&#39;", "'").replace("&amp;", "&"));
        }
        // code in a post (Reddit, a forum): lines indented four spaces or a tab, as people share it there
        StringBuilder run = new StringBuilder();
        for (String line : (page + "\n").split("\n")) {
            if (line.matches("(?: {4}|\t).*") && !line.trim().isEmpty() || run.length() > 0 && line.trim().isEmpty()) {
                run.append(line.replaceFirst("^(?: {4}|\t)", "")).append('\n');
            } else {
                if (run.toString().trim().split("\n").length >= 2) {
                    blocks.add(run.toString());
                }
                run.setLength(0);
            }
        }
        String best = null;
        int bestScore = 0;
        for (String b : blocks) {
            String c = b.trim();
            if (c.length() < 10 || c.length() > 4000) {
                continue;
            }
            int s = looks(c, lang);
            if (s > bestScore) {
                best = c;
                bestScore = s;
            }
        }
        return bestScore >= 2 ? best : null;
    }

    /** How much a block reads as the language: its own words and marks. */
    static int looks(String code, String lang) {
        String[] signs;
        switch (lang == null ? "" : lang) {
            case "python": signs = new String[]{"def ", "print(", "import ", ":\n", "self", "elif "}; break;
            case "javascript": signs = new String[]{"const ", "let ", "function", "=>", "console.log", "require("}; break;
            case "typescript": signs = new String[]{": string", ": number", "interface ", "const ", "=>", "console.log"}; break;
            case "java": signs = new String[]{"public ", "class ", "System.out", "static ", "void ", "new "}; break;
            case "kotlin": signs = new String[]{"fun ", "val ", "var ", "println(", "class ", ": Int"}; break;
            case "csharp": signs = new String[]{"using System", "Console.Write", "public ", "static void", "var ", "namespace "}; break;
            case "cpp": signs = new String[]{"#include", "std::", "cout", "int main", "vector<", "auto "}; break;
            case "c": signs = new String[]{"#include", "printf(", "int main", "char ", "malloc", "return 0"}; break;
            case "go": signs = new String[]{"package main", "func ", "fmt.", ":=", "import ("}; break;
            case "rust": signs = new String[]{"fn ", "let ", "println!", "mut ", "impl ", "::"}; break;
            case "php": signs = new String[]{"<?php", "$", "echo ", "function ", "->"}; break;
            case "ruby": signs = new String[]{"def ", "puts ", "end\n", "do |", ".each"}; break;
            case "bash": signs = new String[]{"#!/bin", "echo ", "$(", "fi\n", "done", "${"}; break;
            case "swift": signs = new String[]{"func ", "let ", "var ", "print(", "import Foundation"}; break;
            case "dart": signs = new String[]{"void main", "print(", "final ", "var ", "import 'package:"}; break;
            case "sql": signs = new String[]{"SELECT ", "FROM ", "WHERE ", "INSERT ", "CREATE TABLE"}; break;
            default: signs = new String[0];
        }
        int n = 0;
        for (String s : signs) {
            n += code.contains(s) ? 1 : 0;
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------------- storage

    private void load() {
        if (learnedFile == null || !learnedFile.isFile()) {
            return;
        }
        try {
            for (String line : new String(Files.readAllBytes(learnedFile.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.split("\t", 3);
                if (p.length == 3 && p[1].matches("\\d{1,4}")) {
                    learned.put(p[0], new String[]{p[1], unescape(p[2])});
                }
            }
        } catch (IOException ignored) {
            // nothing learned yet
        }
    }

    private void save() {
        if (learnedFile == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String[]> e : learned.entrySet()) {
            b.append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t').append(escape(e.getValue()[1])).append('\n');
        }
        try {
            File dir = learnedFile.getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            File tmp = new File(learnedFile.getPath() + ".part");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(learnedFile)) {
                learnedFile.delete();
                tmp.renameTo(learnedFile);
            }
        } catch (IOException ignored) {
            // kept in memory for now
        }
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
