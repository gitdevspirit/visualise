package visualise.boot;

/**
 * Static entry points that the engine's injected bytecode calls. Lives on the bootstrap path so every
 * classloader in the game can see it. Never throws into the game.
 */
public final class Hooks {
    private Hooks() {}

    public static volatile Sink sink;

    public static void startGameHead()          { if (sink == null) Boot.startPayload(); Sink s = sink; if (s != null) try { s.startGameHead(); } catch (Throwable t) { Boot.log(t); } }
    public static void startGameReturn()        { Sink s = sink; if (s != null) try { s.startGameReturn(); } catch (Throwable t) { Boot.log(t); } }
    public static void runTickHead()            { Sink s = sink; if (s != null) try { s.runTickHead(); } catch (Throwable t) { Boot.log(t); } }
    public static void runTickReturn()          { Sink s = sink; if (s != null) try { s.runTickReturn(); } catch (Throwable t) { Boot.log(t); } }
    public static void loadWorld()              { Sink s = sink; if (s != null) try { s.loadWorld(); } catch (Throwable t) { Boot.log(t); } }
    public static boolean clickMouse()          { Sink s = sink; if (s != null) try { return s.clickMouse(); } catch (Throwable t) { Boot.log(t); } return false; }
    public static void resize()                 { Sink s = sink; if (s != null) try { s.resize(); } catch (Throwable t) { Boot.log(t); } }
    public static void render3D(float pt)       { Sink s = sink; if (s != null) try { s.render3D(pt); } catch (Throwable t) { Boot.log(t); } }
    public static void render2D(float pt)       { Sink s = sink; if (s != null) try { s.render2D(pt); } catch (Throwable t) { Boot.log(t); } }
    public static void keyState(int k, boolean d) { Sink s = sink; if (s != null) try { s.keyState(k, d); } catch (Throwable t) { Boot.log(t); } }
}
