package myau.util;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class KeyBindUtil {
    public static String getKeyName(int keyCode) {
        if (keyCode < 0) {
            int mouseButton = keyCode + 100;
            switch (mouseButton) {
                case 0:
                    return "LMB";
                case 1:
                    return "RMB";
                case 2:
                    return "MMB";
                case 3:
                    return "MOUSE3";
                case 4:
                    return "MOUSE4";
                case 5:
                    return "MOUSE5";
                case 6:
                    return "MOUSE6";
                case 7:
                    return "MOUSE7";
                default:
                    String buttonName = Mouse.getButtonName(mouseButton);
                    return buttonName != null ? buttonName : "MOUSE" + mouseButton;
            }
        }
        return Keyboard.getKeyName(keyCode);
    }

    public static boolean isKeyDown(int keyCode) {
        return keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
    }

    public static void updateKeyState(int keyCode) {
        KeyBindUtil.setKeyBindState(keyCode, keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode));
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        KeyBinding.setKeyBindState(keyCode, pressed);
    }

    public static void pressKeyOnce(int keyCode) {
        KeyBinding.onTick(keyCode);
    }

    /**
     * Parses a key name typed in chat: keyboard names (LWJGL) plus mouse buttons
     * (LMB, RMB, MMB, MOUSE3, MOUSE4, ...). Returns null when the name is unknown.
     */
    public static Integer parseKeyName(String name) {
        String n = name.trim().toUpperCase();
        switch (n) {
            case "LMB": case "LEFT": case "LEFTCLICK": return -100;
            case "RMB": case "RIGHT": case "RIGHTCLICK": return -99;
            case "MMB": case "MIDDLE": case "MIDDLECLICK": return -98;
            default:
        }
        if (n.startsWith("MOUSE")) {
            try {
                int button = Integer.parseInt(n.substring(5));
                if (button >= 0 && button < 16) return button - 100;
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
        int key = Keyboard.getKeyIndex(n);
        return key == Keyboard.KEY_NONE ? null : key;
    }
}
