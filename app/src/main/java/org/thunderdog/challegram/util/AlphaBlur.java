package org.thunderdog.challegram.util;

/** Blur transparent foregrounds without dark halos from transparent pixels. */
public final class AlphaBlur {
  private int[] scratch = new int[0];

  public void blur (int[] pixels, int width, int height) {
    if (width < 1 || height < 1 || pixels.length < width * height) throw new IllegalArgumentException();
    if (scratch.length < width * height) scratch = new int[width * height];
    for (int pass = 0; pass < 2; pass++) {
      filter(pixels, scratch, width, height, true);
      filter(scratch, pixels, width, height, false);
    }
  }

  private static void filter (int[] source, int[] target, int width, int height, boolean horizontal) {
    final int radius = 2, count = radius * 2 + 1;
    int length = horizontal ? width : height, lines = horizontal ? height : width;
    int step = horizontal ? 1 : width;
    for (int line = 0; line < lines; line++) {
      int base = horizontal ? line * width : line;
      int alpha = 0, red = 0, green = 0, blue = 0;
      for (int k = -radius; k <= radius; k++) {
        int color = source[base + Math.max(0, Math.min(length - 1, k)) * step], a = color >>> 24;
        alpha += a; red += ((color >>> 16) & 255) * a; green += ((color >>> 8) & 255) * a; blue += (color & 255) * a;
      }
      for (int x = 0; x < length; x++) {
        target[base + x * step] = alpha == 0 ? 0 : (alpha / count << 24) | (red / alpha << 16) | (green / alpha << 8) | blue / alpha;
        int old = source[base + Math.max(0, x - radius) * step], next = source[base + Math.min(length - 1, x + radius + 1) * step];
        int oldAlpha = old >>> 24, nextAlpha = next >>> 24;
        alpha += nextAlpha - oldAlpha;
        red += ((next >>> 16) & 255) * nextAlpha - ((old >>> 16) & 255) * oldAlpha;
        green += ((next >>> 8) & 255) * nextAlpha - ((old >>> 8) & 255) * oldAlpha;
        blue += (next & 255) * nextAlpha - (old & 255) * oldAlpha;
      }
    }
  }
}
