package net.minecraft.client.gui;
public class GuiScreen {
    public void drawScreen(int x, int y, float pt) { System.out.println("  game: BASE drawScreen (override NOT applied)"); }
    protected void helper() { System.out.println("  game: helper"); }
}
