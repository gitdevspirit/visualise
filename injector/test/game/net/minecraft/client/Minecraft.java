package net.minecraft.client;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.settings.KeyBinding;
public class Minecraft {
    private static Minecraft INSTANCE;
    private final int[] unused = new int[0];
    private int rightClickDelayTimer = 7;      // private + final-less: the accessor must still reach it
    private boolean clicks;
    public static Minecraft getMinecraft() { return INSTANCE; }
    private void startGame() { System.out.println("  game: startGame body"); }
    private void runTick() {
        System.out.println("  game: runTick body");
        KeyBinding.setKeyBindState(42, true);
    }
    public void loadWorld(WorldClient w, String s) { System.out.println("  game: loadWorld"); }
    private void clickMouse() { System.out.println("  game: clickMouse body (not cancelled)"); }
    public void updateFramebufferSize() { System.out.println("  game: resize"); }
    public static void main(String[] a) throws Exception {
        INSTANCE = new Minecraft();
        System.out.println("game: boot");
        INSTANCE.startGame();
        INSTANCE.runTick();
        INSTANCE.loadWorld(null, "w");
        INSTANCE.clickMouse();
        INSTANCE.clickMouse();
        INSTANCE.updateFramebufferSize();
        new EntityRenderer().renderWorldPass(0, 0.5f, 0L);
        new GuiIngame().renderGameOverlay(0.25f);
        System.out.println("game: rightClickDelayTimer now " + INSTANCE.rightClickDelayTimer);
    }
}
