package myau.agent;

import visualise.boot.Hooks;

/** Called once by the injector engine after the payload is defined in the game's classloader. */
public final class Entry {
    private Entry() {}

    public static void start() {
        Hooks.sink = new PayloadSink();
    }
}
