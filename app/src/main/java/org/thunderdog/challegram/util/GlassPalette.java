package org.thunderdog.challegram.util;

/** Shared foreground roles for translucent surfaces. All returned colours are opaque. */
public final class GlassPalette {
  // The light veil must protect content from the darkest possible photo underneath.
  public static final float LIGHT_VEIL = .78f;
  public final int surface, text, secondary, icon, active, placeholder;
  public final int contrastBackground;

  public GlassPalette (int filling, int header, int accent, int text, int secondary, int icon, boolean dark) {
    surface = dark ? filling : blend(filling, header, .18f);
    // Reserve another 15% for selected/pressed overlays inside the surface.
    contrastBackground = blend(0xff000000, surface, LIGHT_VEIL * .85f);
    if (dark) {
      this.text = text;
      this.secondary = secondary;
      this.icon = icon;
      this.active = accent;
      this.placeholder = secondary;
    } else {
      this.text = contrast(blend(blend(text, surface, .14f), accent, .14f), contrastBackground, 4.5);
      this.secondary = contrast(blend(blend(text, surface, .34f), accent, .24f), contrastBackground, 4.5);
      this.icon = this.secondary;
      this.active = contrast(accent, contrastBackground, 4.5);
      this.placeholder = this.secondary;
    }
  }

  public static int blend (int from, int to, float factor) {
    int r = Math.round(((from >> 16) & 255) * (1f - factor) + ((to >> 16) & 255) * factor);
    int g = Math.round(((from >> 8) & 255) * (1f - factor) + ((to >> 8) & 255) * factor);
    int b = Math.round((from & 255) * (1f - factor) + (to & 255) * factor);
    return 0xff000000 | r << 16 | g << 8 | b;
  }

  private static double linear (int channel) {
    double value = channel / 255.0;
    return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
  }

  public static double luminance (int color) {
    return .2126 * linear((color >> 16) & 255) + .7152 * linear((color >> 8) & 255) + .0722 * linear(color & 255);
  }

  public static double ratio (int first, int second) {
    double a = luminance(first), b = luminance(second);
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
  }

  /** Keep the accent hue, moving only as far towards black/white as contrast requires. */
  private static int contrast (int candidate, int background, double minimum) {
    if (ratio(candidate, background) >= minimum) return candidate;
    int target = ratio(0xff000000, background) >= ratio(0xffffffff, background) ? 0xff000000 : 0xffffffff;
    float low = 0f, high = 1f;
    for (int i = 0; i < 12; i++) {
      float mid = (low + high) * .5f;
      if (ratio(blend(candidate, target, mid), background) >= minimum) high = mid;
      else low = mid;
    }
    return blend(candidate, target, high);
  }
}
