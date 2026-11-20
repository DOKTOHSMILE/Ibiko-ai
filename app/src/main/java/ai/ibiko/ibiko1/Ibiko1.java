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
 * Ibiko 1 · 0.1, Ibiko's own model, in one class the app plugs in like any other model: {@link #answer} takes a
 * message and answers through the {@link Host}. Everything it is lives in this folder: its memory of confirmed answers
 * ({@link Knowledge}), the facts it read ({@link Facts}), its dictionary ({@link Lexicon}), its language formulas
 * ({@link Grammar}), its research ({@link ResearchAnswer}) and its learning sessions ({@link LearningJob}). Remove the
 * folder and the few lines that plug it in, and the app runs on its other models.
 *
 * <p>Why Java and not a native engine: Ibiko 1 is rules and lookups that run in microseconds, so it lives in the app's
 * own language with no bridge to cross. A trained network is another matter: {@link Training} writes what Ibiko 1
 * learned as a training file, and a model trained from it runs on the device through the app's llama.cpp engine
 * (C++, the fastest way to run a language model on a phone).
 */
public final class Ibiko1 {
    public static final String NAME = "Ibiko 1 · 0.1";

    /** How an answer came: from memory, from research, or neither (a greeting, a sum). */
    public enum How { MEMORY, RESEARCH, OTHER }

    /** What Ibiko 1 needs from the screen it answers on. */
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

        /** Whether a model is downloaded on the device that Ibiko 1 can ask for what its own memory doesn't hold. */
        default boolean modelReady() {
            return false;
        }

        /**
         * Asks the device's model (blocking: called off the main thread). Null when there is none or it failed. Its
         * answer is shown as unchecked, and the pilot's check or cancel teaches Ibiko 1, as with any model.
         */
        default String askModel(String system, String question) {
            return null;
        }

