package myau.config;

import myau.module.Module;

/**
 * A saved config shown as a button in the ClickGUI's "Configs" window. Left click loads it, right click
 * opens its Bind row (a key that loads it). Never registered with the ModuleManager, so it is not a real
 * feature and is not written into configs.
 */
public class ConfigModule extends Module {
    public ConfigModule(String name) {
        super(name, false, true);
    }

    /** Lit up while this is the most recently loaded / saved config. */
    @Override
    public boolean isEnabled() {
        return getName().equalsIgnoreCase(ConfigManager.active);
    }

    /** Clicking the button loads the config instead of toggling anything. */
    @Override
    public boolean toggle() {
        ConfigManager.load(getName());
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // state is derived from ConfigManager.active
    }
}
