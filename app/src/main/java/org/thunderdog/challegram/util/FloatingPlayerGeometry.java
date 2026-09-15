package org.thunderdog.challegram.util;

/** Shared overlay spacing; the player surface itself always keeps its full height. */
public final class FloatingPlayerGeometry {
  private FloatingPlayerGeometry () { }

  public static float visibility (float offset, int height) {
    return height > 0 ? Math.max(0f, Math.min(1f, offset / height)) : 0f;
  }

  public static int gap (float offset, int height, int gap) {
    return Math.round(gap * visibility(offset, height));
  }

  public static int inset (float offset, int height, int gap) {
    float visibility = visibility(offset, height);
    return Math.round(height * visibility) + Math.round(gap * visibility);
  }
}
