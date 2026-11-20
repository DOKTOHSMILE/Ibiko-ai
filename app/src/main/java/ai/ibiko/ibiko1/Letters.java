package ai.ibiko.ibiko1;

import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short letters, notes, emails and messages, written offline: "write me a short letter", "a thank-you note to my
 * teacher", "an email to my boss asking for leave", "a birthday message for my sister". Who it is to sets the tone
 * (family and friends warm, a boss or teacher formal); what it is for sets what it says. Each is put together fresh
 * from a few ways of saying each part, with blanks in [brackets] for what only the writer knows. Pure Java.
 */
public final class Letters {
    private Letters() {
    }

    private static final Pattern ASK = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:(?:can|could|would) you\\s+)?(?:please\\s+)?"
            + "(?:write|draft|compose|create|make|prepare|give|send|help me (?:write|with))(?: me| for me| us)?\\s+"
            + "(?:a|an|one|some)?\\s*((?:short|quick|brief|small|simple|nice|lovely|sweet|formal|informal|polite|professional|warm|"
            + "heartfelt|little|thank[- ]you|thanks|apology|birthday|congratulations|congratulatory|love|leave|sick|complaint|"
            + "application|invitation|welcome|sympathy|condolence|get[- ]well|farewell|goodbye|resignation|cover)\\s+)*"
            + "(letter|note|message|email|e-mail|mail|card|text|wish|wishes)\\b(.*)$");

    /** Whether a message asks for a letter or a message to be written. */
    public static boolean asks(String t) {
        return t != null && ASK.matcher(t.trim()).matches();
    }

    enum Purpose { THANKS, SORRY, BIRTHDAY, CONGRATS, INVITE, LEAVE, COMPLAINT, APPLY, RESIGN, GET_WELL, SYMPATHY, MISS, FAREWELL, WELCOME, GENERAL }

    static Purpose purpose(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        if (s.matches("(?s).*\\b(thank|thanks|grateful|gratitude|appreciat)\\w*.*")) {
            return Purpose.THANKS;
        }
        if (s.matches("(?s).*\\b(sorry|apolog\\w*|forgive)\\b.*")) {
            return Purpose.SORRY;
        }
        if (s.matches("(?s).*\\bbirthday\\b.*")) {
            return Purpose.BIRTHDAY;
        }
        if (s.matches("(?s).*\\b(congrat\\w*|graduat\\w*|promotion|wedding|new baby|engagement)\\b.*")) {
            return Purpose.CONGRATS;
        }
        if (s.matches("(?s).*\\b(invit\\w*|party|come to)\\b.*")) {
            return Purpose.INVITE;
        }
        if (s.matches("(?s).*\\b(leave|sick|absence|absent|day off|time off|permission|excuse)\\b.*")) {
            return Purpose.LEAVE;
        }
        if (s.matches("(?s).*\\b(complain\\w*|complaint|refund|faulty|poor service|disappointed)\\b.*")) {
            return Purpose.COMPLAINT;
        }
        if (s.matches("(?s).*\\b(apply|application|job|position|vacancy|cover letter|internship|admission)\\b.*")) {
            return Purpose.APPLY;
        }
        if (s.matches("(?s).*\\b(resign\\w*|quit\\w*)\\b.*")) {
            return Purpose.RESIGN;
        }
        if (s.matches("(?s).*\\b(get well|recover\\w*|ill|in hospital|feel better)\\b.*")) {
            return Purpose.GET_WELL;
        }
        if (s.matches("(?s).*\\b(sympathy|condolence\\w*|passed away|loss|died|funeral)\\b.*")) {
            return Purpose.SYMPATHY;
        }
        if (s.matches("(?s).*\\b(miss|missing|love you|i love|romantic|love letter|love)\\b.*")) {
            return Purpose.MISS;
        }
        if (s.matches("(?s).*\\b(farewell|goodbye|leaving|moving away)\\b.*")) {
            return Purpose.FAREWELL;
        }
        if (s.matches("(?s).*\\bwelcome\\b.*")) {
            return Purpose.WELCOME;
        }
        return Purpose.GENERAL;
    }

    private static final Pattern TO = Pattern.compile("(?i)\\b(?:to|for)\\s+(?:(?:my|our|the|a|an)\\s+)?((?:dear|dearest|lovely|best|old|new|former|class|head|school|house)?\\s*"
            + "[\\p{L}'-]+(?:\\s+[\\p{Lu}][\\p{L}'-]+)?)");

    /** Who it is to, as the greeting names them ("Mum"), or null. */
    static String recipient(String rest) {
        Matcher m = TO.matcher(rest);
        while (m.find()) {
            String who = m.group(1).trim();
            String[] two = who.split("\\s+");
            if (two.length == 2 && !Character.isUpperCase(two[1].codePointAt(0))) {
                who = two[0]; // "my boss asking for…": the second word is what comes next, not a surname
            }
            String low = who.toLowerCase(Locale.ROOT);
            if (low.matches("(say|thank|ask|apologi[sz]e|tell|invite|wish|congratulate|request|complain|apply|resign|"
                    + "celebrate|let|show|express|be|get|come|my|me|him|her|them|us|you|work|school|leave|the|a|an)( .*)?")) {
                continue; // "to say thanks", "to ask for leave": what it is for, not who it is to
            }
            if (low.matches("mum|mom|mother|mommy|mummy|mama")) {
                return "Mum";
            }
            if (low.matches("dad|father|daddy|papa")) {
                return "Dad";
            }
            if (low.matches("(boss|manager|supervisor|employer|hr|principal|headmaster|headmistress|landlord|lecturer|professor|"
                    + "director|ceo|client|customer|company|bank|school|council|hiring manager|recruiter)")) {
                return low.equals("hr") ? "HR" : null; // formal: the name goes in a blank
            }
            if (low.matches("teacher|class teacher|head teacher")) {
                return null;
            }
            String[] w = who.split("\\s+");
            StringBuilder b = new StringBuilder();
            for (String x : w) {
                if (!x.isEmpty()) {
                    b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));
                }
            }
            return b.toString();
        }
        return null;
    }

    enum Tone { WARM, FRIENDLY, FORMAL }

    static Tone tone(String t, Purpose p) {
        String s = t.toLowerCase(Locale.ROOT);
        if (s.matches("(?s).*\\b(formal|professional|boss|manager|supervisor|employer|hr|principal|headmaster|teacher|lecturer|"
                + "professor|landlord|director|ceo|client|customer|company|bank|council|sir|madam|hiring|recruiter|office)\\b.*")
                || p == Purpose.APPLY || p == Purpose.RESIGN || p == Purpose.COMPLAINT || p == Purpose.LEAVE) {
            return Tone.FORMAL;
        }
        if (s.matches("(?s).*\\b(mum|mom|mother|mommy|mummy|mama|dad|father|daddy|papa|sister|brother|wife|husband|son|daughter|"
                + "grandma|grandmother|grandpa|grandfather|aunt|aunty|auntie|uncle|cousin|love|darling|babe|baby|girlfriend|"
                + "boyfriend|fianc\\w*|partner|family)\\b.*") || p == Purpose.MISS) {
            return Tone.WARM;
        }
        return Tone.FRIENDLY;
    }

    private static String pick(Random r, String... ways) {
        return ways[r.nextInt(ways.length)];
    }

    /** The letter asked for, or null when the message doesn't ask for one. */
    public static String write(String t) {
        return write(t, new Random());
    }

    static String write(String t, Random r) {
        Matcher m = ASK.matcher(t == null ? "" : t.trim());
        if (!m.matches()) {
            return null;
        }
        String kind = m.group(2).toLowerCase(Locale.ROOT);
        String rest = m.group(3) == null ? "" : m.group(3);
        Purpose p = purpose(t);
        Tone tone = tone(t, p);
        String who = recipient(rest);
        boolean email = kind.matches("email|e-mail|mail");
        boolean shortOne = kind.matches("message|text|wish|wishes|card|note") || t.toLowerCase(Locale.ROOT).matches("(?s).*\\b(short|quick|brief|small|little)\\b.*");

        String hello = tone == Tone.FORMAL ? (who == null ? "Dear [Name]," : "Dear " + who + ",")
                : tone == Tone.WARM ? "Dear " + (who == null ? "[Name]" : who) + ","
                : pick(r, "Hi ", "Hello ", "Dear ") + (who == null ? "[Name]" : who) + ",";
        String body = body(p, tone, r, shortOne);
        String bye = tone == Tone.FORMAL ? pick(r, "Yours sincerely,", "Kind regards,", "Best regards,")
                : tone == Tone.WARM ? pick(r, "With love,", "Lots of love,", "All my love,", "Love always,")
                : pick(r, "Best wishes,", "Warm regards,", "Take care,", "Cheers,");
        StringBuilder b = new StringBuilder();
        if (email) {
            b.append("Subject: ").append(subject(p, r)).append("\n\n");
        }
        b.append(hello).append("\n\n").append(body).append("\n\n").append(bye).append("\n[Your name]");
        return b.toString();
    }

    private static String subject(Purpose p, Random r) {
        switch (p) {
            case THANKS: return pick(r, "Thank you", "With thanks");
            case SORRY: return "My apologies";
            case BIRTHDAY: return "Happy birthday!";
            case CONGRATS: return "Congratulations!";
            case INVITE: return "You're invited";
            case LEAVE: return "Request for leave on [date]";
            case COMPLAINT: return "Complaint about [product or service]";
            case APPLY: return "Application for the [position] role";
            case RESIGN: return "Resignation, [your name]";
            case GET_WELL: return "Get well soon";
            case SYMPATHY: return "With deepest sympathy";
            case FAREWELL: return "Goodbye, and thank you";
            case WELCOME: return "Welcome!";
            default: return pick(r, "Checking in", "Hello from [your name]", "A quick note");
        }
    }

    private static String body(Purpose p, Tone tone, Random r, boolean shortOne) {
        boolean formal = tone == Tone.FORMAL;
        String b;
        switch (p) {
            case THANKS:
                b = formal ? pick(r, "I am writing to sincerely thank you for [what they did].", "Thank you very much for [what they did].")
                        + " " + pick(r, "Your help made a real difference, and I am truly grateful.", "It meant a great deal to me, and I appreciate it.")
                        : pick(r, "Thank you so much for [what they did]!", "I just wanted to say a big thank you for [what they did].")
                        + " " + pick(r, "It really made my day.", "You have no idea how much it meant to me.", "I'm so lucky to have you.");
                break;
            case SORRY:
                b = formal ? "I would like to apologise for [what happened]. " + pick(r, "It was not my intention to cause any inconvenience,"
                        + " and I will make sure it does not happen again.", "I take full responsibility and have taken steps to put it right.")
                        : pick(r, "I'm really sorry about [what happened].", "I owe you an apology for [what happened].")
                        + " " + pick(r, "I didn't mean to hurt you, and I hope you can forgive me.", "You matter a lot to me, and I want to make it right.");
                break;
            case BIRTHDAY:
                b = pick(r, "Happy birthday!", "Wishing you a very happy birthday!", "Happy birthday to someone truly special!")
                        + " " + pick(r, "I hope your day is filled with joy, laughter and everything you love.",
                        "May this new year bring you good health, happiness and all your heart desires.",
                        "Enjoy every moment of your special day: you deserve it.");
                break;
            case CONGRATS:
                b = pick(r, "Congratulations on [the achievement]!", "Huge congratulations on [the achievement]!")
                        + " " + pick(r, "All your hard work has paid off, and I couldn't be prouder.",
                        "You truly deserve this, and I'm so happy for you.", "This is just the beginning of great things for you.");
                break;
            case INVITE:
                b = (formal ? "I would be delighted if you could join us for [event] on [date] at [time], at [place]."
                        : "I'd love for you to come to [event] on [date] at [time], at [place].")
                        + " " + pick(r, "It won't be the same without you.", "Please let me know if you can make it.", "Hope to see you there!");
                break;
            case LEAVE:
                b = "I am writing to request leave from [start date] to [end date] " + pick(r, "due to [reason].", "because [reason].")
                        + " I will make sure my work is up to date before I go, and [colleague] has agreed to cover anything urgent."
                        + " Thank you for considering my request.";
                break;
            case COMPLAINT:
                b = "I am writing to complain about [product or service], which I bought on [date]. [Describe the problem briefly.]"
                        + " I would be grateful if you could [refund, replace or repair it] at your earliest convenience."
                        + " I look forward to your reply.";
                break;
            case APPLY:
                b = "I am writing to apply for the [position] role at [company]. With my experience in [your field] and my skills in"
                        + " [two or three skills], I am confident I can add real value to your team. I would welcome the chance to"
                        + " discuss my application, and I have attached my CV for your review. Thank you for your time and consideration.";
                break;
            case RESIGN:
                b = "Please accept this letter as formal notice of my resignation from my position as [job title], effective [last day]."
                        + " Thank you for the opportunities I have had here; I am grateful for everything I have learned."
                        + " I will do all I can to make the handover smooth.";
                break;
            case GET_WELL:
                b = pick(r, "I was so sorry to hear you're not feeling well.", "I'm thinking of you and wishing you a speedy recovery.")
                        + " " + pick(r, "Take all the time you need to rest, and know that I'm here if you need anything.",
                        "Sending you lots of strength and good wishes.");
                break;
            case SYMPATHY:
                b = "I was deeply sorry to hear about the loss of [name]. " + pick(r, "Please accept my heartfelt condolences.",
                        "My thoughts are with you and your family at this difficult time.")
                        + " " + pick(r, "If there is anything I can do, please don't hesitate to ask.", "They will be greatly missed.");
                break;
            case MISS:
                b = pick(r, "I just wanted to tell you how much I love and miss you.", "Not a day goes by that I don't think of you.")
                        + " " + pick(r, "Thank you for always being there for me, for every kind word and every bit of care.",
                        "You mean the world to me, and I'm so grateful for you.")
                        + (shortOne ? "" : " " + pick(r, "I can't wait to see you again soon.", "I hope you're keeping well and taking care of yourself."));
                break;
            case FAREWELL:
                b = pick(r, "As I say goodbye, I want to thank you for everything.", "It's hard to believe it's time to say goodbye.")
                        + " " + pick(r, "I'll always remember the good times we shared.", "I'll miss you, and I hope we stay in touch.");
                break;
            case WELCOME:
                b = pick(r, "Welcome! We're so glad to have you with us.", "A warm welcome to you!")
                        + " " + pick(r, "If you need anything at all, just ask.", "We're looking forward to getting to know you.");
                break;
            default:
                b = formal ? "I hope this letter finds you well. I am writing to [say why you are writing]."
                        + " Please let me know if you need any further information. Thank you for your time."
                        : tone == Tone.WARM
                        ? pick(r, "I hope you're doing well.", "I've been thinking about you and wanted to write.")
                        + " " + pick(r, "Things here are going well: [a little news from you].", "I wanted to share a bit of news: [your news].")
                        + " " + pick(r, "Thank you for always being there for me. You mean the world to me.", "I miss you and can't wait to see you soon.")
                        : pick(r, "I hope you're doing well!", "It's been a while, so I thought I'd write.")
                        + " " + pick(r, "I wanted to let you know [your news].", "Here's a quick update: [your news].")
                        + " " + pick(r, "Let's catch up soon.", "Write back when you can.");
                break;
        }
        return b;
    }
}
