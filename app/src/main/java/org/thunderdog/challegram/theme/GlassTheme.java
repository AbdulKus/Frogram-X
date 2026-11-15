package org.thunderdog.challegram.theme;

import org.thunderdog.challegram.util.GlassPalette;

/** Derived roles stay tied to the current theme, including live custom-theme edits. */
public final class GlassTheme {
  private GlassTheme () { }
  private static volatile Entry cached;

  public static boolean isColor (int id) {
    return id == ColorId.glassText || id == ColorId.glassTextSecondary || id == ColorId.glassIcon ||
      id == ColorId.glassActive || id == ColorId.glassPlaceholder;
  }

  public static int getColor (ThemeDelegate theme, int id) {
    GlassPalette palette = palette(theme);
    if (id == ColorId.glassText) return palette.text;
    if (id == ColorId.glassTextSecondary) return palette.secondary;
    if (id == ColorId.glassActive) return palette.active;
    if (id == ColorId.glassPlaceholder) return palette.placeholder;
    return palette.icon;
  }

  public static GlassPalette palette (ThemeDelegate theme) {
    int filling = theme.getColor(ColorId.filling), header = theme.getColor(ColorId.headerBackground);
    int accent = theme.getColor(ColorId.textLink), text = theme.getColor(ColorId.text);
    int secondary = theme.getColor(ColorId.textLight), icon = theme.getColor(ColorId.icon);
    boolean dark = theme.isDark();
    Entry entry = cached;
    if (entry == null || entry.filling != filling || entry.header != header || entry.accent != accent ||
        entry.text != text || entry.secondary != secondary || entry.icon != icon || entry.dark != dark) {
      cached = entry = new Entry(filling, header, accent, text, secondary, icon, dark);
    }
    return entry.palette;
  }

  private static final class Entry {
    final int filling, header, accent, text, secondary, icon;
    final boolean dark;
    final GlassPalette palette;
    Entry (int filling, int header, int accent, int text, int secondary, int icon, boolean dark) {
      this.filling = filling; this.header = header; this.accent = accent; this.text = text;
      this.secondary = secondary; this.icon = icon; this.dark = dark;
      palette = new GlassPalette(filling, header, accent, text, secondary, icon, dark);
    }
  }
}
