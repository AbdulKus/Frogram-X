package org.thunderdog.challegram.util;

/** Reusable, separable blur for the small opaque chat backdrops. Works with one-pixel edges too. */
public final class GlassBlur {
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
    final int radius = 3, count = radius * 2 + 1;
    int length = horizontal ? width : height;
    int lines = horizontal ? height : width;
    int step = horizontal ? 1 : width;
    for (int line = 0; line < lines; line++) {
      int base = horizontal ? line * width : line;
      int red = 0, green = 0, blue = 0;
      for (int k = -radius; k <= radius; k++) {
        int color = source[base + Math.max(0, Math.min(length - 1, k)) * step];
        red += (color >>> 16) & 255; green += (color >>> 8) & 255; blue += color & 255;
      }
      for (int x = 0; x < length; x++) {
        target[base + x * step] = 0xff000000 | (red / count << 16) | (green / count << 8) | blue / count;
        int old = source[base + Math.max(0, x - radius) * step];
        int next = source[base + Math.min(length - 1, x + radius + 1) * step];
        red += ((next >>> 16) & 255) - ((old >>> 16) & 255);
        green += ((next >>> 8) & 255) - ((old >>> 8) & 255);
        blue += (next & 255) - (old & 255);
      }
    }
  }
}
