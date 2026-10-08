package myau.ui.clickgui;

import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.KeybindSetting;
import myau.module.Module;
import myau.module.Setting;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**

* Raven-style ClickGUI for Spirit Visuals.
*
* The layout follows the classic Raven aesthetic:
* * compact draggable category frames
* * dark flat panels with a purple accent header
* * one-line module buttons
* * right-click a module to expose its settings
* * left-click toggles modules
* * middle/right-click interactions stay lightweight and keyboard-friendly
*
* Everything is rendered with the GL 2.1 helpers already used by this project.
  */
  public class RavenClickGui extends GuiScreen {

  private static final int ACCENT       = 0xFF9B5CFF;
  private static final int ACCENT_DARK  = 0xFF7040B8;
  private static final int BG           = 0xD9080808;
  private static final int FRAME        = 0xFF171717;
  private static final int HEADER       = 0xFF232323;
  private static final int MODULE       = 0xFF202020;
  private static final int HOVER        = 0xFF292929;
  private static final int SETTINGS     = 0xFF191919;
  private static final int TEXT         = 0xFFF0F0F0;
  private static final int MUTED        = 0xFF929292;
  private static final int OFF          = 0xFF505050;
  private static final int LINE         = 0xFF303030;

  private static final int FRAME_W      = 150;
  private static final int HEADER_H     = 24;
  private static final int ROW_H        = 20;
  private static final int PAD          = 5;
  private static final int GAP          = 8;

  private final List<String> categories;
  private final List<List<Module>> categoryModules;
  private final Map<Module, Boolean> expanded = new HashMap<>();
  private final Map<Module, Float> toggleAnim = new HashMap<>();

  private int selectedCategory = 0;

  private int[] frameX;
  private int[] frameY;

  private int draggingFrame = -1;
  private int dragDX, dragDY;

  private SliderSetting draggingSlider;
  private float sliderX;
  private float sliderW;

  private KeybindSetting listeningKeybind;
  private DropdownSetting openDropdown;

  private String search = "";
  private boolean searchOpen;

  private long lastMs = System.currentTimeMillis();

  public RavenClickGui(List<String> categories, List<List<Module>> categoryModules) {
  this.categories = categories;
  this.categoryModules = categoryModules;

   frameX = new int[categories.size()];
   frameY = new int[categories.size()];
   for (int i = 0; i < categories.size(); i++) {
       frameX[i] = 25 + i * (FRAME_W + GAP);
       frameY[i] = 35;
   }

  }

  @Override
  public void initGui() {
  Keyboard.enableRepeatEvents(true);
  lastMs = System.currentTimeMillis();
  }

  @Override
  public void onGuiClosed() {
  Keyboard.enableRepeatEvents(false);
  draggingFrame = -1;
  draggingSlider = null;
  listeningKeybind = null;
  openDropdown = null;
  }

  @Override
  public boolean doesGuiPauseGame() {
  return false;
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
  long now = System.currentTimeMillis();
  float dt = Math.min((now - lastMs) / 1000f, 0.1f);
  lastMs = now;

   ScaledResolution sr = new ScaledResolution(mc);
   int sw = sr.getScaledWidth();
   int sh = sr.getScaledHeight();

   GuiRender.rect(0, 0, sw, sh, BG);

   drawTopBar(sw);
   drawSearch(sw);

   for (int i = 0; i < categories.size(); i++) {
       drawFrame(i, mouseX, mouseY, dt);
   }

   if (openDropdown != null) {
       drawDropdown(mouseX, mouseY);
   }

  }

  private void drawTopBar(int sw) {
  GuiRender.rect(0, 0, sw, 2, ACCENT);
  GuiRender.textNoShadow("Spirit", 12, 10, TEXT);
  GuiRender.textNoShadow("Raven", 12 + GuiRender.textW("Spirit") + 5, 10, ACCENT);

   String hint = "LMB toggle  •  RMB settings  •  ESC close";
   GuiRender.textNoShadow(hint, sw - GuiRender.textW(hint) - 12, 10, MUTED);

  }

  private void drawSearch(int sw) {
  float w = 130;
  float x = sw - w - 10;
  float y = 28;
  GuiRender.roundedRect(x, y, w, 18, 3, 0xFF171717);
  GuiRender.border(x, y, w, 18, searchOpen ? ACCENT_DARK : LINE);
  String s = search.isEmpty() ? "Search modules" : search;
  if (searchOpen) s += "|";
  GuiRender.textNoShadow(s, x + 6, y + 5, search.isEmpty() && !searchOpen ? MUTED : TEXT);
  }

  private void drawFrame(int index, int mouseX, int mouseY, float dt) {
  int x = frameX[index];
  int y = frameY[index];

   List<Module> modules = visibleModules(categoryModules.get(index));
   int height = HEADER_H + 2 + modulesHeight(modules);

   GuiRender.roundedRect(x, y, FRAME_W, height, 4, FRAME);
   GuiRender.roundedRectTop(x, y, FRAME_W, HEADER_H, 4, HEADER);

   // Raven-like accent strip under the category title.
   GuiRender.rect(x + 4, y + HEADER_H - 2, FRAME_W - 8, 2,
           index == selectedCategory ? ACCENT : ACCENT_DARK);

   String title = categories.get(index);
   GuiRender.textNoShadow(title, x + 8, y + 7, TEXT);

   if (modules.isEmpty()) {
       GuiRender.textNoShadow("No modules", x + 8, y + HEADER_H + 7, MUTED);
       return;
   }

   float cy = y + HEADER_H + 2;
   for (Module module : modules) {
       cy = drawModule(module, x + PAD, cy, FRAME_W - PAD * 2, mouseX, mouseY, dt);
   }

  }

  private float drawModule(Module module, float x, float y, float w, int mx, int my, float dt) {
  boolean hover = inside(mx, my, x, y, w, ROW_H);
  boolean enabled = module.isEnabled();

   float a = toggleAnim.getOrDefault(module, enabled ? 1f : 0f);
   a = lerp(a, enabled ? 1f : 0f, dt * 16f);
   toggleAnim.put(module, a);

   int bg = hover ? HOVER : MODULE;
   GuiRender.roundedRect(x, y, w, ROW_H - 1, 2, bg);

   // Enabled state is intentionally subtle, like Raven's compact buttons.
   if (a > 0.01f) {
       GuiRender.rect(x, y, 2, ROW_H - 1, GuiRender.withAlpha(ACCENT, (int)(a * 255)));
   }

   int textColor = enabled ? TEXT : MUTED;
   GuiRender.textNoShadow(module.getName(), x + 7, y + 5, textColor);

   List<Setting> settings = visibleSettings(module);
   if (!settings.isEmpty()) {
       String marker = expanded.getOrDefault(module, false) ? "-" : "+";
       GuiRender.textNoShadow(marker, x + w - 12, y + 5, enabled ? ACCENT : MUTED);
   }

   float next = y + ROW_H;

   if (expanded.getOrDefault(module, false) && !settings.isEmpty()) {
       next += 2;
       GuiRender.roundedRect(x, next, w, settings.size() * ROW_H + 4, 2, SETTINGS);

       float sy = next + 2;
       for (Setting setting : settings) {
           drawSetting(setting, x + 4, sy, w - 8, mx, my);
           sy += ROW_H;
       }
       next = sy + 2;
   }

   return next;

  }

  private void drawSetting(Setting setting, float x, float y, float w, int mx, int my) {
  boolean hover = inside(mx, my, x, y, w, ROW_H);

   if (setting instanceof BooleanSetting) {
       BooleanSetting s = (BooleanSetting) setting;
       GuiRender.textNoShadow(s.getName(), x + 2, y + 5, hover ? TEXT : MUTED);

       float bx = x + w - 18;
       float by = y + 6;
       GuiRender.roundedRect(bx, by, 12, 8, 4, s.getValue() ? ACCENT : OFF);
       GuiRender.fillCircle(
               s.getValue() ? bx + 9 : bx + 3,
               by + 4,
               3,
               TEXT
       );

   } else if (setting instanceof SliderSetting) {
       SliderSetting s = (SliderSetting) setting;
       String value = formatNumber(s.getValue());
       GuiRender.textNoShadow(s.getName(), x + 2, y + 2, hover ? TEXT : MUTED);
       GuiRender.textNoShadow(value, x + w - GuiRender.textW(value) - 2, y + 2, TEXT);

       sliderX = x + 2;
       sliderW = w - 4;
       float pct = MathHelper.clamp_float(s.getPercent(), 0f, 1f);
       float railY = y + 13;
       GuiRender.sliderRail(sliderX, railY, sliderW, 3, pct, LINE, ACCENT);
       GuiRender.fillCircle(sliderX + sliderW * pct, railY + 1.5f, 3, TEXT);

       if (draggingSlider == s) {
           float p = MathHelper.clamp_float((mx - sliderX) / sliderW, 0f, 1f);
           s.setValue(s.getMin() + (s.getMax() - s.getMin()) * p);
       }

   } else if (setting instanceof DropdownSetting) {
       DropdownSetting s = (DropdownSetting) setting;
       GuiRender.textNoShadow(s.getName(), x + 2, y + 5, hover ? TEXT : MUTED);

       String value = s.getValue();

       // Fixed: w is a float, so cast the calculated width to int
       // before passing it to Math.min(int, int).
       int bw = Math.min((int)(w * 2 / 3), GuiRender.textW(value) + 14);

       float bx = x + w - bw;
       GuiRender.roundedRect(bx, y + 3, bw, 14, 2, openDropdown == s ? ACCENT_DARK : MODULE);
       GuiRender.textNoShadow(value, bx + 5, y + 6, TEXT);
       GuiRender.textNoShadow("<", bx + bw - 9, y + 6, MUTED);

   } else if (setting instanceof KeybindSetting) {
       KeybindSetting s = (KeybindSetting) setting;
       GuiRender.textNoShadow(s.getName(), x + 2, y + 5, hover ? TEXT : MUTED);
       String key = listeningKeybind == s ? "..." : s.getDisplayName();
       int bw = GuiRender.textW(key) + 10;
       float bx = x + w - bw;
       GuiRender.roundedRect(bx, y + 3, bw, 14,
               2, listeningKeybind == s ? ACCENT_DARK : MODULE);
       GuiRender.textNoShadow(key, bx + 5, y + 6,
               listeningKeybind == s ? TEXT : MUTED);
   }

   GuiRender.rect(x + 2, y + ROW_H - 1, w - 4, 1, 0xFF222222);

  }

  private void drawDropdown(int mx, int my) {
  if (openDropdown == null) return;

   String[] options = openDropdown.getOptions();
   int width = 105;
   int x = 0;
   int y = 0;

   // Find the setting visually so the popup follows it.
   outer:
   for (int c = 0; c < categories.size(); c++) {
       float cy = frameY[c] + HEADER_H + 2;
       for (Module module : visibleModules(categoryModules.get(c))) {
           cy += ROW_H;
           if (expanded.getOrDefault(module, false)) {
               for (Setting setting : visibleSettings(module)) {
                   if (setting == openDropdown) {
                       x = frameX[c] + FRAME_W - width - 5;
                       y = (int) cy;
                       break outer;
                   }
                   cy += ROW_H;
               }
               cy += 4;
           }
       }
   }

   int h = options.length * 18 + 4;
   GuiRender.roundedRect(x, y, width, h, 3, 0xFF121212);
   GuiRender.border(x, y, width, h, ACCENT_DARK);

   for (int i = 0; i < options.length; i++) {
       float oy = y + 2 + i * 18;
       boolean hover = inside(mx, my, x + 2, oy, width - 4, 18);
       if (hover) GuiRender.roundedRect(x + 2, oy, width - 4, 18, 2, HOVER);
       GuiRender.textNoShadow(options[i], x + 7, oy + 5,
               i == openDropdown.getIndex() ? ACCENT : TEXT);
   }

  }

  private List<Module> visibleModules(List<Module> source) {
  if (search.isEmpty()) return source;
  List<Module> result = new ArrayList<>();
  String q = search.toLowerCase();
  for (Module m : source) {
  if (m.getName().toLowerCase().contains(q)) result.add(m);
  }
  return result;
  }

  private List<Setting> visibleSettings(Module module) {
  List<Setting> result = new ArrayList<>();
  for (Setting setting : module.getSettings()) {
  if (setting.isVisible()) result.add(setting);
  }
  return result;
  }

  private int modulesHeight(List<Module> modules) {
  int h = 0;
  for (Module module : modules) {
  h += ROW_H;
  if (expanded.getOrDefault(module, false)) {
  h += 6 + visibleSettings(module).size() * ROW_H;
  }
  }
  return h;
  }

  @Override
  public void mouseClicked(int mx, int my, int button) throws IOException {
  if (openDropdown != null) {
  handleDropdownClick(mx, my);
  return;
  }

   // Search field.
   ScaledResolution sr = new ScaledResolution(mc);
   int sw = sr.getScaledWidth();
   float searchX = sw - 140;
   if (inside(mx, my, searchX, 28, 130, 18)) {
       searchOpen = true;
       return;
   }

   // Frame dragging by the header.
   for (int i = 0; i < categories.size(); i++) {
       if (inside(mx, my, frameX[i], frameY[i], FRAME_W, HEADER_H)) {
           if (button == 0) {
               draggingFrame = i;
               dragDX = mx - frameX[i];
               dragDY = my - frameY[i];
           }
           selectedCategory = i;
           return;
       }
   }

   // Module/settings hit testing.
   for (int c = 0; c < categories.size(); c++) {
       float y = frameY[c] + HEADER_H + 2;
       for (Module module : visibleModules(categoryModules.get(c))) {
           if (inside(mx, my, frameX[c] + PAD, y, FRAME_W - PAD * 2, ROW_H)) {
               if (button == 0) {
                   module.toggle();
               } else if (button == 1 && !visibleSettings(module).isEmpty()) {
                   expanded.put(module, !expanded.getOrDefault(module, false));
               }
               return;
           }

           y += ROW_H;

           if (expanded.getOrDefault(module, false)) {
               List<Setting> settings = visibleSettings(module);
               y += 2;
               for (Setting setting : settings) {
                   if (inside(mx, my, frameX[c] + PAD + 4, y, FRAME_W - PAD * 2 - 8, ROW_H)) {
                       clickSetting(setting, mx, my, button,
                               frameX[c] + PAD + 4,
                               FRAME_W - PAD * 2 - 8);
                       return;
                   }
                   y += ROW_H;
               }
               y += 6;
           }
       }
   }

  }

  private void clickSetting(Setting setting, int mx, int my, int button, float x, float w) {
  if (setting instanceof BooleanSetting && button == 0) {
  ((BooleanSetting) setting).toggle();
  } else if (setting instanceof DropdownSetting && button == 0) {
  openDropdown = (DropdownSetting) setting;
  } else if (setting instanceof KeybindSetting && button == 0) {
  listeningKeybind = (KeybindSetting) setting;
  listeningKeybind.startListening();
  } else if (setting instanceof SliderSetting && button == 0) {
  draggingSlider = (SliderSetting) setting;
  sliderX = x + 2;
  sliderW = w - 4;
  float p = MathHelper.clamp_float((mx - sliderX) / sliderW, 0f, 1f);
  draggingSlider.setValue(
  draggingSlider.getMin() +
  (draggingSlider.getMax() - draggingSlider.getMin()) * p
  );
  }
  }

  private void handleDropdownClick(int mx, int my) {
  String[] options = openDropdown.getOptions();

   // Recompute popup position exactly as in drawDropdown.
   int width = 105, x = 0, y = 0;
   outer:
   for (int c = 0; c < categories.size(); c++) {
       float cy = frameY[c] + HEADER_H + 2;
       for (Module module : visibleModules(categoryModules.get(c))) {
           cy += ROW_H;
           if (expanded.getOrDefault(module, false)) {
               for (Setting setting : visibleSettings(module)) {
                   if (setting == openDropdown) {
                       x = frameX[c] + FRAME_W - width - 5;
                       y = (int) cy;
                       break outer;
                   }
                   cy += ROW_H;
               }
               cy += 4;
           }
       }
   }

   if (inside(mx, my, x, y, width, options.length * 18 + 4)) {
       int index = (my - y - 2) / 18;
       if (index >= 0 && index < options.length) {
           openDropdown.setIndex(index);
       }
   }
   openDropdown = null;

  }

  @Override
  public void mouseReleased(int mx, int my, int button) {
  if (button == 0) {
  draggingFrame = -1;
  draggingSlider = null;
  }
  }

  @Override
  protected void mouseClickMove(int mx, int my, int button, long timeSinceLastClick) {
  if (draggingFrame >= 0) {
  frameX[draggingFrame] = mx - dragDX;
  frameY[draggingFrame] = my - dragDY;
  }
  }

  @Override
  public void handleMouseInput() throws IOException {
  super.handleMouseInput();
  int wheel = Mouse.getEventDWheel();
  if (wheel != 0) {
  // Raven traditionally uses independent frames; wheel over a frame
  // moves the frame itself vertically, making crowded GUIs easy to arrange.
  for (int i = 0; i < categories.size(); i++) {
  if (inside(Mouse.getX() / new ScaledResolution(mc).getScaleFactor(),
  0, frameX[i], frameY[i], FRAME_W, 400)) {
  frameY[i] += wheel > 0 ? 8 : -8;
  break;
  }
  }
  }
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) throws IOException {
  if (listeningKeybind != null) {
  if (keyCode == Keyboard.KEY_ESCAPE) {
  listeningKeybind.cancelListening();
  } else {
  listeningKeybind.setKeyCode(keyCode);
  }
  listeningKeybind = null;
  return;
  }

   if (keyCode == Keyboard.KEY_ESCAPE) {
       if (searchOpen && !search.isEmpty()) {
           search = "";
           return;
       }
       if (searchOpen) {
           searchOpen = false;
           return;
       }
       mc.displayGuiScreen(null);
       return;
   }

   if (searchOpen) {
       if (keyCode == Keyboard.KEY_BACK) {
           if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
       } else if (typedChar >= 32) {
           search += typedChar;
       }
   }

  }

  private static boolean inside(int mx, int my, float x, float y, float w, float h) {
  return mx >= x && mx <= x + w && my >= y && my <= y + h;
  }

  private static float lerp(float a, float b, float t) {
  return a + (b - a) * MathHelper.clamp_float(t, 0f, 1f);
  }

  private static String formatNumber(double value) {
  if (value == Math.floor(value) && Math.abs(value) < 1_000_000) {
  return Integer.toString((int) value);
  }
  return String.format("%.2f", value);
  }
  }
