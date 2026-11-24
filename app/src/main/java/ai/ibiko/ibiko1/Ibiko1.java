package ai.ibiko.ibiko1;

import ai.ibiko.ibiko1.formulas.Commands;
import ai.ibiko.ibiko1.formulas.Grammar;
import ai.ibiko.ibiko1.learner.Learner;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko v1 · 0.0.1, Ibiko's own model, in one class the app plugs in like any other model: {@link #answer} takes a
 * message and answers through the {@link Host}. Everything it is lives in this folder: its memory of confirmed answers
 * ({@link Knowledge}), the facts it read ({@link Facts}), its dictionary ({@link Lexicon}), its language formulas
 * ({@link Grammar}), its research ({@link ResearchAnswer}) and its learning sessions ({@link LearningJob}). Remove the
 * folder and the few lines that plug it in, and the app runs on its other models.
 *
 * <p>Why Java and not a native engine: Ibiko v1 is rules and lookups that run in microseconds, so it lives in the app's
 * own language with no bridge to cross. A trained network is another matter: {@link Training} writes what Ibiko v1
 * learned as a training file, and a model trained from it runs on the device through the app's llama.cpp engine
 * (C++, the fastest way to run a language model on a phone).
 */
public final class Ibiko1 {
    public static final String NAME = "Ibiko v1 · 0.0.1";

    /** How an answer came: from memory, from research, or neither (a greeting, a sum). */
    public enum How { MEMORY, RESEARCH, OTHER }

    /** What Ibiko v1 needs from the screen it answers on. */
    public interface Host {
        /** The persona answering ("obi"). */
        String persona();

        /** A step shown while it thinks, and the end of the steps. */
        void step(String step);

        void stepsDone();

        /** Its answer in the chat. */
        void say(String persona, String text);

        /** Check and cancel under {@code answer}; none under a set reply; and the state of the buttons. */
        void askRating(String question, String answer);

        void noRating(String text);

        boolean ratingWaiting();

        void clearRating();

        boolean online();

        /** An exact sum for the message ("12 × 7 = 84"), or null. */
        String sum(String message);

        /** What the brain box learned from the other models: an answer, learning one, forgetting one, its sizes. */
        String recallTalk(String question);

        void learnTalk(String question, String answer);

        void forgetTalk(String question);

        int talkSize();

        int shapes();

        /** What the pilot has said about themselves, as lines. */
        List<String> aboutPilot();

        /** One answer, counted in today's use; then the workload ring. */
        void answered(How how);

        void note(String text);

        void later(Runnable r, long ms);

        void onScreen(Runnable r);

        android.content.Context context();

        /** What the person likes to be called, or null when they haven't said. */
        default String pilotName() {
            return null;
        }

        /** Whether this chat's tools allow a function code ("SR"). */
        default boolean allows(String code) {
            return false;
        }

        /** Whether a model is downloaded on the device that Ibiko v1 can ask for what its own memory doesn't hold. */
        default boolean modelReady() {
            return false;
        }

        /**
         * Asks the device's model (blocking: called off the main thread). Null when there is none or it failed. Its
         * answer is shown as unchecked, and the pilot's check or cancel teaches Ibiko v1, as with any model.
         */
        default String askModel(String system, String question) {
            return null;
        }

        /** Whether an API model the pilot set up can be asked what Ibiko v1 doesn't know (online, and allowed). */
        default boolean teacherReady() {
            return false;
        }

        /** The API model's answer, or null. Blocking: off the main thread. Costs the pilot tokens: asked only when needed. */
        default String askTeacher(String system, String question) {
            return null;
        }

        /** The API model's name, as the pilot knows it ("Claude", "GPT-5"). */
        default String teacherName() {
            return "the API model";
        }

        /** Opens the Learning chat, where the learning conversation happens; false where there is none (no screen). */
        default boolean openLearningChat() {
            return false;
        }

        /** Carries out a function code line ("§SR weather in Lagos") through the app's gate for every model. */
        default void act(String message, String codeLine) {
        }
    }

    private final Endpoint endpoint = new Endpoint(this);

    /** The door the app talks to Ibiko v1 through. */
    public Endpoint endpoint() {
        return endpoint;
    }

    private final Host host;
    private final File dir;
    private Knowledge knowledge;
    private Facts facts;
    private Lexicon lexicon;
    private Learner learner;
    /** Answers are numbered: a newer message makes an older one's steps stop. */
    private int token;
    private String worker;
    private How how = How.OTHER;
    /** The question it last asked "accurate?" about, for a correction typed instead of tapped. */
    private String ratingQuestion;
    /** A question waiting for the person's own explanation ({@link Inquiry}), or null. */
    private volatile Inquiry.Pending inquiry;
    /** What the conversation is about ("computing", "botany"), from the words said lately; null when it isn't clear. */
    private volatile String topic;
    private volatile long topicAt;
    /** A question it couldn't answer and asked the person back about: their reply is formed before it is kept. */
    private volatile Curiosity.Gap gap;
    /** The last answer worked out by asking back, open to "no, it's …" for {@link Inquiry#WAIT_MS}. */
    private volatile String inquiryQuestion, inquiryAnswer;
    private volatile long inquiryAnsweredAt;

    /** Settings › Learning: ask what the person means before answering what it doesn't know. */
    public static final String ASK_BACK = "ibiko1_ask_back";

