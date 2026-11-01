package net.minecraft.client.renderer;
import net.minecraft.profiler.Profiler;
public class EntityRenderer {
    private final Profiler mcProfiler = new Profiler();
    public void renderWorldPass(int pass, float partialTicks, long nano) {
        mcProfiler.endStartSection("terrain");
        mcProfiler.endStartSection("hand");
        System.out.println("  game: hand pass");
    }
}
