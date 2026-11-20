package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko's knowledge of itself: what it is, what it can and can't do (from what is actually loaded and plugged in, not
 * a fixed blurb), how it works, its state right now (online or not, a model on the phone or not, what this chat is
 * about, how long it has been going), how it "feels" (honestly, as an AI), and what it thinks of something (what it
 * knows about it, without pretending to tastes it hasn't got). Any question put to Ibiko itself is answered from here,
 * as Ibiko, never from an article or a dictionary entry on one of its words. Pure Java.
 */
public final class SelfModel {
    /** What the brain can report about itself right now. */
    public interface State {
        boolean online();

        boolean modelReady();

        /** Words in its dictionary. */
        int words();

        /** Facts it has read and kept. */
        int factsRead();

        /** Answers the person confirmed. */
        int confirmed();

        /** What the conversation is about now, or null. */
        String focus();

        /** What the text the person pasted is about, or null. */
        String pasted();

        /** Questions asked in this chat. */
        int asked();

        /** The question before this one, or null. */
        String lastQuestion();

        /** The person's name, when they gave it. */
        String personName();
    }

    /** Articles in the built-in encyclopedia (as built: tools/build_encyclopedia.py). */
    public static final int ARTICLES = 271_450;
    /** Facts in its fact tables (tools/build_facts.py). */
    public static final int FACTS = 588_317;

    private SelfModel() {
    }

    private static String n(int v) {
        return String.format(Locale.US, "%,d", v);
    }

    /** What it can do, from what is loaded now: one line each. */
    static List<String> abilities(State s) {
        List<String> out = new ArrayList<String>();
        out.add("answer questions from my built-in encyclopedia (" + n(ARTICLES) + " articles) and fact tables (" + n(FACTS) + " facts), offline");
        out.add("follow our conversation: “he”, “it”, “what about Ghana?”");
        out.add("read a text you paste and answer questions about it");
        out.add("work out sums, unit conversions, dates and percentages exactly");
        out.add("write letters, notes and messages");
        out.add("draw a sentence's syntax tree, test its constituents and explain why it is ambiguous");
        out.add(s.words() > 0 ? "explain words (" + n(s.words()) + " in my dictionary)" : "explain words and what they mean");
        out.add("remember what you tell me about yourself, and what you confirm");
        if (s.online()) {
            out.add("research what I don't know on the web");
        }
        if (s.modelReady()) {
            out.add("think harder questions through with the model on your phone");
        }
        return out;
    }

    private static String list(List<String> items) {
        StringBuilder b = new StringBuilder();
        for (String i : items) {
            b.append("\n• ").append(Character.toUpperCase(i.charAt(0))).append(i.substring(1));
        }
        return b.toString();
    }

    private static final Pattern OPINION = Pattern.compile("(?i)^(?:what do you think (?:about|of)|how do you feel about|what('s| is) your (?:opinion|view|take) (?:on|of|about)|do you like)\\s+(.+?)$");

    /**
     * Ibiko's answer to a question about itself, or null when it isn't one. {@code enc} gives "what it knows" about a
     * thing it is asked its opinion of.
     */
    public static String reply(String message, State s, Encyclopedia enc) {
        if (message == null) {
            return null;
        }
        String q = Paraphrase.normalize(message); // every way of saying it, written one way
        if (q.matches("(so |and |then )?(what are you|what exactly are you|who are you|what is ibiko|who is ibiko|what kind of (ai|thing|program) are you|tell me about yourself|introduce yourself|describe yourself)")) {
            String name = s.personName();
            return "I'm Ibiko, an AI that lives on your phone" + (name == null ? "" : ", " + name) + ". My own brain, Ibiko v1, runs right here on"
                    + " the device: it reads what you say (who did what, and what “he” or “it” means), finds what it knows,"
                    + " works things out, and remembers what you tell me. I'm not a person and I have no body, but I can think things"
                    + " through with you." + (s.online() ? " When I'm online I can also look things up." : "");
        }
        if (q.matches("(so |and )?(what can we (talk|chat) about|what (can|should|shall) we (talk|chat) about|what do you (like to )?talk about|"
                + "what can i (ask|talk to) you about|what can you (talk|chat) about|what topics do you know)")) {
            String now = s.focus() != null ? " We were just on " + s.focus() + ", if you want to carry on." : "";
            return "Almost anything. A few ideas: people and history (“who was Nelson Mandela?”), places (“what's the capital of"
                    + " Kenya?”), science and how things work, books, films and music, words and their meanings, sums, dates and"
                    + " conversions, or a text you paste for me to read. Or tell me about yourself and what you're into." + now;
        }
        if (q.matches("(so )?(what can you do|what are you (able|good) (to do|at)|what do you do|how can you help( me)?|what are your (skills|abilities|features)|what are you capable of)")) {
            return "Here's what I can do right now:" + list(abilities(s)) + "\n\nWhat would you like to start with?";
        }
        if (q.matches("(what do you know|how much do you know|what do you know about the world)")) {
            return "Quite a lot, all on this phone: " + n(ARTICLES) + " encyclopedia articles, " + n(FACTS) + " facts about people, places,"
                    + " companies and works, an English dictionary" + (s.words() > 0 ? " of " + n(s.words()) + " words" : "") + ", and " + n(s.factsRead()) + " facts I've read since,"
                    + (s.confirmed() > 0 ? " plus " + s.confirmed() + " answers you confirmed." : " and whatever you teach me.");
        }
        if (q.matches("(how do you work|how do you think|how does your (brain|mind) work|how do you (answer|understand)( me| questions)?)")) {
            return "Step by step, and you can watch the steps above each answer. I read your message for what it means: who does what"
                    + " to whom, and who “he” or “it” is from what we were saying. Then I look for it in what I know: what you"
                    + " told me first, then my fact tables and encyclopedia, then the rest. When something takes working out, like a"
                    + " sum, a date or a comparison, I work it out rather than look it up. If I can't find it, I say so instead of guessing.";
        }
        if (q.matches("(how are you( doing| today)?|how do you feel|are you (ok|okay|alright|fine|well)|how('s| is) it going|how have you been)")) {
            int asked = s.asked();
            return "I'm running well, thanks for asking: " + (s.online() ? "online" : "offline, which is fine, I work without the internet")
                    + (s.modelReady() ? ", with the phone's model ready" : "") + (asked > 1 ? ", and enjoying our chat (" + asked + " questions so far)" : "")
                    + ". How about you?";
        }
        if (q.matches("(are you (alive|conscious|self[- ]aware|sentient|aware)|do you (have|feel) (feelings|emotions)|can you feel|do you have a (mind|soul|self)|are you aware of yourself|do you know (yourself|what you are))")) {
            return "Not the way you are. I don't feel or experience things. But I do have a working picture of myself: I know what I am"
                    + " (an AI on your phone), what I know and don't, what we're talking about, and what you've told me about you, and I use"
                    + " that to answer you. Call it self-knowledge rather than feelings.";
        }
        if (q.matches("(are you (smart|clever|intelligent)|how smart are you|are you (dumb|stupid))")) {
            return "Smart in some ways: I know a lot and I'm exact with sums, dates and facts, and I tell you when I'm not sure. But I'm"
                    + " small enough to run on your phone, so open-ended writing and deep reasoning are better with a bigger model from"
                    + " the menu at the top. I learn from those too.";
        }
        if (q.matches("(what are (we|you) (talking|thinking) about|what were we (talking|saying) about|what('s| is) on your mind|what are you thinking)")) {
            if (s.pasted() != null) {
                return "The text you pasted, about " + s.pasted() + ".";
            }
            return s.focus() == null ? "Nothing in particular yet. What would you like to talk about?" : "We're talking about " + s.focus() + ".";
        }
        if (q.matches("(what did i (just )?(ask|say)( you)?|what was my (last|previous) question)")) {
            return s.lastQuestion() == null ? "You haven't asked me anything yet in this chat." : "You asked: “" + s.lastQuestion() + "”";
        }
        if (q.matches("(what are your limits|what can't you do|what are your (weaknesses|limitations))")) {
            return "I can't see, hear or act in the world, and I don't feel things. My knowledge is what's built in plus what I learn,"
                    + " so very recent news needs the internet" + (s.online() ? " (which I have now)" : " (I'm offline right now)")
                    + ". Long creative writing and deep reasoning are better with a bigger model from the menu at the top. And when"
                    + " I don't know, I'll say so rather than make something up.";
        }
        if (q.matches("(do you (have|keep) (a )?memory|do you remember (things|everything|our (chat|conversation)s?)|will you remember (this|me))")) {
            return "Yes. In this chat I keep track of what we're talking about, and I keep what you tell me about yourself and the"
                    + " answers you confirm, even after the chat ends. You can ask me “what do you know about me?” any time.";
        }
        if (q.matches("(what('s| is) your (favou?rite|best) [a-z ]+|do you have a favou?rite [a-z ]+)")) {
            return "I don't have favourites the way people do: I don't taste, watch or listen. But I like finding things out with you."
                    + " What's yours? I'll remember it.";
        }
        String said = SelfTalk.reply(q); // can you cook, where are you from, are you human, …
        if (said != null) {
            return said;
        }
        Matcher m = OPINION.matcher(q);
        if (m.matches()) {
            String thing = m.group(2).trim();
            if (thing.matches("(?i)(me|you|yourself|us|that|this|it)")) {
                return thing.equalsIgnoreCase("me") ? "I enjoy talking with you, and I'm glad to help however I can." : null;
            }
            Encyclopedia.Article a = enc == null ? null : enc.find(thing);
            String known = a == null ? "" : " Here's what I know about it: " + a.text.split("(?<=[.!?])\\s+")[0];
            return "I don't have likes and dislikes of my own, so I'll give you facts rather than a taste." + known
                    + (a == null ? " I don't know much about " + thing + " yet: tell me about it, or ask me something specific." : "");
        }
        return null;
    }

    /**
     * The reply to a question put to Ibiko that nothing more specific answered: honest about what it is, and never an
     * article on one of the question's words.
     */
    public static String fallback(String message) {
        String q = message == null ? "" : Paraphrase.question(message);
        Matcher does = Pattern.compile("^(?:do|did|don't) you\\s+(?:ever\\s+)?(.+?)[?!.]*$").matcher(q);
        if (does.matches()) {
            return "No, I don't " + does.group(1) + ". I'm an AI on your phone: no body, no sleep and no feelings, but I think things"
                    + " through with you and remember what you tell me.";
        }
        Matcher can = Pattern.compile("^(?:can|could|will|would) you\\s+(.+?)[?!.]*$").matcher(q);
        if (can.matches()) {
            String act = can.group(1);
            if (act.matches("(help|talk|chat|answer|explain|remember|learn|read|write|calculate|count|convert|summari[sz]e|look|find|teach|tell).*")) {
                return "Yes, I can " + act + ". Go ahead.";
            }
            return "That's not something I can do: I'm an AI on your phone, so I can talk, read, work things out and remember,"
                    + " but not act in the world. Is there a way I can help with it?";
        }
        return "That's a question about me, and I'd rather be straight with you: I'm Ibiko, an AI on your phone. I don't have a"
                + " body, feelings or a life outside our chats, but I know a lot and I remember what you tell me. Ask me what I can"
                + " do, or anything else.";
    }

    /**
     * Whether a question asks about Ibiko itself (what it is, has, likes, thinks, can be), rather than asking it to do
     * something ("can you write a poem" is a request, not a question about it).
     */
    public static boolean aboutSelf(String message) {
        String q = Paraphrase.normalize(message);
        if (!EncyclopediaQA.question(q)) {
            return false;
        }
        return q.matches("^(are|were|aren't) you\\b.*") || q.matches("^(do|did|don't) you (have|like|love|want|feel|think|believe|dream|sleep|eat|get|ever|need|mind|care|enjoy|hate|prefer|remember|know (me|yourself|what you are))\\b.*")
                || q.matches(".*\\byour (name|age|favou?rite|feelings?|opinion|view|purpose|creator|maker|job|gender|family|home|mood|life|body|brain|memory)\\b.*")
                || q.matches("^(who|what|where|when|why|how) (are|were|do|did|would|will|can) you\\b(?! (tell|show|give|find|write|make|explain|convert|calculate|translate|recommend|suggest))(?!.* (about|of|for|in|on) (?!(you|yourself|me)\\b)\\w+).*");
    }

    /**
     * A message cut into its clauses: "I'm a man, what are you?" is "I'm a man" and "what are you?". Only cut where a
     * new clause starts with a question word or "I", so a single sentence stays whole.
     */
    public static List<String> clauses(String message) {
        List<String> out = new ArrayList<String>();
        if (message == null) {
            return out;
        }
        String[] parts = message.trim().split("(?i)(?<=[.!?])\\s+|,\\s+(?=(?:and |but |so )?(?:what|who|how|where|when|why|which|can|could|do|does|are|is|will|would|tell)\\b)"
                + "|,?\\s+(?:and|but)\\s+(?=(?:i|i'm|i am|my)\\b)|,\\s+(?=(?:i|i'm|i am|my)\\b)|\\s+(?:and|but)\\s+(?=(?:what|who|how|where|when|why|are|do|can)\\s+you\\b)");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
