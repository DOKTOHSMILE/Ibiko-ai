package ai.ibiko.social.campaign;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * WhatsApp, the free way: a click-to-chat link (wa.me) put in posts and replies, so a customer opens a chat with the
 * business in one tap, and posts handed to WhatsApp itself to share to Status or a group. No WhatsApp Business
 * Platform, no per-message cost, no server. Pure Java.
 */
public final class WhatsApp {
    /** WhatsApp, then WhatsApp Business: the apps a post can be handed to. */
    public static final String[] APPS = {"com.whatsapp", "com.whatsapp.w4b"};

    private WhatsApp() {
    }

    /**
     * The chat link for what the person gave: a wa.me or api.whatsapp.com link stays as it is; a phone number in
     * international form ("+234 803 123 4567") becomes https://wa.me/2348031234567, with {@code message} typed in for the
     * customer when given. Null for a local number without a country code ("0803…": the country can't be guessed) or
     * anything else.
     */
    public static String link(String given, String message) {
        String g = given == null ? "" : given.trim();
        if (g.isEmpty()) {
            return null;
        }
        if (isLink(g)) {
            return g.startsWith("http") ? g : "https://" + g;
        }
        if (!g.matches("[+0-9 ()\\-.]{7,24}")) {
            return null;
        }
        boolean plus = g.startsWith("+") || g.startsWith("00");
        String digits = g.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (!plus && digits.startsWith("0") || digits.length() < 8 || digits.length() > 15) {
            return null; // a local number, or not a phone number
        }
        String m = message == null ? "" : message.trim();
        return "https://wa.me/" + digits + (m.isEmpty() ? "" : "?text=" + URLEncoder.encode(m, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /** A WhatsApp chat link (wa.me/…, api.whatsapp.com/send…, chat.whatsapp.com/… for a group). */
    public static boolean isLink(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        return t.startsWith("wa.me/") || t.startsWith("api.whatsapp.com/") || t.startsWith("chat.whatsapp.com/");
    }

    /** What a customer is offered to say when they open the chat. */
    public static String greeting(Campaign.Brief b) {
        String about = b.offer.isEmpty() ? b.business : b.offer;
        about = about.replaceAll("\\s+", " ").trim();
        if (about.length() > 60) {
            int cut = about.lastIndexOf(' ', 60);
            about = about.substring(0, cut > 20 ? cut : 60);
        }
        return about.isEmpty() ? "Hello, I saw your post." : "Hello, I saw your post about " + about + ".";
    }

    /**
     * The post with the chat link at its end, when it isn't in it already and there is room ({@code most} characters);
     * a post with no room for it is left as it is.
     */
    public static String withLink(String post, String chat, int most) {
        String p = post == null ? "" : post.trim();
        if (chat == null || chat.isEmpty() || p.contains(chat) || containsLink(p)) {
            return p;
        }
        String add = "\n\nChat with us on WhatsApp: " + chat;
        if (p.length() + add.length() <= most) {
            return p + add;
        }
        String shortAdd = "\n\nWhatsApp: " + chat;
        return p.length() + shortAdd.length() <= most ? p + shortAdd : p;
    }

    private static boolean containsLink(String p) {
        for (String w : p.split("\\s+")) {
            if (isLink(w.replaceAll("[).,!]+$", ""))) {
                return true;
            }
        }
        return false;
    }
}
