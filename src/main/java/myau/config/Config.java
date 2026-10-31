package myau.config;

import com.google.gson.*;
import myau.Myau;
import myau.module.Module;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.KeybindSetting;
import myau.module.Setting;
import myau.module.SliderSetting;
import myau.util.ChatUtil;
import myau.property.Property;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;

public class Config {
    // Own logger instead of Minecraft's private static one: this class runs inside Myau.init() at startup, so it
    // must not depend on a mixin accessor having applied (a failed accessor means a ClassCastException here).
    private static final Logger LOGGER = LogManager.getLogger("SpiritVisuals");
    public static Minecraft mc = Minecraft.getMinecraft();
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public String name;
    public File file;

    public static String lastConfig;

    public Config(String name, boolean newConfig) {
        this.name = name;
        lastConfig = name;
        if (name.equals("!") || name.equals("default")) {
            this.name = "default";
        }
        this.file = new File("./config/SpiritVisuals/", String.format("%s.json", this.name));
        try {
            file.getParentFile().mkdirs();
            if (newConfig) {
                LOGGER.info(String.format("Created: %s", this.file.getName()));
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    public void load() {
        try {
            if (!file.exists()) {
                ConfigManager.notify("NOT FOUND", this.name, false);
                save();
                return;
            }

            JsonElement parsed = new JsonParser().parse(new BufferedReader(new FileReader(file)));
            if (parsed == null || !parsed.isJsonObject()) {
                ConfigManager.notify("INVALID", this.name, false);
                return;
            }

            JsonObject jsonObject = parsed.getAsJsonObject();
            for (Module module : Myau.moduleManager.modules.values()) {
                JsonElement moduleEl = jsonObject.get(module.getName());
                if (moduleEl != null && moduleEl.isJsonObject()) {
                    JsonObject object = moduleEl.getAsJsonObject();

                    // Existing property system
                    ArrayList<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
                    if (list != null) {
                        for (Property<?> property : list) {
                            if (object.has(property.getName())) {
                                try {
                                    property.read(object);
                                } catch (Exception e) {
                                    LOGGER.warn(
                                        String.format("Failed to load property %s for module %s",
                                            property.getName(), module.getName()));
                                }
                            }
                        }
                    }

                    // New settings system
                    if (object.has("settings")) {
                        JsonObject settingsObj = object.getAsJsonObject("settings");
                        for (Setting setting : module.getSettings()) {
                            if (!settingsObj.has(setting.getName())) continue;
                            try {
                                if (setting instanceof SliderSetting) {
                                    ((SliderSetting) setting).setValue(
                                        settingsObj.get(setting.getName()).getAsDouble());
                                } else if (setting instanceof BooleanSetting) {
                                    ((BooleanSetting) setting).setValue(
                                        settingsObj.get(setting.getName()).getAsBoolean());
                                } else if (setting instanceof KeybindSetting) {
                                    ((KeybindSetting) setting).setKeyCode(
                                        settingsObj.get(setting.getName()).getAsInt());
                                } else if (setting instanceof DropdownSetting) {
                                    ((DropdownSetting) setting).setIndex(
                                        settingsObj.get(setting.getName()).getAsInt());
                                }
                            } catch (Exception e) {
                                LOGGER.warn(
                                    String.format("Failed to load setting %s for module %s",
                                        setting.getName(), module.getName()));
                            }
                        }
                    }

                    if (object.has("toggled")) {
                        JsonElement toggled = object.get("toggled");
                        if (toggled != null && toggled.isJsonPrimitive()) {
                            module.setEnabled(toggled.getAsBoolean());
                        }
                    }

                    if (object.has("key")) {
                        JsonElement key = object.get("key");
                        if (key != null && key.isJsonPrimitive()) {
                            module.setKey(key.getAsInt());
                        }
                    }

                    if (object.has("hidden")) {
                        JsonElement hidden = object.get("hidden");
                        if (hidden != null && hidden.isJsonPrimitive()) {
                            module.setHidden(hidden.getAsBoolean());
                        }
                    }
                }
            }
            
            
            
            ConfigManager.notify("LOADED", this.name, true);
        } catch (FileNotFoundException e) {
            ConfigManager.notify("NOT FOUND", this.name, false);
        } catch (JsonSyntaxException e) {
            ConfigManager.notify("INVALID JSON", this.name, false);
            LOGGER.error("JSON Syntax Error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Error loading config: " + e.getMessage());
            ConfigManager.notify("LOAD FAILED", this.name, false);
        }
    }

    public void save() {
        try {
            
            
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject object = new JsonObject();
            for (Module module : Myau.moduleManager.modules.values()) {
                JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("toggled", module.isEnabled());
                moduleObject.addProperty("key", module.getKey());
                moduleObject.addProperty("hidden", module.isHidden());

                // Existing property system
                ArrayList<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
                if (list != null) {
                    for (Property<?> property : list) {
                        try {
                            property.write(moduleObject);
                        } catch (Exception e) {
                            LOGGER.warn(
                                String.format("Failed to save property %s for module %s",
                                    property.getName(), module.getName()));
                        }
                    }
                }

                // New settings system
                if (!module.getSettings().isEmpty()) {
                    JsonObject settingsObj = new JsonObject();
                    for (Setting setting : module.getSettings()) {
                        try {
                            if (setting instanceof SliderSetting) {
                                settingsObj.addProperty(setting.getName(),
                                    ((SliderSetting) setting).getValue());
                            } else if (setting instanceof BooleanSetting) {
                                settingsObj.addProperty(setting.getName(),
                                    ((BooleanSetting) setting).getValue());
                            } else if (setting instanceof KeybindSetting) {
                                settingsObj.addProperty(setting.getName(),
                                    ((KeybindSetting) setting).getKeyCode());
                            } else if (setting instanceof DropdownSetting) {
                                settingsObj.addProperty(setting.getName(),
                                    ((DropdownSetting) setting).getIndex());
                            }
                        } catch (Exception e) {
                            LOGGER.warn(
                                String.format("Failed to save setting %s for module %s",
                                    setting.getName(), module.getName()));
                        }
                    }
                    moduleObject.add("settings", settingsObj);
                }

                object.add(module.getName(), moduleObject);
            }

            PrintWriter printWriter = new PrintWriter(new FileWriter(file));
            printWriter.println(gson.toJson(object));
            printWriter.close();
            ConfigManager.notify("SAVED", this.name, true);
        } catch (IOException e) {
            LOGGER.error("Error saving config: " + e.getMessage());
            ConfigManager.notify("SAVE FAILED", this.name, false);
        }
    }
}
