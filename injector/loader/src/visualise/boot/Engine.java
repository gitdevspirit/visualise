package visualise.boot;

/** Implemented by the engine jar (loaded in an isolated loader so its ASM never clashes with the game's). */
public interface Engine {
    /** Returns replacement class bytes, or null to leave the class alone. */
    byte[] transform(ClassLoader loader, String internalName, byte[] classBytes) throws Exception;

    /** Defines the payload in the game's classloader and runs its entry point. Called from the first game hook. */
    void startPayload() throws Exception;
}
