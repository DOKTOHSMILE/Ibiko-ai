package ai.ibiko.social.campaign;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The networks' own rules, checked before a post goes out: a post the network would take down can get the account
 * restricted, and that stops every campaign on it. Each rule names what it catches, where it applies, and whether the
 * network bans it outright ({@link Level#BANNED}: held back unless the person says post anyway) or only frowns on it
 * ({@link Level#RISKY}: shown, never held). A list of words, not a lawyer: it catches the common cases. Pure Java.
 */
public final class PlatformRules {
    public enum Level { RISKY, BANNED }

    /** One rule a post broke. */
    public static final class Flag {
        public final String what, why;
        public final Level level;

        Flag(String what, String why, Level level) {
            this.what = what;
            this.why = why;
            this.level = level;
        }
    }

    private static final class Rule {
        final String what, why;
        final Pattern words;
        final Set<String> banned, risky; // networks

        Rule(String what, String why, String words, String banned, String risky) {
            this.what = what;
            this.why = why;
            this.words = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:" + words + ")(?![\\p{L}\\p{N}])");
            this.banned = set(banned);
            this.risky = set(risky);
        }

        private static Set<String> set(String s) {
            Set<String> out = new LinkedHashSet<String>();
            for (String n : s.split(",")) {
                if (!n.trim().isEmpty()) {
                    out.add(n.trim());
                }
            }
            return out;
        }
    }

    private static final String META = "facebook,instagram";
    private static final String ALL = "facebook,instagram,x,linkedin";

    private static final List<Rule> RULES = new ArrayList<Rule>();

    static {
        RULES.add(new Rule("weapons", "Facebook and Instagram don't allow selling weapons, and that includes stun guns, tasers"
                + " and pepper spray; X and LinkedIn limit posts that sell them",
                "tasers?|tazers?|stun ?guns?|pepper ?spray|(?<!glue |heat |nail |spray |paint |massage |water |toy |staple |caulk |grease |soldering |bubble |hot glue )guns?|firearms?|pistols?|rifles?|shotguns?|ammo|ammunition"
                        + "|airsoft|air ?guns?|crossbows?|brass knuckles|knuckle ?dusters?|switchblades?|machetes?|explosives?",
                META, "x,linkedin"));
        RULES.add(new Rule("drugs", "every network bans selling drugs and prescription medicine",
                "weed|cannabis|marijuana|codeine|tramadol|cocaine|mdma|kush|loud pack|prescription (?:drugs?|pills?)"
                        + "|without (?:a )?prescription",
                ALL, ""));
        RULES.add(new Rule("tobacco and vapes", "Facebook and Instagram don't allow selling tobacco or vapes",
                "vapes?|vaping|e-?cig(?:arette)?s?|cigarettes?|shisha|hookah|nicotine|tobacco",
                META, "x,linkedin"));
        RULES.add(new Rule("alcohol", "alcohol sales are limited (age and country rules) on every network",
                "alcohol|liquor|whisk(?:e)?y|vodka|gin|tequila|beer|wine|champagne|cognac",
                "", ALL));
        RULES.add(new Rule("adult products", "Facebook, Instagram and LinkedIn don't allow selling adult products",
                "sex toys?|adult toys?|vibrators?|dildos?|aphrodisiacs?|sexual enhancement|manpower pills?",
                "facebook,instagram,linkedin", "x"));
        RULES.add(new Rule("counterfeits", "every network bans selling fakes of other brands",
                "replicas?|first copy|aaa copy|master copy|fake (?:designer|brand)|counterfeits?|knock-?offs?",
                ALL, ""));
        RULES.add(new Rule("gambling", "betting and gambling posts are limited on every network",
                "betting|bet ?slips?|casino|sure ?odds|fixed matches|jackpot|lottery",
                "", ALL));
        RULES.add(new Rule("money claims", "promises of easy money read as a scam, and networks take them down",
                "guaranteed (?:profit|income|returns?)|double your money|get rich|passive income guaranteed|risk-?free investment"
                        + "|\\d+% (?:daily|weekly) (?:profit|returns?)",
                ALL, ""));
        RULES.add(new Rule("health claims", "claims that a product cures illness or melts weight away are removed as misleading",
                "cures?|100% (?:cure|effective)|miracle (?:cure|cream|pill)|lose \\d+ ?(?:kg|kilos?|pounds|lbs) in|permanent(?:ly)? cure",
                "", ALL));
        RULES.add(new Rule("engagement bait", "asking for likes, shares or tags to win gets posts shown to fewer people",
                "like and share|share to win|tag \\d+ friends|tag (?:a|your) friends? to win|comment yes|like if you",
                "", ALL));
    }

    private PlatformRules() {
    }

    /** The network's name as the rules know it ("facebook", "x", "instagram", "linkedin"). */
    static String net(String network) {
        String n = network == null ? "" : network.trim().toLowerCase(Locale.ROOT);
        return "twitter".equals(n) ? "x" : n;
    }

    /** Every rule {@code text} breaks on {@code network}; empty when it breaks none. */
    public static List<Flag> check(String network, String text) {
        List<Flag> out = new ArrayList<Flag>();
        String n = net(network);
        String t = text == null ? "" : text;
        for (Rule r : RULES) {
            boolean banned = r.banned.contains(n), risky = r.risky.contains(n);
            if ((banned || risky) && r.words.matcher(t).find()) {
                out.add(new Flag(r.what, r.why, banned ? Level.BANNED : Level.RISKY));
            }
        }
        return out;
    }

    /** True when {@code text} breaks a rule {@code network} bans outright. */
    public static boolean banned(String network, String text) {
        for (Flag f : check(network, text)) {
            if (f.level == Level.BANNED) {
                return true;
            }
        }
        return false;
    }

    /** The flags as one line for the screen, "" when there are none. */
    public static String said(List<Flag> flags) {
        StringBuilder s = new StringBuilder();
        for (Flag f : flags) {
            s.append(s.length() == 0 ? "" : " · ").append(f.level == Level.BANNED ? "Not allowed: " : "Risky: ").append(f.what)
                    .append(" (").append(f.why).append(')');
        }
        return s.toString();
    }

    /**
     * What a brief offers that the chosen networks ban (each rule once), so the person hears it before planning; empty
     * when nothing is.
     */
    public static List<Flag> brief(Campaign.Brief b, List<String> networks) {
        StringBuilder all = new StringBuilder(b.business).append('\n').append(b.offer);
        for (Product p : b.products) {
            all.append('\n').append(p.name).append(' ').append(p.about);
        }
        String words = all.toString();
        List<Flag> out = new ArrayList<Flag>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String n : networks) {
            for (Flag f : check(n, words)) {
                if (f.level == Level.BANNED && seen.add(f.what)) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    /** The rules, as the brain is told them when it plans and writes. */
    public static final String FOR_THE_BRAIN = "Follow each network's rules. On Facebook and Instagram never post that you"
            + " sell weapons (including stun guns, tasers and pepper spray), drugs or prescription medicine, tobacco or vapes,"
            + " adult products or copies of other brands; if the business sells such items, build those posts around its other"
            + " products. Nowhere promise easy money or cures, and never ask for likes, shares or tags to win.";
}