        /** Carries out a function code line ("§SR weather in Lagos") through the app's gate for every model. */
        default void act(String message, String codeLine) {
        }
    }

    private final Endpoint endpoint = new Endpoint(this);

    /** The door the app talks to Ibiko 1 through. */
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

    /**
     * Loads what the first question would otherwise wait for: the built-in facts, and the dictionary and concept tables
     * unpacked in place. Call it off the main thread at start (the app does it while its splash shows).
     */
    public static void warm(File filesDir) {
        File k = new File(new File(filesDir, "brain"), "knowledge");
        File lexicon = Store.unpacked(k, "lexicon.tsv");
        Store.unpacked(k, ai.ibiko.learn.WordRank.FILE); // the words by use, read with the dictionary (pack format 2)
        ai.ibiko.learn.WordBook.open(lexicon); // first: every English word its fixed id before anything files words
        builtIn();
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
                    TextCode.open(Plug.asset, "knowledge/local_facts.tsv"));
        }
        return builtIn;
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

    /** The learning score line, read from the files (for screens that don't hold Ibiko 1). */
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

    /** One of the pilot's messages, to any brain: it may judge Ibiko 1's last answer, and it is read for language. */
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
        learner().heard(said, System.currentTimeMillis());
    }

    /** Any model's answer: read for language only (a model's claim is not a fact). */
    public void readReply(String reply) {
        replied = reply != null && !reply.trim().isEmpty();
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
            think(Arrays.asList("Reading what you pasted", about.isEmpty() ? "Finding what it's about" : "Finding what it's about: " + about,
                    asked != null ? "Finding the sentence that answers your question" : "Picking out the main points"),
                    () -> saysPlain(who, reply));
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
        // a clear command: Ibiko 1 operates the app like any model, by writing one of its function codes
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
            // "Stop" with nothing of Ibiko 1's running: said plainly, not kept as a fact ("Noted.")
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, "Nothing is running right now, so there's nothing to stop."));
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
        if (!host.online() && host.modelReady()) {
            // offline, not in memory: the device's model answers, with what Ibiko 1 already knows about it
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
        final String subject = ResearchAnswer.subject(t);
        how = How.RESEARCH;
        final Methods.Way way = methods().recall(t);
        think(Arrays.asList("Reading your question", "Checking what I already know",
                way != null ? "Following the way that worked before: " + way.said() : "Thinking it through"), () -> {
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
                            r = LearningJob.research(host.context(), t);
                        }
                        if (r != null) {
                            long now = System.currentTimeMillis();
                            facts().learn(r.text, ResearchAnswer.subject(t), 2, now); // learning while being used
                            learner().read(r.text, Learner.Source.RESEARCH, now);
                        }
                        final ResearchAnswer found = r != null && knowledge().rejected(t, r.shown()) ? null : r;
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
                            more.add("Writing the answer");
                            think(more, () -> {
                                saysSure(who, shown, found.sources.size() >= 2 ? ai.ibiko.ibiko1.formulas.Critical.Ground.TWO_SOURCES
                                        : ai.ibiko.ibiko1.formulas.Critical.Ground.ONE_SOURCE,
                                        found.sources.isEmpty() ? null : "Read: " + String.join(", ", found.sources)); // one block
                                rateable(t, shown);
                            }, false);
                        });
                    }, "ibiko-research").start();
                });
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

    /** Ibiko 1 and the device's model as one brain: what Ibiko 1 knows goes in, and the model can run a function. */
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
                    + "Question: " + t;
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
                rateable(t, shown.trim()); // the pilot's check keeps it in Ibiko 1's memory (and in the next adapter's training)
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
        if (a == null) {
            return Phrasing.unknown(subject, online, LearningJob.plan(host.context()).on, times);
        }
        String[] s = a.text.split("(?<=[.!?])\\s+");
        String opening = s[0] + (s.length > 1 && s[0].length() < 160 ? " " + s[1] : "");
        return "I couldn't find a sure answer to exactly that, but here's what I know about " + a.title + ": " + opening
                + "\n\nIf you tell me which part you need, I'll work from there.";
    }

    private void says(String who, String reply) {
        String lead = leadIn;
        leadIn = null;
        String text = lead == null ? reply : lead + " " + reply; // "Got it, you're a man. I'm Ibiko, …"
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

    /** Questions about Ibiko 1 itself: what it learned, what it knows, who it is. Null for anything else. */
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
     * A statement: right after an answer it asked about, it is taken as the right answer to that question; "a pig is
     * an animal" on its own is kept as what a pig is. Nothing is searched.
     */
    public String learnStatement(String t) {
        String said = t.trim();
        if (ratingQuestion != null && host.ratingWaiting()) {
            String q = ratingQuestion;
            ratingQuestion = null;
            host.clearRating();
            knowledge().confirm(q, said);
            host.learnTalk(q, said);
            return "Got it. When you ask “" + q + "”, I'll answer: " + said;
        }
        // talk before the statement ("Well, ", "So ", "Oh ") is not part of what it says
        said = said.replaceFirst("(?i)^(well|so|oh|um+|hmm+|ok|okay|yes|no|and|but|like|now)\\b[,!.]?\\s+(?=\\S)", "");
        if (said.isEmpty()) {
            return "Noted.";
        }
        said = Character.toUpperCase(said.charAt(0)) + said.substring(1);
        Matcher m = Pattern.compile("(?i)^(?:(the|a|an) )?(.{2,60}?) (is|are|was|were) (.{2,300}?)[.!]*$").matcher(said);
        if (m.matches() && m.group(2).matches("(?i)(today|tomorrow|yesterday|tonight|now|it|this|that|he|she|they|there|here)\\b.*")) {
            // "today is Christmas day", "it is cold": true now, or about something only the chat knows; not kept as a fact
            return "Noted.";
        }
        if (m.matches()) {
            String subject = (m.group(1) == null ? "" : m.group(1) + " ") + m.group(2);
            String verb = m.group(3).toLowerCase(Locale.US);
            String differs = questionReply(said, "pilot"); // G8: the pilot's word is kept, and a difference is said
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

    /** Something the pilot asked any model about: learned in the next session if Ibiko 1 doesn't know it yet. */
    public void noticeQuestion(String said) {
        if (ResearchAnswer.subject(said) != null && knowledge().recall(said) == null) {
            LearningJob.remember(host.context(), said);
        }
    }
}
