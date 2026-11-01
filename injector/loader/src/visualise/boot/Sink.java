package visualise.boot;

/** Receiver for game hooks. Implemented by the payload (inside the game's classloader). */
public interface Sink {
    void startGameHead();
    void startGameReturn();
    void runTickHead();
    void runTickReturn();
    void loadWorld();
    /** @return true to cancel the click. */
    boolean clickMouse();
    void resize();
    void render3D(float partialTicks);
    void render2D(float partialTicks);
    void keyState(int key, boolean state);
}
