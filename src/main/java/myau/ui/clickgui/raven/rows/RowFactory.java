package myau.ui.clickgui.raven.rows;

import myau.Myau;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.KeybindSetting;
import myau.module.Module;
import myau.module.Setting;
import myau.module.SliderSetting;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;

import java.util.ArrayList;
import java.util.List;

/** Turns a module's Setting and Property fields into GUI rows. */
public final class RowFactory {

    private RowFactory() {}

    public static List<SettingRow> build(Module module) {
        List<SettingRow> rows = new ArrayList<>();

        for (Setting setting : module.getSettings()) {
            SettingRow row = fromSetting(setting);
            if (row != null) rows.add(row);
        }

        if (Myau.propertyManager != null) {
            List<Property<?>> properties = Myau.propertyManager.properties.get(module.getClass());
            if (properties != null) {
                for (Property<?> property : properties) {
                    SettingRow row = fromProperty(property);
                    if (row != null) rows.add(row);
                }
            }
        }

        rows.add(new BindRow("Bind", module::getKey, module::setKey, null));
        rows.add(new BoolRow("Hide in list", module::isHidden, module::setHidden, null));
        return rows;
    }

    private static SettingRow fromSetting(Setting s) {
        if (s instanceof BooleanSetting) {
            BooleanSetting b = (BooleanSetting) s;
            return new BoolRow(s.getName(), b::getValue, b::setValue, s::isVisible);
        }
        if (s instanceof SliderSetting) {
            SliderSetting sl = (SliderSetting) s;
            return new SliderRow(s.getName(), sl::getValue, sl::setValue, sl.getMin(), sl.getMax(), s::isVisible);
        }
        if (s instanceof DropdownSetting) {
            DropdownSetting d = (DropdownSetting) s;
            return new ModeRow(s.getName(), d::getValue, d::next, d::prev, s::isVisible);
        }
        if (s instanceof KeybindSetting) {
            KeybindSetting k = (KeybindSetting) s;
            return new BindRow(s.getName(), k::getKeyCode, k::setKeyCode, s::isVisible);
        }
        return null;
    }

    private static SettingRow fromProperty(Property<?> p) {
        String name = prettify(p.getName());
        if (p instanceof BooleanProperty) {
            BooleanProperty b = (BooleanProperty) p;
            return new BoolRow(name, () -> b.getValue(), v -> b.setValue(v), p::isVisible);
        }
        if (p instanceof FloatProperty) {
            FloatProperty f = (FloatProperty) p;
            return new SliderRow(name, () -> f.getValue(),
                    v -> f.setValue((float) (Math.round(v * 100.0) / 100.0)),
                    f.getMinimum(), f.getMaximum(), p::isVisible);
        }
        if (p instanceof IntProperty) {
            IntProperty i = (IntProperty) p;
            return new SliderRow(name, () -> i.getValue(),
                    v -> i.setValue((int) Math.round(v)),
                    i.getMinimum(), i.getMaximum(), p::isVisible);
        }
        if (p instanceof PercentProperty) {
            PercentProperty pc = (PercentProperty) p;
            return new SliderRow(name, () -> pc.getValue(),
                    v -> pc.setValue((int) Math.round(v)),
                    pc.getMinimum(), pc.getMaximum(), p::isVisible);
        }
        if (p instanceof ModeProperty) {
            ModeProperty m = (ModeProperty) p;
            return new ModeRow(name, m::getModeString, m::nextMode, m::previousMode, p::isVisible);
        }
        if (p instanceof ColorProperty) {
            ColorProperty c = (ColorProperty) p;
            return new ColorRow(name, () -> c.getValue(), v -> c.setValue(v), p::isVisible);
        }
        if (p instanceof TextProperty) {
            TextProperty t = (TextProperty) p;
            return new TextRow(name, () -> t.getValue(), v -> t.setValue(v), p::isVisible);
        }
        return null;
    }

    private static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
