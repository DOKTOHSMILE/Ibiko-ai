package ai.ibiko;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import ai.ibiko.act.Actions;
import ai.ibiko.brain.BrainAnswer;
import ai.ibiko.brain.LearningJournal;
import ai.ibiko.brain.OfflineBrain;
import ai.ibiko.brain.Onboarding;
import ai.ibiko.brain.PendingAction;
import ai.ibiko.stage.StageMode;
import ai.ibiko.stage.StageListener;
import ai.ibiko.stage.StageView;
import ai.ibiko.net.Connectivity;
import ai.ibiko.net.KnowledgeStore;
import ai.ibiko.net.SyncWorker;
import ai.ibiko.turn.LocalTurn;
import ai.ibiko.turn.TurnLine;
import ai.ibiko.turn.TurnRoute;
import ai.ibiko.voice.AmbientPad;
import ai.ibiko.voice.ListenBridge;
import ai.ibiko.voice.ListenCallback;
import ai.ibiko.voice.TalkBridge;

/** Retry and a clean workspace. Moved out of MainActivity. */
final class Retries {
    private final MainActivity a;

    Retries(MainActivity a) {
        this.a = a;
    }

    /** "Retry", "try again", "ask that again": words that ask for the message before them, never asked again themselves. */
    static boolean isRetry(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
        return t.matches("^(?:please )?(retry|try (that )?again|again|redo|regenerate|ask (that )?again|one more time)( please)?$");
    }

    /**
     * "Retry", "try again": the user's last message before this one is asked again, as it was (with any file it
     * carried), instead of being read as a new request.
     */
    void retryLast() {
        if (inPlace()) {
            return;
        }
        if (a.lastAsked != null && !a.lastAsked.trim().isEmpty() && !isRetry(a.lastAsked)) {
            a.askTheBrain(a.lastAsked); // the whole question, with the file it carried
            return;
        }
        java.util.List<ai.ibiko.chat.ChatBook.Message> msgs = a.chats == null ? new java.util.ArrayList<ai.ibiko.chat.ChatBook.Message>()
                : a.chats.current().messages;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ai.ibiko.chat.ChatBook.Message m = msgs.get(i);
            if (!"user".equals(m.role) || !"text".equals(m.kind) || m.body == null) {
                continue;
            }
            if (isRetry(m.body) || ai.ibiko.turn.Meaning.Command.RETRY == a.meanings().get(m.body)) {
                continue; // every "retry" is passed over, not only the newest
            }
            a.askTheBrain(m.body);
            return;
        }
        a.play(LocalTurn.speakAs(a.stage.agent(), "There's nothing earlier in this chat to ask again."), null);
    }

    /**
     * Retry as a redo, not a new request: the message the reply answered is found (a typed "retry" passed over), the
     * old reply and everything after it are taken out of the chat (and out of the model's memory of it), and that same
     * message is asked again, so the new reply takes the old one's place. False when the chat has no such message.
     */
    boolean inPlace() {
        if (a.chats == null) {
            return false;
        }
        ai.ibiko.chat.ChatBook.Section here = a.chats.current();
        java.util.List<ai.ibiko.chat.ChatBook.Message> msgs = here.messages;
        int at = -1;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ai.ibiko.chat.ChatBook.Message m = msgs.get(i);
            if ("user".equals(m.role) && "text".equals(m.kind) && m.body != null && !m.body.trim().isEmpty()
                    && !isRetry(m.body) && ai.ibiko.turn.Meaning.Command.RETRY != a.meanings().get(m.body)) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return false;
        }
        String asked = msgs.get(at).body;
        if (!a.chats.cutFrom(here.id, at)) {
            return false;
        }
        if (a.chatPane != null) {
            a.chatPane.show();
        }
        if (a.model != null && a.hub != null) {
            // the model forgets the old exchange too, on its own worker (a reply still being written is stopped first)
            final ai.ibiko.llm.LocalModel m = a.model;
            a.hub.worker.execute(() -> m.rewindTo(asked));
        }
        a.lastAsked = null;
        a.voiceTurn = false;
        a.answer(asked); // the same message, sent again in its place: its new reply replaces the old one
        return true;
    }

    /** Clears Ibiko's workspace (clones, sandbox, files) and its kept work, after a yes: it can't be undone. */
    void clearWorkspace() {
        final java.io.File ws = a.chatSpace();
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setMessage("Delete this chat's files on this device: its cloned repositories, its memories and pictures?"
                        + " Other chats' files stay. Changes that weren't pushed are lost.")
                .setPositiveButton("Delete", (d, w) -> new Thread(() -> {
                    deleteTree(ws);
                    a.handler.post(() -> a.play(LocalTurn.speakAs(a.stage.agent(),
                            "Done. This chat's files are deleted; other chats keep theirs."), null));
                }, "ibiko-clear-workspace").start())
                .setNegativeButton("Keep it", null) // nothing happened, so nothing is said
                .show();
    }

    /**
     * Deletes {@code f} and what is in it. A link is removed, never followed: a link in a chat's files (made by a
     * command, or by git) to /sdcard or to the app's own folder made "delete this chat's files" delete those.
     */
    static void deleteTree(java.io.File f) {
        java.io.File[] kids = java.nio.file.Files.isSymbolicLink(f.toPath()) ? null : f.listFiles();
        if (kids != null) {
            for (java.io.File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }
}
