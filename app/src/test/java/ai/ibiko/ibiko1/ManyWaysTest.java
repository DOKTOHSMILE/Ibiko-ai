package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.Test;

/**
 * Every message Ibiko reads about itself and the person, said in as many ways as people say it: each core wording with
 * greetings and fillers before it, polite wrappers, the assistant's name, slang ("u", "r", "ur", "wat", "im"), shouting
 * and lower case, and every kind of ending. Hundreds of thousands of messages; each must reach the same answer.
 */
public class ManyWaysTest {
    private static final SelfModel.State STATE = new SelfModel.State() {
        @Override public boolean online() { return false; }
        @Override public boolean modelReady() { return false; }
        @Override public int words() { return 0; }
        @Override public int factsRead() { return 0; }
        @Override public int confirmed() { return 0; }
        @Override public String focus() { return null; }
        @Override public String pasted() { return null; }
        @Override public int asked() { return 3; }
        @Override public String lastQuestion() { return null; }
        @Override public String personName() { return null; }
    };

    private static final String[] LEAD = {"", "hey ", "hi, ", "hello ", "so ", "ok ", "okay so ", "um ", "ibiko, ", "hey ibiko ",
            "and ", "well, ", "please ", "yo "};
    private static final String[] WRAP = {"", "can you tell me ", "tell me ", "i want to know ", "i wonder ", "could you please tell me "};
    private static final String[] TAIL = {"", "?", "??", " ?", "?!", "!", ".", " please", ", ibiko", " ibiko?", " please?", "...", " 🙂"};

    /** Each way of spelling it: as written, with slang, shouted, capitalised. */
    private static List<String> spellings(String s) {
        Set<String> out = new LinkedHashSet<String>();
        out.add(s);
        String slang = s.replaceAll("\\byou\\b", "u").replaceAll("\\bare\\b", "r").replaceAll("\\byour\\b", "ur")
                .replaceAll("\\bwhat\\b", "wat").replaceAll("\\bi am\\b", "im");
        out.add(slang);
        out.add(s.replace("what is", "what's").replace("i am", "i'm").replace("you are", "you're"));
        List<String> cased = new ArrayList<String>();
        for (String o : out) {
            cased.add(o.toUpperCase(Locale.ROOT));
            cased.add(Character.toUpperCase(o.charAt(0)) + o.substring(1));
        }
        out.addAll(cased);
        return new ArrayList<String>(out);
    }

    /** Runs every way of saying each core wording through {@code read} and checks the answer has {@code expect}. */
    private static int everyWay(String[] cores, boolean question, Function<String, String> read, String expect) {
        int n = 0;
        Set<String> missed = new LinkedHashSet<String>();
        for (String core : cores) {
            for (String lead : LEAD) {
                for (String wrap : question && core.matches("^(what|who|how|where).*") ? WRAP : new String[]{""}) {
                    for (String spelled : spellings(core)) {
                        for (String tail : TAIL) {
                            String said = lead + wrap + spelled + tail;
                            String got = read.apply(said);
                            if (got == null || !got.contains(expect)) {
                                missed.add(Paraphrase.normalize(said) + "  <-  " + said + "  ->  " + got);
                            }
                            n++;
                        }
                    }
                }
            }
        }
        StringBuilder b = new StringBuilder();
        Set<String> forms = new LinkedHashSet<String>();
        for (String m : missed) {
            if (forms.add(m.substring(0, m.indexOf("  <-")))) {
                b.append("\n").append(m);
            }
        }
        assertTrue(missed.size() + " of " + n + " missed; their forms:" + b, missed.isEmpty());
        return n;
    }

    private static Function<String, String> self() {
        return s -> SelfModel.reply(s, STATE, null);
    }

