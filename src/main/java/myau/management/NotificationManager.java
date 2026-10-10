package myau.management;

import myau.util.ChatUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {

    public static class NotificationEntry {
        public final String message;
        public final long startMillis;
        public final long durationMillis;
        public final int color; // RGB
        /** Set for module-toggle notifications, null for plain messages. */
        public final String moduleName;
        /** TRUE = toggled on, FALSE = toggled off, null = not a toggle notification. */
        public final Boolean state;

        public NotificationEntry(String message, long durationMillis) {
            this(message, durationMillis, 0xFFFFFF);
        }

        public NotificationEntry(String message, long durationMillis, int color) {
            this(message, durationMillis, color, null, null);
        }

        public NotificationEntry(String message, long durationMillis, int color, String moduleName, Boolean state) {
            this.message = message;
            this.durationMillis = durationMillis;
            this.color = color;
            this.moduleName = moduleName;
            this.state = state;
            this.startMillis = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return this.durationMillis >= 0 && System.currentTimeMillis() - this.startMillis >= this.durationMillis;
        }

        public long getAge() {
            return System.currentTimeMillis() - this.startMillis;
        }
    }

    private final List<NotificationEntry> entries = new ArrayList<>();

    // Batches rapid-fire module toggles (e.g. loading a config that flips many
    // modules at once) into a single "N modules toggled" notification instead
    // of spamming one card per module.
    private static final long BATCH_WINDOW_MS = 150L;
    private static final int  BATCH_THRESHOLD = 3;

    private final List<PendingToggle> pendingToggles = new ArrayList<>();
    private long batchStartMillis = -1L;

    private static final class PendingToggle {
        final String name;
        final boolean enabled;
        final long durationMillis;
        final int color;
        final boolean toast;
        final int chatStyle; // -1 = no chat line

        PendingToggle(String name, boolean enabled, long durationMillis, int color, boolean toast, int chatStyle) {
            this.name = name;
            this.enabled = enabled;
            this.durationMillis = durationMillis;
            this.color = color;
            this.toast = toast;
            this.chatStyle = chatStyle;
        }
    }

    /** Use this (instead of add()) for module enable/disable notifications so rapid bursts get batched. */
    public synchronized void addToggle(String moduleName, boolean enabled, long durationMillis, int color) {
        addToggle(moduleName, enabled, durationMillis, color, true, -1);
    }

    /**
     * @param toast     show an on-screen notification
     * @param chatStyle -1 for no chat line, otherwise the ChatUtil.sendToggle style (0 or 1)
     */
    public synchronized void addToggle(String moduleName, boolean enabled, long durationMillis, int color,
                                       boolean toast, int chatStyle) {
        pendingToggles.add(new PendingToggle(moduleName, enabled, durationMillis, color, toast, chatStyle));

        if (batchStartMillis < 0) {
            batchStartMillis = System.currentTimeMillis();
        }
    }

    private synchronized void flushBatchIfDue() {
        if (batchStartMillis < 0) return;
        if (System.currentTimeMillis() - batchStartMillis < BATCH_WINDOW_MS) return;

        List<PendingToggle> batch = new ArrayList<>(pendingToggles);
        pendingToggles.clear();
        batchStartMillis = -1L;

        List<PendingToggle> enabledList = new ArrayList<>();
        List<PendingToggle> disabledList = new ArrayList<>();

        for (PendingToggle t : batch) {
            (t.enabled ? enabledList : disabledList).add(t);
        }

        emit(enabledList, true);
        emit(disabledList, false);
    }

    /** Turns one batch of same-direction toggles into toasts and/or chat lines (3+ are collapsed). */
    private void emit(List<PendingToggle> list, boolean enabled) {
        if (list.isEmpty()) return;
        String word = enabled ? " toggled" : " untoggled";

        List<PendingToggle> toasts = new ArrayList<>();
        List<PendingToggle> chats = new ArrayList<>();
        for (PendingToggle t : list) {
            if (t.toast) toasts.add(t);
            if (t.chatStyle >= 0) chats.add(t);
        }

        if (toasts.size() >= BATCH_THRESHOLD) {
            PendingToggle sample = toasts.get(0);
            String group = toasts.size() + " modules";
            entries.add(new NotificationEntry(group + word, sample.durationMillis, sample.color, group, enabled));
        } else {
            for (PendingToggle t : toasts) {
                entries.add(new NotificationEntry(t.name + word, t.durationMillis, t.color, t.name, enabled));
            }
        }

        if (chats.size() >= BATCH_THRESHOLD) {
            ChatUtil.sendToggle(chats.size() + " modules", enabled, chats.get(0).chatStyle);
        } else {
            for (PendingToggle t : chats) {
                ChatUtil.sendToggle(t.name, enabled, t.chatStyle);
            }
        }
    }

    public synchronized void add(String message) {
        this.add(message, 3000L);
    }

    public synchronized void add(String message, long durationMillis) {
        this.add(message, durationMillis, 0xFFFFFF);
    }

    public synchronized void add(String message, int color) {
        this.add(message, 3000L, color);
    }

    public synchronized void add(String message, long durationMillis, int color) {
        this.entries.add(new NotificationEntry(message, durationMillis, color));
    }

    public synchronized List<NotificationEntry> getActive() {
        flushBatchIfDue();

        // cleanup expired entries and return a copy of active entries (newest last)
        Iterator<NotificationEntry> it = this.entries.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired()) {
                it.remove();
            }
        }
        return new ArrayList<>(this.entries);
    }

    public synchronized void clear() {
        this.entries.clear();
        this.pendingToggles.clear();
        this.batchStartMillis = -1L;
    }
}