    private boolean asksBack() {
        try {
            android.content.Context c = host.context();
            return c == null || c.getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE).getBoolean(ASK_BACK, true);
        } catch (RuntimeException e) {
            return true;
        }
    }

    public Ibiko1(Host host, File filesDir) {
        this.host = host;
        this.dir = new File(filesDir, "brain");
    }

    public synchronized Knowledge knowledge() {
        if (knowledge == null) {
            knowledge = Store.knowledge(dir);
        }
        return knowledge;
    }

    public synchronized Facts facts() {
        if (facts == null) {
            facts = Store.facts(dir);
        }
        return facts;
    }

    public synchronized Lexicon lexicon() {
        if (lexicon == null) {
            lexicon = Store.lexicon(dir);
        }
        return lexicon;
    }

    private static BuiltIn builtIn;
    private WordTalk wordTalk;

    /** The words engine (meanings both ways, morphology, grammar), opened on first use; null without the dictionary. */
    synchronized WordTalk wordTalk() {
        if (wordTalk == null) {
            ai.ibiko.ibiko1.words.Meanings m = meanings(new File(dir, "knowledge"));
            wordTalk = m == null ? null : new WordTalk(m, new ai.ibiko.ibiko1.words.SenseTags(m,
                    TextCode.open(Plug.asset, "knowledge/senses_extra.tsv")));
        }
        return wordTalk;
    }

    /** The dictionary turned both ways ({@link ai.ibiko.ibiko1.words.MeaningIndex}) from a knowledge folder, or null. */
    public static ai.ibiko.ibiko1.words.Meanings meanings(File knowledge) {
        ai.ibiko.ibiko1.words.MeaningIndex index = ai.ibiko.ibiko1.words.MeaningIndex.of(Store.unpacked(knowledge, "lexicon.tsv"));
        if (index != null) {
            try {
                // the words the dictionary lacks ("they", "went", "wahala", "selfie"), after its own
                index.addMore(TextCode.open(Plug.asset, "knowledge/lexicon_more.tsv"));
            } catch (RuntimeException ignored) {
                // none shipped here: the dictionary alone
            }
        }
        return index == null ? null : new ai.ibiko.ibiko1.words.Meanings(index);
    }

    /**
     * Loads what the first question would otherwise wait for: the built-in facts, and the dictionary and concept tables
     * unpacked in place. Call it off the main thread at start (the app does it while its splash shows).
     */
    public static void warm(File filesDir) {
        File k = new File(new File(filesDir, "brain"), "knowledge");
        File lexicon = Store.unpacked(k, "lexicon.tsv");
        Store.unpacked(k, ai.ibiko.learn.WordRank.FILE); // the words by use, read with the dictionary (pack format 2)
        ai.ibiko.learn.WordBook.open(lexicon); // first: every English word its fixed id before anything files words
        ai.ibiko.ibiko1.words.MeaningIndex.of(lexicon); // the dictionary turned both ways: built once, then loaded
        builtIn();
        ai.ibiko.ibiko1.neural.Neural.get(); // the tokenizer and word vectors (docs/IBIKO_NEURAL.md), read once
        meanings(k); // the dictionary both ways, shared: memory and the truth checks match reworded questions through it
        Store.unpacked(k, "concepts.tsv");
        // what was kept in older forms is brought to format 2, the same text: each chat's conversation (its script file)
        // and every coded store (answers learned, facts, words, meanings, the brain box)
        ai.ibiko.learn.Identifier.convertAll(filesDir);
        ai.ibiko.learn.CodeFile.upgradeAll(new File(filesDir, "brain"));
        ai.ibiko.learn.CodeFile.upgradeAll(new File(filesDir, "learning"));
        ai.ibiko.learn.CodeFile.upgradeAll(ai.ibiko.main.ChatSpace.root(filesDir));
    }

    /** What shipped with the app: known from the moment it is installed, with no internet and nothing learned yet. */
    private static CoreFacts coreFacts;

    /** The app's core facts, the pack the other brains are given too (knowledge/base_knowledge.json). */
    public static synchronized CoreFacts coreFacts() {
        if (coreFacts == null || coreFacts.size() == 0) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(Plug.asset, "knowledge/base_knowledge.json");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            coreFacts = new CoreFacts(in);
        }
        return coreFacts;
    }

    public static synchronized BuiltIn builtIn() {
        if (builtIn == null) {
            builtIn = new BuiltIn(TextCode.open(Plug.asset, "knowledge/world_facts.tsv"),
                    TextCode.open(Plug.asset, "knowledge/country_more.tsv"), TextCode.open(Plug.asset, "knowledge/elements_more.tsv"),
                    TextCode.open(Plug.asset, "knowledge/local_facts.tsv"),
                    TextCode.open(Plug.asset, "knowledge/wikidata_facts.tsv")); // Wikidata (CC0): see WIKIDATA_LICENSE.txt
        }
        return builtIn;
    }

    private Composer composer;

    /**
     * Facts put together (ages at events, comparisons, the most in a place), over the built-in facts and what it reads.
     * Its memory first (events it read or was told before, sure ones); then the encyclopedia, read by the events
     * formula when asked, and what it read is kept in memory: it finds facts for itself and remembers them.
     */
    synchronized Composer composer() {
        if (composer == null) {
            composer = new Composer(builtIn(), title -> {
                Encyclopedia e = encyclopedia();
                return e == null ? null : e.find(title);
            }, subject -> facts().happenings(subject),
                    article -> facts().learn(article.text, article.title, 2, System.currentTimeMillis()))
                    .measures((subject, what) -> facts().measure(subject, what));
        }
        return composer;
    }

    private static Encyclopedia encyclopedia;

    /** The encyclopedia that ships with the app: every Simple English Wikipedia article's opening, offline. */
    public static synchronized Encyclopedia encyclopedia() {
        if (encyclopedia == null) {
            encyclopedia = new Encyclopedia();
        }
        return encyclopedia;
    }

    /** How answers were reached, by kind of request: learned from every reply, used by every brain. */
    public Methods methods() {
        return Store.methods(dir);
    }

    /**
     * A reply was just reached through these steps (as the chat showed them), by any brain: the way is learned now,
     * with no tap needed. The pilot's check or cross later only makes it stronger or weaker.
     */
    public void learnWay(String request, List<String> shownSteps) {
        learnThinking(request, String.join(". ", shownSteps)); // the steps' own moves (looked up, compared, checked)
        methods().observe(request, Methods.steps(shownSteps), System.currentTimeMillis());
    }

    /** The independent learner: grammar, sentence shapes, the pilot's word on answers, open questions, the score. */
    public synchronized Learner learner() {
        if (learner == null) {
            learner = Store.learner(dir);
        }
        return learner;
    }

    /** The learning score line, read from the files (for screens that don't hold Ibiko v1). */
    public static String scoreLine(File filesDir) {
        File d = new File(filesDir, "brain");
        return Store.learner(d).scoreLine();
    }

    /** Premeditation: the pilot's habits (what follows what) and the look-before check ({@code formulas/Foresight}). */
    public ai.ibiko.ibiko1.formulas.Foresight foresight() {
        return Store.foresight(dir);
    }

    /** For any brain, with the request: think ahead, and the next step this pilot usually takes after one like it. */
    public String premeditate(String request) {
        return premeditate(request, "default");
    }

    /** The same premeditation, with UCL references isolated to the conversation that asked. */
    public String premeditate(String request, String conversationId) {
        return foundation(request, conversationId) + foresight().hint(Methods.kind(request)) + reflection().hint(Methods.kind(request))
                + ai.ibiko.ibiko1.formulas.Critical.brief(ai.ibiko.ibiko1.formulas.Critical.read(request))
                + ai.ibiko.ibiko1.cognition.Cognition.plan(request) // pass 1: the plan, the same for every brain
                + (ai.ibiko.ibiko1.formulas.Analyst.hasData(request) ? ai.ibiko.ibiko1.formulas.Analyst.BRIEF : "")
                + (ai.ibiko.ibiko1.formulas.Probability.applies(request) ? ai.ibiko.ibiko1.formulas.Probability.BRIEF : "");
    }

    /**
     * The foundation every answer stands on ({@code formulas/Truth}): all that is known about this request, from every
     * store, settled by how trustworthy each source is, with how sure it is.
     */
    public String foundation(String request) {
        return foundation(request, "default");
    }

    /** The truth foundation with the UCL reading scoped to the requesting conversation. */
    public String foundation(String request, String conversationId) {
        List<ai.ibiko.ibiko1.formulas.Truth.Claim> claims = new ArrayList<ai.ibiko.ibiko1.formulas.Truth.Claim>();
        long now = System.currentTimeMillis();
        String about = Methods.kind(request) + ":" + (request == null ? "" : request.trim().toLowerCase(Locale.US));
        try {
            Knowledge.Fact k = knowledge().recall(request);
            if (k != null && k.right) {
                claims.add(new ai.ibiko.ibiko1.formulas.Truth.Claim(about, k.answer,
                        k.sure ? ai.ibiko.ibiko1.formulas.Truth.Source.CONFIRMED : ai.ibiko.ibiko1.formulas.Truth.Source.LEARNED, now));
            }
            String looked = lookUp(request);
            if (looked != null) {
                claims.add(new ai.ibiko.ibiko1.formulas.Truth.Claim(about, looked, ai.ibiko.ibiko1.formulas.Truth.Source.BUILT_IN, now));
            }
            String shipped = builtIn().answer(request);
            if (shipped != null) {
                claims.add(new ai.ibiko.ibiko1.formulas.Truth.Claim(about, shipped, ai.ibiko.ibiko1.formulas.Truth.Source.BUILT_IN, now));
            }
            String core = coreFacts().answer(request);
            if (core != null) {
                claims.add(new ai.ibiko.ibiko1.formulas.Truth.Claim(about, core, ai.ibiko.ibiko1.formulas.Truth.Source.BUILT_IN, now));
            }
            String read = facts().answer(request);
            if (read != null) {
                claims.add(new ai.ibiko.ibiko1.formulas.Truth.Claim(about, read, ai.ibiko.ibiko1.formulas.Truth.Source.LEARNED, now));
            }
            List<String> pilot = host == null ? null : host.aboutPilot();
            String q = request == null ? "" : request.toLowerCase(Locale.US);
            if (pilot != null && q.matches("(?s).*\\b(my|me|i|i'm|mine)\\b.*")) {
                for (String p : pilot) {
                    claims.add(new ai.ibiko.ibiko1.formulas.Truth.Claim("pilot:" + p.toLowerCase(Locale.US), p,
                            ai.ibiko.ibiko1.formulas.Truth.Source.PILOT, now));
                }
            }
        } catch (RuntimeException e) {
            // a store that can't be read leaves the rules alone
        }
        // copies of one piece of evidence (the same words from two stores) count once, not as agreement
        List<ai.ibiko.ibiko1.formulas.Truth.Claim> distinct = new ArrayList<ai.ibiko.ibiko1.formulas.Truth.Claim>();
        for (ai.ibiko.ibiko1.formulas.Truth.Claim c : claims) {
            boolean copy = false;
            for (ai.ibiko.ibiko1.formulas.Truth.Claim d : distinct) {
                copy |= d.source == c.source && ai.ibiko.ibiko1.cognition.Evidence.nearSame(d.says, c.says);
            }
            if (!copy) {
                distinct.add(c);
            }
        }
        claims = distinct;
        String checkedBefore = "";
        try {
            checkedBefore = ledger().about(request, 3); // G9: the known mistakes and checked truths about this
        } catch (RuntimeException e) {
            // the ledger can't be read: the rest stands
        }
        String meaning = "";
        try {
            ai.ibiko.ibiko1.comprehend.Comprehension.Meaning m = understood(request, conversationId);
            if (m != null && (!m.operations.isEmpty() || !m.references.isEmpty() || !m.senses.isEmpty() || m.negated)) {
                meaning = " " + m.brief(); // what the message means, worked out before any brain reads it
            }
        } catch (RuntimeException e) {
            // no reading: the rest stands
        }
        String decision = "";
        try {
            decision = ai.ibiko.suit.Comprehension.of(request).decisionLine() + " ";
        } catch (RuntimeException e) {
            // the reading failed: the facts still stand
        }
        return decision + ai.ibiko.ibiko1.formulas.Truth.brief(claims) + checkedBefore + meaning;
    }

    /** G8: the witnesses a sentence is asked of, most trusted first (the built-in reference, then facts held firmly). */
    public List<ai.ibiko.ibiko1.formulas.Truth.Witness> witnesses() {
        List<ai.ibiko.ibiko1.formulas.Truth.Witness> w = new ArrayList<ai.ibiko.ibiko1.formulas.Truth.Witness>();
        w.add(knowledge()::check); // what the pilot confirmed or corrected comes first: their word over any reference
        w.add(builtIn()::check);
        w.add(facts()::check);
        w.add(ledger()); // G9: what was judged before, when nothing above speaks to it now
        return w;
    }

    /** G9: every sentence judged true or false, kept. */
    public ai.ibiko.ibiko1.formulas.TruthLedger ledger() {
        return Store.truthLedger(dir);
    }

    /** G8 out: a reply from any brain, asked "true or false?" sentence by sentence; the correction to say, or null. */
    public String questionReply(String reply) {
        return questionReply(reply, "out");
    }

    /** As {@link #questionReply(String)}; {@code way} is where the text came from ("out", "in", "pilot"), for the ledger. */
    public String questionReply(String reply, String way) {
        try {
            List<ai.ibiko.ibiko1.formulas.Truth.Checked> checked = ai.ibiko.ibiko1.formulas.Truth.question(reply, witnesses());
            ledger().record(checked, way, System.currentTimeMillis()); // G9: true and false both kept
            return ai.ibiko.ibiko1.formulas.Truth.correction(checked);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The calibration of how sure answers may sound, against how they turned out ({@code cognition/Calibration}). */
    public ai.ibiko.ibiko1.cognition.Calibration calibration() {
        return Store.calibration(dir);
    }

    /** What was learned, as records with their source and how far they are verified ({@code cognition/LearningRecords}). */
    public ai.ibiko.ibiko1.cognition.LearningRecords learningRecords() {
        return Store.learningRecords(dir);
    }

    /** The label each recent answer was given, for its rating to calibrate. */
    private final java.util.Map<String, ai.ibiko.ibiko1.cognition.Calibration.Label> labelled = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, ai.ibiko.ibiko1.cognition.Calibration.Label>() {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, ai.ibiko.ibiko1.cognition.Calibration.Label> e) {
                    return size() > 60;
                }
            });

    /**
     * Passes 2 and 3 for any brain's reply, once shown (the phone's model, an API model): its sentences asked "true or
     * false?" (G8), its working done again, its consistency and its causes checked, how sure it may be worked out; what
     * to say under it (a correction, a wrong step, a disagreement, a cause claimed from a link), or null.
     */
    public String reviewReply(String question, String reply) {
        try {
            long now = System.currentTimeMillis();
            List<ai.ibiko.ibiko1.formulas.Truth.Checked> checked = ai.ibiko.ibiko1.formulas.Truth.question(reply, witnesses());
            ledger().record(checked, "out", now);
            String correction = ai.ibiko.ibiko1.formulas.Truth.correction(checked);
            ai.ibiko.ibiko1.cognition.Cognition.Review r = ai.ibiko.ibiko1.cognition.Cognition.review(question, reply, checked,
                    question == null ? null : knownLines(question), 0.4, calibration(), false);
            labelled.put(answerKey(reply), r.label);
            if (correction == null) {
                return r.note;
            }
            return r.note == null ? correction : correction + "\n\n" + r.note;
        } catch (RuntimeException e) {
            return questionReply(reply);
        }
    }

    private static String answerKey(String answer) {
        String a = answer == null ? "" : answer.trim().replaceAll("\\s+", " ");
        return a.length() > 200 ? a.substring(0, 200) : a;
    }

    /** G8 in: a text to learn from, without the sentences found false. */
    public String trueOnly(String text) {
        List<ai.ibiko.ibiko1.formulas.Truth.Checked> checked = ai.ibiko.ibiko1.formulas.Truth.question(text, witnesses());
        ledger().record(checked, "in", System.currentTimeMillis());
        return ai.ibiko.ibiko1.formulas.Truth.trueOnly(checked);
    }

    private ai.ibiko.ibiko1.comprehend.ConversationReading comprehensionSessions;
    private ai.ibiko.ibiko1.comprehend.Lookup lookup;

    /**
     * The comprehension layer (ibiko1/comprehend): WordNet and ConceptNet, unpacked once from the app and read in
     * place, and the reading of each message with its conversation context.
     */
    private synchronized void loadComprehension() {
        if (comprehensionSessions == null) {
            File k = new File(dir, "knowledge");
            File lex = Store.unpacked(k, "lexicon.tsv"), con = Store.unpacked(k, "concepts.tsv");
            ai.ibiko.ibiko1.comprehend.Dictionary d = new ai.ibiko.ibiko1.comprehend.Dictionary(
                    new ai.ibiko.ibiko1.comprehend.SortedTable(lex));
            ai.ibiko.ibiko1.comprehend.Concepts c = new ai.ibiko.ibiko1.comprehend.Concepts(
                    new ai.ibiko.ibiko1.comprehend.SortedTable(con));
            comprehensionSessions = new ai.ibiko.ibiko1.comprehend.ConversationReading(d, c);
            lookup = new ai.ibiko.ibiko1.comprehend.Lookup(d, c);
        }
    }

    /** The default UCL parser, retained for callers that predate per-chat reading. */
    public synchronized ai.ibiko.ibiko1.comprehend.Comprehension comprehension() {
        loadComprehension();
        return comprehensionSessions.parser("default");
    }

    /** A dictionary or common-sense question answered offline ("what does X mean", "what is a knife used for"), or null. */
    public String lookUp(String question) {
        loadComprehension();
        try {
            return lookup.answer(question);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Default-chat compatibility overload. */
    public synchronized ai.ibiko.ibiko1.comprehend.Comprehension.Meaning understood(String message) {
        return understood(message, "default");
    }

    /** The message's structured meaning, with references resolved only against this conversation's own history. */
    public synchronized ai.ibiko.ibiko1.comprehend.Comprehension.Meaning understood(String message, String conversationId) {
        if (message == null) {
            return null;
        }
        try {
            loadComprehension();
            return comprehensionSessions.read(conversationId, message);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Learning from its own replies: how each one went, judged by what the pilot did next ({@code formulas/Reflection}). */
    public ai.ibiko.ibiko1.formulas.Reflection reflection() {
        return Store.reflection(dir);
    }

    /** The pilot's last request and whether a reply to it has been given since (any brain's). */
    private volatile String lastRequest;
    private volatile boolean replied;

    /** One of the pilot's messages, to any brain: it may judge Ibiko v1's last answer, and it is read for language. */
    public void hear(String said) {
        hear(said, "default");
    }

    /** One of the pilot's messages, scoped to the chat it was sent in. */
    public void hear(String said, String conversationId) {
        String before = lastRequest;
        if (before != null && replied) {
            reflection().observe(Methods.kind(before), before, said); // the next message judges the last reply
        }
        lastRequest = said;
        replied = false;
        understood(said, conversationId); // references stay inside this chat, not another chat's recent objects
        foresight().heard(said, Methods.kind(said), System.currentTimeMillis()); // what this pilot asks after what
        learner().heard(ai.ibiko.ibiko1.formulas.NumberSense.redact(said), System.currentTimeMillis()); // never a PIN or a phone number
        // "I prefer short answers": the person's preference, kept as theirs (never as a fact about the world)
        if (said != null && said.length() < 240 && ai.ibiko.ibiko1.cognition.Evidence.type(said) == ai.ibiko.ibiko1.cognition.Evidence.Type.PREFERENCE) {
            try {
                learningRecords().learn(ai.ibiko.ibiko1.cognition.LearningRecords.Category.preference, said, said.trim(), "user_statement",
                        conversationId == null ? "chat" : conversationId, 0.9, System.currentTimeMillis(), false, "preference");
            } catch (RuntimeException e) {
                // not kept this time
            }
        }
    }

    /**
     * Any model's answer: read for language, and for facts and events kept as weak ones (seen once). A model's claim
     * alone is not a fact: it is answered from or reasoned with only once something else says the same (the
     * encyclopedia, a page it researched, the pilot, or another reply), which makes it sure.
     */
    public void readReply(String reply) {
        replied = reply != null && !reply.trim().isEmpty();
        if (replied) {
            final String text = reply.length() > 6000 ? reply.substring(0, 6000) : reply;
            Thread t = new Thread(() -> {
                try {
                    facts().learn(text, null, 1, System.currentTimeMillis());
                } catch (RuntimeException ignored) {
                    // read for language all the same
                }
            }, "ibiko1-read-reply");
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
        learner().read(reply, Learner.Source.MODEL, System.currentTimeMillis());
        if (replied && lastRequest != null) {
            learnThinking(lastRequest, reply); // the reasoning written into a reply ("first…, then…, so…")
        }
    }

    /** People's posts and pages: read for language only. */
    public void readSocial(String text) {
        learner().read(text, Learner.Source.SOCIAL, System.currentTimeMillis());
    }

    private static String cap(String persona) {
        return persona == null || persona.isEmpty() ? "Ibiko" : Character.toUpperCase(persona.charAt(0)) + persona.substring(1);
    }

    /**
     * Answers a message: exact sums, a greeting, questions about itself, a statement learned, what the pilot confirmed
     * (asked any way), a word from its dictionary, a fact it read (said by its sentence formula), what an earlier model
     * answered; otherwise it researches, answers in the sources' words and asks whether that was right. Nothing is made
     * up.
     */
    public void answer(final String typed) {
        thinkingSince = System.currentTimeMillis(); // Stop shows until it answers, or is stopped
        understood = null; // worked out again for this message
        final String message = typosFixed(asQuestion(person().fillIn(typed))); // "the country I live in": the country they said // "capital of france", typed as a search: "What is the capital of france?"
        final String who = host.persona();
        long heardAt = System.currentTimeMillis();
        // what the conversation is about: "when was he born?" stands on its own as "when was Nelson Mandela born?"
        // (not in the turn after it asked back, which is an explanation or a correction, read as said)
        // a long text pasted in: read and kept, never rewritten with the conversation's names
        // code pasted in: read as code (to explain), never as a passage of prose or rewritten with the talk's names
        final ai.ibiko.ibiko1.formulas.CodeReader.Snippet snippet = inquiry == null ? ai.ibiko.ibiko1.formulas.CodeReader.find(message) : null;
        // an error message pasted in: read as an error (to explain), not as prose, and not rewritten with the talk's names
        final boolean errorPasted = inquiry == null && snippet == null && errors().carries(message);
        final Passage pasted = inquiry == null && snippet == null && !errorPasted ? Passage.of(message, heardAt) : null;
        final Context.Resolved withContext = pasted != null || snippet != null || errorPasted || inquiry != null || inquiryAnswer != null && Inquiry.corrects(message)
                ? Context.unchanged(message) : context.resolve(message, heardAt);
        turnDomain = null;
        // questions inside the question ("the capital of the country where Kilimanjaro is"): the inner ones answered
        // from the facts first, so what is left is one question
        // (not a yes-or-no claim: "is Lagos the capital of Nigeria?" is checked as said, not made "is Lagos Abuja?")
        final ai.ibiko.ibiko1.formulas.Hops.Done hops = pasted != null || snippet != null || errorPasted || inquiry != null
                || ai.ibiko.ibiko1.formulas.Verify.check(withContext.text, this::fact) != null ? null
                : ai.ibiko.ibiko1.formulas.Hops.resolve(withContext.text, this::fact);
        final String t = hops == null ? withContext.text : hops.text;
        if (leadIn == null) {
            previous = current; // a message's own rest (after "I'm a man, …") isn't a new question
            current = message;
        }
        asking = t; // what this turn asks, as understood: kept with its answer in the conversation
        contextStep = withContext.changed() ? "Reading it with what we were talking about: " + withContext.said() : null;
        if (typoStep != null) {
            contextStep = contextStep == null ? typoStep : typoStep + "; " + contextStep; // "Reading “captal” as “capital”"
        }
        if (hops != null) {
            String worked = String.join("; ", hops.steps) + ". So: " + t;
            contextStep = contextStep == null ? worked : contextStep + "; " + worked;
        }
        rolesStep = roleStep(t);
        if (pasted == null) {
            noteFocus(t, heardAt);
        }
        if (worker != null) {
            Plug.workers.end(worker, false); // a newer message took over
        }
        worker = Plug.workers.start(cap(who) + " · " + NAME, t, NAME);
        how = How.OTHER;
        critical = ai.ibiko.ibiko1.formulas.Critical.read(t); // K1–K8: what it asks and what it takes for granted
        times = Phrasing.heard(askedHere, t); // a question asked again (in this chat) is answered as a person would
        kind = Methods.kind(t);
        if (inquiryTurn(who, t)) {
            return; // the explanation it asked for, or a correction of what it worked out
        }
        // what we're talking about: "what is root?" after talk of Linux servers means the administrator account
        String talkAbout = ai.ibiko.ibiko1.words.SenseTags.subject(t);
        if (talkAbout != null) {
            topic = talkAbout;
            topicAt = heardAt;
        } else if (heardAt - topicAt > 30 * 60 * 1000L) {
            topic = null;
        }
        // what this message asks, read with the running conversation: every part below reads this one understanding
        Context.Focus inFocus = context.focus(heardAt);
        understood = new Understanding(message, t, questionTypes().of(message), topic, inFocus == null ? null : inFocus.name,
                talk.recent(Understanding.TURNS));
        if (inquiry == null && curiousTurn(who, message.trim())) {
            return; // the answer to what it asked back about, formed into a sentence before it is kept
        }
        // a maths problem in lettered parts about one equation: each part worked out, or said plainly that it can't be yet
        final String partsDone = inquiry == null ? ai.ibiko.ibiko1.formulas.Algebra.parts(message) : null;
        if (partsDone != null) {
            think(Arrays.asList("Reading the problem and its parts", "Working out the discriminant as a polynomial",
                    "Solving each part exactly"), () -> saysExact(who, partsDone));
            return;
        }
        // a passage with numbered tasks: every task done, in order, not a summary of the passage
        final PassageTasks tasks = inquiry == null ? PassageTasks.of(message) : null;
        if (tasks != null) {
            // what a task asks that the passage only uses ("what does 'no wahala' mean?"): its sayings and words
            tasks.also = q -> {
                // a quoted phrase made of sayings ("Abeg, fire up the gen"): read piece by piece
                java.util.regex.Matcher quoted = java.util.regex.Pattern.compile("[\"“]([^\"”]{2,80})[\"”]").matcher(q);
                if (quoted.find()) {
                    String glossed = expressions().gloss(quoted.group(1));
                    if (glossed != null) {
                        return glossed;
                    }
                }
                String saying = expressions().explain(q.replaceAll("[\"“”']", " "), null);
                if (saying != null) {
                    return saying;
                }
                java.util.regex.Matcher w = java.util.regex.Pattern.compile("(?i)what does [\"“']?([\\p{L}' -]{2,30}?)[\"”']? mean").matcher(q);
                return w.find() ? dictionaryMeaning(w.group(1)) : null;
            };
        }
        if (tasks != null) {
            // a yes-or-no claim about the world in a task ("Is Onitsha the capital of Anambra State?"): its facts
            tasks.facts = q -> {
                ai.ibiko.ibiko1.formulas.Verify.Said v = ai.ibiko.ibiko1.formulas.Verify.check(q, this::fact);
                return v != null ? v.text : claimChecked(q);
            };
        }
        if (tasks != null) {
            passage = pasted;
            final String done = tasks.reply();
            how = How.MEMORY;
            List<String> doing = new ArrayList<String>(Arrays.asList("Reading the passage and the " + tasks.tasks.size() + " tasks",
                    "What each asks: " + intents(tasks.intents)));
            String hiddenStep = PromptReader.step(tasks.hidden);
            if (hiddenStep != null) {
                doing.add(hiddenStep);
            }
            doing.addAll(Arrays.asList("Tracking each person's counts and money step by step",
                    "Answering only from the passage (nothing it doesn't say)", "Checking each answer against the task's rules"));
            think(doing, () -> saysExact(who, done));
            return;
        }
        if (pasted != null) {
            passage = pasted;
            final String main = pasted.main();
            for (String n : pasted.names) {
                if (!n.equals(main)) {
                    // the other names it is about: "the company", "it" (the main one is set last, so it leads)
                    context.about("Tell me about " + n, n, n, Context.Kind.THING, heardAt);
                }
            }
            if (main != null) {
                // its main person or thing is who "he", "she" and "it" mean in the questions that follow
                context.about("Tell me about " + main, main, main, pasted.kind(), heardAt);
            }
            String asked = pasted.question == null ? null
                    : Passage.asksWhole(pasted.question) ? pasted.whole(pasted.question) : pasted.answer(pasted.question);
            final String reply = asked != null ? asked : pasted.readReply();
            String about = pasted.about();
            List<String> reading = new ArrayList<String>(Arrays.asList("Reading what you pasted",
                    about.isEmpty() ? "Finding what it's about" : "Finding what it's about: " + about));
            String hiddenStep = PromptReader.step(pasted.hidden);
            if (hiddenStep != null) {
                reading.add(hiddenStep);
            }
            reading.add(asked != null ? "Finding the sentence that answers your question" : "Picking out the main points");
            think(reading, () -> saysPlain(who, reply));
            return;
        }
        // a question about the text pasted earlier: answered from it, before anything else
        final Passage held = passage;
        if (held != null && held.fresh(heardAt)) {
            final String fromText = Passage.asksWhole(message) || Passage.asksWhole(t) ? held.whole(message) : held.answer(t);
            if (fromText != null) {
                how = How.MEMORY;
                think(Arrays.asList("Reading your question", "Looking in the text you pasted",
                        "Taking what it says"), () -> saysPlain(who, fromText));
                return;
            }
        }
        // "what about oil?" while talking about Nigeria: what links the two, from what it has read about the subject
        if (withContext.link != null) {
            final String link = withContext.link, to = withContext.linkTo;
            final String linked = linked(to, link);
            if (linked != null) {
                how = How.MEMORY;
                think(Arrays.asList("Reading it with what we were talking about: " + to, "Looking for what links " + link + " to " + to,
                        "Found it in what I've read about " + to), () -> saysPlain(who, "On " + link + " and " + to + ": " + linked));
                return;
            }
            // nothing read links them: the question goes on as "tell me about oil in Nigeria" (looked up, or searched for)
        }
        // "tell me more", after something was said about a subject: what hasn't been said yet
        if (withContext.changed() && t.startsWith("Tell me more about ")) {
            final String subject = t.substring("Tell me more about ".length());
            final String more = more(subject, lastSaid);
            final String all = more != null ? more : encyclopedia().find(subject) == null ? null
                    : "That's everything I have on " + subject + " for now. Ask me about any part of it, or about something it mentions.";
            if (all != null) {
                think(Arrays.asList("Reading your message", "Reading on in my encyclopedia's article on " + subject),
                        () -> saysPlain(who, all));
                return;
            }
        }
        Grammar.Parse parse = Grammar.parse(t);
        // calculus on polynomials, exact and offline: derivatives, integrals, areas, stationary points, tangents
        final String calculus = ai.ibiko.ibiko1.formulas.Calculus.reply(message);
        if (calculus != null) {
            think(Arrays.asList("Reading the maths", "Applying the power rule term by term", "Working it out exactly"),
                    () -> saysExact(who, calculus));
            return;
        }
        // algebra, exact and offline: equations, inequalities, systems, a quadratic with a parameter, with the working
        final String solved = ai.ibiko.ibiko1.formulas.Algebra.reply(message);
        if (solved != null) {
            think(Arrays.asList("Reading the maths", "Putting everything on one side", "Working it out exactly, step by step",
                    "Checking the answer"), () -> saysExact(who, solved));
            return;
        }
        // a real-world problem (money, interest, loans, break-even, travel, percentages) turned into its formula
        final String real = ai.ibiko.ibiko1.formulas.WordProblems.reply(message);
        if (real != null) {
            think(Arrays.asList("Reading the problem", "Picking out what's known and what's asked", "Choosing the formula that joins them",
                    "Putting the numbers in"), () -> saysExact(who, real));
            return;
        }
        final String sum = sumOf(t);
        if (sum != null) {
            think(Arrays.asList("Reading your question", "Working it out exactly"), () -> says(who, sum));
            return;
        }
        // a sentence's structure (its syntax tree) is one of the readings weighed below: "Parse JSON" while coding is code
        // words, meanings and grammar: what a word means (here), the word for a meaning, synonyms, a sentence explained,
        // whether two say the same, a word's morphemes, whether a sentence is grammatical
        // a word with meanings in several subjects, asked about while the subject is known: that subject's meaning
        // the thinking playground: every way this message could be meant, weighed by its words and the conversation,
        // the best taken first (not the first pattern that fits); the others kept to fall back on
        // a learning conversation with the pilot's API model: started, or stopped, when asked
        if (pasted == null && learningTurn(who, message)) {
            return;
        }
        // "what do you think about remote work?": a view of its own, reasoned from what it reads, with how sure it is
        if (pasted == null && opinionTurn(who, message)) {
            return;
        }
        // any open request to make something, or for ideas ("write a business plan for my bakery", "plan a birthday party",
        // "give me ideas for a fundraiser"): researched, weighed, joined into new ideas and made in its own words
        if (pasted == null && snippet == null && ambitionTurn(who, t)) {
            return;
        }
        // a saying, before the word-by-word readers ("me, myself and I", "break a leg", "no wahala"): what it means, and what it implies
        final String saying = pasted == null ? expressions().explain(message, previous) : null;
        if (saying != null) {
            think(Arrays.asList("Reading your message", "That's a saying: its words don't add up to what it means",
                    "Recalling what it means and what it implies"), () -> saysPlain(who, saying));
            return;
        }
        if (ranked(who, message, t, snippet)) {
            return;
        }
        final WordTalk byTopic = topic == null && !t.matches("(?is).*\\b(in|for)\\s+\\p{L}+\\s*[?.!]*$") ? null : wordTalk();
        if (byTopic != null) {
            byTopic.topic(topic);
            final String inSubject = byTopic.inSubject(t);
            if (inSubject != null) {
                final String subj = topic;
                think(Arrays.asList("Reading your question", "That word means different things in different subjects",
                        subj == null ? "The question says which subject" : "We're talking about " + subj + ": taking that meaning"),
                        () -> saysPlain(who, inSubject));
                return;
            }
        }
        if (WordTalk.asks(message)) {
            final WordTalk words = wordTalk();
            if (words != null) {
                words.topic(topic);
            }
            final String said = words == null ? null : words.reply(message);
            if (said != null) {
                think(Arrays.asList("Reading your question", "Looking up each word's meanings, both ways",
                        "Choosing the meaning that fits the words around it", "Putting it into plain words"), () -> saysPlain(who, said));
                return;
            }
        }
        // units, dates and percentages: worked out exactly, on the device
        final String everyday = Everyday.answer(t);
        if (everyday != null) {
            think(Arrays.asList("Reading your question", "Working it out exactly"), () -> saysPlain(who, everyday));
            return;
        }
        if (parse.intent == Grammar.Intent.GREETING) {
            final String hello = greeting(host.pilotName(), java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                    greeted++);
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, hello));
            return;
        }
        // a clear command: Ibiko v1 operates the app like any model, by writing one of its function codes
        final String code = Commands.code(t, host::allows);
        if (code != null) {
            think(Arrays.asList("Reading your message", "That's something the app does"), () -> {
                host.stepsDone();
                if (worker != null) {
                    Plug.workers.end(worker, true);
                    worker = null;
                }
                answeredNow(How.OTHER);
                host.act(t, code);
            });
            return;
        }
        // what the person says about themselves ("I'm a man", "my name is Ada", "I live in Lagos"): understood, kept and
        // said back; the rest of the message ("…, what are you?") is answered after it
        if (pasted == null) {
            List<String> parts = SelfModel.clauses(t);
            StringBuilder took = new StringBuilder();
            List<String> rest = new ArrayList<String>();
            for (String part : parts) {
                String ack = person().heard(part);
                if (ack == null) {
                    ack = tally.heard(part); // "I have 3 apples", "I buy 4 more": counts kept through the talk
                }
                if (ack != null) {
                    took.append(took.length() == 0 ? "" : " ").append(ack);
                } else {
                    rest.add(part);
                }
            }
            if (took.length() > 0) {
                if (rest.isEmpty()) {
                    final String ack = took.toString();
                    think(Arrays.asList("Reading what you said", "That's about you: keeping it"), () -> saysPlain(who, ack));
                    return;
                }
                leadIn = took.toString(); // said first, then the answer to the rest
                answer(String.join(" ", rest));
                return;
            }
        }
        // a question about the person ("who am I?", "what's my name?", "what do you know about me?")
        final String counted = tally.asked(t);
        if (counted != null) {
            think(Arrays.asList("Reading your question", "That's a count you've been keeping with me", "Adding it up"), () -> saysPlain(who, counted));
            return;
        }
        final String aboutYou = person().asked(t, host.aboutPilot());
        if (aboutYou != null) {
            think(Arrays.asList("Reading your question", "That's about you", "Recalling what you told me"), () -> saysPlain(who, aboutYou));
            return;
        }
        // a question about Ibiko itself: answered from its picture of itself, never from an article on one of its words
        final String me = SelfModel.reply(t, selfState(), encyclopedia());
        if (me != null) {
            think(Arrays.asList("Reading your question", "That's about me", "Checking what I am and what I can do right now"),
                    () -> saysPlain(who, me));
            return;
        }
        final String self = aboutMe(t);
        if (self != null) {
            think(Arrays.asList("Reading your question", "Looking through my memory"), () -> saysPlain(who, self));
            return;
        }
        // any other question about Ibiko itself ("do you dream?", "what's your purpose?"): honest, as itself
        if (SelfModel.aboutSelf(t)) {
            final String honest = SelfModel.fallback(t);
            think(Arrays.asList("Reading your question", "That's about me"), () -> saysPlain(who, honest));
            return;
        }
        // a letter, note, email or message to write: put together offline, with blanks for what only the writer knows
        final String letter = Letters.write(t);
        if (letter != null) {
            think(Arrays.asList("Reading your message", "Working out who it's to and what it's for", "Writing it"),
                    () -> saysPlain(who, letter));
            return;
        }
        if (t.trim().matches("(?i)(stop|cancel|halt|wait|pause)( it| that| please)?[.!]*")) {
            // "Stop" with nothing of Ibiko v1's running: said plainly, not kept as a fact ("Noted.")
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, "Nothing is running right now, so there's nothing to stop."));
            return;
        }
        // the conversation itself: "what have we talked about?", "what did you say about Nigeria?", "go back to Ghana"
        final Conversation.Ask aboutTalk = Conversation.read(t);
        if (aboutTalk != null && talkTurn(who, aboutTalk, heardAt)) {
            return;
        }
        // coding: "how do I reverse a string in Go?" from its own tested recipes; the web when it hasn't got one
        final String[] lastCode = lastRecipe;
        final String followLang = lastCode == null ? null : ai.ibiko.ibiko1.formulas.Recipes.followUp(t);
        final ai.ibiko.ibiko1.formulas.Recipes.Ask coding = followLang != null && !lastCode[0].startsWith("q:")
                ? ai.ibiko.ibiko1.formulas.Recipes.ask(lastCode[0], followLang) // "and in Rust?": the same task
                : ai.ibiko.ibiko1.formulas.Recipes.read(t);
        if (coding != null && codingTurn(who, t, coding)) {
            return;
        }
        // a yes-or-no claim ("is Lagos the capital of Nigeria?"): checked against the facts, yes or no first
        final ai.ibiko.ibiko1.formulas.Verify.Said checked = ai.ibiko.ibiko1.formulas.Verify.check(t, this::fact);
        if (checked != null && !knowledge().rejected(t, checked.text)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question: a yes or no", "Looking up " + checked.thing + "'s facts",
                    "Checking what you said against them: " + (checked.yes ? "it holds" : "it doesn't")), () -> {
                        saysSure(who, checked.text, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                                "Source: Simple English Wikipedia, \u201c" + checked.thing + "\u201d (its information box)");
                        rateable(t, checked.text);
                    });
            return;
        }
        // the answer to "should I change it?" after the person said something against what it holds
        final Facts.Conflict dispute = disputed;
        disputed = null;
        if (dispute != null) {
            final String settled = settle(dispute, t);
            if (settled != null) {
                think(Arrays.asList("Reading your answer", "Settling which is right"), () -> saysPlain(who, settled));
                return;
            }
        }
        // "why?" after a reason: the reason behind it, one step further back along what it has read
        final ai.ibiko.ibiko1.formulas.Causes.Link reasonBefore = lastReason;
        lastReason = null; // anything else moves on from it
        if (reasonBefore != null && t.trim().matches("(?i)(but |and |so |ok,? )?(why|why is that|why's that|why so|how come|but why|and why|"
                + "what caused that|what caused it|why did that happen)[?!. ]*")) {
            whyFurther(who, reasonBefore);
            return;
        }
        if (parse.intent == Grammar.Intent.THANKS) {
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, "You're welcome."));
            return;
        }
        if (parse.intent == Grammar.Intent.STATEMENT) {
            // a statement teaches; it isn't researched
            think(Arrays.asList("Reading what you said", "Saving it to memory"), () -> saysPlain(who, learnStatement(t)));
            return;
        }
        final Knowledge.Fact known = knowledge().recall(t);
        if (known != null && (known.sure || builtIn().answer(t) == null)) { // G1: built-in reference outranks the unchecked
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Checking what I already know",
                    known.sure ? "Found it: you confirmed this before" : "Found it: I learned this in a learning session"), () -> {
                        if (known.sure) {
                            saysSure(who, known.answer, ai.ibiko.ibiko1.formulas.Critical.Ground.CONFIRMED); // confirmed before: no need to ask again
                        } else {
                            saysSure(who, known.answer, ai.ibiko.ibiko1.formulas.Critical.Ground.UNCHECKED, "Learned in a learning session; not checked yet");
                            rateable(t, known.answer);
                        }
                    });
            return;
        }
        final String mentioned = knowledge().mention(t);
        if (mentioned != null && !mentioned.trim().endsWith("?") && !knowledge().rejected(t, mentioned)) { // a question is no answer
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Taking the words in it", "Finding a sentence that mentions the same details"),
                    () -> saysPlain(who, mentioned));
            return;
        }
        // a question about the person ("what's my current location?", "what is my favourite colour?") is never
        // searched for or answered from the encyclopedia (my blood group is not the article on
        // blood groups): only they know it, so it asks
        if (aboutThePerson(t)) {
            gap = Curiosity.open(t, System.currentTimeMillis());
            final String unknownYou = "I don't know that about you yet. Tell me, and I'll remember it.";
            think(Arrays.asList("Reading your question", "That's about you: not something to search for"), () -> saysPlain(who, unknownYou));
            return;
        }
        // data in the message (a list of numbers, a table): analysed with the data formula (formulas/Analyst), exactly
        if (ai.ibiko.ibiko1.formulas.Analyst.hasData(t)) {
            final java.util.List<ai.ibiko.ibiko1.formulas.Analyst.Profile> data = ai.ibiko.ibiko1.formulas.Analyst.read(t);
            if (!data.isEmpty()) {
                how = How.MEMORY;
                think(Arrays.asList("Reading your question", "Taking stock of the data", "Cleaning it (and saying what changed)",
                        "Working the figures out exactly", "Checking every total a second way", "Reading what the figures say"),
                        () -> saysPlain(who, ai.ibiko.ibiko1.formulas.Analyst.report(data)));
                return;
            }
        }
        // reasoning (tell two things apart, how, is X a Y, what follows, why): every reading this message allows,
        // weighed with its words and the conversation, the best tried first
        if (reasoned(who, t)) {
            return;
        }
        // facts put together into an answer no single fact holds: an age at an event, who is older, the years between,
        // two mountains or rivers compared, the longest in a place, "the inventor of …" read as the person
        final Reasoner.Result composed = composer().answer(t);
        if (composed != null && !knowledge().rejected(t, composed.text)) {
            how = How.MEMORY;
            List<String> steps = new ArrayList<String>();
            steps.add("Reading your question");
            steps.add("No one fact answers it: putting facts together");
            steps.addAll(composed.steps);
            think(steps, () -> {
                saysSure(who, composed.text, ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED);
                rateable(t, composed.text);
            });
            return;
        }
        // a question that needs facts put together (compare, rank, count, list, chain, add up): reasoned step by step on
        // what it knows, each value said, so the answer can be checked
        final Reasoner.Result reasoned = new Reasoner(builtIn()).answer(t);
        if (reasoned != null && !knowledge().rejected(t, reasoned.text)) {
            how = How.MEMORY;
            List<String> steps = new ArrayList<String>();
            steps.add("Reading your question");
            steps.addAll(reasoned.steps);
            think(steps, () -> {
                saysSure(who, reasoned.text, ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED);
                rateable(t, reasoned.text);
            });
            return;
        }
        // a fact its encyclopedia's tables give exactly ("when was Nelson Mandela born", "who founded Microsoft")
        final FactsQA.Found fact = new FactsQA(encyclopedia()).answer(t);
        if (fact != null && !knowledge().rejected(t, fact.text)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Finding what it is about: " + fact.title,
                    "Looking it up in my encyclopedia's facts"), () -> {
                saysSure(who, fact.text, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                        "Source: Simple English Wikipedia, \u201c" + fact.title + "\u201d (its information box)");
                rateable(t, fact.text);
            });
            return;
        }
        // who did what to whom ("who invented the telephone", "what did Thomas Edison invent"): read from the articles'
        // sentences by their roles, active or passive
        final Roles.Found role = new Roles(encyclopedia()).answer(t);
        if (role != null && !knowledge().rejected(t, role.text)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Reading my encyclopedia's article on " + role.title,
                    "Finding the sentence with the same action"), () -> {
                saysSure(who, role.text, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                        "Source: Simple English Wikipedia, \u201c" + role.title + "\u201d");
                rateable(t, role.text);
            });
            return;
        }
        // the encyclopedia that ships with the app: "what is a volcano", "who was Nelson Mandela", offline
        final String explained = encyclopedia().answer(t);
        if (explained != null && !knowledge().rejected(t, explained)) {
            how = How.MEMORY;
            final Encyclopedia.Article seen = encyclopedia().find(Encyclopedia.asked(t));
            think(Arrays.asList("Reading your question", "Checking what I already know",
                    "Found it in my built-in encyclopedia" + (seen == null ? "" : ": \u201c" + seen.title + "\u201d (Simple English Wikipedia)")), () -> {
                saysSure(who, explained, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                        seen == null ? null : "Source: Simple English Wikipedia, \u201c" + seen.title + "\u201d");
                rateable(t, explained);
            });
            return;
        }
        // any other question its articles answer ("when did Nigeria gain independence"): the sentence that says it
        final EncyclopediaQA.Found read = new EncyclopediaQA(encyclopedia()).answer(t);
        if (read != null && !knowledge().rejected(t, read.said())) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Finding what it is about: " + read.title,
                    "Reading my encyclopedia's article on it", "Taking the sentence that answers it"), () -> {
                saysSure(who, read.said(), ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                        "Source: Simple English Wikipedia, \u201c" + read.title + "\u201d");
                rateable(t, read.said());
            });
            return;
        }
        // a word's meaning: from its own dictionary, or learned from Wiktionary now
        final String word = Lexicon.asked(t);
        if (word != null) {
            final Lexicon.Word w = lexicon().get(word);
            if (w != null) {
                how = How.MEMORY;
                think(Arrays.asList("Reading your question", "Looking up “" + word + "” in my dictionary"), () -> {
                    saysSure(who, w.said(), ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN);
                    rateable(t, w.said());
                });
                return;
            }
        }
        // the dictionary (WordNet) and common sense (ConceptNet), offline
        final String looked = lookUp(t);
        if (looked != null) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Looking it up in my built-in dictionary and common sense"), () -> {
                saysSure(who, looked, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN);
                rateable(t, looked);
            });
            return;
        }
        // the language formulas: the question parsed, matched to a fact it read, the answer said as a sentence
        final String formed = facts().answer(t);
        if (formed != null && !knowledge().rejected(t, formed)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Parsing what it asks", "Found it in what I've read",
                    "Putting it into a sentence"), () -> {
                        saysSure(who, formed, ai.ibiko.ibiko1.formulas.Critical.Ground.ONE_SOURCE);
                        rateable(t, formed);
                    });
            return;
        }
        // what shipped with the app: answered at once, offline, however the question is worded
        final String shipped = builtIn().answer(t);
        if (shipped != null && !knowledge().rejected(t, shipped)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Checking what I already know", "Found it in my built-in knowledge"), () -> {
                saysSure(who, shipped, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN);
                rateable(t, shipped);
            });
            return;
        }
        // the app's core facts, shared with the other brains: "At what temperature does water boil?"
        final String core = coreFacts().answer(t);
        if (core != null && !knowledge().rejected(t, core)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Checking what I already know", "Found it in the app's core facts"), () -> {
                saysSure(who, core, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN);
                rateable(t, core);
            });
            return;
        }
        final String learned = host.recallTalk(t);
        if (learned != null && !knowledge().rejected(t, learned)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Checking what I already know", "Found an answer an earlier model gave"), () -> {
                saysSure(who, learned, ai.ibiko.ibiko1.formulas.Critical.Ground.UNCHECKED, "Learned from an earlier model");
                rateable(t, learned);
            });
            return;
        }
        if (ai.ibiko.ibiko1.formulas.Critical.decision(t)) {
            // K7, K10: a judgement no fact settles gets the way to decide it, not a verdict found on some page
            think(Arrays.asList("Reading your question", "That's a judgement, not a fact", "Naming what would decide it",
                    "Weighing the other side"), () -> saysPlain(who, ai.ibiko.ibiko1.formulas.Critical.deliberate(t)));
            return;
        }
        // thinking before looking (formulas/Asking): a name it nearly is ("world up" → "World Cup"), what is asked for,
        // and whether it can have happened yet
        final List<String> framing = new ArrayList<String>();
        final String asked = repaired(t, framing);
        final ai.ibiko.ibiko1.formulas.Asking.Frame frame = ai.ibiko.ibiko1.formulas.Asking.of(asked);
        int thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (frame.notYet(thisYear)) {
            final String notYet = notYet(frame, asked, thisYear);
            framing.add(0, "Reading your question");
            framing.add("Looking for " + frame.said());
            framing.add("It's " + thisYear + " now: " + frame.year + " hasn't come yet");
            how = How.MEMORY;
            think(framing, () -> saysPlain(who, notYet));
            return;
        }
        if (!host.online() && host.modelReady()) {
            // offline, not in memory: the device's model answers, with what Ibiko v1 already knows about it
            final String subject0 = ResearchAnswer.subject(t);
            think(Arrays.asList("Reading your question", "Checking what I already know", "Not in my memory: asking the device's model"),
                    () -> fromModel(who, t, subject0, false));
            return;
        }
        if (!host.online()) {
            LearningJob.remember(host.context(), t);
            learner().unanswered(t, System.currentTimeMillis());
            final String offline = noSureAnswer(t, ResearchAnswer.subject(t), false);
            think(Arrays.asList("Reading your question", "Checking what I already know"), () -> saysPlain(who, offline));
            return;
        }
        final String subject = ResearchAnswer.subject(asked);
        how = How.RESEARCH;
        final Methods.Way way = methods().recall(t);
        List<String> researching = new ArrayList<String>(Arrays.asList("Reading your question"));
        researching.addAll(framing);
        if (frame.wants != ai.ibiko.ibiko1.formulas.Asking.Wants.OTHER) {
            researching.add("Looking for " + frame.said());
        }
        final Understanding reading = understood == null ? null : understood.with(asked);
        if (reading != null && reading.step() != null && (reading.focus != null || !reading.recent.isEmpty())) {
            researching.add(reading.step());
        }
        researching.add("Checking what I already know");
        researching.add(way != null ? "Following the way that worked before: " + way.said() : "Thinking it through");
        think(researching, () -> {
                    final int mine = token;
                    new Thread(() -> {
                        ResearchAnswer r = null;
                        if (word != null) {
                            // a word it doesn't know yet: its meaning from Wiktionary, kept in the dictionary
                            String page = Plug.web.get(Lexicon.url(word));
                            if (page != null && lexicon().learn(word, page) && lexicon().get(word) != null) {
                                r = ResearchAnswer.of(lexicon().get(word).said(), "en.wiktionary.org");
                            }
                        }
                        if (r == null) {
                            // the question as understood (its name repaired), searched in words phrased for the most results
                            int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                            List<String> searches = reading == null ? null
                                    : reading.searches(ai.ibiko.ibiko1.formulas.SearchPhrase.get(), year);
                            r = LearningJob.research(host.context(), asked, searches, q -> host.onScreen(() -> {
                                if (mine == token) {
                                    host.step("Searching the web for: " + q);
                                }
                            }));
                        }
                        if (r != null) {
                            long now = System.currentTimeMillis();
                            facts().learn(r.text, ResearchAnswer.subject(t), 2, now); // learning while being used
                            learner().read(r.text, Learner.Source.RESEARCH, now);
                        }
                        final ResearchAnswer found = r != null && knowledge().rejected(t, r.shown()) ? null : r;
                        // cross-checked against social media: a structured search (the thing, its year, the answer's own
                        // words), posts weighed for independent accounts that state it or dispute it
                        final ai.ibiko.ibiko1.formulas.Corroborate.Verdict social = found == null ? null : crossCheck(asked, frame, found.text);
                        host.onScreen(() -> {
                            if (mine != token) {
                                return; // a newer message took over
                            }
                            // only a question with a subject to explain ("what is a zorblat"): one about Ibiko itself
                            // ("what limitations do you face") or with nothing named goes the usual way
                            if (asksBack() && host.online() && subject != null && (found != null || !host.modelReady() && !host.teacherReady())) {
                                // not in memory: before answering, it asks what the person means (Inquiry); when the search
                                // found nothing and the phone has a model, that model answers instead, as before
                                inquiry = new Inquiry.Pending(t, subject, found, System.currentTimeMillis());
                                saysPlain(who, Inquiry.askBack(t, subject, found != null));
                                return;
                            }
                            if (found == null && host.teacherReady()) {
                                // not in memory, not on the web: the pilot's API model is asked, and its answer checked
                                think(Arrays.asList("Not in what I know or on the web: asking " + host.teacherName()), () -> fromTeacher(who, t, subject), false);
                                return;
                            }
                            if (found == null && host.modelReady()) {
                                think(Arrays.asList("Thinking it over more"), () -> fromModel(who, t, subject, true), false);
                                return;
                            }
                            if (found == null) {
                                LearningJob.remember(host.context(), t);
                                learner().unanswered(t, System.currentTimeMillis());
                                final String none = noSureAnswer(t, subject, true);
                                think(Arrays.asList("Putting together what I do know"), () -> saysPlain(who, none), false);
                                return;
                            }
                            final String shown = found.shown();
                            List<String> more = new ArrayList<String>();
                            String socialSaid = social == null ? "" : social.said();
                            if (!socialSaid.isEmpty()) {
                                more.add("Checking it against social media");
                                more.add(socialSaid);
                            }
                            more.add("Writing the answer");
                            // a single source that many independent accounts back up is as good as two; disputed, it says so
                            final boolean backed = social != null && social.confirmed();
                            final String note = (found.sources.isEmpty() ? "" : "Read: " + String.join(", ", found.sources))
                                    + (socialSaid.isEmpty() ? "" : (found.sources.isEmpty() ? "" : " · ") + socialSaid);
                            think(more, () -> {
                                saysSure(who, shown, found.sources.size() >= 2 || backed ? ai.ibiko.ibiko1.formulas.Critical.Ground.TWO_SOURCES
                                        : ai.ibiko.ibiko1.formulas.Critical.Ground.ONE_SOURCE,
                                        note.isEmpty() ? null : note); // one block
                                rateable(t, shown);
                            }, false);
                        });
                    }, "ibiko-research").start();
                });
    }

    /**
     * An answer held up against social media: searched with a structured query (the year, the thing asked about, and
     * the answer's own words: "2022 world cup argentina"), never the raw question, and weighed by
     * {@link ai.ibiko.ibiko1.formulas.Corroborate}. Null when nothing could be read. At most about 20 seconds.
     */
    ai.ibiko.ibiko1.formulas.Corroborate.Verdict crossCheck(String question, ai.ibiko.ibiko1.formulas.Asking.Frame frame, String answer) {
        java.util.Set<String> key = ai.ibiko.ibiko1.formulas.Corroborate.answerWords(question, answer);
        if (key.isEmpty() || frame.about.isEmpty()) {
            return null; // nothing to look for: a description isn't something posts confirm
        }
        StringBuilder q = new StringBuilder();
        if (frame.year > 0) {
            q.append(frame.year).append(' ');
        }
        q.append(frame.about);
        int n = 0;
        for (String k : key) {
            if (n++ < 3) {
                q.append(' ').append(k);
            }
        }
        final String query = q.toString();
        java.util.concurrent.ExecutorService one = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.List<ai.ibiko.ibiko1.formulas.Corroborate.Post> posts = one.submit(() -> Plug.social.apply(query))
                    .get(20, java.util.concurrent.TimeUnit.SECONDS);
            if (posts == null || posts.isEmpty()) {
                return null;
            }
            return ai.ibiko.ibiko1.formulas.Corroborate.weigh(question, answer, posts);
        } catch (Exception e) {
            return null; // slow or unreachable: the answer stands on its sources
        } finally {
            one.shutdownNow();
        }
    }

    /** The question with a name it nearly is put right ("world up" → "World Cup"), the change said in {@code steps}. */
    String repaired(String question, List<String> steps) {
        final Encyclopedia e = encyclopedia();
        if (e == null) {
            return question;
        }
        String[] r = ai.ibiko.ibiko1.formulas.Asking.repair(question, phrase -> {
            Encyclopedia.Article a = e.find(phrase);
            return a != null && a.title.equalsIgnoreCase(phrase);
        });
        if (r[1] != null) {
            steps.add(r[1]);
        }
        return r[0];
    }

    /** "Who won the 2027 World Cup?" asked in 2026: it hasn't happened yet, said plainly, with what can be asked instead. */
    static String notYet(ai.ibiko.ibiko1.formulas.Asking.Frame f, String asked, int thisYear) {
        String thing = f.about.isEmpty() ? "that" : "the " + f.year + " " + titled(asked, f.about);
        return "That hasn't happened yet: " + thing + " is still to come (it's " + thisYear + " now), so there's no "
                + (f.winner ? "winner" : "result") + " to report. Ask me once it has taken place and I'll look it up; or ask what's"
                + " planned for it.";
    }

    /** The words of {@code plain} as the question wrote them ("world cup" in "the 2027 World Cup" → "World Cup"). */
    private static String titled(String question, String plain) {
        Matcher m = Pattern.compile("(?i)" + Pattern.quote(plain).replace(" ", "\\E\\s+\\Q")).matcher(question);
        return m.find() ? m.group() : plain;
    }

    /**
     * The turn after it asked back: the person's explanation is searched and compared with the first search; "just
     * answer" gives the first search's answer. A correction ("no, it's …") of an answer worked out this way replaces
     * it. False when the message is neither (a new question goes its own way).
     */
    private boolean inquiryTurn(final String who, final String t) {
        long now = System.currentTimeMillis();
        final Inquiry.Pending p = inquiry;
        if (p != null) {
            inquiry = null;
            boolean newQuestion = t.trim().endsWith("?") && p.subject != null
                    && !t.toLowerCase(Locale.US).contains(p.subject.toLowerCase(Locale.US));
            if (!p.expired(now) && !newQuestion) {
                if (Inquiry.dropsIt(t)) {
                    answerFirst(who, p);
                } else {
                    explained(who, p, t);
                }
                return true;
            }
        }
        if (inquiryAnswer != null && now - inquiryAnsweredAt <= Inquiry.WAIT_MS && Inquiry.corrects(t)) {
            corrected(who, t);
            return true;
        }
        return false;
    }

    /** "Just answer": what the first search found, as it would have said it. */
    private void answerFirst(final String who, final Inquiry.Pending p) {
        if (p.first == null) {
            final String none = noSureAnswer(p.question, p.subject, true);
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, none));
            return;
        }
        how = How.RESEARCH;
        think(Arrays.asList("Reading your message", "Going with what I found"), () -> {
            saysSure(who, p.first.shown(), ai.ibiko.ibiko1.formulas.Critical.Ground.ONE_SOURCE,
                    p.first.sources.isEmpty() ? null : "Read: " + String.join(", ", p.first.sources));
            opened(p.question, p.first.text);
        });
    }

    /** The explanation: searched, compared with the first search, and the closer answer given with "I may be wrong". */
    private void explained(final String who, final Inquiry.Pending p, final String explanation) {
        how = How.RESEARCH;
        think(Arrays.asList("Reading what you explained", "Searching on what you said", "Comparing it with what I found first"), () -> {
            final int mine = token;
            new Thread(() -> {
                ResearchAnswer second = host.online() ? LearningJob.research(host.context(), Inquiry.explanationQuery(p, explanation)) : null;
                if (second != null && knowledge().rejected(p.question, second.shown())) {
                    second = null;
                }
                final Inquiry.Outcome o = Inquiry.compare(p.question, explanation, p.first, second);
                final long now = System.currentTimeMillis();
                learner().read(explanation, Learner.Source.PILOT, now); // the person's own words teach it too
                final List<Knowledge.Fact> rel = o == null ? new ArrayList<Knowledge.Fact>()
                        : Inquiry.related(p.question, o.text, knowledge().confirmed(), 2);
                if (o != null) {
                    facts().learn(o.text, p.subject, 2, now);
                }
                host.onScreen(() -> {
                    if (mine != token) {
                        return; // a newer message took over
                    }
                    if (o == null) {
                        LearningJob.remember(host.context(), p.question);
                        learner().unanswered(p.question, now);
                        saysPlain(who, noSureAnswer(p.question, p.subject, true));
                        return;
                    }
                    saysSure(who, o.shown(), o.agree ? ai.ibiko.ibiko1.formulas.Critical.Ground.TWO_SOURCES
                            : ai.ibiko.ibiko1.formulas.Critical.Ground.ONE_SOURCE, o.note, Inquiry.tiesIn(rel),
                            o.sources.isEmpty() ? null : "Read: " + String.join(", ", o.sources));
                    opened(p.question, o.text);
                });
            }, "ibiko1-inquiry").start();
        });
    }

    /** An answer worked out by asking back: open to a check, a cancel, or a typed correction. */
    private void opened(String question, String answer) {
        inquiryQuestion = question;
        inquiryAnswer = answer;
        inquiryAnsweredAt = System.currentTimeMillis();
        rateable(question, answer);
    }

    /**
     * "No, it's …": the answer is marked wrong for good, the person's words become the answer, and they are matched by
     * word ids with what it already knows.
     */
    private void corrected(final String who, String t) {
        final String q = inquiryQuestion, wrong = inquiryAnswer, right = Inquiry.correction(t);
        inquiryQuestion = null;
        inquiryAnswer = null;
        ratingQuestion = null;
        host.clearRating();
        long now = System.currentTimeMillis();
        knowledge().reject(q, wrong);
        knowledge().confirm(q, right);
        host.learnTalk(q, right);
        facts().learn(right, ResearchAnswer.subject(q), 2, now);
        learner().judged(false, now);
        methods().judge(q, false);
        final String ties = Inquiry.tiesIn(Inquiry.related(q, right, knowledge().confirmed(), 2));
        think(Arrays.asList("Reading your correction", "Replacing what I said", "Matching it with what I already know"),
                () -> saysPlain(who, "Thank you for correcting me. When you ask “" + q.replaceAll("[?.!]+$", "") + "”, I'll answer: "
                        + right + ties));
    }

    static final String TEACHER_SYSTEM = "Answer the question in two to four plain, factual sentences. State facts with their dates and"
            + " numbers. If it is not known, or is about the future, say so plainly; never guess. When the conversation so far is"
            + " given, use it only to tell what the question refers to.";

    /**
     * L1–L4: what Ibiko v1 doesn't know, asked of the pilot's API model, then checked by Ibiko against what it holds
     * ({@link TeacherCheck}): an answer that goes against a fact it holds sure is shown beside that fact and not kept; one
     * its own reading backs is kept as known; a new one is kept as unsure until the pilot says it's right. Next time the
     * question is answered from memory: no tokens, offline, at once.
     */
    private void fromTeacher(final String who, final String t, final String subject) {
        final int mine = token;
        final Understanding reading = understood;
        final String name = host.teacherName();
        new Thread(() -> {
            String a = null;
            try {
                // asked with the conversation before it, so "when did he die?" is about the one we were talking about
                a = host.askTeacher(TEACHER_SYSTEM, reading == null ? "Question: " + PromptReader.guard(t) : reading.forModel(t));
            } catch (RuntimeException ignored) {
                // no answer: said below
            }
            final String said = a == null ? null : ai.ibiko.suit.FunctionCode.withoutCode(a).trim();
            Encyclopedia.Article art = subject == null ? null : encyclopedia().find(subject);
            final TeacherCheck.Result r = said == null || said.isEmpty() || ai.ibiko.learn.Corroborate.failure(said) ? null
                    : TeacherCheck.check(said, subject, facts(), knowledge(), art == null ? null : art.text);
            if (r != null) {
                long now = System.currentTimeMillis();
                if (r.verdict == TeacherCheck.Verdict.CONFIRMED) {
                    knowledge().confirm(t, said); // checked: known from now on
                    facts().learn(said, subject, 2, now);
                } else if (r.verdict == TeacherCheck.Verdict.NEW) {
                    knowledge().learnUnchecked(t, said); // kept as unsure: "accurate?" until the pilot says
                    facts().learn(said, subject, 1, now);
                }
            }
            host.onScreen(() -> {
                if (mine != token) {
                    return;
                }
                if (r == null) {
                    LearningJob.remember(host.context(), t);
                    learner().unanswered(t, System.currentTimeMillis());
                    saysPlain(who, noSureAnswer(t, subject, true));
                    return;
                }
                how = How.OTHER;
                switch (r.verdict) {
                    case CONFIRMED:
                        saysSure(who, said, ai.ibiko.ibiko1.formulas.Critical.Ground.TWO_SOURCES,
                                "From " + name + ", checked against " + r.because + ": it agrees. I'll know it from now on.");
                        break;
                    case CONFLICT:
                        saysPlain(who, name + " says: " + said + "\n\nBut that goes against what I hold: " + r.because
                                + ". I've kept what I had. Tell me which is right, and I'll remember it.");
                        break;
                    default:
                        saysSure(who, said, ai.ibiko.ibiko1.formulas.Critical.Ground.UNCHECKED,
                                "From " + name + ". I couldn't check it against what I know, so I've kept it as unsure: tell me if it's right.");
                        rateable(t, said);
                        break;
                }
            });
        }, "ibiko1-teacher").start();
    }

    /** Ibiko v1 and the device's model as one brain: what Ibiko v1 knows goes in, and the model can run a function. */
    static final String MODEL_SYSTEM = "You are Ibiko, answering for Ibiko's own brain. Answer in a few plain sentences."
            + " Use what Ibiko already knows when it is given, and the conversation so far to tell what the question refers to."
            + " If you don't know, say so. "
            + ai.ibiko.suit.FunctionCode.directions()
            + " /no_think";

    /** How long the device's model may take before it gives a proper reply from what it knows instead. */
    static final long MODEL_WAIT_MS = 40_000;

    private void fromModel(final String who, final String t, final String subject, final boolean online) {
        final int mine = token;
        final Understanding reading = understood;
        final String known = foundation(t);
        final boolean[] settled = {false};
        host.later(() -> {
            if (mine == token && !settled[0]) {
                // the model is taking too long: a proper reply from what it knows, and the late answer is let go
                settled[0] = true;
                token++;
                LearningJob.remember(host.context(), t);
                learner().unanswered(t, System.currentTimeMillis());
                saysPlain(who, noSureAnswer(t, subject, online));
            }
        }, MODEL_WAIT_MS);
        new Thread(() -> {
            String asked = (known == null || known.trim().isEmpty() ? "" : "What Ibiko already knows:\n" + known.trim() + "\n\n")
                    + (reading == null ? "Question: " + PromptReader.guard(t) : reading.forModel(t)); // pasted orders fenced off as text
            String a = host.askModel(MODEL_SYSTEM, asked);
            final String said = a == null ? null : a.trim();
            // the device's model is checked the way the API model is: against the facts, the knowledge and the encyclopedia
            String plain = said == null ? null : ai.ibiko.suit.FunctionCode.withoutCode(said);
            TeacherCheck.Result checked = null;
            if (plain != null && !plain.trim().isEmpty() && ai.ibiko.suit.FunctionCode.parse(said) == null
                    && !ai.ibiko.learn.Corroborate.failure(plain)) {
                try {
                    Encyclopedia.Article art = subject == null ? null : encyclopedia().find(subject);
                    checked = TeacherCheck.check(plain.trim(), subject, facts(), knowledge(), art == null ? null : art.text);
                } catch (RuntimeException ignored) {
                    // unchecked: said as the model's own, below
                }
            }
            final TeacherCheck.Result check = checked;
            host.onScreen(() -> {
                if (mine != token || settled[0]) {
                    return; // a newer message took over, or it already replied
                }
                settled[0] = true;
                if (said == null || said.isEmpty() || ai.ibiko.learn.Corroborate.failure(said)) {
                    LearningJob.remember(host.context(), t);
                    learner().unanswered(t, System.currentTimeMillis());
                    saysPlain(who, noSureAnswer(t, subject, online));
                    return;
                }
                ai.ibiko.suit.FunctionCode.Call call = ai.ibiko.suit.FunctionCode.parse(said);
                ai.ibiko.llm.BrainOrder order = call == null ? null : ai.ibiko.llm.BrainOrder.fromCode(call);
                if (order != null && ai.ibiko.llm.CodeFit.fits(order, t)) {
                    if (!host.allows(call.code.name())) {
                        saysPlain(who, "That control is turned off for this chat.");
                        return;
                    }
                    host.stepsDone();
                    if (worker != null) {
                        Plug.workers.end(worker, true);
                        worker = null;
                    }
                    answeredNow(How.OTHER);
                    host.act(t, call.toString());
                    return;
                }
                String shown = call == null ? said : ai.ibiko.suit.FunctionCode.withoutCode(said);
                if (shown == null || shown.trim().isEmpty()) {
                    LearningJob.remember(host.context(), t);
                    learner().unanswered(t, System.currentTimeMillis());
                    saysPlain(who, noSureAnswer(t, subject, online));
                    return;
                }
                how = How.OTHER;
                if (check != null && check.verdict == TeacherCheck.Verdict.CONFLICT) {
                    saysPlain(who, "The device's model says: " + shown.trim() + "\n\nBut that goes against what I hold: " + check.because
                            + ". I've kept what I had. Tell me which is right, and I'll remember it.");
                    return;
                }
                if (check != null && check.verdict == TeacherCheck.Verdict.CONFIRMED) {
                    knowledge().confirm(t, shown.trim());
                    saysSure(who, shown.trim(), ai.ibiko.ibiko1.formulas.Critical.Ground.TWO_SOURCES,
                            "Thought through with the device's model, and checked against " + check.because + ": it agrees.");
                    return;
                }
                saysSure(who, shown.trim(), ai.ibiko.ibiko1.formulas.Critical.Ground.UNCHECKED, "Thought through with the device's model");
                rateable(t, shown.trim()); // the pilot's check keeps it in Ibiko v1's memory (and in the next adapter's training)
            });
        }, "ibiko1-model").start();
    }

    private void rateable(String question, String answer) {
        ratingQuestion = question;
        learner().answered(question, answer, System.currentTimeMillis());
        host.askRating(question, answer);
    }

    /** When the current answer began, or 0 when Ibiko v1 isn't thinking: the app shows Stop meanwhile. */
    private volatile long thinkingSince;
    /** The longest a turn counts as thinking: past it the Stop button gives way, whatever is still pending. */
    static final long THINKING_MAX_MS = MODEL_WAIT_MS + 20_000;

    /** Whether Ibiko v1 is working out an answer now (its steps showing, or waiting on a model). */
    public boolean isThinking() {
        long since = thinkingSince;
        return since != 0 && System.currentTimeMillis() - since < THINKING_MAX_MS;
    }

    private void answeredNow(How h) {
        thinkingSince = 0;
        host.answered(h);
    }

    /**
     * The Stop button: the answer being worked out is let go, like a model's reply that is stopped. Its pending steps and
     * any late answer (from the web, the device's model or the API model) see a newer token and say nothing. The screen
     * clears its own steps (the app's Stop drops them rather than folding them into the chat).
     */
    public void stop() {
        token++;
        thinkingSince = 0;
        if (worker != null) {
            Plug.workers.end(worker, false);
            worker = null;
        }
    }

    /** It thinks visibly, like the models: its steps show one by one, then it answers. */
    private void think(List<String> steps, Runnable then) {
        think(steps, then, true);
    }

    private void think(List<String> given, final Runnable then, boolean fresh) {
        final List<String> steps = new ArrayList<String>(fresh && kind != null && !given.isEmpty()
                && given.get(0).startsWith("Reading") ? thinking().steps(kind, given) : given);
        if (fresh && !steps.isEmpty() && steps.get(0).startsWith("Reading")) {
            // how the message was read: what "he" and "it" are, and who does what to whom
            int at = 1;
            if (rolesStep != null) {
                steps.add(at, rolesStep);
            }
            if (contextStep != null) {
                steps.add(at, contextStep);
            }
        }
        if (fresh && critical != null && !critical.findings.isEmpty() && !steps.isEmpty() && steps.get(0).startsWith("Reading")) {
            steps.add(1, "Questioning what it takes for granted"); // critical reasoning, before any answer
        }
        final int mine = fresh ? ++token : token;
        for (int i = 0; i < steps.size(); i++) {
            final String step = steps.get(i);
            host.later(() -> {
                if (mine == token) {
                    host.step(step);
                    if (worker != null) {
                        Plug.workers.step(worker, step);
                    }
                }
            }, 120 + i * 650L);
        }
        host.later(() -> {
            if (mine == token) {
                then.run();
            }
        }, 120 + steps.size() * 650L + 250);
    }

    /** How many times it has greeted the person since it started: the first time it says what it can do. */
    private int greeted;

    /**
     * A warm, short greeting for the time of day, by name when it knows one, as a person would say it: what it can do
     * only the first time, and a different line after that, so it doesn't repeat itself word for word.
     */
    static String greeting(String name, int hour, int times) {
        String part = hour < 5 ? "Hello" : hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        String when = hour < 5 ? "tonight" : hour < 12 ? "this morning" : hour < 17 ? "this afternoon" : "this evening";
        String who = name == null || name.trim().isEmpty() ? "" : " " + name.trim();
        if (times == 0) {
            return part + who + ", it's Ibiko here with you. How can I help you " + when + "? I answer from what I know,"
                    + " look up what I don't, and learn when you tell me I'm right or wrong.";
        }
        String[] again = {
            "Hello again" + who + ". What can I do for you?",
            part + who + ". What would you like to know?",
            "Hi" + who + ", I'm here. What's on your mind?",
        };
        return again[(times - 1) % again.length];
    }

    /** A set reply (a greeting, its memory list, "noted", "I don't know"): no check and cancel, nothing to learn. */
    /** The intents of the tasks, in order ("calculate, extract, summarize"). */
    private static String intents(List<PromptReader.Intent> all) {
        List<String> out = new ArrayList<String>();
        for (PromptReader.Intent i : all) {
            out.add(i.name().toLowerCase(Locale.ROOT).replace('_', '/'));
        }
        return String.join(", ", out);
    }

    /** Said exactly as composed: answers under rules (a letter left out, raw JSON) are not redrafted by the inner voice. */
    private void saysExact(String who, String text) {
        host.noRating(text);
        String all = (lastSaid == null ? "" : lastSaid + "\n") + text;
        lastSaid = all.length() > 6000 ? all.substring(all.length() - 6000) : all;
        kept(text);
        host.stepsDone();
        if (worker != null) {
            Plug.workers.end(worker, true);
            worker = null;
        }
        host.say(who, text);
        answeredNow(how);
    }

    private void saysPlain(String who, String text) {
        host.noRating(text);
        says(who, text);
    }

    /** How many times this question was asked before in this run, and its kind (for the learned way of thinking). */
    private volatile int times;
    private volatile String kind;

    /**
     * How the other brains think, learned from the live chat ({@code formulas/Thinking}): Deep Thinking's plan and
     * review, the working steps, the reasoning in a reply.
     */
    public ai.ibiko.ibiko1.formulas.Thinking thinking() {
        synchronized (this) {
            if (thinking == null) {
                thinking = new ai.ibiko.ibiko1.formulas.Thinking(new File(dir, "thinking.tsv"));
            }
            return thinking;
        }
    }

    private ai.ibiko.ibiko1.formulas.Thinking thinking;

    /** A brain's reasoning for {@code request}, read in the live chat: its moves are learned (T1–T5). */
    public void learnThinking(String request, String reasoning) {
        if (request != null && reasoning != null) {
            thinking().learn(Methods.kind(request), reasoning);
        }
    }

    /** Sentences already stored that mention this question, fed to the ibiko1 reasoning module. The whole saved answer, then the one sentence. */
    public java.util.List<String> knownLines(String question) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        Knowledge.Fact known = knowledge().recall(question);
        if (known != null && known.answer != null && !known.answer.trim().isEmpty()) {
            out.add(known.answer.trim());
        }
        String mentioned = knowledge().mention(question);
        if (mentioned != null && !out.contains(mentioned)) {
            out.add(mentioned);
        }
        return out;
    }

    /** How the request was read by critical reasoning ({@code formulas/Critical}); set as each message comes in. */
    private volatile ai.ibiko.ibiko1.formulas.Critical.Reading critical;

    /**
     * A factual answer, said with critical reasoning: what the question took for granted or overstated (K3–K8), when it
     * matters, and how sure the answer is from how it was reached (K9), never more.
     */
    /**
     * An answer, said as just the answer: no line on how sure it is, no notes, no source under it (how it was reached
     * is in its steps). {@code ground} is kept for the record of how answers are reached.
     */
    private void saysSure(String who, String text, ai.ibiko.ibiko1.formulas.Critical.Ground ground, String... notes) {
        // how it was reached goes into the thinking (its steps), never under the answer
        for (String n : notes) {
            if (n != null && !n.trim().isEmpty()) {
                host.step(n.trim());
            }
        }
        String critical = times > 0 ? "" : ai.ibiko.ibiko1.formulas.Critical.notes(this.critical);
        for (String n : critical.split("\n")) {
            if (!n.trim().isEmpty()) {
                host.step(n.trim());
            }
        }
        host.step(ai.ibiko.ibiko1.formulas.Critical.sure(ground));
        says(who, Phrasing.again(text, times));
    }

    /**
     * The reply when nothing answers the question surely: what it does know about the subject (its encyclopedia's
     * opening on it) when it has that, then plainly that it couldn't confirm the rest; otherwise the plain "I couldn't
     * find a reliable answer". A proper answer either way, never a bare failure.
     */
    String noSureAnswer(String t, String subject, boolean online) {
        if (!EncyclopediaQA.question(t) || SelfTalk.toMe(t)) {
            // a request ("write…", "make…") or a question to Ibiko itself: an article on a word in it is no answer
            return Phrasing.cantYet(online, times);
        }
        Encyclopedia.Article a = subject == null ? null : encyclopedia().find(subject);
        if (a == null && subject != null) {
            // no article, but the dictionary knows the word ("okada": a motorcycle taxi, in Nigeria): that, plainly
            String meant = dictionaryMeaning(subject);
            if (meant != null) {
                return meant;
            }
        }
        if (a == null) {
            java.util.List<Object[]> subs = new EncyclopediaQA(encyclopedia()).subjects(t, 1);
            a = subs.isEmpty() ? null : (Encyclopedia.Article) subs.get(0)[0];
        }
        // the gap it couldn't fill: it asks the person back, and forms their answer before keeping it
        gap = Curiosity.open(t, System.currentTimeMillis());
        String askBack = gap == null ? "" : "\n\n" + Curiosity.askBack(t);
        if (a == null) {
            return Phrasing.unknown(subject, online, LearningJob.plan(host.context()).on, times) + askBack;
        }
        String[] s = a.text.split("(?<=[.!?])\\s+");
        String opening = s[0] + (s.length > 1 && s[0].length() < 160 ? " " + s[1] : "");
        return "I couldn't find a sure answer to exactly that, but here's what I know about " + a.title + ": " + opening
                + "\n\nIf you tell me which part you need, I'll work from there." + (gap == null ? "" : " Or, if you know the answer, "
                + "tell me: I'll put it into a proper sentence and check it with you before I keep it.");
    }

    /**
     * The turn after it asked back about a gap: the reply is read against the question, hedges and filler come off,
     * and it is formed into one complete sentence. A sure reply is kept as that sentence (and shown, so a wrong reading
     * can be put right); an unsure one ("I think…") is checked first. "I don't know" leaves the question for a learning
     * session; a new question or a request goes its own way.
     */
    private boolean curiousTurn(final String who, final String t) {
        final Curiosity.Gap g = gap;
        long now = System.currentTimeMillis();
        if (g == null) {
            return false;
        }
        if (g.expired(now)) {
            gap = null;
            return false;
        }
        if (g.formed != null) {
            if (Curiosity.yes(t)) {
                gap = null;
                keepFilled(g.question, g.formed);
                final String kept = "Thanks, kept: “" + g.formed + "” Ask me “" + g.question + "” any time.";
                think(Arrays.asList("Reading your message", "You confirmed it", "Keeping it"), () -> saysPlain(who, kept));
                return true;
            }
            if (Curiosity.no(t)) {
                String other = Curiosity.instead(t);
                if (other == null) {
                    gap = new Curiosity.Gap(g.question, now, null);
                    final String again = "Then I won't keep that. What is it, then?";
                    think(Arrays.asList("Reading your message", "Not keeping it"), () -> saysPlain(who, again));
                    return true;
                }
                fillGap(who, new Curiosity.Gap(g.question, now, null), other);
                return true;
            }
            gap = null; // neither: nothing is kept, and the message is answered as it is
            return false;
        }
        if (Curiosity.movesOn(g, t)) {
            gap = null;
            return false;
        }
        fillGap(who, g, t);
        return true;
    }

    private void fillGap(final String who, final Curiosity.Gap g, final String reply) {
        final Curiosity.Reading r = Curiosity.read(g, reply);
        final long now = System.currentTimeMillis();
        if (r.declined) {
            gap = null;
            LearningJob.remember(host.context(), g.question);
            learner().unanswered(g.question, now);
            final String later = "No problem. I'll keep the question and look into it in my next learning session.";
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, later));
            return;
        }
        if (r.statement == null || r.statement.isEmpty()) {
            gap = null;
            final String none = "I couldn't take an answer to “" + g.question + "” out of that, so I haven't kept anything.";
            think(Arrays.asList("Reading your message", "Looking for the answer in it"), () -> saysPlain(who, none));
            return;
        }
        final String differs = questionReply(r.statement, "pilot"); // what it already holds to be otherwise
        if (r.unsure || differs != null) {
            gap = new Curiosity.Gap(g.question, now, r.statement);
            final String check = (differs == null ? "" : "Before I keep it: " + differs.replaceFirst("^A correction to what I just said: ", "")
                    + "\n\n") + "So, put properly: “" + r.statement + "” Is that right?";
            think(Arrays.asList("Reading your answer", "Taking off the filler and the “I think”", "Putting it into a proper sentence",
                    r.unsure ? "You sounded unsure: checking it with you first" : "It differs from what I hold: checking with you first"),
                    () -> saysPlain(who, check));
            return;
        }
        gap = null;
        keepFilled(g.question, r.statement);
        final String kept = "Thanks, that fills a gap. I've kept it as: “" + r.statement + "” If I've put it wrong, just say no.";
        think(Arrays.asList("Reading your answer", "Taking off the filler", "Putting it into a proper sentence", "Keeping it"), () -> {
            saysPlain(who, kept);
            opened(g.question, r.statement); // "no, it's …" right after still corrects it
        });
    }

    private ai.ibiko.ibiko1.formulas.Recipes recipes;
    /** The coding language last asked for in this chat: the next coding question without one means it too. */
    private volatile String codingLang;
    /** The recipe just given (task, language), for the thumbs up or down that confirms or drops it. */
    private volatile String[] lastRecipe;

    /** The coding recipes: shipped (each tested before it was built), and what it has found and had confirmed. */
    public synchronized ai.ibiko.ibiko1.formulas.Recipes recipes() {
        if (recipes == null) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/code/recipes.tsv"); // shipped coded (.ibk)
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            recipes = new ai.ibiko.ibiko1.formulas.Recipes(in, new File(dir, "learned_recipes.tsv"));
        }
        return recipes;
    }

    /**
     * R1–R4: a coding request answered from the recipes; when it hasn't one, looked up on the web (online), the code
     * that reads as the language taken from what is found, given as untested and kept until it is confirmed. False when
     * it is not a request it can do anything with (the rest of Ibiko takes it).
     */
    private boolean codingTurn(final String who, final String t, ai.ibiko.ibiko1.formulas.Recipes.Ask ask) {
        final ai.ibiko.ibiko1.formulas.Recipes book = recipes();
        String lang = ask.lang != null ? ask.lang : codingLang;
        if (ask.lang != null) {
            codingLang = ask.lang;
        }
        if (ask.task != null && lang == null) {
            // no language named (in this message or the chat): the first it has, and the others it can give
            List<String> langs = book.languages(ask.task);
            if (langs.isEmpty()) {
                return false;
            }
            String first = langs.contains("python") ? "python" : langs.get(0);
            final ai.ibiko.ibiko1.formulas.Recipes.Recipe r = book.get(ask.task, first);
            List<String> others = new ArrayList<String>();
            for (String l : langs) {
                if (!l.equals(first)) {
                    others.add(ai.ibiko.ibiko1.formulas.Recipes.shown(l));
                }
            }
            final String said = r.said() + (others.isEmpty() ? "" : "\n\nI have it in " + String.join(", ", others) + " too: just say which.");
            lastRecipe = new String[]{ask.task, first};
            how = How.MEMORY;
            think(Arrays.asList("Reading your question: code to write", "No language named: starting with " + ai.ibiko.ibiko1.formulas.Recipes.shown(first),
                    "Taking my tested recipe"), () -> {
                        saysExact(who, said);
                        rateable(t, said);
                    });
            return true;
        }
        if (ask.task != null) {
            final ai.ibiko.ibiko1.formulas.Recipes.Recipe r = book.get(ask.task, lang);
            if (r != null) {
                lastRecipe = new String[]{ask.task, lang};
                how = How.MEMORY;
                final String said = r.said();
                think(Arrays.asList("Reading your question: code to write in " + ai.ibiko.ibiko1.formulas.Recipes.shown(lang),
                        r.tested ? "Taking my tested recipe" : "Taking what I found before (not confirmed yet)"), () -> {
                            saysExact(who, said);
                            rateable(t, said);
                        });
                return true;
            }
        }
        if (lang == null) {
            return false;
        }
        // not in its recipes: the web, when it can reach it; said plainly when it can't
        final String language = lang, task = ask.task != null ? ask.task : "q:" + t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]+", " ").trim();
        if (!host.online() || Plug.web == null) {
            List<String> has = ask.task == null ? new ArrayList<String>() : book.languages(ask.task);
            final String said = "I don't have that in " + ai.ibiko.ibiko1.formulas.Recipes.shown(language) + " yet"
                    + (has.isEmpty() ? "" : " (I have it in " + shownAll(has) + ")")
                    + ". When you're online, ask again and I'll look it up, try to tell working code from the rest, and keep it once you confirm it works.";
            think(Arrays.asList("Reading your question: code to write in " + ai.ibiko.ibiko1.formulas.Recipes.shown(language), "Not in my recipes yet"),
                    () -> saysPlain(who, said));
            return true;
        }
        how = How.RESEARCH;
        think(Arrays.asList("Reading your question: code to write in " + ai.ibiko.ibiko1.formulas.Recipes.shown(language),
                "Not in my recipes: looking it up on the web and in social media posts", "Taking the code that reads as " + ai.ibiko.ibiko1.formulas.Recipes.shown(language)), () -> {
                    final int mine = token;
                    new Thread(() -> {
                        String found = null;
                        try {
                            // search words, not the sentence: "reverse a string python code example"
                            String w = ai.ibiko.ibiko1.formulas.SearchPhrase.get().words(t, 0);
                            String q = (w.isEmpty() ? t : w) + (ai.ibiko.ibiko1.formulas.Recipes.language(w) == null
                                    ? " " + ai.ibiko.ibiko1.formulas.Recipes.shown(language) : "") + " code example";
                            found = ai.ibiko.ibiko1.formulas.Recipes.code(Plug.web.search(q), language);
                        } catch (RuntimeException ignored) {
                            // nothing found on the web
                        }
                        if (found == null) {
                            // social media too: people share working code in posts (Reddit, the networks signed in here)
                            try {
                                StringBuilder posts = new StringBuilder();
                                for (ai.ibiko.ibiko1.formulas.Corroborate.Post p : Plug.social.apply(t + " " + ai.ibiko.ibiko1.formulas.Recipes.shown(language))) {
                                    posts.append(p.text).append("\n\n");
                                }
                                found = ai.ibiko.ibiko1.formulas.Recipes.code(posts.toString(), language);
                            } catch (RuntimeException ignored) {
                                // nothing there either
                            }
                        }
                        final String code = found;
                        if (code != null) {
                            book.found(task, language, code); // kept untested, until it is confirmed
                            lastRecipe = new String[]{task, language};
                        }
                        host.onScreen(() -> {
                            if (mine != token) {
                                return;
                            }
                            if (code == null) {
                                saysPlain(who, "I looked it up but found no code I could trust for that in "
                                        + ai.ibiko.ibiko1.formulas.Recipes.shown(language) + ". Could you say it another way, or try another language?");
                                return;
                            }
                            String said = "Here's what I found for it in " + ai.ibiko.ibiko1.formulas.Recipes.shown(language) + ":\n\n```"
                                    + language + "\n" + code + "\n```\n\nFound on the web, not tested yet: tell me if it works (thumbs up),"
                                    + " and I'll keep it for next time, offline.";
                            saysExact(who, said);
                            rateable(t, said);
                        });
                    }, "ibiko1-code-lookup").start();
                });
        return true;
    }

    /** The subject of this turn's answer ("code", "web", "facts"…), for the next message's reading; null: not known. */
    private volatile String turnDomain;
    /** What the last turns were about, for reading the next message with the conversation. */
    private final ai.ibiko.ibiko1.formulas.Readings.Context readingContext = new ai.ibiko.ibiko1.formulas.Readings.Context();

    /**
     * P1–P4: the readers that recognise the message offer their readings; they are weighed (the message's own words, the
     * conversation's last subjects, a short follow-up) and tried best first. True when one answered; false leaves the
     * message to the rest of Ibiko (a word's meaning chosen first goes on to the word reader there).
     */
    /** A message too close to call between two readings, asked about: the message and the two readings, until answered. */
    private static final class Unsure {
        final String message, t;
        final List<ai.ibiko.ibiko1.formulas.Readings.Kind> kinds;

        Unsure(String message, String t, List<ai.ibiko.ibiko1.formulas.Readings.Kind> kinds) {
            this.message = message;
            this.t = t;
            this.kinds = kinds;
        }
    }

    private volatile Unsure unsure;

    /** The reading the person picked when asked which they meant, for the message read again; null otherwise. */
    private ai.ibiko.ibiko1.formulas.Readings.Kind picked;

    private boolean ranked(final String who, String message, final String t, final ai.ibiko.ibiko1.formulas.CodeReader.Snippet snippet) {
        // the answer to "Do you mean a website, or code to write?": the first message, read the way picked
        final Unsure asked = unsure;
        unsure = null;
        if (asked != null && snippet == null && picked == null) {
            ai.ibiko.ibiko1.formulas.Readings.Kind k = ai.ibiko.ibiko1.formulas.Readings.choose(message, asked.kinds);
            if (k != null) {
                picked = k;
                try {
                    contextStep = "You picked " + k.said + " for \"" + asked.message + "\"";
                    return ranked(who, asked.message, asked.t, null);
                } finally {
                    picked = null;
                }
            }
        }
        List<ai.ibiko.ibiko1.formulas.Readings.Kind> offered = new ArrayList<ai.ibiko.ibiko1.formulas.Readings.Kind>();
        boolean runIt = snippet != null && message.replace(snippet.code, " ").matches("(?is).*\\b(?:run|execute|exec)\\b.*");
        if (snippet != null && !runIt) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.CODE);
        }
        // an error message pasted (on its own, or with the code that gave it): what it means and the fix
        final ai.ibiko.ibiko1.formulas.ErrorTalk.Found error = runIt ? null : errors().find(message);
        // or a follow-up about the error just explained: "how do I fix it?", "why?"
        final ai.ibiko.ibiko1.formulas.ErrorTalk.Found errorBefore = lastError;
        final ai.ibiko.ibiko1.formulas.ErrorTalk.Ask errorAsk = error == null && snippet == null && errorBefore != null
                ? ai.ibiko.ibiko1.formulas.ErrorTalk.followUp(message) : null;
        // an error it doesn't know by heart: its kind, a fix learned before, or one looked up on the web
        final ai.ibiko.ibiko1.formulas.ErrorTalk.Unknown unknownError = error == null && !runIt ? errors().unknown(message) : null;
        // "that fixed it" / "still the same error", after a fix found on the web
        final String triedFix = pendingErrorFix;
        final Boolean fixWorked = triedFix == null || error != null || unknownError != null ? null : ai.ibiko.ibiko1.formulas.ErrorTalk.worked(message);
        if (error != null || errorAsk != null || unknownError != null || fixWorked != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.ERROR);
        }
        // a follow-up about the code just read: convert it, one line of it, a mistake in it
        final ai.ibiko.ibiko1.formulas.CodeReader.Snippet before = lastSnippet;
        final ai.ibiko.ibiko1.formulas.CodeReader.Follow follow = snippet == null && before != null
                ? followOf(message, t) : null;
        if (follow != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.CODE);
        }
        // readings offered only from the message's words, not its usual phrasing ("Write html for a bakery website", "Sort a list")
        final java.util.Set<ai.ibiko.ibiko1.formulas.Readings.Kind> loose = new java.util.HashSet<ai.ibiko.ibiko1.formulas.Readings.Kind>();
        final ai.ibiko.ibiko1.formulas.Sites.Ask sureSite = snippet == null ? ai.ibiko.ibiko1.formulas.Sites.read(t) : null;
        // a change to the site just made ("add a pricing section", "make it blue"): a website reading too
        final boolean siteChange = sureSite == null && snippet == null && lastDesign != null && ai.ibiko.ibiko1.formulas.Sites.asksChange(t);
        final ai.ibiko.ibiko1.formulas.Sites.Ask siteAsk = sureSite != null || siteChange || snippet != null ? sureSite
                : ai.ibiko.ibiko1.formulas.Sites.loose(t);
        if (siteAsk != null && sureSite == null) {
            loose.add(ai.ibiko.ibiko1.formulas.Readings.Kind.SITE);
        }
        if (siteAsk != null || siteChange) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.SITE);
        }
        // a small web app asked to be made: "make a calculator", "I need a contact form"
        final ai.ibiko.ibiko1.formulas.Apps.App appAsk = snippet == null ? apps().asked(t) : null; // weighed with a site change
        // a change to the app just made ("add a question: …", "my WhatsApp number is …", "make it green")
        final ai.ibiko.ibiko1.formulas.Apps.Made made = lastMade;
        final boolean appChange = appAsk == null && snippet == null && ai.ibiko.ibiko1.formulas.Apps.asksChange(made, t);
        if (appAsk != null || appChange) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.APP);
        }
        final String[] lastCode = lastRecipe;
        final String followLang = lastCode == null ? null : ai.ibiko.ibiko1.formulas.Recipes.followUp(t);
        final ai.ibiko.ibiko1.formulas.Recipes.Ask sureCoding = snippet != null ? null : followLang != null && !lastCode[0].startsWith("q:")
                ? ai.ibiko.ibiko1.formulas.Recipes.ask(lastCode[0], followLang) : ai.ibiko.ibiko1.formulas.Recipes.read(t);
        final ai.ibiko.ibiko1.formulas.Recipes.Ask coding = sureCoding != null || snippet != null ? sureCoding
                : ai.ibiko.ibiko1.formulas.Recipes.loose(t);
        if (coding != null && sureCoding == null) {
            loose.add(ai.ibiko.ibiko1.formulas.Readings.Kind.CODING);
        }
        if (coding != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.CODING);
        }
        // comparisons said ("Ada is taller than Obi"), kept for the conversation; a question about them reasoned from them
        List<ai.ibiko.ibiko1.formulas.Order.Fact> compared = ai.ibiko.ibiko1.formulas.Order.read(message);
        if (!compared.isEmpty()) {
            synchronized (orderFacts) {
                orderFacts.addAll(compared);
                while (orderFacts.size() > 30) {
                    orderFacts.remove(0);
                }
            }
        }
        List<ai.ibiko.ibiko1.formulas.Order.Fact> known;
        synchronized (orderFacts) {
            known = new ArrayList<ai.ibiko.ibiko1.formulas.Order.Fact>(orderFacts);
        }
        final ai.ibiko.ibiko1.formulas.Order.Question orderAsk = snippet == null && !known.isEmpty() ? ai.ibiko.ibiko1.formulas.Order.asked(message, known) : null;
        final String ordered = orderAsk == null ? null : ai.ibiko.ibiko1.formulas.Order.answer(orderAsk, known);
        if (ordered != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.ORDER);
        }
        // "what is the highest mountain in the world?": a record, found by what it is
        final String[] recordAsk = snippet == null ? ai.ibiko.ibiko1.formulas.Records.asked(t) : null;
        final String record = recordAsk == null ? null : records().answer(recordAsk);
        if (record != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.RECORD);
        }
        // "how do you spell necessary?": spelled out, or the real word it's nearest to
        final String spellWord = snippet == null ? ai.ibiko.ibiko1.formulas.Spelling.asked(message) : null;
        final String spelled = spellWord == null ? null : ai.ibiko.ibiko1.formulas.Spelling.reply(spellWord,
                w -> ai.ibiko.ibiko1.words.Meanings.shared() != null && ai.ibiko.ibiko1.words.Meanings.shared().headword(w) != null
                        && ai.ibiko.ibiko1.words.Meanings.shared().headword(w).equals(w));
        if (spelled != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.SPELL);
        }
        // "who will win the next World Cup?": what hasn't happened, said plainly
        String future = snippet == null ? ai.ibiko.ibiko1.formulas.Spelling.future(message) : null;
        if (future == null && snippet == null && !host.online()) {
            future = ai.ibiko.ibiko1.formulas.Spelling.weather(message); // a forecast it can't see offline (online, it's looked up)
        }
        final String notHappened = future;
        if (notHappened != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.FUTURE);
        }
        // the kind of question, by how it starts ("why": a reason; "what was": the past; "what happened": an event…), said in
        // the thinking; a question that is only its opening ("What was?") answered from its kind and the subject we're on
        final ai.ibiko.ibiko1.formulas.QuestionTypes.Kind qkind = snippet == null ? questionTypes().of(message) : null;
        if (qkind != null) {
            String kindStep = "It's " + qkind.said();
            contextStep = contextStep == null ? kindStep : contextStep + "; " + kindStep;
        }
        Context.Focus onNow = context.focus(System.currentTimeMillis());
        boolean followsAnswer = lastAnswer != null && (ai.ibiko.ibiko1.formulas.Social.huh(message)
                || qkind != null && qkind.row.type.equals("REASON")); // "Why?" / "What?" just after an answer: the follow-ups do those
        final String openingOnly = qkind == null || followsAnswer ? null
                : ai.ibiko.ibiko1.formulas.QuestionTypes.alone(qkind, onNow == null ? null : onNow.name);
        // said to Ibiko itself: "can I ask you something private?", "You!", a question cut short
        String toMe = snippet == null ? ai.ibiko.ibiko1.formulas.Social.reply(message, lastAnswer, previous, openingOnly) : null;
        if (toMe == null) {
            toMe = openingOnly;
        }
        final String social = toMe;
        if (social != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.SOCIAL);
        }
        // logic from what was said: "All cats are mammals. Tom is a cat. Is Tom a mammal?"
        final String reasoned = snippet == null ? ai.ibiko.ibiko1.formulas.Logic.answer(message, logicKnown) : null;
        if (snippet == null) {
            logicKnown.addAll(ai.ibiko.ibiko1.formulas.Logic.read(message)); // kept for the conversation's later questions
        }
        if (reasoned != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.LOGIC);
        }
        // everyday sums in words: "half of 250", "20% off 5000", "is 97 prime?", "what day is 10 days after Monday?"
        String sum = snippet == null ? ai.ibiko.ibiko1.formulas.Quick.reply(message) : null;
        if (sum == null && snippet == null) {
            sum = ai.ibiko.ibiko1.formulas.TextOps.reply(message); // "how many letters in Nigeria?", "reverse hello", "sort … alphabetically"
        }
        if (sum == null && snippet == null) {
            sum = ai.ibiko.ibiko1.formulas.Patterns.next(message); // "what comes next: 2, 6, 18, 54?": the rule, then the number
        }
        if (sum == null && snippet == null) {
            sum = ai.ibiko.ibiko1.formulas.Patterns.analogy(message, analogyWords()); // "hot is to cold as up is to what?"
        }
        if (sum == null && snippet == null) {
            sum = storyInOneMessage(message); // "Kemi baked 40 cakes and sold 15. How many cakes were left?"
        }
        if (sum == null && snippet == null) {
            sum = yearsAgo(t); // "how many years ago was Nelson Mandela born?": the date looked up, then the years counted
        }
        final String quick = sum;
        if (quick != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.QUICK);
        }
        // a counting story: "I have 3 apples and eat one: how many are left?"
        final String counted = snippet == null ? ai.ibiko.ibiko1.formulas.Counting.reply(message) : null;
        if (counted != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.COUNT);
        }
        // "shorter", "in one sentence", "as points", "more simply", "give me an example": the last answer, said again
        ai.ibiko.ibiko1.formulas.Reshape.Ask reshaped = snippet == null && lastAnswer != null ? ai.ibiko.ibiko1.formulas.Reshape.asked(message) : null;
        if (reshaped == null && snippet == null && lastAnswer != null && ai.ibiko.ibiko1.formulas.Social.huh(message)) {
            reshaped = ai.ibiko.ibiko1.formulas.Reshape.Ask.SIMPLER; // "What?" just after an answer: it wasn't followed, so said again more simply
        }
        final ai.ibiko.ibiko1.formulas.Reshape.Ask reshapeAsk = reshaped;
        if (reshapeAsk != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.RESHAPE);
        }
        if (snippet == null && WordTalk.asks(message)) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.WORD);
        }
        // a sentence's structure: its syntax tree, its constituents, why it is ambiguous
        final String structure = snippet == null ? SyntaxTalk.reply(message) : null;
        if (structure != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.SYNTAX);
        }
        if (offered.isEmpty()) {
            return false;
        }
        Context.Focus f = context.focus(System.currentTimeMillis());
        readingContext.focus = f == null ? null : f.name;
        List<ai.ibiko.ibiko1.formulas.Readings.Reading> order = ai.ibiko.ibiko1.formulas.Readings.weigh(t, offered, readingContext, loose);
        final ai.ibiko.ibiko1.formulas.Readings.Kind chosen = picked;
        if (chosen != null) {
            // the person said which they meant: that reading first, whatever the weighing
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).kind == chosen) {
                    order.add(0, order.remove(i));
                    break;
                }
            }
        } else if (ai.ibiko.ibiko1.formulas.Readings.unsure(order)) {
            // too close to call, and nothing said before tips it: ask, don't guess
            final List<ai.ibiko.ibiko1.formulas.Readings.Kind> two = Arrays.asList(order.get(0).kind, order.get(1).kind);
            unsure = new Unsure(message, t, two);
            final String question = ai.ibiko.ibiko1.formulas.Readings.ask(order);
            how = How.MEMORY;
            think(Arrays.asList("Weighing what you meant: " + two.get(0).said + " or " + two.get(1).said + ", equally likely",
                    "Nothing we said before tips it one way"), () -> saysExact(who, question));
            return true;
        } else if (order.size() > 1 || !loose.isEmpty()) {
            String weighed = ai.ibiko.ibiko1.formulas.Readings.said(order);
            contextStep = contextStep == null ? weighed : contextStep + "; " + weighed;
        }
        for (ai.ibiko.ibiko1.formulas.Readings.Reading r : order) {
            switch (r.kind) {
                case CODE:
                    turnDomain = "code";
                    if (snippet == null) {
                        codeFollow(who, before, follow);
                    } else {
                        lastSnippet = snippet;
                        codeTurn(who, snippet);
                    }
                    return true;
                case SITE:
                    if (siteChange) {
                        if (siteChangeTurn(who, t)) {
                            turnDomain = "web";
                            return true;
                        }
                        break;
                    }
                    if (siteTurn(who, t, siteAsk)) {
                        turnDomain = "web";
                        return true;
                    }
                    break;
                case RECORD:
                    turnDomain = "facts";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading your question: the " + recordAsk[0] + "est " + recordAsk[1] + " in " + recordAsk[2],
                            "Looking it up among the records my encyclopedia names"), () -> saysPlain(who, record));
                    return true;
                case SPELL:
                    turnDomain = "language";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading your question: how a word is spelled", "Looking it up in my dictionary"),
                            () -> saysExact(who, spelled));
                    return true;
                case FUTURE:
                    turnDomain = "reasons";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading your question: it's about something that hasn't happened yet"), () -> saysPlain(who, notHappened));
                    return true;
                case COUNT:
                    turnDomain = "reasons";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading the story: what there is to start with", "Following each thing that happens to it",
                            "Working out what's asked"), () -> saysExact(who, counted));
                    return true;
                case QUICK:
                    turnDomain = "reasons";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading the sum in your words", "Working it out exactly"), () -> saysExact(who, quick));
                    return true;
                case SOCIAL:
                    turnDomain = "talk";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading what you said to me"), () -> saysPlain(who, social));
                    return true;
                case LOGIC:
                    turnDomain = "reasons";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading what you said as rules and facts", "Following them to your question",
                            "Saying only what follows from them"), () -> saysExact(who, reasoned));
                    return true;
                case ORDER:
                    turnDomain = "reasons";
                    how = How.MEMORY;
                    think(Arrays.asList("Reading your question: who is more, from what you told me", "Putting each one above or below the others",
                            "Following the chain from one to the next"), () -> saysExact(who, ordered));
                    return true;
                case RESHAPE:
                    if (reshapeTurn(who, reshapeAsk)) {
                        turnDomain = "talk";
                        return true;
                    }
                    break;
                case ERROR:
                    turnDomain = "code";
                    if (fixWorked != null) {
                        pendingErrorFix = null;
                        final String heard;
                        if (fixWorked) {
                            errors().confirm(triedFix);
                            heard = "Good. I'll keep that fix: next time that error comes up, I'll know it without looking it up.";
                        } else {
                            errors().wrong(triedFix);
                            heard = "Sorry it didn't work. I won't give that fix again. Paste the error you get now (it may have changed), and the"
                                    + " lines of code around where it happens, and I'll look again.";
                        }
                        how = How.MEMORY;
                        think(Arrays.asList("Reading your reply about the fix I found", fixWorked ? "It worked: keeping it" : "It didn't: dropping it"),
                                () -> saysPlain(who, heard));
                        return true;
                    }
                    if (unknownError != null) {
                        unknownErrorTurn(who, t, unknownError);
                        return true;
                    }
                    if (error == null) {
                        final String more = ai.ibiko.ibiko1.formulas.ErrorTalk.followed(errorBefore, errorAsk, lastErrorCode);
                        how = How.MEMORY;
                        think(Arrays.asList("Reading your question about the error we're on: " + errorBefore.line),
                                () -> saysExact(who, more));
                        return true;
                    }
                    // the code pasted with it (in fences, or the error's own message when it's all one paste)
                    lastError = error;
                    lastErrorCode = snippet != null && !snippet.code.contains(error.line) ? snippet.code : null; // the error itself isn't "your code"
                    final String fixIt = ai.ibiko.ibiko1.formulas.ErrorTalk.explain(error, lastErrorCode);
                    how = How.MEMORY;
                    List<String> reading = new ArrayList<String>(Arrays.asList("Reading what you pasted: an error message",
                            "Recognising it: " + error.known.seen.replaceAll("'[^']*'|\"[^\"]*\"|`[^`]*`", "…")));
                    if (error.file != null) {
                        reading.add("Finding where it happened: " + error.file + (error.at > 0 ? " line " + error.at : ""));
                    }
                    reading.add("What causes it, and the fix");
                    think(reading, () -> saysExact(who, fixIt));
                    return true;
                case APP:
                    turnDomain = "web";
                    if (appChange) {
                        final String changed = ai.ibiko.ibiko1.formulas.Apps.change(made, t);
                        if (changed == null) {
                            break;
                        }
                        lastSnippet = new ai.ibiko.ibiko1.formulas.CodeReader.Snippet(made.html, "html", "");
                        final String again = "Done: " + changed + ".\n\n```html\n" + made.html + "```\n\nSave it over your "
                                + made.app.id.replace('_', '-') + ".html.";
                        how = How.MEMORY;
                        think(Arrays.asList("Reading your request: a change to the " + made.app.name + " I made", "Making the change: " + changed,
                                "Writing the page again"), () -> saysExact(who, again));
                        return true;
                    }
                    lastMade = new ai.ibiko.ibiko1.formulas.Apps.Made(appAsk);
                    lastSnippet = new ai.ibiko.ibiko1.formulas.CodeReader.Snippet(appAsk.html, "html", ""); // "what does line 40 do?"
                    lastDesign = null; // the thing just made is this app: "make it blue" changes it, not the site before
                    final String handed = ai.ibiko.ibiko1.formulas.Apps.reply(appAsk);
                    how = How.MEMORY;
                    think(Arrays.asList("Reading your request: a " + appAsk.name, "Taking my " + appAsk.name + ", tested in a browser",
                            "Saying what it does and how it works"), () -> saysExact(who, handed));
                    return true;
                case CODING:
                    if (codingTurn(who, t, coding)) {
                        turnDomain = "code";
                        return true;
                    }
                    break;
                case SYNTAX:
                    turnDomain = "language";
                    think(Arrays.asList("Reading the sentence", "Tagging each word's part of speech", "Grouping the words into phrases, bottom up",
                            "Checking every way the rules allow"), () -> saysPlain(who, structure));
                    return true;
                case WORD:
                    turnDomain = "language";
                    if (chosen == ai.ibiko.ibiko1.formulas.Readings.Kind.WORD) {
                        // picked after being asked: the word reader answers the first message here, not the answer ("the second")
                        final WordTalk words = wordTalk();
                        final String said = words == null ? null : words.reply(message);
                        if (said != null) {
                            think(Arrays.asList("Reading your question", "Looking up each word's meanings, both ways",
                                    "Putting it into plain words"), () -> saysPlain(who, said));
                            return true;
                        }
                    }
                    return false; // the word reader, further on, answers it
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * The second weighing: the reasoning readings a question allows (two things told apart, how something is done,
     * whether one thing is a kind of another, what something leads to, a reason), ranked by the message's own words
     * and the conversation, tried best first; the ones that don't answer fall through to the next. False: none did.
     */
    private boolean reasoned(final String who, final String t) {
        List<ai.ibiko.ibiko1.formulas.Readings.Kind> offered = new ArrayList<ai.ibiko.ibiko1.formulas.Readings.Kind>();
        ai.ibiko.ibiko1.formulas.Asking.Wants wants = ai.ibiko.ibiko1.formulas.Asking.of(t).wants;
        if (ai.ibiko.ibiko1.formulas.Contrast.pair(t) != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.CONTRAST);
        }
        if (wants == ai.ibiko.ibiko1.formulas.Asking.Wants.HOW) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.HOW);
        }
        if (ai.ibiko.ibiko1.formulas.Kinds.asked(t) != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.KINDQ);
        }
        if (ai.ibiko.ibiko1.formulas.Causes.ahead(t) != null) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.AHEAD);
        }
        if (wants == ai.ibiko.ibiko1.formulas.Asking.Wants.WHY) {
            offered.add(ai.ibiko.ibiko1.formulas.Readings.Kind.WHY);
        }
        if (offered.isEmpty()) {
            return false;
        }
        List<ai.ibiko.ibiko1.formulas.Readings.Reading> order = ai.ibiko.ibiko1.formulas.Readings.weigh(t, offered, readingContext);
        if (order.size() > 1) {
            String weighed = ai.ibiko.ibiko1.formulas.Readings.said(order);
            contextStep = contextStep == null ? weighed : contextStep + "; " + weighed;
        }
        for (ai.ibiko.ibiko1.formulas.Readings.Reading r : order) {
            boolean done;
            switch (r.kind) {
                case CONTRAST: done = contrastTurn(who, t); break;
                case HOW: done = answerHow(who, t); break;
                case KINDQ: done = kindTurn(who, t); break;
                case AHEAD: done = answerThen(who, t); break;
                case WHY: done = answerWhy(who, t); break;
                default: done = false;
            }
            if (done) {
                turnDomain = r.kind.domain;
                return true;
            }
        }
        return false;
    }

    private boolean contrastTurn(final String who, final String t) {
        final String[] pair = ai.ibiko.ibiko1.formulas.Contrast.pair(t);
        if (pair != null && encyclopedia() != null) {
            final Encyclopedia.Article a = encyclopedia().find(pair[0]), b = encyclopedia().find(pair[1]);
            final String told = a == null || b == null || a.title.equals(b.title) ? null
                    : ai.ibiko.ibiko1.formulas.Contrast.tell(pair[0], a.text, pair[1], b.text);
            if (told != null && !knowledge().rejected(t, told)) {
                how = How.MEMORY;
                long now = System.currentTimeMillis();
                facts().learn(a.text, a.title, 2, now); // what it read is kept
                facts().learn(b.text, b.title, 2, now);
                think(Arrays.asList("Reading your question: two things to tell apart", "Reading my encyclopedia on " + a.title + " and on " + b.title,
                        "Finding what kind of thing each is", "Finding what each says that the other doesn't"), () -> {
                            saysSure(who, told, ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                                    "Source: Simple English Wikipedia, \u201c" + a.title + "\u201d and \u201c" + b.title + "\u201d");
                            rateable(t, told);
                        });
                return true;
            }
        }
        return false;
    }

    private boolean kindTurn(final String who, final String t) {
        final String[] kindAsked = ai.ibiko.ibiko1.formulas.Kinds.asked(t);
        if (kindAsked != null && encyclopedia() != null) {
            final List<ai.ibiko.ibiko1.formulas.Kinds.Step> chain = ai.ibiko.ibiko1.formulas.Kinds.chain(kindAsked[0], kindAsked[1], name -> {
                Encyclopedia.Article a = encyclopedia().find(name);
                return a == null ? null : ai.ibiko.ibiko1.formulas.Contrast.kinds(a.text, a.title);
            });
            if (chain != null) {
                final String yes = ai.ibiko.ibiko1.formulas.Kinds.said(chain);
                if (!knowledge().rejected(t, yes)) {
                    how = How.MEMORY;
                    List<String> steps = new ArrayList<String>();
                    steps.add("Reading your question: is " + kindAsked[0] + " a kind of " + kindAsked[1] + "?");
                    for (ai.ibiko.ibiko1.formulas.Kinds.Step st : chain) {
                        steps.add("Reading what " + st.thing + " is: " + st.said);
                    }
                    steps.add("Reached " + kindAsked[1] + ": yes");
                    think(steps, () -> {
                        saysSure(who, yes, ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED,
                                "Worked out from Simple English Wikipedia, step by step");
                        rateable(t, yes);
                    });
                    return true;
                }
            }
            // not reached: a sure "no" only when it reaches another kind of the same group ("a bat is a mammal": not a bird)
            final List<ai.ibiko.ibiko1.formulas.Kinds.Step> other = chain != null ? null : ai.ibiko.ibiko1.formulas.Kinds.not(kindAsked[0], kindAsked[1], name -> {
                Encyclopedia.Article a = encyclopedia().find(name);
                return a == null ? null : ai.ibiko.ibiko1.formulas.Contrast.kinds(a.text, a.title);
            });
            if (other != null) {
                final String no = ai.ibiko.ibiko1.formulas.Kinds.saidNo(other, kindAsked[1]);
                if (!knowledge().rejected(t, no)) {
                    how = How.MEMORY;
                    List<String> steps = new ArrayList<String>();
                    steps.add("Reading your question: is " + kindAsked[0] + " a kind of " + kindAsked[1] + "?");
                    for (ai.ibiko.ibiko1.formulas.Kinds.Step st : other) {
                        steps.add("Reading what " + st.thing + " is: " + st.said);
                    }
                    steps.add("That's another group from " + kindAsked[1] + ": no");
                    think(steps, () -> {
                        saysSure(who, no, ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED, "Worked out from Simple English Wikipedia, step by step");
                        rateable(t, no);
                    });
                    return true;
                }
            }
        }
        return false;
    }

    /** The code last pasted and read, for questions about it ("convert it to Go", "what does line 3 do?"). */
    private volatile ai.ibiko.ibiko1.formulas.CodeReader.Snippet lastSnippet;

    /**
     * C5: a question about the code just read. Converted: when the code is one of its tested recipes, the same recipe in
     * the language asked (tested too); otherwise said plainly that it can't convert it exactly. One line: in plain words.
     * A mistake: what it can see.
     */
    /** A follow-up about the code we're on, read from the message as typed first ("explain it": not "explain line"). */
    private static ai.ibiko.ibiko1.formulas.CodeReader.Follow followOf(String message, String t) {
        ai.ibiko.ibiko1.formulas.CodeReader.Follow f = ai.ibiko.ibiko1.formulas.CodeReader.follow(message);
        return f != null ? f : ai.ibiko.ibiko1.formulas.CodeReader.follow(t);
    }

    private void codeFollow(final String who, ai.ibiko.ibiko1.formulas.CodeReader.Snippet s, ai.ibiko.ibiko1.formulas.CodeReader.Follow f) {
        final String said;
        List<String> steps = new ArrayList<String>(Arrays.asList("Reading your question about the code we're on"));
        if (f.is == ai.ibiko.ibiko1.formulas.CodeReader.Follow.Is.EXPLAIN) {
            codeTurn(who, s);
            return;
        }
        switch (f.is) {
            case CONVERT: {
                String task = recipes().matchTask(s.code, s.lang);
                ai.ibiko.ibiko1.formulas.Recipes.Recipe r = task == null ? null : recipes().get(task, f.lang);
                if (r != null) {
                    lastRecipe = new String[]{task, f.lang};
                    codingLang = f.lang;
                    said = "It does the same as my recipe to " + ai.ibiko.ibiko1.formulas.Recipes.titleOf(task) + ", so here it is in "
                            + ai.ibiko.ibiko1.formulas.Recipes.shown(f.lang) + ":\n\n" + r.said().replaceFirst("^Here's how to [^:]*:\\n\\n", "");
                    steps.add("It matches my tested recipe to " + ai.ibiko.ibiko1.formulas.Recipes.titleOf(task));
                    steps.add("Taking the same recipe in " + ai.ibiko.ibiko1.formulas.Recipes.shown(f.lang));
                } else {
                    said = "I can't convert that one to " + ai.ibiko.ibiko1.formulas.Recipes.shown(f.lang) + " exactly on my own yet: it isn't one of my "
                            + "tested recipes, and I won't guess at code. Choose a model from the menu at the top and the coding agent will convert it;"
                            + " or ask me for a task (\"how do I sort a list in " + ai.ibiko.ibiko1.formulas.Recipes.shown(f.lang) + "?\").";
                    steps.add("It isn't one of my tested recipes");
                }
                break;
            }
            case LINE:
                said = ai.ibiko.ibiko1.formulas.CodeReader.line(s, f.line);
                steps.add("Finding that line and putting it in plain words");
                break;
            default:
                said = ai.ibiko.ibiko1.formulas.CodeReader.checked(s);
                steps.add("Checking the brackets, the strings and how each line is formed");
                break;
        }
        how = How.MEMORY;
        think(steps, () -> saysExact(who, said));
    }

    /** C1–C4: pasted code explained in plain words. */
    private void codeTurn(final String who, ai.ibiko.ibiko1.formulas.CodeReader.Snippet snippet) {
        final String said = ai.ibiko.ibiko1.formulas.CodeReader.explain(snippet, recipes());
        how = How.MEMORY;
        think(Arrays.asList("Reading what you pasted: it's code", "Working out the language from how it's written: "
                        + ai.ibiko.ibiko1.formulas.CodeReader.name(snippet.lang), "Finding its parts: what it defines, brings in, and does",
                "Going through it line by line", "Putting it in plain words"), () -> saysExact(who, said));
    }

    /** The site last built, for changes to it ("add a pricing section", "make it blue", "call it Mama's Kitchen"). */
    private volatile ai.ibiko.ibiko1.formulas.Sites.Design lastDesign;

    /** W5: the site just made, changed as asked, and given whole again with what changed. */
    private boolean siteChangeTurn(final String who, final String t) {
        final ai.ibiko.ibiko1.formulas.Sites.Design d = lastDesign;
        final String changed = d == null ? null : ai.ibiko.ibiko1.formulas.Sites.change(d, t);
        if (changed == null) {
            return false;
        }
        final String html = ai.ibiko.ibiko1.formulas.Sites.build(d);
        lastSnippet = new ai.ibiko.ibiko1.formulas.CodeReader.Snippet(html, "html", "");
        final String said = "Done: " + changed + ".\n\n```html\n" + html + "```\n\nSave it over your index.html. Ask for any other change: a section added or "
                + "taken out, a colour, the name, your number.";
        how = How.MEMORY;
        think(Arrays.asList("Reading your request: a change to the site I made", "Making the change: " + changed, "Writing the page again"),
                () -> saysExact(who, said));
        return true;
    }

    private ai.ibiko.ibiko1.formulas.Sites sites;

    private ai.ibiko.ibiko1.formulas.QuestionTypes questionTypes;

    /** The kinds of question by how they start (knowledge/language/question_types.tsv: a newer one downloaded first). */
    private ai.ibiko.ibiko1.formulas.Expressions expressions;

    /** Idioms and sayings and what they mean (knowledge/language/expressions.tsv). */
    public synchronized ai.ibiko.ibiko1.formulas.Expressions expressions() {
        if (expressions == null || expressions.size() == 0) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/language/expressions.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            expressions = new ai.ibiko.ibiko1.formulas.Expressions(in);
        }
        return expressions;
    }

    public synchronized ai.ibiko.ibiko1.formulas.QuestionTypes questionTypes() {
        if (questionTypes == null) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/language/question_types.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            questionTypes = new ai.ibiko.ibiko1.formulas.QuestionTypes(in);
        }
        return questionTypes;
    }

    private ai.ibiko.ibiko1.formulas.Records records;

    /** The records the encyclopedia names (knowledge/records.tsv: a newer one downloaded to the phone first). */
    public synchronized ai.ibiko.ibiko1.formulas.Records records() {
        if (records == null) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/records.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            records = new ai.ibiko.ibiko1.formulas.Records(in);
        }
        return records;
    }

    private ai.ibiko.ibiko1.formulas.ErrorTalk errors;

    /** The rules and facts said in this conversation ("all cats are mammals"), for questions about them later. */
    private final ai.ibiko.ibiko1.formulas.Logic.Known logicKnown = new ai.ibiko.ibiko1.formulas.Logic.Known();

    private static final java.util.regex.Pattern YEARS_AGO = java.util.regex.Pattern.compile("(?i)^\\s*how (?:many years ago|long ago) "
            + "(?:was|did|were)\\s+(.{2,60}?)\\s+(born|die|pass away)\\s*[?.!]*\\s*$");

    /** Two steps: the date it happened, from the facts; then the whole years from it to today. Null when not asked or not known. */
    String yearsAgo(String t) {
        java.util.regex.Matcher m = YEARS_AGO.matcher(t);
        if (!m.matches()) {
            return null;
        }
        String who = m.group(1).trim(), relation = m.group(2).equalsIgnoreCase("born") ? "born" : "died";
        String when = fact(who, relation);
        if (when == null) {
            return null;
        }
        java.util.regex.Matcher d = java.util.regex.Pattern.compile("(?:(\\d{1,2}) (January|February|March|April|May|June|July|August|September|October"
                + "|November|December) )?(\\d{3,4})").matcher(when);
        if (!d.find()) {
            return null;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        int year = Integer.parseInt(d.group(3));
        int years = today.getYear() - year;
        if (d.group(1) != null) {
            java.time.LocalDate then = java.time.LocalDate.of(year, java.time.Month.valueOf(d.group(2).toUpperCase(Locale.ROOT)), Integer.parseInt(d.group(1)));
            years = java.time.Period.between(then, today).getYears(); // whole years, counting the day
        }
        String name = who.replaceFirst("(?i)^(the )", "");
        return years + " years ago. " + Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + (relation.equals("born") ? "was born" : "died")
                + " on " + d.group() + ", and today is " + today.getDayOfMonth() + " " + today.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                + " " + today.getYear() + ".";
    }

    /** The comparisons said in this conversation ("Ada is taller than Obi"), for "who is the tallest?" later. */
    private final List<ai.ibiko.ibiko1.formulas.Order.Fact> orderFacts = new ArrayList<ai.ibiko.ibiko1.formulas.Order.Fact>();

    /**
     * A sum, worked out exactly: the host's own way first (the chat screen's), else Ibiko's, so the sums work wherever
     * Ibiko v1 runs (picked as a model, in a campaign, in tests), not only on the chat screen.
     */
    private String sumOf(String t) {
        if (!aboutTheSum(t)) {
            return null; // "Add a question: What is 2+2? 3, 4, 5. Answer: 4" asks for a change, not a sum
        }
        String byHost = host.sum(t);
        if (byHost != null) {
            return byHost;
        }
        ai.ibiko.suit.Comprehension c = ai.ibiko.suit.Comprehension.of(t);
        if (c.math != null && c.mathValue != null && !c.mathValue.isEmpty()) {
            return c.math + " = " + c.mathValue;
        }
        return root(t);
    }

    /** "the square root of 169", "cube root of 27": exact when it is whole, else to six places. */
    static String root(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\b(square|cube) root of (\\d+(?:\\.\\d+)?)").matcher(t);
        if (!m.find()) {
            return null;
        }
        boolean square = m.group(1).equalsIgnoreCase("square");
        double n = Double.parseDouble(m.group(2));
        double r = square ? Math.sqrt(n) : Math.cbrt(n);
        long whole = Math.round(r);
        boolean exact = Math.abs(square ? whole * (double) whole - n : whole * (double) whole * whole - n) < 1e-9;
        String v = exact ? String.valueOf(whole) : new java.math.BigDecimal(r).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return (square ? "√" : "∛") + m.group(2) + " = " + v + (exact ? "" : " (to six places)");
    }

    /** The words a sum may come with: once they and the maths are taken out, nothing of substance is left. */
    private static final java.util.regex.Pattern SUM_WORDS = java.util.regex.Pattern.compile("(?i)\\b(?:what|what's|whats|is|are|how|much|many|"
            + "calculate|compute|work|out|solve|evaluate|find|the|of|a|an|please|equals?|equal|to|answer|result|value|sum|product|total|"
            + "plus|minus|times|multiplied|divided|by|over|add|added|subtract|subtracted|from|and|squared|cubed|square|cube|root|power|"
            + "percent|percentage|half|double|tell|me|can|you|quick|quickly|again)\\b");

    /** Whether the message is about a sum, not a sum inside something else (a quiz question, a story, a sentence). */
    static boolean aboutTheSum(String t) {
        String rest = SUM_WORDS.matcher(t).replaceAll(" ").replaceAll("[\\d.,+\\-*/×÷^()%=?!:;\\s]+", " ").trim();
        return rest.isEmpty() || rest.split(" ").length <= 1 && rest.length() <= 3;
    }

    private static final java.util.regex.Pattern BARE = java.util.regex.Pattern.compile("(?i)^\\s*(?:the\\s+)?(capital(?: city)?|population|currency|"
            + "president|prime minister|leader|king|queen|official language|languages?|area|size|height|length|founder|flag|anthem|"
            + "dialling code|dialing code|time zone|motto)\\s+of\\s+([\\p{L}][\\p{L} .'-]{1,40}?)\\s*[.!]*$");

    private static final String[][] CHAT_SPELLINGS = {{"wats|whats|what's|watz|wots", "what is"}, {"wat|wht|wot", "what"},
        {"whos|who's|hu's|hoos", "who is"}, {"whr|wer", "where"}, {"hw", "how"}, {"wen", "when"}}; // not "y" for "why": "Y is a letter"
    private static final java.util.regex.Pattern ASKING_START = java.util.regex.Pattern.compile("(?i)^\\s*(?:what|who|where|when|why|how|which|whose|"
            + "is|are|was|were|can|could|does|do|did|will|would|should)\\b"); // not "tell me", "define": asks, not questions

    /**
     * A question typed the way people chat: "whats the capital of ghana", "who's mandela" (no question mark, a chat
     * spelling for the question word). Its first word put right and a question mark added, so it's read as asked, not
     * as something said ("Noted."). Only the first word: names and the rest stay as typed.
     */
    static String asked(String message) {
        String m = message.trim();
        for (String[] c : CHAT_SPELLINGS) {
            java.util.regex.Matcher x = java.util.regex.Pattern.compile("(?i)^(?:" + c[0] + ")\\b(?=\\s+\\S)").matcher(m);
            if (x.find()) {
                m = c[1] + m.substring(x.end());
                break;
            }
        }
        if (!m.endsWith("?") && !m.contains("\n") && m.length() < 160 && ASKING_START.matcher(m).find()
                && !m.matches("(?is).*[.!]\\s+\\S.*")) { // one sentence, asked: not a story that ends with a question
            m = m.replaceAll("[.!]+$", "") + "?";
        }
        return m.equals(message.trim()) ? message : m;
    }

    /** The misspellings put right in the message being answered ("“captal” as “capital”"), for the thinking; null when none. */
    private volatile String typoStep;

    /**
     * A question's misspelled words put right ("wat is the captal of nigeria": capital), so a typo doesn't send it to the
     * wrong answer. Only questions: what someone tells it, pastes or codes is read as written.
     */
    private String typosFixed(String message) {
        typoStep = null;
        final ai.ibiko.ibiko1.words.Meanings words = ai.ibiko.ibiko1.words.Meanings.shared();
        if (message == null || words == null || !ASKING_START.matcher(message).find() || !message.trim().endsWith("?")) {
            return message;
        }
        ai.ibiko.ibiko1.formulas.Spelling.Fixed f;
        try {
            final Encyclopedia book = encyclopedia();
            // words said in this conversation are the person's own ("garri", a name): never typos
            final java.util.Set<String> said = new java.util.HashSet<String>();
            StringBuilder talked = new StringBuilder(current == null ? "" : current).append(' ').append(previous == null ? "" : previous);
            for (Conversation.Turn turn : talk.recent(10)) {
                talked.append(' ').append(turn.asked).append(' ').append(turn.said);
            }
            for (String w : talked.toString().toLowerCase(Locale.ROOT).split("[^\\p{L}']+")) {
                said.add(w);
                if (w.length() > 3 && w.endsWith("s")) {
                    said.add(w.substring(0, w.length() - 1)); // "flarns" said: "flarn" is theirs too
                }
            }
            // a word is known when the dictionary has it, the encyclopedia uses it ("became", "took"), or it was said here
            f = ai.ibiko.ibiko1.formulas.Spelling.fix(message, w -> words.headword(w) != null || said.contains(w) || book.uses(w),
                    w -> words.index().has(w), w -> words.index().senses(w).size(), words::soundsLike);
        } catch (RuntimeException e) {
            return message;
        }
        if (f == null) {
            return message;
        }
        typoStep = "Reading " + String.join(", ", f.changes);
        return f.text;
    }

    /** A bare phrase typed as a search ("capital of france", "population of Kano"): the question it stands for. */
    static String asQuestion(String message) {
        if (message == null) {
            return null;
        }
        message = asked(message);
        java.util.regex.Matcher m = BARE.matcher(message);
        // a sentence about it ("the capital of France is lovely") isn't a search: the name runs into a verb
        if (!m.matches() || m.group(2).matches("(?i).*\\b(?:is|are|was|were|has|have|had|does|do|did|looks?|seems?|and|but|because)\\b.*")) {
            return message;
        }
        return "What is the " + m.group(1).toLowerCase(Locale.ROOT) + " of " + m.group(2).trim() + "?";
    }

    /** The last answer (not a reshaping of one), and what it was about, for "shorter", "as points", "give me an example". */
    private volatile String lastAnswer, lastAnswerAbout;
    private volatile boolean reshaping;
    private ai.ibiko.ibiko1.formulas.Reshape reshape;

    /** The plain words, for "more simply" (knowledge/language/plain_words.tsv: a newer one downloaded to the phone first). */
    public synchronized ai.ibiko.ibiko1.formulas.Reshape reshape() {
        if (reshape == null) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/language/plain_words.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            reshape = new ai.ibiko.ibiko1.formulas.Reshape(in);
        }
        return reshape;
    }

    /** S1–S5: the last answer said again as asked. False: nothing to say it from (code, or no example in what it read). */
    private boolean reshapeTurn(final String who, ai.ibiko.ibiko1.formulas.Reshape.Ask ask) {
        final String before = lastAnswer;
        String said;
        if (ask == ai.ibiko.ibiko1.formulas.Reshape.Ask.EXAMPLE) {
            String about = lastAnswerAbout;
            Encyclopedia.Article a = about == null ? null : encyclopedia().find(about);
            String ex = a == null ? null : ai.ibiko.ibiko1.formulas.Reshape.example(a.text, lastSaid);
            if (about == null) {
                return false;
            }
            said = ex != null ? "For example: " + ex : "I haven't read an example of " + about + " that I could give you. Ask me about a part of it,"
                    + " and I'll say what I know.";
        } else {
            said = reshape().again(before, ask);
            if (said == null || said.equals(before.trim())) {
                return false;
            }
        }
        final String out = said;
        how = How.MEMORY;
        think(Arrays.asList("Reading your request: my last answer, " + ask.said, "Dropping what it said twice",
                ask == ai.ibiko.ibiko1.formulas.Reshape.Ask.EXAMPLE ? "Finding an example in what I've read" : "Saying it again, " + ask.said), () -> {
                    reshaping = true;
                    try {
                        saysExact(who, out);
                    } finally {
                        reshaping = false;
                    }
                });
        return true;
    }

    /** The signature of the error whose fix was found on the web, until it's said to work or not. */
    private volatile String pendingErrorFix;

    /**
     * E5–E7: an error it doesn't know by heart: what kind it is, and a fix: one learned before (said as confirmed or
     * not), else looked up on the web and in social posts, kept unconfirmed until the person says it worked.
     */
    private void unknownErrorTurn(final String who, final String t, final ai.ibiko.ibiko1.formulas.ErrorTalk.Unknown u) {
        final String kind = ai.ibiko.ibiko1.formulas.ErrorTalk.kindSaid(u);
        final String head = "That error isn't one I know by heart: " + u.line + "\n\n" + (kind == null ? "" : "What kind it is: " + kind + ".\n\n");
        String[] had = errors().learned(u);
        if (had != null) {
            final boolean sure = "1".equals(had[0]);
            final String said = head + "The fix" + (sure ? " (it worked for you before)" : " I found last time (not confirmed yet)") + ":\n\n" + had[1]
                    + (sure ? "" : "\n\nTell me if it works, and I'll keep it.");
            if (!sure) {
                pendingErrorFix = u.signature;
            }
            how = How.MEMORY;
            think(Arrays.asList("Reading what you pasted: an error message", sure ? "I learned the fix for this one before, and it worked"
                    : "I found a fix for this one before"), () -> saysExact(who, said));
            return;
        }
        if (!host.online() || Plug.web == null || Plug.web == Plug.NONE) {
            final String said = head + "Paste the lines of code around where it happens and I'll read them with it. When you're online, I can also"
                    + " look it up, and keep the fix once you tell me it worked.";
            how = How.MEMORY;
            think(Arrays.asList("Reading what you pasted: an error message", "Not one I know by heart: telling its kind"), () -> saysPlain(who, said));
            return;
        }
        how = How.RESEARCH;
        think(Arrays.asList("Reading what you pasted: an error message", "Not one I know by heart: looking it up on the web and in posts",
                "Taking the sentences that say what to do about it"), () -> {
                    final int mine = token;
                    new Thread(() -> {
                        String fix = null;
                        try {
                            fix = ai.ibiko.ibiko1.formulas.ErrorTalk.fixFrom(Plug.web.search(ai.ibiko.ibiko1.formulas.ErrorTalk.query(u)), u);
                        } catch (RuntimeException ignored) {
                            // nothing on the web
                        }
                        if (fix == null) {
                            try {
                                StringBuilder posts = new StringBuilder();
                                for (ai.ibiko.ibiko1.formulas.Corroborate.Post p : Plug.social.apply(ai.ibiko.ibiko1.formulas.ErrorTalk.query(u))) {
                                    posts.append(p.text).append("\n\n");
                                }
                                fix = ai.ibiko.ibiko1.formulas.ErrorTalk.fixFrom(posts.toString(), u);
                            } catch (RuntimeException ignored) {
                                // nothing there either
                            }
                        }
                        final String found = fix;
                        if (found != null) {
                            errors().found(u, found);
                            pendingErrorFix = u.signature;
                        }
                        host.onScreen(() -> {
                            if (mine != token) {
                                return;
                            }
                            String said = found == null
                                    ? head + "I looked it up but found nothing I'd trust as a fix. Paste the lines of code around where it happens and I'll read them with it."
                                    : head + "What I found on the web (not confirmed yet):\n\n" + found + "\n\nTry it and tell me if it worked: I'll keep it for next time, offline.";
                            saysExact(who, said);
                            if (found != null) {
                                rateable(t, said);
                            }
                        });
                    }, "ibiko1-error-lookup").start();
                });
    }

    /** The error last explained, and the code that came with it, for "how do I fix it?" and "why?". */
    private volatile ai.ibiko.ibiko1.formulas.ErrorTalk.Found lastError;
    private volatile String lastErrorCode;

    /** The programming errors it explains (knowledge/code/errors.tsv: a newer one downloaded to the phone first). */
    public synchronized ai.ibiko.ibiko1.formulas.ErrorTalk errors() {
        if (errors == null) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/code/errors.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            errors = new ai.ibiko.ibiko1.formulas.ErrorTalk(in, new File(dir, "learned_errors.tsv"));
        }
        return errors;
    }

    private ai.ibiko.ibiko1.formulas.Apps apps;

    /** The app last handed over, for changes to it ("add a question: …", "my WhatsApp number is …"). */
    private volatile ai.ibiko.ibiko1.formulas.Apps.Made lastMade;

    /** The small web apps (knowledge/web/apps.tsv: a newer one downloaded to the phone first). */
    public synchronized ai.ibiko.ibiko1.formulas.Apps apps() {
        if (apps == null) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/web/apps.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            apps = new ai.ibiko.ibiko1.formulas.Apps(in);
        }
        return apps;
    }

    /** What it knows about kinds of websites (shipped coded: knowledge/web/sites.tsv.ibk). */
    public synchronized ai.ibiko.ibiko1.formulas.Sites sites() {
        if (sites == null) {
            java.io.InputStream in = null;
            try {
                in = TextCode.open(path -> Plug.asset.apply(path), "knowledge/web/sites.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            sites = new ai.ibiko.ibiko1.formulas.Sites(in);
        }
        return sites;
    }

    /**
     * W1–W4: a website request. Built: the kind read from the request (a bakery is a restaurant site), the business's name,
     * place and number put in, one responsive HTML file given whole. Explained or compared: from what it knows of each
     * kind. False when it names no kind it knows (the rest of Ibiko, or the coding agent, takes it).
     */
    private volatile LearningTalk learningTalk;

    /** Whether Ibiko is in a learning conversation with the API model now. */
    public boolean learning() {
        LearningTalk l = learningTalk;
        return l != null && l.going();
    }

    /**
     * "Start a learning conversation with my API model", "go and learn 20 things", "stop learning": Ibiko asks the API
     * model what it doesn't know, checks each answer against what it holds, keeps it by the verdict, and shows every
     * exchange ({@link LearningTalk}). False when the message isn't about one.
     */
    private boolean learningTurn(final String who, String message) {
        if (LearningTalk.stops(message)) {
            LearningTalk l = learningTalk;
            if (l == null || !l.going()) {
                return false;
            }
            l.stop();
            think(Arrays.asList("Reading your message", "Ending the learning conversation"),
                    () -> saysPlain(who, "Stopped. What I learned so far is kept; ask me about any of it."));
            return true;
        }
        final int count = LearningTalk.asked(message);
        if (count == 0) {
            return false;
        }
        if (!host.teacherReady()) {
            think(Arrays.asList("Reading your message: a learning conversation with your API model"), () -> saysPlain(who,
                    "I need an API model to learn from: add one in Settings › Models (your API key), then ask me again. Each question I ask it "
                            + "uses your tokens, so I'll ask " + count + " and stop."));
            return true;
        }
        if (learning()) {
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, "I'm already in a learning conversation. Say “stop learning” to end it."));
            return true;
        }
        if (host.openLearningChat()) {
            // the conversation happens in its own chat, through the same way as every message
            think(Arrays.asList("Reading your message: a learning conversation with " + host.teacherName(), "Opening the Learning chat"),
                    () -> saysPlain(who, "Opening the Learning chat: I'll type my questions to " + host.teacherName() + " there and send them the way"
                            + " you send yours, check every answer before I keep it, and you can join in. Each question uses your API tokens;"
                            + " say “stop learning” there to end it."));
            return true;
        }
        final String teacher = host.teacherName();
        learningTalk = new LearningTalk(learningBrain(), text -> host.onScreen(() -> host.say(host.persona(), text)));
        how = How.OTHER;
        think(Arrays.asList("Reading your message: a learning conversation with " + teacher, "Picking what I don't know yet"), () -> {
            saysPlain(who, "Starting a learning conversation with " + teacher + ": I'll ask " + count + (count == 1 ? " question" : " questions")
                    + " about what I don't know: first what I couldn't answer before, then what each answer makes me curious about, then "
                    + "random subjects. I'll check every answer against what I hold before I keep it. Each question uses your API tokens; "
                    + "say “stop learning” to end it.");
            learningTalk.start(count);
        });
        return true;
    }

    /**
     * What a learning conversation is given of this brain: the API model to ask, and the check and keep every answer
     * goes through ({@link TeacherCheck}), so what is learned in the Learning chat is learned the same way as here.
     */
    public LearningTalk.Brain learningBrain() {
        return new LearningTalk.Brain() {
            @Override
            public String ask(String question) {
                String a = host.askTeacher(TEACHER_SYSTEM, "Question: " + PromptReader.guard(question));
                return a == null ? null : ai.ibiko.suit.FunctionCode.withoutCode(a).trim();
            }

            @Override
            public String teacher() {
                return host.teacherName();
            }

            @Override
            public String checkAndKeep(String question, String answer, String subject) {
                if (ai.ibiko.learn.Corroborate.failure(answer)) {
                    return "Not kept: that isn't an answer.";
                }
                String about = subject != null ? subject : ResearchAnswer.subject(question);
                Encyclopedia.Article art = about == null ? null : encyclopedia().find(about);
                TeacherCheck.Result r = TeacherCheck.check(answer, about, facts(), knowledge(), art == null ? null : art.text);
                long now = System.currentTimeMillis();
                learner().read(answer, Learner.Source.RESEARCH, now); // the language of it, learned too
                ai.ibiko.ibiko1.cognition.LearningRecords.Record rec = learningRecords().learn(ai.ibiko.ibiko1.cognition.LearningRecords.Category.fact,
                        question, answer, r != null && r.verdict == TeacherCheck.Verdict.CONFIRMED ? "verified_source" : "api_model", teacher(), 0.6, now,
                        false, "learning-conversation");
                if (rec != null && r != null && r.verdict == TeacherCheck.Verdict.CONFLICT) {
                    rec.status = ai.ibiko.ibiko1.cognition.LearningRecords.Verification.disputed;
                }
                if (r == null || r.verdict == TeacherCheck.Verdict.NEW) {
                    knowledge().learnUnchecked(question, answer);
                    facts().learn(answer, about, 1, now);
                    return "Kept as unsure: it's new to me, so I'll hold it until you confirm it.";
                }
                if (r.verdict == TeacherCheck.Verdict.CONFIRMED) {
                    knowledge().confirm(question, answer);
                    facts().learn(answer, about, 2, now);
                    return "Kept as known: it agrees with " + r.because + ".";
                }
                return "Set aside: it goes against what I hold (" + r.because + "). Tell me which is right.";
            }

            @Override
            public List<String> open() {
                return learner().openQuestions();
            }

            @Override
            public String randomSubject(java.util.Random r) {
                return encyclopedia().randomSubject(r);
            }

            @Override
            public boolean knows(String name) {
                return encyclopedia().find(name) != null;
            }
        };
    }

    private static final java.util.regex.Pattern OPINION_ASK = java.util.regex.Pattern.compile("(?i)^\\s*(?:so\\s+|and\\s+|ok(?:ay)?,?\\s+)?(?:what do you think (?:about|of)|"
            + "what are your thoughts (?:on|about)|what(?:'s| is) your (?:opinion|view|take) (?:on|of|about)|your thoughts on|how do you feel about|"
            + "do you think (?:that )?(.{2,60}?) is (?:good|bad|worth it|a good idea)|is (.{2,60}?) (?:good or bad|a good idea|worth it))(?:\\s*(.{2,80}?))?\\s*[?.!]*\\s*$");

    /**
     * A view of its own: what it reads about the thing (the web, when online, and its encyclopedia) weighed for and
     * against, a probability that it's good on balance, what it could lead to, and what would change its mind
     * ({@code ideas/Opinion}). False when it isn't asked for a view, or the view is about Ibiko or the person.
     */
    private boolean opinionTurn(final String who, String message) {
        java.util.regex.Matcher m = OPINION_ASK.matcher(message == null ? "" : message.trim());
        if (!m.matches()) {
            return false;
        }
        String raw = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        if (raw == null) {
            return false;
        }
        String s0 = raw.trim();
        // "my idea: selling zobo online", "my plan to open a bakery": the idea itself is what's weighed
        java.util.regex.Matcher theirs = java.util.regex.Pattern.compile("(?i)^(?:my|our) (?:business )?(?:idea|plan|proposal|project|dream)(?:\\s+(?:of|to|for|about))?\\s*[:,-]?\\s*(.{3,80})$").matcher(s0);
        if (theirs.matches()) {
            s0 = theirs.group(1).replaceFirst("(?i)^(?:to\\s+)", "");
        }
        final String subject = s0.replaceFirst("(?i)^(?:the|a|an)\\s+", "").replaceAll("[?.!]+$", "");
        if (subject.isEmpty() || subject.matches("(?i).*\\b(?:you|yourself|me|myself|i|my|ibiko|this|that|it)\\b.*")) {
            return false; // about Ibiko, the person, or what was just said: its own readers answer those
        }
        final boolean online = host.online() && Plug.web != Plug.NONE;
        how = online ? How.RESEARCH : How.MEMORY;
        think(Arrays.asList("Reading your question: you want my view of " + subject, online ? "Reading what's said for and against it"
                : "Weighing what I already know of it"), () -> {
                    final int mine = token;
                    new Thread(() -> {
                        ai.ibiko.ibiko1.ideas.Corpus c = online ? ai.ibiko.ibiko1.ideas.Research.forOpinion(subject, step -> host.onScreen(() -> {
                            if (mine == token) {
                                host.step(step);
                            }
                        })) : new ai.ibiko.ibiko1.ideas.Corpus();
                        String article = articleText(subject);
                        if (!article.isEmpty()) {
                            ai.ibiko.ibiko1.ideas.Corpus.Source own = c.add("my encyclopedia");
                            for (String sentence : article.split("(?<=[.!?])\\s+")) {
                                own.sentences.add(sentence);
                            }
                        }
                        final String view = ai.ibiko.ibiko1.ideas.Opinion.form(subject, c);
                        host.onScreen(() -> {
                            if (mine != token) {
                                return;
                            }
                            host.step("Counting the sources for and against, and how sure that makes me");
                            String known = article.isEmpty() ? "" : " What I know of it: " + String.join(" ",
                                    java.util.Arrays.asList(article.split("(?<=[.!?])\\s+")).subList(0, Math.min(2, article.split("(?<=[.!?])\\s+").length)));
                            saysPlain(who, view != null ? view : "I don't have a view of my own on " + subject + " yet: what I've read doesn't weigh for or against it."
                                    + known + (online ? "" : " When you're online, ask again and I'll read what's said for and against it."));
                        });
                    }, "ibiko1-opinion").start();
                });
        return true;
    }

    /**
     * The general rule for open requests ({@code ideas/}): what is asked to be made (or ideas for) is researched (or
     * recalled, or thought from its own encyclopedia offline), each part weighed by how many sources share it, parts
     * said together joined into new ideas, and the result written in its own words: a document, with a prototype page
     * for an app or a platform. Websites, the apps it builds and code have their own readers and keep them.
     */
    private boolean ambitionTurn(final String who, final String t) {
        final ai.ibiko.ibiko1.ideas.Ambition.Ask ask = ai.ibiko.ibiko1.ideas.Ambition.read(t);
        if (ask == null || ai.ibiko.ibiko1.formulas.Sites.read(t) != null || ai.ibiko.ibiko1.formulas.Sites.loose(t) != null
                || apps().asked(t) != null || ai.ibiko.ibiko1.formulas.Recipes.read(t) != null) {
            return false;
        }
        final String whole = ask.whole();
        final String key = (ask.kind == ai.ibiko.ibiko1.ideas.Ambition.Kind.IDEAS ? "ideas " : "") + whole;
        final boolean online = host.online() && Plug.web != Plug.NONE;
        final ai.ibiko.ibiko1.ideas.Ideation.Plan before = ai.ibiko.ibiko1.ideas.Research.recall(dir, key);
        final java.util.function.Function<String, String> meaning = this::nounMeaning;
        final ai.ibiko.ibiko1.formulas.Sites.Brief brief = ai.ibiko.ibiko1.formulas.Sites.brief(t);
        how = online ? How.RESEARCH : How.MEMORY;
        think(Arrays.asList("Reading your request: " + (ask.kind == ai.ibiko.ibiko1.ideas.Ambition.Kind.IDEAS ? "ideas for " : "to make ") + whole,
                online ? "Researching what goes into " + whole + ", before deciding anything" : before != null
                        ? "I'm offline: thinking from what I researched before" : "I'm offline: thinking from what I already know"), () -> {
                    final int mine = token;
                    new Thread(() -> {
                        ai.ibiko.ibiko1.ideas.Corpus corpus;
                        java.util.function.Consumer<String> step = x -> host.onScreen(() -> {
                            if (mine == token) {
                                host.step(x);
                            }
                        });
                        if (online) {
                            corpus = ask.kind == ai.ibiko.ibiko1.ideas.Ambition.Kind.IDEAS ? ai.ibiko.ibiko1.ideas.Research.forIdeas(whole, step)
                                    : ai.ibiko.ibiko1.ideas.Research.forAnything(whole, step);
                        } else {
                            corpus = new ai.ibiko.ibiko1.ideas.Corpus();
                        }
                        wordTalk(); // the dictionary open: what words mean, who counts as a person
                        // offline, what it already knows is the source: its encyclopedia's articles on the thing and what it's for
                        // (online, those articles are evidence for the parts, not sources to count)
                        for (String subject : corpus.size() >= 2 ? new String[0] : new String[]{ask.goal, ask.about}) {
                            String article = subject.isEmpty() ? "" : articleText(subject.replaceFirst("^(?:my|our|your|a|an|the)\\s+", ""));
                            if (!article.isEmpty()) {
                                ai.ibiko.ibiko1.ideas.Corpus.Source own = corpus.add("my encyclopedia: " + subject);
                                for (String sentence : article.split("(?<=[.!?])\\s+")) {
                                    own.sentences.add(sentence);
                                }
                            }
                        }
                        String aboutText = (articleText(ask.goal) + " " + articleText(ask.about)).toLowerCase(Locale.ROOT);
                        ai.ibiko.ibiko1.ideas.Ideation.Plan plan = ai.ibiko.ibiko1.ideas.Ideation.think(corpus, ask.about.isEmpty() ? ask.goal : ask.about,
                                ask.goal, t, w -> aboutText.contains(w.toLowerCase(Locale.ROOT)), this::isPerson);
                        boolean learned = false;
                        if (plan.parts.size() >= 3 && online) {
                            ai.ibiko.ibiko1.ideas.Research.keep(dir, key, plan); // learned: next time, and offline, it starts here
                        } else if (plan.parts.size() < 3 && before != null) {
                            plan = before;
                            learned = true;
                        }
                        final ai.ibiko.ibiko1.ideas.Ideation.Plan thought = plan;
                        final ai.ibiko.ibiko1.ideas.Corpus read = corpus;
                        final boolean fromBefore = learned;
                        host.onScreen(() -> {
                            if (mine != token) {
                                return;
                            }
                            if (thought.parts.size() < 2) {
                                saysPlain(who, (online ? "I couldn't find enough about " + whole + " to think it through just now."
                                        : "I'm offline and haven't researched " + whole + " before, so I don't have enough to think it through yet.")
                                        + " Tell me the parts you want in it, or ask again " + (online ? "in a moment" : "when you're online") + ".");
                                return;
                            }
                            host.step("Weighing how likely each part is needed, from how many sources have it");
                            if (!thought.ideas.isEmpty()) {
                                host.step("Joining parts that go together into ideas no source has");
                            }
                            host.step("Writing it in my own words, checked against the sources");
                            ai.ibiko.ibiko1.ideas.Maker.Anything made = ai.ibiko.ibiko1.ideas.Maker.anything(thought, ask, brief, meaning, null, read, fromBefore);
                            String out = made.said;
                            if (made.prototype != null) {
                                lastDesign = made.prototype;
                                lastMade = null;
                                String html = ai.ibiko.ibiko1.formulas.Sites.build(made.prototype);
                                lastSnippet = new ai.ibiko.ibiko1.formulas.CodeReader.Snippet(html, "html", "");
                                out += "\n\nA prototype to click through (each part a screen):\n\n```html\n" + html + "```";
                            }
                            saysExact(who, out);
                        });
                    }, "ibiko1-ambition").start();
                });
        return true;
    }

    /** The dictionary, as the analogies ask it: opposites, meanings, and the words a meaning names. */
    private ai.ibiko.ibiko1.formulas.Patterns.Words analogyWords() {
        final ai.ibiko.ibiko1.words.Meanings words = ai.ibiko.ibiko1.words.Meanings.shared();
        if (words == null) {
            return null;
        }
        return new ai.ibiko.ibiko1.formulas.Patterns.Words() {
            @Override
            public List<String> opposites(String w) {
                return words.opposites(w);
            }

            @Override
            public List<String> meanings(String w) {
                List<String> out = new ArrayList<String>();
                for (ai.ibiko.ibiko1.words.MeaningIndex.Sense s : words.senses(w)) {
                    if (!s.definition.isEmpty() && !Character.isUpperCase(s.definition.charAt(0))) {
                        out.add(s.definition);
                    }
                }
                return out;
            }

            @Override
            public List<String> meaning(String... holding) {
                return words.withMeaning(holding);
            }
        };
    }

    /**
     * A short story and its question in one message ("Tunde keeps 4 cows and grows maize. How many cows does Tunde keep?"):
     * worked out from what the message itself says, not looked up; null when it isn't one.
     */
    private String storyInOneMessage(String message) {
        if (message == null || message.length() > 1500) {
            return null;
        }
        String m = message.trim();
        List<String> parts = Passage.split(m);
        if (parts.size() < 2 || !parts.get(parts.size() - 1).trim().endsWith("?") || !m.matches("(?s).*\\d.*")) {
            return null;
        }
        String question = parts.get(parts.size() - 1).trim();
        int at = m.lastIndexOf(question);
        if (at <= 0) {
            return null;
        }
        try {
            return new Story(m.substring(0, at).trim()).answer(question);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A noun's first meaning in the dictionary, or null. */
    private String nounMeaning(String w) {
        ai.ibiko.ibiko1.words.Meanings words = ai.ibiko.ibiko1.words.Meanings.shared();
        if (words == null) {
            return null;
        }
        for (ai.ibiko.ibiko1.words.MeaningIndex.Sense sense : words.senses(w)) {
            // a common noun's meaning, not a name's ("Service": a Canadian writer)
            // (the dictionary says a common noun in lower case; a person's starts "Canadian writer…", "English poet…")
            if (sense.pos.equals("noun") && !sense.definition.isEmpty() && !Character.isUpperCase(sense.definition.charAt(0))) {
                return sense.definition.split(";")[0];
            }
        }
        return null;
    }

    /** "a school website", "website for my bakery": what the site is of. */
    static String siteSubject(String t, ai.ibiko.ibiko1.formulas.Sites.Brief brief) {
        if (brief != null && !brief.trade.isEmpty()) {
            return brief.trade.trim().toLowerCase(Locale.ROOT);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\b(?:a|an|my|our|the|me a|us a)\\s+((?:[a-z]+\\s+){0,2}?[a-z]+)\\s+(?:web ?site|site|web ?page|landing page)\\b")
                .matcher(t);
        if (m.find()) {
            String s = m.group(1).toLowerCase(Locale.ROOT).replaceAll("^(?:new|simple|nice|good|modern|beautiful|professional|small|basic|great|cool)\\s+", "");
            return s.matches("(?:new|simple|nice|good|modern|beautiful|professional|small|basic|great|cool|web|my|our)") ? null : s;
        }
        return null;
    }

    /**
     * A site thought through instead of laid out from a stored kind: what such sites have is researched (or recalled from
     * the last research, offline), each part given a probability, related parts joined into new ideas, and the page
     * written in Ibiko's own words ({@code ideas/}). False when there is nothing to think from (offline, never researched).
     */
    private boolean thinkThenBuild(final String who, final String t, final ai.ibiko.ibiko1.formulas.Sites book) {
        final ai.ibiko.ibiko1.formulas.Sites.Brief brief = ai.ibiko.ibiko1.formulas.Sites.brief(t);
        final String what = siteSubject(t, brief);
        if (what == null) {
            return false;
        }
        final String key = what + " website";
        final boolean online = host.online() && Plug.web != Plug.NONE;
        final ai.ibiko.ibiko1.ideas.Ideation.Plan before = ai.ibiko.ibiko1.ideas.Research.recall(dir, key);
        if (!online && before == null) {
            return false; // nothing researched yet, and no way to look: the kinds it knows
        }
        final ai.ibiko.ibiko1.formulas.Sites.Kind look = book.kind(t);
        final ai.ibiko.ibiko1.words.Meanings words = ai.ibiko.ibiko1.words.Meanings.shared();
        final java.util.function.Function<String, String> meaning = this::nounMeaning;
        how = How.RESEARCH;
        think(Arrays.asList("Reading your request: a " + key + " to make", online ? "Researching what " + what + " websites have, before deciding anything"
                : "I'm offline: thinking from what I researched before on " + key + "s"), () -> {
                    final int mine = token;
                    new Thread(() -> {
                        wordTalk(); // the dictionary open: what words mean, who counts as a person
                        ai.ibiko.ibiko1.ideas.Corpus corpus = new ai.ibiko.ibiko1.ideas.Corpus();
                        ai.ibiko.ibiko1.ideas.Ideation.Plan plan = null;
                        if (online) {
                            corpus = ai.ibiko.ibiko1.ideas.Research.forMaking(what, "website", step -> host.onScreen(() -> {
                                if (mine == token) {
                                    host.step(step);
                                }
                            }));
                            String about = (meaning.apply(what) == null ? "" : meaning.apply(what)) + " " + articleText(what);
                            final String aboutLow = about.toLowerCase(Locale.ROOT);
                            plan = ai.ibiko.ibiko1.ideas.Ideation.think(corpus, what, "website", t,
                                    w -> aboutLow.contains(w.toLowerCase(Locale.ROOT)), this::isPerson);
                            if (plan.parts.size() >= 3) {
                                ai.ibiko.ibiko1.ideas.Research.keep(dir, key, plan); // learned: next time, and offline, it starts here
                            } else {
                                plan = before; // the web gave too little: what was learned before, if anything
                            }
                        } else {
                            plan = before;
                        }
                        final ai.ibiko.ibiko1.ideas.Ideation.Plan thought = plan;
                        final ai.ibiko.ibiko1.ideas.Corpus read = corpus;
                        host.onScreen(() -> {
                            if (mine != token) {
                                return;
                            }
                            if (thought == null) {
                                saysPlain(who, "I couldn't find enough about " + what + " websites to think it through just now. Ask again in a moment, "
                                        + "or tell me the sections you want and I'll build them.");
                                return;
                            }
                            host.step("Weighing how likely each part is needed, from how many sources have it");
                            if (!thought.ideas.isEmpty()) {
                                host.step("Joining parts that go together into ideas no source has");
                            }
                            host.step("Writing every section in my own words, checked against the sources");
                            ai.ibiko.ibiko1.ideas.Maker.Made made = ai.ibiko.ibiko1.ideas.Maker.site(thought, what, brief, meaning, look, read, !online || thought == before);
                            lastDesign = made.design;
                            lastMade = null;
                            String html = ai.ibiko.ibiko1.formulas.Sites.build(made.design);
                            lastSnippet = new ai.ibiko.ibiko1.formulas.CodeReader.Snippet(html, "html", "");
                            saysExact(who, made.said + "\n\n```html\n" + html + "```\n\nSave it as index.html and open it in a browser.");
                        });
                    }, "ibiko1-think-build").start();
                });
        return true;
    }

    /** The subject's article in the encyclopedia, as plain text (what Ibiko already knows of it); empty when none. */
    private String articleText(String subject) {
        try {
            Encyclopedia.Article a = encyclopedia().find(subject);
            return a == null || a.text == null ? "" : a.text;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** "An okada is a motorcycle taxi (Nigeria)." from the dictionary's first meanings of a word; null when it has none. */
    String dictionaryMeaning(String subject) {
        ai.ibiko.ibiko1.words.Meanings words = ai.ibiko.ibiko1.words.Meanings.shared();
        String w = subject.trim().toLowerCase(Locale.ROOT).replaceFirst("^(?:a|an|the)\\s+", "");
        if (words == null || w.isEmpty() || w.split("\\s+").length > 3) {
            return null;
        }
        java.util.List<ai.ibiko.ibiko1.words.MeaningIndex.Sense> senses = words.senses(w);
        if (senses.isEmpty()) {
            return null;
        }
        String def = senses.get(0).definition.trim();
        String region = "";
        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("^\\(([^)]{2,40})\\)\\s*").matcher(def);
        if (tag.find()) {
            region = " (" + tag.group(1) + ")";
            def = def.substring(tag.end());
        }
        def = def.replaceAll("[.;]+$", "");
        String article = w.matches("[aeiou].*") ? "An " : "A ";
        boolean thing = senses.get(0).pos.equals("noun");
        String said = thing ? article + w + " is " + def + region + "."
                : "“" + w + "” means " + def + region + ".";
        if (senses.size() > 1 && !senses.get(1).definition.isEmpty()) {
            said += " It can also mean " + senses.get(1).definition.replaceAll("^\\([^)]*\\)\\s*", "").replaceAll("[.;]+$", "") + ".";
        }
        return said + " (From my dictionary.)";
    }

    /** Whether a word names a kind of person ("parent", "student"), from what it means in the dictionary. */
    boolean isPerson(String w) {
        ai.ibiko.ibiko1.words.Meanings words = ai.ibiko.ibiko1.words.Meanings.shared();
        if (words == null) {
            return false;
        }
        for (ai.ibiko.ibiko1.words.MeaningIndex.Sense s : words.senses(w)) {
            if (s.pos.equals("noun") && s.definition.matches("(?i).*\\b(a person|someone|one who|people|a member of|an adult|a child|a man or woman|an individual)\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private boolean siteTurn(final String who, final String t, ai.ibiko.ibiko1.formulas.Sites.Ask ask) {
        final ai.ibiko.ibiko1.formulas.Sites book = sites();
        if (book.size() == 0) {
            return false;
        }
        how = How.MEMORY;
        switch (ask.is) {
            case MADE_OF: {
                final String said = ai.ibiko.ibiko1.formulas.Sites.madeOf();
                think(Arrays.asList("Reading your question: what a website is made of"), () -> saysPlain(who, said));
                return true;
            }
            case EXPLAIN: {
                final ai.ibiko.ibiko1.formulas.Sites.Kind k = book.kind(ask.a);
                if (k == null) {
                    return false;
                }
                final String said = ai.ibiko.ibiko1.formulas.Sites.explain(k);
                think(Arrays.asList("Reading your question: a kind of website", "What I know of a " + k.name), () -> saysPlain(who, said));
                return true;
            }
            case COMPARE: {
                final ai.ibiko.ibiko1.formulas.Sites.Kind a = book.kind(ask.a), b = book.kind(ask.b);
                if (a == null || b == null || a == b) {
                    return false;
                }
                final String said = ai.ibiko.ibiko1.formulas.Sites.compare(a, b);
                think(Arrays.asList("Reading your question: two kinds of website", "What each is for, its sections, features and design"),
                        () -> saysPlain(who, said));
                return true;
            }
            default: {
                if (thinkThenBuild(who, t, book)) {
                    return true; // researched and thought through: Ibiko's own site, not a stored layout
                }
                ai.ibiko.ibiko1.formulas.Sites.Kind found = book.kind(t);
                final ai.ibiko.ibiko1.formulas.Sites.Kind k = found != null ? found : book.kind("business");
                if (k == null) {
                    return false;
                }
                final ai.ibiko.ibiko1.formulas.Sites.Brief brief = ai.ibiko.ibiko1.formulas.Sites.brief(t);
                final ai.ibiko.ibiko1.formulas.Sites.Design design = ai.ibiko.ibiko1.formulas.Sites.design(k, brief);
                lastDesign = design; // kept, so "add a pricing section" or "make it blue" changes this site
                lastMade = null;
                final String html = ai.ibiko.ibiko1.formulas.Sites.build(design);
                lastSnippet = new ai.ibiko.ibiko1.formulas.CodeReader.Snippet(html, "html", ""); // "what does line 40 do?"
                List<String> heads = new ArrayList<String>();
                for (String[] sec : k.sections) {
                    heads.add(sec[1]);
                }
                String name = brief.name.isEmpty() ? "your business" : brief.name;
                final String said = "Here's a " + k.name + " for " + name + (brief.place.isEmpty() ? "" : " in " + brief.place) + ": "
                        + String.join(" · ", heads) + ". It works on phones first, and its forms "
                        + (brief.phone.isEmpty() && brief.email.isEmpty() ? "need your WhatsApp number or email (tell me and I'll put it in)"
                        : "send straight to " + (brief.phone.isEmpty() ? "your email" : "your WhatsApp")) + ".\n\n```html\n" + html + "```\n\n"
                        + "Save it as index.html and open it in a browser. Change the words, prices and photos to your own. To put it online free: "
                        + "drag it onto Netlify Drop, or use GitHub Pages. For logins, payments or a database, choose a model and the coding agent can build on it.";
                think(Arrays.asList("Reading your request: a website to build", "It's a " + k.name + (found == null ? " (no kind named: a business site)" : ""),
                        "Taking the name, place and number from what you said", "Laying out its sections: " + String.join(", ", heads),
                        "Writing the page: HTML, CSS for phones first, and the script for the menu and forms"), () -> saysExact(who, said));
                return true;
            }
        }
    }

    private static String shownAll(List<String> langs) {
        List<String> out = new ArrayList<String>();
        for (String l : langs) {
            out.add(ai.ibiko.ibiko1.formulas.Recipes.shown(l));
        }
        return String.join(", ", out);
    }

    /** A fact from the encyclopedia's tables, as stored: {@code thing}'s {@code relation}, or null. */
    String fact(String thing, String relation) {
        try {
            Encyclopedia e = encyclopedia();
            Encyclopedia.Article a = e == null ? null : e.find(thing);
            return a == null ? null : e.facts(a.title).get(relation);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static final java.util.regex.Pattern CLAIM = java.util.regex.Pattern.compile("(?i)^\\s*(?:is|was)\\s+(.+?)\\s+the\\s+(capital|capital city|largest city|"
            + "currency|president|governor|official language|head of state)\\s+of\\s+(.+?)\\s*\\?\\s*$");

    /**
     * "Is Onitsha the capital of Anambra State?" checked against the built-in facts (Nigeria's states and their
     * capitals, the countries): yes or no first, then the fact. Null when there is no fact for it.
     */
    String claimChecked(String q) {
        java.util.regex.Matcher c = q == null ? null : CLAIM.matcher(q);
        if (c == null || !c.matches()) {
            return null;
        }
        String known = null;
        try {
            known = builtIn().answer("What is the " + c.group(2) + " of " + c.group(3).trim() + "?");
        } catch (RuntimeException e) {
            // no fact for it
        }
        if (known == null || known.trim().isEmpty()) {
            return null;
        }
        String a = c.group(1).trim();
        boolean yes = known.toLowerCase(Locale.ROOT).matches("(?s).*\\b" + java.util.regex.Pattern.quote(a.toLowerCase(Locale.ROOT)) + "\\b.*");
        return (yes ? "Yes. " : "No. ") + known.trim() + (yes ? "" : " " + a + " is not its " + c.group(2).toLowerCase(Locale.ROOT) + ".");
    }

    /** Counts of things the person said they have, kept through this conversation ({@code formulas/Tally}). */
    private final ai.ibiko.ibiko1.formulas.Tally tally = new ai.ibiko.ibiko1.formulas.Tally();

    /** What the message being answered asks, read with the conversation ({@link Understanding}); null before it is read. */
    private volatile Understanding understood;

    /** What the message being answered asks, read with the conversation; null when none is being answered. */
    public Understanding understood() {
        return understood;
    }

    /** The conversation so far, turn by turn (one per chat), to be talked about. */
    private final Conversation talk = new Conversation();
    /** What the message being answered asks, as understood. */
    private volatile String asking;

    /** The conversation so far, for a host that starts a new chat. */
    public Conversation conversation() {
        return talk;
    }

    /** Keeps the turn just answered: what was asked, what it was about, and what was said. */
    private static final java.util.regex.Pattern WHO_ASKED = java.util.regex.Pattern.compile("(?i)^\\s*who\\b(?!\\s+(?:am|are)\\s+(?:i|you)\\b)");
    private static final java.util.regex.Pattern FULL_NAME = java.util.regex.Pattern.compile("\\b([A-Z][a-z]+(?:\\s+[A-Z]\\.?)?(?:\\s+[A-Z][a-z]+){1,2})\\b");

    /** "Who wrote Things Fall Apart?" answered "Chinua Achebe": the person "he" means in the next question. */
    private void personInAnswer(String asked, String said) {
        if (asked == null || said == null || !WHO_ASKED.matcher(asked).find()) {
            return;
        }
        try {
            java.util.regex.Matcher m = FULL_NAME.matcher(said.length() > 400 ? said.substring(0, 400) : said);
            for (int tries = 0; m.find() && tries < 4; tries++) {
                String name = m.group(1);
                if (asked.contains(name)) {
                    continue; // the thing asked about ("Things Fall Apart"), not who did it
                }
                Encyclopedia.Article a = encyclopedia().find(name);
                Context.Kind kind = a == null ? null : Context.kindOf(a, encyclopedia().facts(a.title));
                if (kind == Context.Kind.MAN || kind == Context.Kind.WOMAN || kind == Context.Kind.PERSON) {
                    context.answerNamed(a.title, kind, System.currentTimeMillis());
                    return;
                }
            }
        } catch (RuntimeException e) {
            // no person read: "he" stays as it was
        }
    }

    private void kept(String said) {
        Context.Focus f = context.focus(System.currentTimeMillis());
        if (!reshaping && said != null && !said.trim().isEmpty()) {
            lastAnswer = said; // what "shorter" or "as points" will say again
            lastAnswerAbout = f == null ? null : f.name;
        }
        talk.add(asking, f == null ? null : f.name, said);
        personInAnswer(asking, said);
        // what this turn was about, for reading the next one with the conversation
        String domain = turnDomain;
        if (domain == null && asking != null) {
            int best = 0;
            for (java.util.Map.Entry<String, java.util.Set<String>> e : ai.ibiko.ibiko1.formulas.Readings.VOCAB.entrySet()) {
                int hits = 0;
                for (String w : asking.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9+#-]+")) {
                    hits += e.getValue().contains(w) ? 1 : 0;
                }
                if (hits > best) {
                    best = hits;
                    domain = e.getKey();
                }
            }
        }
        readingContext.said(domain);
        if (!"code".equals(domain)) {
            lastError = null; // "how do I fix it?" is about the error only while we're still on it
            lastErrorCode = null;
        }
        // a turn about code or websites makes computing the subject, as naming a computing word does: "what is a bug?"
        // next means the programming one
        String subject = domain == null ? null : SUBJECT_OF.get(domain);
        if (subject != null) {
            topic = subject;
            topicAt = System.currentTimeMillis();
        }
        turnDomain = null;
    }

    /** The word senses' subject that a reading's domain is. */
    private static final java.util.Map<String, String> SUBJECT_OF = new java.util.HashMap<String, String>();

    static {
        SUBJECT_OF.put("code", "computing");
        SUBJECT_OF.put("web", "computing");
    }

    /**
     * V1–V4: a question about the conversation itself, answered from it. "Go back to X" makes X the subject again (so
     * "he", "it" and "the capital" are X's) and says what was said about it last. False when the conversation has nothing
     * for it (a subject never talked about): the message goes on as any other.
     */
    private boolean talkTurn(String who, Conversation.Ask ask, long now) {
        final String reply;
        List<String> steps = new ArrayList<String>(Arrays.asList("Reading your message: it's about our conversation"));
        switch (ask.kind) {
            case RECAP:
                String recap = talk.recap();
                reply = recap == null ? "We haven't talked about anything yet in this chat. What's on your mind?" : recap;
                steps.add("Going back over what we said, subject by subject");
                break;
            case RECALL:
                String recalled = talk.recall(ask.subject);
                if (recalled == null) {
                    return false; // not talked about here: a question like any other ("what did you say about X" → about X)
                }
                reply = recalled;
                steps.add("Finding what I said about " + ask.subject);
                break;
            case RETURN:
                List<Conversation.Turn> before = talk.about(ask.subject);
                if (before.isEmpty()) {
                    return false;
                }
                String name = talk.named(ask.subject);
                Encyclopedia e = encyclopedia();
                Encyclopedia.Article a = e == null ? null : e.find(name);
                context.about("Tell me about " + name, a == null ? name : a.title, name,
                        a == null ? Context.Kind.THING : Context.kindOf(a, e.facts(a.title)), now);
                Conversation.Turn last = before.get(before.size() - 1);
                reply = "Back to " + name + ". Last time you asked “" + Conversation.clip(last.asked, 80) + "”, and I said: "
                        + Conversation.clip(last.said, 300) + "\n\nWhat would you like to know next about " + name + "?";
                steps.add("Making " + name + " what we're talking about again");
                break;
            case FIRST:
            case LAST:
                String q = talk.question(ask.kind == Conversation.Ask.Kind.FIRST);
                reply = q == null ? "You haven't asked me anything yet in this chat."
                        : (ask.kind == Conversation.Ask.Kind.FIRST ? "The first thing you asked was: “" : "The last thing you asked was: “") + q + "”";
                steps.add("Looking back through our conversation");
                break;
            default:
                return false;
        }
        how = How.MEMORY;
        think(steps, () -> saysPlain(who, reply));
        return true;
    }

    /** Something the person said against what it holds, waiting for "should I change it?"; null when nothing is. */
    private volatile Facts.Conflict disputed;

    /** A fact as said: "Ghana became independent on 6 March 1957", "the population of Lagos is 15,000,000". */
    static String shown(Grammar.Triple t) {
        if (t.relation.matches("population|length|elevation|area")) {
            return "the " + t.relation + " of " + t.subject + " is " + t.object;
        }
        if (t.surface != null && !t.surface.isEmpty() && t.surface.length() <= 160) {
            String s = t.surface.trim().replaceAll("[.!]+$", "");
            return s.matches("^(The|A|An|It|This|These|There)\\b.*") ? Character.toLowerCase(s.charAt(0)) + s.substring(1) : s;
        }
        return t.subject + " " + t.relation + " " + t.object;
    }

    /**
     * T4: the person's answer to "should I change it?": yes (or what they said, named again) makes theirs the fact and
     * drops what it had; no (or what it had, named) keeps it. Anything else isn't an answer: null, read as a new message.
     */
    String settle(Facts.Conflict c, String reply) {
        String low = reply.trim().toLowerCase(Locale.US);
        String held = c.held.object.toLowerCase(Locale.US), said = c.said.object.toLowerCase(Locale.US);
        boolean namesHeld = low.contains(held) && !low.contains(said), namesSaid = low.contains(said) && !low.contains(held);
        boolean yes = low.matches("^(yes|yeah|yep|yup|sure|ok|okay|please do|do it|change it|update it|go ahead|correct|right)\\b.*")
                || low.matches(".*\\b(i'?m right|i am right|mine is right|you'?re wrong|you are wrong|yours is wrong|trust me|i'?m sure|"
                + "i am sure|use mine|change it|update it)\\b.*");
        boolean no = low.matches("^(no|nope|nah|don'?t|do not|keep it|leave it)\\b.*")
                || low.matches(".*\\b(you'?re right|you are right|yours is right|i was wrong|i'?m wrong|i am wrong|my mistake|my bad|"
                + "keep yours|keep what you had|never ?mind)\\b.*");
        if (namesSaid || yes && !no && !namesHeld) {
            facts().settle(c.said, c.held, System.currentTimeMillis());
            learner().read(shown(c.said), Learner.Source.PILOT, System.currentTimeMillis());
            return "Updated: " + cap1(shown(c.said)) + ". I've dropped " + c.held.object + ".";
        }
        if (namesHeld || no) {
            facts().settle(c.held, c.said, System.currentTimeMillis());
            return "Okay, I'll keep what I had: " + shown(c.held) + ".";
        }
        return null;
    }

    private static String cap1(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The last reason given, for "why?" to go on from; null when the talk has moved on. */
    private volatile ai.ibiko.ibiko1.formulas.Causes.Link lastReason;

    /**
     * A why-question answered from cause and effect: first the reasons in its memory (read before, or told), then the
     * encyclopedia's article on what is asked about, read for its reasons (and kept). False when neither has a
     * reason for what is asked: the rest of Ibiko takes the question.
     */
    private boolean answerWhy(String who, String question) {
        List<ai.ibiko.ibiko1.formulas.Causes.Link> known = facts().causes();
        ai.ibiko.ibiko1.formulas.Causes.Why why = ai.ibiko.ibiko1.formulas.Causes.why(question, known);
        String title = null;
        if (why == null) {
            for (Encyclopedia.Article a : whyArticles(question)) {
                List<ai.ibiko.ibiko1.formulas.Causes.Link> read = new ArrayList<ai.ibiko.ibiko1.formulas.Causes.Link>(
                        ai.ibiko.ibiko1.formulas.Causes.read(a.text, a.title));
                if (read.isEmpty()) {
                    continue;
                }
                read.addAll(known);
                why = ai.ibiko.ibiko1.formulas.Causes.why(question, read);
                if (why != null) {
                    title = a.title;
                    facts().learn(a.text, a.title, 2, System.currentTimeMillis()); // what it read is kept
                    break;
                }
            }
        }
        if (why == null || knowledge().rejected(question, why.said())) {
            return false;
        }
        final ai.ibiko.ibiko1.formulas.Causes.Why found = why;
        final String source = title;
        how = How.MEMORY;
        List<String> steps = new ArrayList<String>();
        steps.add("Reading your question: it asks for a reason");
        steps.add(source == null ? "Looking through the reasons I've read and been told" : "Reading my encyclopedia's article on " + source + " for causes");
        steps.add("Found what it explains: " + found.link.effect);
        if (found.behind != null) {
            steps.add("Going one step further back: why " + Causes0.plain(found.link.cause));
        }
        think(steps, () -> {
            lastReason = found.last();
            if (source == null) {
                saysSure(who, found.said(), ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED,
                        "From what I've read and been told: " + found.link.sentence.trim());
            } else {
                saysSure(who, found.said(), ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                        "Source: Simple English Wikipedia, \u201c" + source + "\u201d");
            }
            rateable(question, found.said());
        });
        return true;
    }

    /**
     * A how-question answered from the ways it knows (read before, or told), then from the encyclopedia's article on
     * the thing asked about (read for how it is made or works, and kept). False when neither says.
     */
    private boolean answerHow(String who, String question) {
        List<ai.ibiko.ibiko1.formulas.Process.Way> known = facts().ways();
        ai.ibiko.ibiko1.formulas.Process.Way way = ai.ibiko.ibiko1.formulas.Process.how(question, known);
        String title = null;
        if (way == null) {
            for (Encyclopedia.Article a : whyArticles(question)) {
                List<ai.ibiko.ibiko1.formulas.Process.Way> read = new ArrayList<ai.ibiko.ibiko1.formulas.Process.Way>(
                        ai.ibiko.ibiko1.formulas.Process.read(a.text, a.title));
                if (read.isEmpty()) {
                    continue;
                }
                way = ai.ibiko.ibiko1.formulas.Process.how(question, read);
                if (way != null) {
                    title = a.title;
                    facts().learn(a.text, a.title, 2, System.currentTimeMillis()); // what it read is kept
                    break;
                }
            }
        }
        if (way == null || knowledge().rejected(question, way.said())) {
            return false;
        }
        final ai.ibiko.ibiko1.formulas.Process.Way found = way;
        final String source = title;
        how = How.MEMORY;
        List<String> steps = new ArrayList<String>();
        steps.add("Reading your question: it asks how");
        steps.add(source == null ? "Looking through the ways I've read and been told" : "Reading my encyclopedia's article on " + source + " for how");
        steps.add(found.steps.isEmpty() ? "Found how " + Causes0.plain(found.thing) + " is " + found.doing : "Found the steps, in order");
        think(steps, () -> {
            if (source == null) {
                saysSure(who, found.said(), ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED, "From what I've read and been told");
            } else {
                saysSure(who, found.said(), ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                        "Source: Simple English Wikipedia, \u201c" + source + "\u201d");
            }
            rateable(question, found.said());
        });
        return true;
    }

    /**
     * K8–K9: what something leads to, from the causes in memory, then from the encyclopedia's article on it (read and
     * kept). False when nothing read starts from it: no outcome is guessed.
     */
    private boolean answerThen(String who, String question) {
        String from = ai.ibiko.ibiko1.formulas.Causes.ahead(question);
        List<ai.ibiko.ibiko1.formulas.Causes.Link> known = facts().causes();
        ai.ibiko.ibiko1.formulas.Causes.Then then = ai.ibiko.ibiko1.formulas.Causes.then(question, known);
        String title = null;
        if (then == null) {
            for (Encyclopedia.Article a : whyArticles("why " + from)) {
                List<ai.ibiko.ibiko1.formulas.Causes.Link> read = new ArrayList<ai.ibiko.ibiko1.formulas.Causes.Link>(
                        ai.ibiko.ibiko1.formulas.Causes.read(a.text, a.title));
                if (read.isEmpty()) {
                    continue;
                }
                read.addAll(known);
                then = ai.ibiko.ibiko1.formulas.Causes.then(question, read);
                if (then != null) {
                    title = a.title;
                    facts().learn(a.text, a.title, 2, System.currentTimeMillis()); // what it read is kept
                    break;
                }
            }
        }
        if (then == null || knowledge().rejected(question, then.said())) {
            return false;
        }
        final ai.ibiko.ibiko1.formulas.Causes.Then found = then;
        final String source = title;
        how = How.MEMORY;
        List<String> steps = new ArrayList<String>();
        steps.add("Reading your question: it asks what " + Causes0.plain(from) + " leads to");
        steps.add(source == null ? "Following the causes I've read and been told, forward" : "Reading my encyclopedia's article on " + source + " for what follows");
        steps.add("Found it: " + Causes0.plain(found.link.sentence.trim()));
        if (found.next != null) {
            steps.add("One step further: " + Causes0.plain(found.next.effect));
        }
        think(steps, () -> {
            saysSure(who, found.said(), source == null ? ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED : ai.ibiko.ibiko1.formulas.Critical.Ground.BUILT_IN,
                    source == null ? "From what I've read and been told" : "Source: Simple English Wikipedia, \u201c" + source + "\u201d");
            rateable(question, found.said());
        });
        return true;
    }

    /** Small helpers for saying a reason in a step. */
    private static final class Causes0 {
        static String plain(String s) {
            return s.length() > 80 ? s.substring(0, 77) + "…" : s;
        }
    }

    /** The articles a why-question may be about: the whole thing asked, then shorter, then its single longer words. */
    private List<Encyclopedia.Article> whyArticles(String question) {
        List<Encyclopedia.Article> out = new ArrayList<Encyclopedia.Article>();
        Encyclopedia e = encyclopedia();
        String about = ai.ibiko.ibiko1.formulas.Asking.of(question).about;
        if (e == null || about.isEmpty()) {
            return out;
        }
        List<String> tries = new ArrayList<String>();
        String[] w = about.split("\\s+");
        for (int n = w.length; n >= 1; n--) {
            for (int i = 0; i + n <= w.length; i++) {
                String phrase = String.join(" ", Arrays.copyOfRange(w, i, i + n));
                if ((n > 1 || phrase.length() > 3) && !tries.contains(phrase)) {
                    tries.add(phrase);
                }
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String phrase : tries) {
            Encyclopedia.Article a;
            try {
                a = e.find(phrase);
            } catch (RuntimeException ex) {
                a = null;
            }
            if (a != null && seen.add(a.title)) {
                out.add(a);
                if (out.size() >= 3) {
                    break;
                }
            }
        }
        return out;
    }

    /**
     * "why?" after a reason: the reason behind it, from memory or the article the reason came from. Where what it has
     * read ends, it says so and asks: the answer it gets is kept as a reason, for next time.
     */
    private void whyFurther(String who, ai.ibiko.ibiko1.formulas.Causes.Link from) {
        times = 0; // "why?" again is about the new reason, not the same question asked twice
        List<ai.ibiko.ibiko1.formulas.Causes.Link> links = new ArrayList<ai.ibiko.ibiko1.formulas.Causes.Link>(facts().causes());
        ai.ibiko.ibiko1.formulas.Causes.Link behind = ai.ibiko.ibiko1.formulas.Causes.behind(from, links);
        if (behind == null) {
            for (Encyclopedia.Article a : whyArticles("why " + from.cause)) {
                links.addAll(ai.ibiko.ibiko1.formulas.Causes.read(a.text, a.title));
                behind = ai.ibiko.ibiko1.formulas.Causes.behind(from, links);
                if (behind != null) {
                    facts().learn(a.text, a.title, 2, System.currentTimeMillis());
                    break;
                }
            }
        }
        if (behind != null) {
            final ai.ibiko.ibiko1.formulas.Causes.Link b = behind;
            final String said = Character.toUpperCase(from.cause.charAt(0)) + from.cause.substring(1) + " " + b.because() + ".";
            think(Arrays.asList("Reading your question: why, one step further back", "Looking for what caused " + Causes0.plain(from.cause),
                    "Found it"), () -> {
                        lastReason = b;
                        saysSure(who, said, ai.ibiko.ibiko1.formulas.Critical.Ground.REASONED, "From: " + b.sentence.trim());
                    });
            return;
        }
        // the end of what it has read: it says so, and asks (the answer is kept as the reason behind it)
        String q = from.noun() ? "What caused " + lowerStart(from.cause) + "?" : "Why " + lowerStart(from.cause) + "?";
        gap = Curiosity.open(q, System.currentTimeMillis());
        final String end = "That's as far back as what I've read goes: I don't know what's behind " + lowerStart(from.cause)
                + " yet. Do you know? Tell me, and I'll keep it as the reason, so next time I can go a step further.";
        think(Arrays.asList("Reading your question: why, one step further back", "Looking for what caused " + Causes0.plain(from.cause),
                "Nothing I've read explains it"), () -> saysPlain(who, end));
    }

    private static String lowerStart(String s) {
        String f = s.split("\\s+")[0];
        return f.matches("(?i)the|a|an|it|this|that|these|those|its|their|his|her|there|many|most|some|people|they")
                ? Character.toLowerCase(s.charAt(0)) + s.substring(1) : s;
    }

    /** A filled gap kept everywhere a confirmed answer goes: memory, the fact store, and the language it learns from. */
    private void keepFilled(String question, String statement) {
        long now = System.currentTimeMillis();
        // the person's own answer: a correction, which supersedes what was held about it (kept, with its history)
        learningRecords().learn(ai.ibiko.ibiko1.cognition.LearningRecords.Category.correction, question, statement, "explicit_user_correction",
                "conversation", 0.95, now, false, "correction");
        knowledge().confirm(question, statement);
        facts().learn(statement, null, 2, now);
        learner().read(statement, Learner.Source.PILOT, now);
        host.learnTalk(question, statement);
    }

    private void says(String who, String reply) {
        String lead = leadIn;
        leadIn = null;
        String drafted = lead == null ? reply : lead + " " + reply; // "Got it, you're a man. I'm Ibiko, …"
        // the inner voice: drafted, read back against the grammar rules, redrafted, confirmed, then said
        ai.ibiko.ibiko1.words.InnerVoice.Said checked = ai.ibiko.ibiko1.words.InnerVoice.confirm(drafted);
        String text = checked.text;
        if (checked.step() != null) {
            host.step(checked.step());
        }
        // passes 2 and 3 (cognition/Metacognition): its working done again, its consistency and its causes checked,
        // and only a concrete defect put right; a clean answer is left exactly as it is
        String asked = asking != null ? asking : current;
        if (asked != null && text != null && !text.isEmpty()) {
            try {
                ai.ibiko.ibiko1.cognition.Cognition.Review r = ai.ibiko.ibiko1.cognition.Cognition.review(asked, text, null, null,
                        how == How.MEMORY ? 0.9 : how == How.RESEARCH ? 0.75 : 0.85, calibration(), true);
                labelled.put(answerKey(r.text), r.label);
                if (!r.text.equals(text)) {
                    host.step("Checked it before saying it, and put right: " + kindsPutRight(r.outcome));
                    text = r.text;
                }
            } catch (RuntimeException e) {
                // the review failed: the answer stands as drafted
            }
        }
        String all = (lastSaid == null ? "" : lastSaid + "\n") + text;
        lastSaid = all.length() > 6000 ? all.substring(all.length() - 6000) : all; // what was said lately
        kept(text);
        host.stepsDone();
        if (worker != null) {
            Plug.workers.end(worker, true);
            worker = null;
        }
        host.say(who, text);
        answeredNow(how);
    }

    private static String kindsPutRight(ai.ibiko.ibiko1.cognition.Metacognition.Outcome o) {
        java.util.List<String> done = new java.util.ArrayList<String>();
        for (String l : o.log) {
            if (l.contains("put right ")) {
                done.add(l.replaceFirst("^.*put right ", ""));
            }
        }
        return done.isEmpty() ? "what didn't hold" : String.join("; ", done);
    }

    /** The text the person pasted last, for questions about it ({@link Passage}). */
    private volatile Passage passage;

    /** A question about the person asking it ("what's my…", "where do I…", "am I…"), not about the world. */
    static boolean aboutThePerson(String t) {
        String q = Paraphrase.normalize(t);
        // "can I …" and "should I …" ask leave or advice ("can I ask you something?", "what can I do with you?"), not about the person
        return EncyclopediaQA.question(q) && (q.matches(".*\\bmy\\b.*") || q.matches("^(where|what|who|when|how|why|which) (am|do|did|was|will) i\\b.*")
                || q.matches("^(am|do|did|was) i\\b.*")) && !q.matches(".*\\b(tell|show|give|teach|help|let) me\\b.*")
                && !q.matches(".*\\bmy (own )?(code|file|files|phone|app|chat|message|messages|question)\\b.*");
    }

    /** What was said lately (the last few thousand characters), so "tell me more" goes on from it. */
    private volatile String lastSaid;

    /** Labels for an article's facts, in the order "tell me more" says them. */
    private static final String[][] FACT_LABELS = {{"born", "Born"}, {"birth_place", "Born in"}, {"died", "Died"},
            {"death_place", "Died in"}, {"occupation", "Work"}, {"known_for", "Known for"}, {"office", "Office"}, {"party", "Party"},
            {"spouse", "Married to"}, {"children", "Children"}, {"education", "Studied at"}, {"genre", "Genre"},
            {"capital", "Capital"}, {"largest_city", "Largest city"}, {"population", "Population"}, {"area", "Area"},
            {"language", "Language"}, {"currency", "Currency"}, {"government", "Government"}, {"leader", "Leader"},
            {"founded", "Founded"}, {"founder", "Founded by"}, {"headquarters", "Headquarters"}, {"key_people", "Key people"},
            {"industry", "Industry"}, {"products", "Products"}, {"employees", "Employees"}, {"author", "Author"},
            {"writer", "Writer"}, {"director", "Director"}, {"starring", "Starring"}, {"released", "Released"},
            {"developer", "Developer"}, {"publisher", "Publisher"}, {"location", "Location"}, {"elevation", "Height"},
            {"length_km", "Length"}, {"country", "Country"}};

    /**
     * More about a subject than was just said: the article's sentences not said yet (a few), or when it was all said,
     * the article's facts as a short list. Null when there is nothing more.
     */
    String more(String subject, String said) {
        Encyclopedia.Article a = encyclopedia().find(subject);
        if (a == null) {
            return null;
        }
        String already = said == null ? "" : said;
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String sentence : a.text.split("(?<=[.!?])\\s+(?=[A-Z\"\u201c(])")) {
            if (!already.contains(sentence.trim()) && n < 3) {
                b.append(b.length() == 0 ? "" : " ").append(sentence.trim());
                n++;
            }
        }
        if (b.length() > 0) {
            return b.toString();
        }
        java.util.Map<String, String> facts = encyclopedia().facts(a.title);
        StringBuilder list = new StringBuilder();
        for (String[] l : FACT_LABELS) {
            String v = facts.get(l[0]);
            if (v == null || v.isEmpty() || already.contains(v)) {
                continue;
            }
            if (l[0].equals("population") && facts.containsKey("population_year")) {
                v += " (" + facts.get("population_year") + ")";
            }
            if (l[0].equals("leader") && facts.containsKey("leader_title")) {
                v += " (" + facts.get("leader_title") + ")";
            }
            list.append("\n\u2022 ").append(l[1]).append(": ").append(v);
        }
        return list.length() == 0 ? null : "More about " + a.title.replaceFirst("\\s*\\([^()]+\\)$", "") + ":" + list;
    }

    /** What the person said about themselves, said before the answer to the rest of their message. */
    private volatile String leadIn;
    /** The person Ibiko talks with, as they described themselves. */
    private Person person;
    /** The message before this one, for "what did I just ask?". */
    private volatile String previous, current;

    synchronized Person person() {
        if (person == null) {
            person = new Person(dir);
        }
        return person;
    }

    /** What Ibiko can tell about itself right now, for its picture of itself ({@link SelfModel}). */
    private SelfModel.State selfState() {
        final long now = System.currentTimeMillis();
        return new SelfModel.State() {
            @Override public boolean online() { return host.online(); }
            @Override public boolean modelReady() { return host.modelReady(); }
            @Override public int words() { return lexicon().size(); }
            @Override public int factsRead() { return facts().size(); }
            @Override public int confirmed() { return knowledge().confirmed().size(); }
            @Override public String focus() {
                Context.Focus f = context.focus(now);
                return f == null ? null : f.name;
            }
            @Override public String pasted() {
                Passage p = passage;
                return p != null && p.fresh(now) ? p.about() : null;
            }
            @Override public int asked() { return askedHere.size(); }
            @Override public String lastQuestion() { return previous; }
            @Override public String personName() { return person().name() != null ? person().name() : host.pilotName(); }
        };
    }

    /** The questions asked in this conversation (one brain per chat), for "as I mentioned a moment ago". */
    private final java.util.Map<String, Integer> askedHere = Phrasing.conversation();

    /**
     * Retry redoes the reply to {@code message} in its place: the old reply is gone, so the question isn't "asked
     * again" ("As I mentioned a moment ago" would be wrong), and the last answer may be tried a different way.
     */
    public void retrying(String message) {
        if (message != null) {
            Phrasing.forget(askedHere, message.trim());
        }
    }

    /** What the conversation is about (per brain: one chat). */
    private final Context context = new Context();

    {
        context.peers = this::peers; // "what about Ghana?" (a country, like Nigeria) or "what about oil?" (oil in Nigeria)
    }

    /**
     * Whether two things are of a kind, read from what the encyclopedia says each is: the same kind of thing (a person,
     * a place) or a kind word in common ("country"). True when either isn't found: it can't tell, and the last question
     * is asked again about the new one, as before.
     */
    boolean peers(String a, String b) {
        try {
            Encyclopedia e = encyclopedia();
            Encyclopedia.Article x = e == null ? null : e.find(a), y = e == null ? null : e.find(b);
            if (x == null || y == null) {
                return true;
            }
            Context.Kind kx = Context.kindOf(x, e.facts(x.title)), ky = Context.kindOf(y, e.facts(y.title));
            if (kx == ky && kx != Context.Kind.THING) {
                return true;
            }
            java.util.Set<String> hx = new java.util.HashSet<String>(), hy = new java.util.HashSet<String>();
            for (ai.ibiko.ibiko1.formulas.Contrast.Kind k : ai.ibiko.ibiko1.formulas.Contrast.kinds(x.text, x.title)) {
                hx.add(ai.ibiko.ibiko1.formulas.Causes.stem(k.head));
            }
            for (ai.ibiko.ibiko1.formulas.Contrast.Kind k : ai.ibiko.ibiko1.formulas.Contrast.kinds(y.text, y.title)) {
                hy.add(ai.ibiko.ibiko1.formulas.Causes.stem(k.head));
            }
            if (hx.isEmpty() || hy.isEmpty()) {
                return kx == ky;
            }
            hx.retainAll(hy);
            return !hx.isEmpty();
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /**
     * What links {@code term} to {@code topic}: the sentences of what it has read about the topic (its article, the
     * facts it keeps about it) that speak of the term. "What about oil?" while talking about Nigeria finds "Nigeria
     * is a major oil producer…". Null when nothing it has read links them.
     */
    String linked(String topic, String term) {
        java.util.Set<String> want = ai.ibiko.ibiko1.formulas.Causes.words(term);
        if (want.isEmpty()) {
            return null;
        }
        List<String> found = new ArrayList<String>();
        Encyclopedia e = encyclopedia();
        Encyclopedia.Article a = e == null ? null : e.find(topic);
        List<String> sentences = new ArrayList<String>();
        if (a != null) {
            sentences.addAll(Arrays.asList(a.text.split("(?<=[.!?])\\s+(?=[A-Z\"“(])")));
        }
        for (Grammar.Triple f : facts().all()) {
            if (f.surface != null && Facts.same(f.subject, topic)) {
                sentences.add(f.surface);
            }
        }
        for (String sentence : sentences) {
            java.util.Set<String> has = ai.ibiko.ibiko1.formulas.Causes.words(sentence);
            if (has.containsAll(want) && !found.contains(sentence.trim())) {
                found.add(sentence.trim());
                if (found.size() >= 3) {
                    break;
                }
            }
        }
        return found.isEmpty() ? null : String.join(" ", found);
    }
    /** The steps that say how the message was read: the context put in, and its roles. */
    private volatile String contextStep, rolesStep;

    /** The conversation's context, for a host that starts a new chat ({@link Context#clear}). */
    public Context context() {
        return context;
    }

    /** "Reading who does what: subject ? · action “invent” · object “the telephone”", for a who-did-what question. */
    private static String roleStep(String t) {
        Roles.Reading r = Roles.question(t);
        return r == null ? null : "Reading who does what: " + r.said();
    }

    /**
     * Remembers what a question is about, for the next one: the article its words name (a name before a common word),
     * the words that named it, and its kind (he, she, it, they, a place).
     */
    private void noteFocus(String t, long now) {
        if (t == null || SelfTalk.toMe(t) || t.matches("(?i).*\\b(we|us|our)\\b.*") || !EncyclopediaQA.question(t) && !t.matches("(?i)^(tell me|explain|describe)\\b.*")) {
            return;
        }
        Passage held = passage;
        if (held != null && held.fresh(now)) {
            for (String n : held.names) {
                if (t.toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT))) {
                    return; // a question about the pasted text: its names stay what the conversation is about
                }
            }
        }
        try {
            java.util.List<Object[]> subs = new EncyclopediaQA(encyclopedia()).subjects(t, 1);
            if (subs.isEmpty()) {
                return;
            }
            Encyclopedia.Article a = (Encyclopedia.Article) subs.get(0)[0];
            @SuppressWarnings("unchecked")
            java.util.Set<String> named = (java.util.Set<String>) subs.get(0)[1];
            if ((Double) subs.get(0)[2] < 10 && named.size() == 1
                    && named.iterator().next().matches("born|died|dead|live|lived|work|worked|start|started|name|called|made|used|year|time")) {
                return; // a word of the question ("born"), not what it is about
            }
            @SuppressWarnings("unchecked")
            java.util.Set<String> words = (java.util.Set<String>) subs.get(0)[1];
            context.about(t, a.title, run(t, words), Context.kindOf(a, encyclopedia().facts(a.title)), now);
        } catch (RuntimeException e) {
            // a question it can't place leaves the context as it was
        }
    }

    /** The words of {@code t} that make up {@code words}, as written ("Nigeria" in "the capital of Nigeria?"). */
    static String run(String t, java.util.Set<String> words) {
        String[] raw = t.replaceAll("[?!.,;:\"\u201c\u201d]", " ").trim().split("\\s+");
        int first = -1, last = -1;
        for (int i = 0; i < raw.length; i++) {
            String w = raw[i].replaceAll("'s$|\u2019s$", "").toLowerCase(Locale.ROOT);
            if (words.contains(w)) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first < 0) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        for (int i = first; i <= last; i++) {
            b.append(b.length() == 0 ? "" : " ").append(raw[i].replaceAll("'s$|\u2019s$", ""));
        }
        return b.toString();
    }

    /** Questions about Ibiko v1 itself: what it learned, what it knows, who it is. Null for anything else. */
    public String aboutMe(String t) {
        String q = t.toLowerCase(Locale.US);
        // "what do you know about shoes" is a question about shoes, not about its memory
        // and "what do you know of coca-cola?", "…on football", "…regarding taxes": questions about those
        String on = "(?!.*\\b(?:about|of|on|regarding|concerning|re)\\b)";
        if (q.matches("(?s).*\\bwhat (have|did) you (learn|learned|learnt)\\b" + on + ".*"
                + "|.*\\bwhat do you (know|remember)\\b" + on + ".*|.*\\bwhat have you (remembered|saved|kept)\\b" + on + ".*")) {
            List<Knowledge.Fact> sure = knowledge().confirmed();
            List<String> me = host.aboutPilot();
            StringBuilder b = new StringBuilder();
            b.append(sure.isEmpty() ? "No answers confirmed yet: tap the check under an answer that's right and I keep it."
                    : "Answers you confirmed (" + sure.size() + (sure.size() > 8 ? ", the latest 8" : "") + "):");
            for (int i = sure.size() - 1, n = 0; i >= 0 && n < 8; i--, n++) {
                Knowledge.Fact f = sure.get(i);
                String a = f.answer.replaceAll("\\s+", " ");
                b.append("\n- ").append(f.question).append(" → ").append(a.length() > 90 ? a.substring(0, 90) + "…" : a);
            }
            if (!me.isEmpty()) {
                b.append("\nAbout you: ").append(String.join("; ", me)).append('.');
            }
            b.append("\nRead and understood: ").append(facts().size()).append(facts().size() == 1 ? " fact, " : " facts, ")
                    .append(lexicon().size()).append(lexicon().size() == 1 ? " word." : " words.");
            b.append("\n").append(learner().scoreLine()).append('.');
            int talk = host.talkSize(), shapes = host.shapes();
            if (talk + shapes > 0) {
                b.append("\nFrom the models you used: ").append(talk).append(talk == 1 ? " answer" : " answers").append(" and ")
                        .append(shapes).append(shapes == 1 ? " action shape." : " action shapes.");
            }
            return b.toString();
        }
        return null;
    }

    /**
     * Whether a message, said right after an answer was marked, is the right answer to that question (and so replaces
     * it): it says so ("no, it's…", "actually…", "the answer is…"), or it is about the same thing (it shares a word with
     * the question), or it is a short name-like answer to a who/what/where question. A request ("sing for me"), a
     * question, or something about the person themselves is not an answer, and never replaces one.
     */
    static boolean answers(String question, String said) {
        String s = Paraphrase.normalize(said), q = Paraphrase.normalize(question);
        if (s.isEmpty() || said.trim().endsWith("?") || EncyclopediaQA.question(s)) {
            return false;
        }
        if (s.matches("^(no|nope|wrong|actually|that's wrong|that is wrong|incorrect|not quite|the answer is|the right answer is|"
                + "the correct answer is|it should be|it's|it is|they are|she is|he is|the)\\b.*")) {
            return true;
        }
        if (s.matches("^(i|i am|my|me|call me|please|can you|could you|will you|let's|lets)\\b.*")
                || ai.ibiko.ibiko1.words.Words.STOP.contains(s.split(" ")[0]) && !s.startsWith("the ")) {
            return false; // about the person, a request, or a remark
        }
        java.util.Set<String> qs = new java.util.HashSet<String>(ai.ibiko.ibiko1.words.Words.content(q));
        for (String w : ai.ibiko.ibiko1.words.Words.content(s)) {
            if (qs.contains(w)) {
                return true; // about the same thing
            }
        }
        // "Who wrote Hamlet?" … "Shakespeare": a short answer of names or numbers, not an order ("sing for me")
        return q.matches("^(who|what|where|when|which|how many|how much)\\b.*") && s.split(" ").length <= 5
                && !s.matches("^(sing|dance|write|tell|show|give|make|play|draw|read|stop|go|come|help|find|open|send|call|cook)\\b.*");
    }

    /**
     * A statement: right after an answer it asked about, it is taken as the right answer to that question; "a pig is
     * an animal" on its own is kept as what a pig is. Nothing is searched.
     */
    public String learnStatement(String t) {
        String said = t.trim();
        if (ratingQuestion != null && host.ratingWaiting() && answers(ratingQuestion, said) && !aboutSomethingElse(ratingQuestion, said)) {
            String q = ratingQuestion;
            ratingQuestion = null;
            host.clearRating();
            // not kept as typed: the answer is taken out of the reply and put into a proper sentence
            String given = Curiosity.no(said) && Curiosity.instead(said) != null ? Curiosity.instead(said) : said;
            String cleaned = Curiosity.clean(given);
            String formed = cleaned.isEmpty() ? said : Curiosity.form(q, cleaned);
            knowledge().confirm(q, formed);
            host.learnTalk(q, formed);
            return "Got it. When you ask “" + q + "”, I'll answer: " + formed;
        }
        // talk before the statement ("Well, ", "So ", "Oh ") is not part of what it says
        said = said.replaceFirst("(?i)^(well|so|oh|um+|hmm+|ok|okay|yes|no|and|but|like|now)\\b[,!.]?\\s+(?=\\S)", "");
        if (said.isEmpty()) {
            return "Noted.";
        }
        said = Character.toUpperCase(said.charAt(0)) + said.substring(1);
        // said against what it holds sure ("Ghana became independent in 1958" when it read 1957): not taken or dropped
        // quietly; it says what it has, and asks
        if (!said.matches("(?i)^(today|tomorrow|yesterday|i|we|my|our|you)\\b.*")) {
            List<Facts.Conflict> against = facts().conflicts(said, null);
            if (!against.isEmpty()) {
                Facts.Conflict c = against.get(0);
                disputed = c;
                return "That's not what I have. I " + (c.strength >= 3 ? "have read more than once" : "read") + " that " + shown(c.held)
                        + ", but you say " + shown(c.said) + ". Should I change it to what you said? (Yes changes it; no keeps what I had.)";
            }
        }
        // something that happened, with its date ("Ghana became independent on 6 March 1957"): understood and kept, to
        // reason with ("how old was Achebe when Ghana became independent?")
        List<ai.ibiko.ibiko1.formulas.Happenings.Happening> happened = ai.ibiko.ibiko1.formulas.Happenings.read(said, null);
        if (!happened.isEmpty() && !said.matches("(?i)^(today|tomorrow|yesterday|i|we|my|our)\\b.*")) {
            facts().learn(said, null, 2, System.currentTimeMillis()); // the pilot's own words: as good as a source
            List<String> got = new ArrayList<String>();
            for (ai.ibiko.ibiko1.formulas.Happenings.Happening h : happened) {
                got.add(h.clause() + " " + h.when.on());
            }
            return "Noted: " + String.join("; ", got) + ". I'll use it when I work things out.";
        }
        // a way ("Glass is made by melting sand", "First …, then …"): understood as how it is done and kept, for how-questions
        List<ai.ibiko.ibiko1.formulas.Process.Way> ways = ai.ibiko.ibiko1.formulas.Process.read(said, null);
        if (!ways.isEmpty() && !said.matches("(?i)^(today|tomorrow|yesterday|i|we|my|our|you)\\b.*")) {
            facts().learn(said, null, 2, System.currentTimeMillis()); // the pilot's own words: as good as a source
            ai.ibiko.ibiko1.formulas.Process.Way w = ways.get(0);
            return "Noted: how " + w.thing + (w.steps.isEmpty() ? " is " + w.doing : " is done, in " + w.steps.size() + " steps")
                    + ". I'll use it when I'm asked how.";
        }
        // a reason ("Plants are green because of chlorophyll"): understood as cause and effect and kept, for why-questions
        List<ai.ibiko.ibiko1.formulas.Causes.Link> reasons = ai.ibiko.ibiko1.formulas.Causes.read(said, null);
        if (!reasons.isEmpty() && !said.matches("(?i)^(today|tomorrow|yesterday|i|we|my|our|you)\\b.*")) {
            facts().learn(said, null, 2, System.currentTimeMillis()); // the pilot's own words: as good as a source
            List<String> got = new ArrayList<String>();
            for (ai.ibiko.ibiko1.formulas.Causes.Link l : reasons) {
                got.add(l.effect + " " + l.because());
            }
            return "Noted: " + String.join("; ", got) + ". I'll use it when I'm asked why.";
        }
        // how many, how long, how high, how big ("Lagos has about 15 million people"): kept as a measure, to compare
        List<ai.ibiko.ibiko1.formulas.Measures.Measure> measured = ai.ibiko.ibiko1.formulas.Measures.read(said, null);
        if (!measured.isEmpty() && !said.matches("(?i)^(today|tomorrow|yesterday|i|we|my|our|you)\\b.*")) {
            facts().learn(said, null, 2, System.currentTimeMillis()); // the pilot's own words: as good as a source
            List<String> got = new ArrayList<String>();
            for (ai.ibiko.ibiko1.formulas.Measures.Measure ms : measured) {
                got.add("the " + ms.what + " of " + ms.subject + " is " + ms.said());
            }
            return "Noted: " + String.join("; ", got) + ". I'll use it when I compare and rank things.";
        }
        Matcher m = Pattern.compile("(?i)^(?:(the|a|an) )?(.{2,60}?) (is|are|was|were) (.{2,300}?)[.!]*$").matcher(said);
        if (m.matches() && m.group(2).matches("(?i)(today|tomorrow|yesterday|tonight|now|it|this|that|he|she|they|there|here)\\b.*")) {
            // "today is Christmas day", "it is cold": true now, or about something only the chat knows; not kept as a fact
            return "Noted.";
        }
        if (m.matches()) {
            String subject = (m.group(1) == null ? "" : m.group(1) + " ") + m.group(2);
            String verb = m.group(3).toLowerCase(Locale.US);
            String differs = questionReply(said, "pilot"); // G8: the pilot's word is kept, and a difference is said
            said = Curiosity.tidy(said); // one clean sentence, not the message as typed
            knowledge().confirm("What " + verb + " " + subject + "?", said);
            facts().learn(said, null, 2, System.currentTimeMillis()); // the pilot's own words: as good as a source
            // a name ("Abuja", "Ngozi Okonjo-Iweala") is kept for "who" too; every word starts with a capital, not only the
            // first one, which any sentence has ("Easter rice is sweet" is a thing)
            if (m.group(1) == null && m.group(2).matches("\\p{Lu}\\S*( \\p{Lu}\\S*)*")) {
                knowledge().confirm("Who " + verb + " " + subject + "?", said); // a name: kept for "who" too
            }
            return "Noted. Ask me what " + verb + " " + subject + " and I'll remember."
                    + (differs == null ? "" : " One thing, though: the built-in reference says otherwise. "
                            + differs.replaceFirst("^A correction to what I just said: ", "") + " I'll go with yours unless you say not to.");
        }
        return "Noted.";
    }

    /** A message it couldn't answer because something inside it failed: said plainly, and logged, instead of a crash. */
    void broke(Throwable e) {
        try {
            android.util.Log.e("Ibiko1", "answer failed", e);
        } catch (RuntimeException | LinkageError ignored) {
            // no Android log (a plain JVM): the reply still says it
        }
        try {
            saysPlain(host.persona(), "Something inside me failed while I was reading that (" + e.getClass().getSimpleName()
                    + "), so I couldn't answer it. Please try again, or ask it another way.");
        } catch (RuntimeException | LinkageError ignored) {
            // nothing more it can do
        }
    }

    /**
     * A statement about something the question didn't ask about ("Ghana became independent on …" after a question on
     * Nigeria): new knowledge, not the answer to that question.
     */
    static boolean aboutSomethingElse(String question, String statement) {
        List<ai.ibiko.ibiko1.formulas.Happenings.Happening> h = ai.ibiko.ibiko1.formulas.Happenings.read(statement, null);
        if (h.isEmpty()) {
            return false;
        }
        String q = question.toLowerCase(Locale.US);
        for (ai.ibiko.ibiko1.formulas.Happenings.Happening x : h) {
            for (String w : x.subject.toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
                if (w.length() > 2 && !w.matches("the|and") && q.contains(w)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The pilot marked an answer (from any model): right keeps it for its intent, wrong marks it so it is never given
     * again and keeps the question for the next learning session.
     */
    public void rate(String question, String answer, boolean right) {
        if (question == null || answer == null) {
            return;
        }
        learner().judged(right, System.currentTimeMillis());
        methods().judge(question, right); // the way that reached it, too
        try {
            ai.ibiko.ibiko1.cognition.Calibration.Label was = labelled.remove(answerKey(answer));
            calibration().outcome(was, right); // how sure it sounded, against how it turned out
            if (right) {
                learningRecords().learn(ai.ibiko.ibiko1.cognition.LearningRecords.Category.fact, question, answer, "user_confirmation", "rating",
                        0.9, System.currentTimeMillis(), false, "rated");
            } else {
                learningRecords().dispute(answer);
            }
        } catch (RuntimeException e) {
            // the rating still counts below
        }
        reflection().judged(Methods.kind(question), right); // and the record of how replies like it go
        String fixed = pendingErrorFix;
        if (fixed != null && answer.contains("(not confirmed yet)")) {
            pendingErrorFix = null;
            if (right) {
                errors().confirm(fixed);
            } else {
                errors().wrong(fixed);
            }
        }
        String[] recipe = lastRecipe;
        if (recipe != null && answer.contains("```")) {
            // a recipe it found: kept as known once it works, dropped for good when it doesn't
            if (right) {
                recipes().confirm(recipe[0], recipe[1]);
            } else {
                recipes().wrong(recipe[0], recipe[1]);
            }
        }
        if (right) {
            knowledge().confirm(question, answer);
            host.learnTalk(question, answer);
            host.note("Saved to Ibiko's memory: it will answer this, however it's asked.");
        } else {
            knowledge().reject(question, answer);
            host.forgetTalk(question);
            LearningJob.remember(host.context(), question);
            host.note("Got it: Ibiko won't give that answer again.");
        }
    }

    /** What the last learning session learned, said once afterwards; null when there is nothing new. */
    public String report() {
        if (LearningJob.reportSeen(host.context())) {
            return null;
        }
        List<String> learned = LearningJob.report(host.context());
        LearningJob.markReportSeen(host.context());
        if (learned.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder("In my last learning session I learned " + learned.size()
                + (learned.size() == 1 ? " new thing" : " new things") + ":");
        for (int i = 0; i < learned.size() && i < 12; i++) {
            b.append("\n- ").append(learned.get(i));
        }
        if (learned.size() > 12) {
            b.append("\n…and ").append(learned.size() - 12).append(" more.");
        }
        b.append("\nAsk me about any of them; tap the check when I get one right.");
        return b.toString();
    }

    /** Something the pilot asked any model about: learned in the next session if Ibiko v1 doesn't know it yet. */
    public void noticeQuestion(String said) {
        if (ResearchAnswer.subject(said) != null && knowledge().recall(said) == null) {
            LearningJob.remember(host.context(), said);
        }
    }
}
