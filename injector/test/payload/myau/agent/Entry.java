package myau.agent;
import visualise.boot.Hooks;
public final class Entry { public static void start() { Hooks.sink = new PayloadSink(); } }
