package myau.mod;
import myau.mixin.IAccessorMinecraft;
import net.minecraft.client.Minecraft;
public class Thing {
    public static void poke() {
        Minecraft mc = Minecraft.getMinecraft();
        IAccessorMinecraft acc = (IAccessorMinecraft) mc;
        int before = acc.getRightClickDelayTimer();
        acc.setRightClickDelayTimer(before + 100);
        net.minecraft.client.gui.GuiScreen g = new MyScreen();
        g.drawScreen(1, 2, 3f);
        System.out.println("  payload: accessor read " + before + ", wrote " + acc.getRightClickDelayTimer());
    }
}
