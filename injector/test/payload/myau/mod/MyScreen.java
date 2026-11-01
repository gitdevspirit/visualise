package myau.mod;
import net.minecraft.client.gui.GuiScreen;
public class MyScreen extends GuiScreen {
    @Override public void drawScreen(int x, int y, float pt) { System.out.println("  payload: MyScreen.drawScreen"); helper(); }
}