    @Test
    public void questionsAboutIbikoAreCaughtEveryWay() {
        Map<String[], String> intents = new LinkedHashMap<String[], String>();
        intents.put(new String[]{"what are you", "what exactly are you", "who are you", "tell me about yourself", "what is ibiko",
                "describe yourself", "introduce yourself", "what kind of ai are you"}, "I'm Ibiko, an AI");
        intents.put(new String[]{"what can we talk about", "what should we talk about", "what can we chat about",
                "what can i talk to you about", "what topics can we talk about", "what do you want to talk about",
                "what can we discuss"}, "Almost anything");
        intents.put(new String[]{"what can you do", "what are your skills", "what are you capable of", "what are you good at",
                "how can you help me", "what are your abilities", "what else can you do for me"}, "Here's what I can do");
        intents.put(new String[]{"how are you", "how are you doing", "how are you today", "how have you been", "how is it going",
                "are you okay", "what's up", "how far"}, "I'm running well");
        intents.put(new String[]{"how do you work", "how do you think", "how does your brain work", "how do you understand me"}, "Step by step");
        intents.put(new String[]{"are you married", "are you even married", "are you single", "are you in a relationship"}, "No, I'm not");
        intents.put(new String[]{"are you tired", "are you sleepy"}, "I don't get tired");
        intents.put(new String[]{"are you conscious", "are you self-aware", "do you have feelings", "can you feel", "are you alive"}, "Not the way you are");
        int total = 0;
        for (Map.Entry<String[], String> e : intents.entrySet()) {
            total += everyWay(e.getKey(), true, self(), e.getValue());
        }
        System.out.println("questions about Ibiko, said every way: " + total);
        assertTrue(total > 50_000);
    }

    @Test
    public void whatThePersonSaysIsCaughtEveryWay() {
        int total = 0;
        total += everyWay(new String[]{"i am a man", "i am a guy", "i am male"}, false, s -> new Person(null).heard(s), "you're a man");
        total += everyWay(new String[]{"i am a woman", "i am a lady", "i am female", "i am a girl"}, false, s -> new Person(null).heard(s), "you're a woman");
        total += everyWay(new String[]{"i am tired", "i am so tired", "i feel tired", "i am really tired"}, false, s -> new Person(null).heard(s), "tired");
        total += everyWay(new String[]{"i live in lagos", "i stay in lagos"}, false, s -> new Person(null).heard(s), "Lagos");
        total += everyWay(new String[]{"i am from enugu"}, false, s -> new Person(null).heard(s), "Enugu");
        total += everyWay(new String[]{"my name is emeka", "call me emeka", "my name's emeka"}, false, s -> new Person(null).heard(s), "Emeka");
        System.out.println("things the person says about themselves, every way: " + total);
        assertTrue(total > 10_000);
    }

    @Test
    public void questionsAboutThePersonAreCaughtEveryWay() {
        java.io.File dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("person").toFile();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        Person p = new Person(dir);
        p.heard("My name is Emeka");
        int total = everyWay(new String[]{"what is my name", "do you remember my name", "do you know my name", "do you recall my name"},
                true, s -> p.asked(s, null), "Emeka");
        total += everyWay(new String[]{"what do you know about me", "what have you learned about me", "do you know me",
                "do you remember me", "what do you know of me"}, true, s -> p.asked(s, null), "your name is Emeka");
        total += everyWay(new String[]{"what is my current location", "where am i", "where am i now", "what is my location",
                "do you know where i am"}, true, s -> p.asked(s, null), "I can't see where you are");
        System.out.println("questions about the person, every way: " + total);
        assertTrue(total > 5_000);
    }

    @Test
    public void theNormalFormIsOneForMany() {
        assertEquals("what are you", Paraphrase.normalize("hey ibiko, wat r u??"));
        assertEquals("what are you", Paraphrase.normalize("So... WHAT ARE YOU exactly?"));
        assertEquals("what are you", Paraphrase.normalize("can you tell me what you are")
                .replace("what you are", "what are you"));
        assertEquals("what can we talk about", Paraphrase.normalize("ok, what can we chat about please"));
        assertEquals("i am a man", Paraphrase.normalize("im a man!!"));
    }
}
