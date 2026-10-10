package myau.util;

import myau.enums.ChatColors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

public class ChatUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void send(IChatComponent iChatComponent) {
        if (ChatUtil.mc.thePlayer != null) {
            ChatUtil.mc.thePlayer.addChatMessage(iChatComponent);
        }
    }

    public static void sendFormatted(String string) {
        ChatUtil.send(new ChatComponentText(ChatColors.formatColor(string)));
    }

    public static void sendRaw(String string) {
        ChatUtil.send(new ChatComponentText(string));
    }

    /**
     * Chat line for a module toggle:  TOGGLED [Module]  /  UNTOGGLED [Module].
     * style 0 = TOGGLED [Module], style 1 = {TOGGLED} [Module].
     */
    public static void sendToggle(String name, boolean enabled, int style) {
        String word = enabled ? "TOGGLED" : "UNTOGGLED";
        String color = enabled ? "&a&l" : "&c&l";
        String state = style == 1 ? color + "{" + word + "}" : color + word;
        ChatUtil.sendFormatted(state + " &r&7[&f" + name + "&7]");
    }

    public static void sendMessage(String string) {
        if (ChatUtil.mc.thePlayer != null) {
            ChatUtil.mc.thePlayer.sendChatMessage(string);
        }
    }
}
