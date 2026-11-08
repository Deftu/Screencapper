package dev.deftu.screencapper.chat;

import dev.deftu.screencapper.MainThread;
import dev.deftu.screencapper.mixin.ChatAccessor;
import net.kyori.adventure.text.Component;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.platform.v1.Platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat lines that can be rewritten after they're posted.
 * <p>
 * New text always goes through OneConfig's own chat path so its component conversion is reused; the freshly added
 * entry is then moved into the slot of the line it replaces. A line is only tracked when posting it actually added a
 * new entry, since chat filters and other mods can drop messages.
 */
public final class ChatLines {
    private static final int MAX_TRACKED = 256;

    private static final Map<String, Object> entries = new LinkedHashMap<String, Object>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    private ChatLines() {
    }

    /** Whether a line is tracked under [key]. Lines that leave chat are dropped once an edit finds them gone */
    public static boolean isTracked(String key) {
        synchronized (entries) {
            return entries.containsKey(key);
        }
    }

    public static void post(String key, Component text) {
        MainThread.run(() -> {
            Object added = display(text);
            synchronized (entries) {
                if (added != null) entries.put(key, added);
                else entries.remove(key);
            }
        });
    }

    /** Rewrites the line posted under [key] in place. Does nothing when that line has already left chat */
    public static void replace(String key, Component text) {
        MainThread.run(() -> {
            Object previous;
            synchronized (entries) {
                previous = entries.get(key);
            }
            List<Object> messages = messages();
            if (previous == null || messages == null || indexOf(messages, previous) < 0) {
                untrack(key);
                return;
            }

            Object fresh = display(text);
            if (fresh == null) return;

            // Posting can trim the oldest lines, so the old slot is looked up again afterwards.
            int index = indexOf(messages, previous);
            messages.remove(0);
            if (index < 0) {
                untrack(key);
            } else {
                messages.set(index - 1, fresh);
                synchronized (entries) {
                    entries.put(key, fresh);
                }
            }
            refresh();
        });
    }

    public static void remove(String key) {
        MainThread.run(() -> {
            Object previous;
            synchronized (entries) {
                previous = entries.remove(key);
            }
            List<Object> messages = messages();
            if (previous == null || messages == null) return;
            int index = indexOf(messages, previous);
            if (index < 0) return;
            messages.remove(index);
            refresh();
        });
    }

    /** Posts [text] and returns the chat entry it created, or null when nothing was added */
    private static Object display(Component text) {
        List<Object> messages = messages();
        Object head = messages == null || messages.isEmpty() ? null : messages.get(0);
        //? if >=1.21 {
        Platform.compatibility().displayChatMessage(text);
        //?} else {
        /*ChatAccessor chat = chat();
        if (chat == null) return null;
        ((net.minecraft.client.gui.chat.ChatGui) chat).addMessage(LegacyText.convert(text));
        *///?}
        if (messages == null || messages.isEmpty()) return null;
        Object added = messages.get(0);
        return added == head ? null : added;
    }

    private static void untrack(String key) {
        synchronized (entries) {
            entries.remove(key);
        }
    }

    private static int indexOf(List<Object> messages, Object target) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == target) return i;
        }
        return -1;
    }

    private static List<Object> messages() {
        ChatAccessor chat = chat();
        return chat == null ? null : chat.screencapper$messages();
    }

    private static void refresh() {
        ChatAccessor chat = chat();
        if (chat != null) chat.screencapper$refresh();
    }

    private static ChatAccessor chat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) return null;
        //? if >=26.2 {
        return (ChatAccessor) minecraft.gui.hud.getChat();
        //?} else {
        /*return (ChatAccessor) minecraft.gui.getChat();
        *///?}
    }
}
