package org.thunderdog.challegram.util;

/** Shared overlay spacing; the player surface itself always keeps its full height. */
public final class FloatingPlayerGeometry {
  private FloatingPlayerGeometry () { }

  private static float clamp01 (float value) {
    return value <= 0f ? 0f : (value >= 1f ? 1f : value);
  }

  public static float visibility (float offset, int height) {
    return height > 0 ? clamp01(offset / height) : 0f;
  }

  public static int gap (float offset, int height, int gap) {
    return Math.round(gap * visibility(offset, height));
  }

  public static int inset (float offset, int height, int gap) {
    float visibility = visibility(offset, height);
    return Math.round(height * visibility) + Math.round(gap * visibility);
  }

  /** Ongoing bar bottom below the current header height, independent of header expansion. */
  public static int barBottom (int headerBottom, int playerSize, float showFactor, float allowance) {
    if (playerSize <= 0) return headerBottom;
    return headerBottom + Math.round(playerSize * clamp01(showFactor) * clamp01(allowance));
  }

  /** Smooth fade-in over the final 20% of floating page-player reveal. */
  public static float pagePlayerAlpha (int headerBottom, int barBottom, int playerSize, float hideFactor) {
    if (playerSize <= 0) return 0f;
    float visible = (barBottom - playerSize * clamp01(hideFactor) - headerBottom) / (float) playerSize;
    float progress = clamp01((visible - 0.8f) / 0.2f);
    return progress * progress * (3f - 2f * progress);
  }

  public static int pagePlayerTop (int headerBottom, int barBottom, int playerSize, float hideFactor, int offset) {
    int hide = Math.round(Math.max(0, playerSize) * clamp01(hideFactor));
    return Math.max(headerBottom, barBottom - Math.max(0, playerSize) - hide) + offset;
  }

  public static int pagePlayerBottom (int barBottom, int playerSize, float hideFactor, int offset) {
    int hide = Math.round(Math.max(0, playerSize) * clamp01(hideFactor));
    return barBottom - hide + offset;
  }
}
