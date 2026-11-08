package dev.deftu.screencapper.chat;

/** Holds the vanilla screenshot message that 1.8.9 should drop instead of printing */
public final class LegacyChatSuppression {
    private static Object suppressed;

    private LegacyChatSuppression() {
    }

    public static void suppress(Object message) {
        suppressed = message;
    }

    public static boolean consume(Object message) {
        if (message == null || message != suppressed) return false;
        suppressed = null;
        return true;
    }
}
