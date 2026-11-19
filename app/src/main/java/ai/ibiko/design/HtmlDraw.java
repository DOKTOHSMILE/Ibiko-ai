package ai.ibiko.design;

import java.util.Locale;

/**
 * Pictures drawn as web pages: the brain designs the picture as one HTML document (CSS for colour, gradients, shadows,
 * type and layout; inline SVG for shapes, icons and illustration; web fonts), and the phone's browser engine draws it
 * into an image. Far richer than plain SVG shapes, and every model can write it. The shape follows the ask: a story or
 * wallpaper stands tall, a banner or thumbnail lies wide, the rest is square. Pure Java.
 */
public final class HtmlDraw {
    private HtmlDraw() {
    }

    /** Width and height for what was asked. */
    public static int[] shape(String what) {
        String t = what == null ? "" : what.toLowerCase(Locale.US);
        if (t.matches(".*\\b(wallpaper|phone background|lock ?screen|story|stories|status|reel|tiktok|portrait|vertical|flyer|poster)\\b.*")) {
            return t.matches(".*\\b(flyer|poster)\\b.*") ? new int[]{1080, 1350} : new int[]{1080, 1920};
        }
        if (t.matches(".*\\b(banner|cover|header|thumbnail|landscape|wide|desktop|youtube|slide|presentation|billboard)\\b.*")) {
            return new int[]{1920, 1080};
        }
        return new int[]{1080, 1080};
    }

    /** The designer's instructions, for a picture {@code w} by {@code h}. */
    public static String system(int w, int h) {
        return "You are Ibiko's designer. Make the picture asked for as ONE self-contained HTML document that is the picture"
                + " itself: exactly " + w + " by " + h + " pixels, nothing outside it, nothing to scroll. Use CSS for colour,"
                + " gradients, shadows, rounded shapes, type and layout (flex or grid), and inline <svg> for icons, shapes,"
                + " characters, scenes and illustration (paths, gradients, filters are fine). Google Fonts may be linked;"
                + " no other outside files, no <img> from the web, no script. Make it look professionally designed: one"
                + " clear idea, a strong focal point, a small harmonious palette, generous spacing, crisp edges, text large"
                + " and readable and only when it belongs (a logo, a poster, a card). Fill the whole canvas with a deliberate"
                + " background. Reply with the HTML document only, starting <!doctype html>.";
    }

    /** The ask as the designer gets it, with what the brain imagined when it differs. */
    public static String brief(String what, String imagined) {
        return "Design: " + what + (imagined == null || imagined.equals(what) ? "" : "\nAs imagined: " + imagined);
    }
}
