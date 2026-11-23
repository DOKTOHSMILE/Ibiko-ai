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
    public void answer(final String message) {
        final String who = host.persona();
        long heardAt = System.currentTimeMillis();
        // what the conversation is about: "when was he born?" stands on its own as "when was Nelson Mandela born?"
        // (not in the turn after it asked back, which is an explanation or a correction, read as said)
        // a long text pasted in: read and kept, never rewritten with the conversation's names
        final Passage pasted = inquiry == null ? Passage.of(message, heardAt) : null;
        final Context.Resolved withContext = pasted != null || inquiry != null || inquiryAnswer != null && Inquiry.corrects(message)
                ? Context.unchanged(message) : context.resolve(message, heardAt);
        final String t = withContext.text;
        if (leadIn == null) {
            previous = current; // a message's own rest (after "I'm a man, …") isn't a new question
            current = message;
        }
        contextStep = withContext.changed() ? "Reading it with what we were talking about: " + withContext.said() : null;
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
        final String sum = host.sum(t);
        if (sum != null) {
            think(Arrays.asList("Reading your question", "Working it out exactly"), () -> says(who, sum));
            return;
        }
        // a sentence's structure: its syntax tree, its constituents, why it is ambiguous
        final String structure = SyntaxTalk.reply(message);
        if (structure != null) {
            think(Arrays.asList("Reading the sentence", "Tagging each word's part of speech", "Grouping the words into phrases, bottom up",
                    "Checking every way the rules allow"), () -> saysPlain(who, structure));
            return;
        }
        // words, meanings and grammar: what a word means (here), the word for a meaning, synonyms, a sentence explained,
        // whether two say the same, a word's morphemes, whether a sentence is grammatical
        // a word with meanings in several subjects, asked about while the subject is known: that subject's meaning
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
                host.answered(How.OTHER);
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
        if (mentioned != null && !knowledge().rejected(t, mentioned)) {
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
        // two things told apart ("difference between a frog and a toad"): each read for what kind of thing it is and
        // what is said of it, then compared: the same kind or not, and what sets each apart
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
                return;
            }
        }
        // how something is made, formed or done, or how it works: the way read out of what it knows and reads
        if (ai.ibiko.ibiko1.formulas.Asking.of(t).wants == ai.ibiko.ibiko1.formulas.Asking.Wants.HOW && answerHow(who, t)) {
            return;
        }
        // "is a frog an animal?": worked out by going up what each thing is (a frog is an amphibian, an amphibian is …)
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
                    return;
                }
            }
        }
        // why something is so: the reason read out of what it knows and reads (cause and effect), not looked up whole
        if (ai.ibiko.ibiko1.formulas.Asking.of(t).wants == ai.ibiko.ibiko1.formulas.Asking.Wants.WHY && answerWhy(who, t)) {
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
                            r = LearningJob.research(host.context(), asked); // the question as understood (its name repaired)
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
                            if (asksBack() && host.online() && subject != null && (found != null || !host.modelReady())) {
                                // not in memory: before answering, it asks what the person means (Inquiry); when the search
                                // found nothing and the phone has a model, that model answers instead, as before
                                inquiry = new Inquiry.Pending(t, subject, found, System.currentTimeMillis());
                                saysPlain(who, Inquiry.askBack(t, subject, found != null));
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

    /** Ibiko v1 and the device's model as one brain: what Ibiko v1 knows goes in, and the model can run a function. */
    static final String MODEL_SYSTEM = "You are Ibiko, answering for Ibiko's own brain. Answer in a few plain sentences."
            + " Use what Ibiko already knows when it is given. If you don't know, say so. "
            + ai.ibiko.suit.FunctionCode.directions()
            + " /no_think";

    /** How long the device's model may take before it gives a proper reply from what it knows instead. */
    static final long MODEL_WAIT_MS = 40_000;

    private void fromModel(final String who, final String t, final String subject, final boolean online) {
        final int mine = token;
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
                    + "Question: " + PromptReader.guard(t); // orders hidden in pasted text are fenced off as text
            String a = host.askModel(MODEL_SYSTEM, asked);
            final String said = a == null ? null : a.trim();
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
                    host.answered(How.OTHER);
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
        host.stepsDone();
        if (worker != null) {
            Plug.workers.end(worker, true);
            worker = null;
        }
        host.say(who, text);
        host.answered(how);
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
        String all = (lastSaid == null ? "" : lastSaid + "\n") + text;
        lastSaid = all.length() > 6000 ? all.substring(all.length() - 6000) : all; // what was said lately
        host.stepsDone();
        if (worker != null) {
            Plug.workers.end(worker, true);
            worker = null;
        }
        host.say(who, text);
        host.answered(how);
    }

    /** The text the person pasted last, for questions about it ({@link Passage}). */
    private volatile Passage passage;

    /** A question about the person asking it ("what's my…", "where do I…", "am I…"), not about the world. */
    static boolean aboutThePerson(String t) {
        String q = Paraphrase.normalize(t);
        return EncyclopediaQA.question(q) && (q.matches(".*\\bmy\\b.*") || q.matches("^(where|what|who|when|how|why|which) (am|do|did|was|will|should|can) i\\b.*")
                || q.matches("^(am|do|did|was|should|can) i\\b.*")) && !q.matches(".*\\b(tell|show|give|teach|help|let) me\\b.*")
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
        if (q.matches("(?s).*\\bwhat (have|did) you (learn|learned|learnt)\\b(?!.*\\babout\\b).*"
                + "|.*\\bwhat do you (know|remember)\\b(?!.*\\babout\\b).*|.*\\bwhat have you (remembered|saved|kept)\\b(?!.*\\babout\\b).*")) {
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
        reflection().judged(Methods.kind(question), right); // and the record of how replies like it go
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
