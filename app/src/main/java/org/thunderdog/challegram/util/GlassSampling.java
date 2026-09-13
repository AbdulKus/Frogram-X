package org.thunderdog.challegram.util;

/** Area averaging before blur keeps subpixel text from flashing on the coarse grid. */
public final class GlassSampling {
  private GlassSampling () { }

  public static void downsample2x (int[] source, int[] target, int width, int height) {
    if (width < 1 || height < 1 || source.length < width * height * 4 || target.length < width * height) throw new IllegalArgumentException();
    int stride = width * 2;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int i = y * 2 * stride + x * 2;
        int a = source[i], b = source[i + 1], c = source[i + stride], d = source[i + stride + 1];
        int red = ((a >>> 16 & 255) + (b >>> 16 & 255) + (c >>> 16 & 255) + (d >>> 16 & 255)) / 4;
        int green = ((a >>> 8 & 255) + (b >>> 8 & 255) + (c >>> 8 & 255) + (d >>> 8 & 255)) / 4;
        int blue = ((a & 255) + (b & 255) + (c & 255) + (d & 255)) / 4;
        target[y * width + x] = 0xff000000 | red << 16 | green << 8 | blue;
      }
    }
  }
}
