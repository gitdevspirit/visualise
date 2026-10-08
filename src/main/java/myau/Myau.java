package myau;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.config.Config;
import myau.event.EventManager;
import myau.management.FriendManager;
import myau.management.NotificationManager;
import myau.management.TargetManager;
import myau.module.Module;
import myau.module.ModuleManager;
import myau.module.modules.*;
import myau.property.Property;
import myau.property.PropertyManager;
import myau.render.RenderEventBridge;
import net.minecraftforge.common.MinecraftForge;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Spirit Visuals - client bootstrap. Only the visual feature set is registered here.
 */
public class Myau {
    public static String clientName = "&7[&bSpirit&7]&r ";
    public static String version;
    public static FriendManager friendManager;
    public static TargetManager targetManager;
    public static PropertyManager propertyManager;
    public static ModuleManager moduleManager;
    public static NotificationManager notificationManager;

    public Myau() {
        this.init();
    }

    public void init() {
        friendManager = new FriendManager();
        targetManager = new TargetManager();
        propertyManager = new PropertyManager();
        moduleManager = new ModuleManager();
        notificationManager = new NotificationManager();

        EventManager.register(moduleManager);

        // ── Render ──
        register(new ESP());
        register(new Chams());
        register(new Tracers());
        register(new NameTags());
        register(new BedESP());
        register(new ItemESP());
        register(new ChestESP());
        register(new Xray());
        register(new FullBright());
        register(new Trajectories());
        register(new ViewClip());
        register(new NoHurtCam());
        // ── HUD ──
        register(new HUD());
        register(new InfoHUD());
        register(new TargetHUD());
        register(new Indicators());
        register(new Radar());
        register(new FPScounter());
        register(new Notifications());
        register(new QualityOfLife());
        register(new GuiModule());

        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();
            for (Field field : module.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                final Object value;
                try {
                    value = field.get(module);
                } catch (IllegalAccessException exception) {
                    throw new RuntimeException(exception);
                }
                if (value instanceof Property<?>) {
                    ((Property<?>) value).setOwner(module);
                    properties.add((Property<?>) value);
                }
            }
            propertyManager.properties.put(module.getClass(), properties);
            EventManager.register(module);
        }

        Config config = new Config("default", true);
        if (config.file.exists()) {
            config.load();
        }
        if (friendManager.file.exists()) {
            friendManager.load();
        }
        if (targetManager.file.exists()) {
            targetManager.load();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(config::save));

        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(Myau.class.getResourceAsStream("/version.json")),
                StandardCharsets.UTF_8)) {
            JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
            version = modInfo.get("version").getAsString();
        } catch (Exception exception) {
            version = "dev";
        }

        MinecraftForge.EVENT_BUS.register(new RenderEventBridge());
    }

    private static void register(Module module) {
        moduleManager.modules.put(module.getClass(), module);
    }
}
