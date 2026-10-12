package myau.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.util.ChatUtil;
import myau.util.KeyBindUtil;
import net.minecraft.network.play.client.C01PacketChatMessage;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Named configs: the {@code .c} chat commands, per-config keybinds, and the list shown in the ClickGUI.
 *
 *   .c s <name>      save current settings        .c l <name>   load
 *   .c d <name>      delete                       .c list       list
 *   .c b <name> <key|none>   bind a key that loads the config (also settable in the ClickGUI)
 */
public class ConfigManager {
    public static final ConfigManager INSTANCE = new ConfigManager();

    private static final File DIR = new File("./config/SpiritVisuals/");
    private static final File BINDS = new File(DIR, "_binds.json");
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$");

    /** Name of the config last loaded or saved (highlighted in the GUI). */
    public static String active = "default";
    /** Bumped whenever the set of configs changes so the GUI window rebuilds itself. */
    public static int version = 0;

    private static final Map<String, Integer> binds = new LinkedHashMap<>();
    private static final Map<String, ConfigModule> modules = new LinkedHashMap<>();

    private ConfigManager() {}

    public static void init() {
        loadBinds();
        modules();
        EventManager.register(INSTANCE);
    }

    // ── files ────────────────────────────────────────────────────────────────

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        File[] files = DIR.listFiles((d, n) -> n.endsWith(".json") && !n.startsWith("_"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                out.add(n.substring(0, n.length() - 5));
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static String existing(String name) {
        for (String n : names()) if (n.equalsIgnoreCase(name)) return n;
        return null;
    }

    public static void save(String name) {
        if (!VALID.matcher(name).matches()) {
            msg("&cInvalid name. Use letters, numbers, - and _ (max 32).");
            return;
        }
        String known = existing(name);
        if (known != null) name = known;
        new Config(name, true).save();
        active = name;
        version++;
        modules();
    }

    public static void load(String name) {
        String known = existing(name);
        if (known == null) {
            msg("&cNo config named &f" + name + "&c. Use &f.c list&c.");
            return;
        }
        new Config(known, false).load();
        active = known;
    }

    public static void delete(String name) {
        String known = existing(name);
        if (known == null) {
            msg("&cNo config named &f" + name + "&c.");
            return;
        }
        if (known.equalsIgnoreCase("default")) {
            msg("&cThe default config can't be deleted.");
            return;
        }
        if (new File(DIR, known + ".json").delete()) {
            binds.remove(known);
            saveBinds();
            version++;
            modules();
            msg("Deleted config &f" + known + "&r.");
        } else {
            msg("&cCouldn't delete &f" + known + "&c.");
        }
    }

    // ── GUI list + binds ─────────────────────────────────────────────────────

    /** One button per saved config (cached so a button keeps its open/animation state). */
    public static List<Module> modules() {
        List<String> names = names();
        modules.keySet().retainAll(names);
        for (String n : names) {
            ConfigModule m = modules.get(n);
            if (m == null) {
                m = new ConfigModule(n);
                Integer key = binds.get(n);
                if (key != null) m.setKey(key);
                modules.put(n, m);
            }
        }
        List<Module> out = new ArrayList<>();
        for (String n : names) out.add(modules.get(n));
        return out;
    }

    public static void setBind(String name, int key) {
        String known = existing(name);
        if (known == null) {
            msg("&cNo config named &f" + name + "&c.");
            return;
        }
        modules();
        ConfigModule m = modules.get(known);
        if (m != null) m.setKey(key);
        saveBinds();
        msg("Config &f" + known + "&r bound to &f" + (key == 0 ? "None" : KeyBindUtil.getKeyName(key)) + "&r.");
    }

    private static void loadBinds() {
        binds.clear();
        try {
            if (!BINDS.exists()) return;
            JsonElement el = new JsonParser().parse(new FileReader(BINDS));
            if (el != null && el.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                    binds.put(e.getKey(), e.getValue().getAsInt());
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Writes every config's key (taken from the GUI buttons) to _binds.json. */
    public static void saveBinds() {
        try {
            binds.clear();
            JsonObject obj = new JsonObject();
            for (ConfigModule m : modules.values()) {
                if (m.getKey() != 0) {
                    binds.put(m.getName(), m.getKey());
                    obj.addProperty(m.getName(), m.getKey());
                }
            }
            DIR.mkdirs();
            try (PrintWriter w = new PrintWriter(new FileWriter(BINDS))) {
                w.println(Config.gson.toJson(obj));
            }
        } catch (Exception ignored) {
        }
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (ConfigModule m : new ArrayList<>(modules.values())) {
            if (m.getKey() != 0 && m.getKey() == event.getKey()) {
                load(m.getName());
            }
        }
    }

    // ── chat commands ────────────────────────────────────────────────────────

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || !(event.getPacket() instanceof C01PacketChatMessage)) return;
        String text = ((C01PacketChatMessage) event.getPacket()).getMessage().trim();
        String lower = text.toLowerCase();
        String rest;
        if (lower.equals(".c") || lower.equals(".config") || lower.equals(".cfg")) {
            rest = "";
        } else if (lower.startsWith(".c ")) {
            rest = text.substring(3);
        } else if (lower.startsWith(".config ")) {
            rest = text.substring(8);
        } else if (lower.startsWith(".cfg ")) {
            rest = text.substring(5);
        } else {
            return;
        }
        event.setCancelled(true);
        run(rest.trim().isEmpty() ? new String[0] : rest.trim().split("\\s+"));
    }

    private static void run(String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase();
        String arg = a.length > 1 ? a[1] : null;
        switch (sub) {
            case "s": case "save":
                if (arg == null) { usage(); return; }
                save(arg);
                return;
            case "l": case "load":
                if (arg == null) { usage(); return; }
                load(arg);
                return;
            case "d": case "del": case "delete": case "remove":
                if (arg == null) { usage(); return; }
                delete(arg);
                return;
            case "list": case "ls":
                List<String> names = names();
                if (names.isEmpty()) {
                    msg("No configs yet. Save one with &f.c s <name>&r.");
                } else {
                    StringBuilder sb = new StringBuilder("Configs: ");
                    for (int i = 0; i < names.size(); i++) {
                        String n = names.get(i);
                        sb.append(n.equalsIgnoreCase(active) ? "&a" : "&f").append(n).append("&r");
                        Integer k = binds.get(n);
                        if (k != null && k != 0) sb.append(" &7[").append(KeyBindUtil.getKeyName(k)).append("]&r");
                        if (i < names.size() - 1) sb.append("&7, &r");
                    }
                    msg(sb.toString());
                }
                return;
            case "b": case "bind":
                if (arg == null || a.length < 3) { usage(); return; }
                int key;
                if (a[2].equalsIgnoreCase("none") || a[2].equalsIgnoreCase("clear")) {
                    key = 0;
                } else {
                    Integer parsed = KeyBindUtil.parseKeyName(a[2]);
                    if (parsed == null) {
                        msg("&cUnknown key &f" + a[2] + "&c. &7Keys, or LMB / RMB / MMB / MOUSE4.");
                        return;
                    }
                    key = parsed;
                }
                setBind(arg, key);
                return;
            default:
                usage();
        }
    }

    private static void usage() {
        msg("&f.c s <name>&7 save  &f.c l <name>&7 load  &f.c d <name>&7 delete  &f.c list  &f.c b <name> <key|LMB|RMB|none>");
    }

    /** Chat line for command feedback (usage, list, errors): tagged "Config", not with the client name. */
    private static void msg(String s) {
        ChatUtil.sendFormatted("&7[&fConfig&7]&r " + s + "&r");
    }

    /**
     * Result of a save / load. Always a chat line (like the TOGGLED ones, no client-name prefix), plus a
     * notification toast when the Notifications module is on.
     */
    public static void notify(String action, String name, boolean ok) {
        // Startup / shutdown loads and saves happen with no world: stay quiet.
        if (net.minecraft.client.Minecraft.getMinecraft().thePlayer == null) return;
        String color = ok ? "&a&l" : "&c&l";
        ChatUtil.sendFormatted(color + action + " &r&7[&f" + name + "&7]&r");
        try {
            myau.module.modules.Notifications n =
                    (myau.module.modules.Notifications) Myau.moduleManager.getModule(myau.module.modules.Notifications.class);
            if (n != null && n.isEnabled() && n.toasts.getValue() && Myau.notificationManager != null) {
                Myau.notificationManager.add(action + " [" + name + "]", (long) (n.duration.getValue() * 1000.0), ok ? 0x55FF55 : 0xFF5555);
            }
        } catch (Exception ignored) {
        }
    }
}
