package org.thunderdog.challegram.util;

/** Placement in the overlay parent's coordinates, independent of status-bar/header offsets. */
public final class AnchoredMenuGeometry {
  private AnchoredMenuGeometry () { }

  private static float clamp (float value, float size, float extent, float margin) {
    float low = Math.min(margin, Math.max(0f, extent - size));
    float high = Math.max(low, extent - size - margin);
    return Math.max(low, Math.min(high, value));
  }

  public static float left (float anchorCenter, float width, float parentWidth, float margin) {
    return clamp(anchorCenter - width / 2f, width, parentWidth, margin);
  }

  public static float top (float anchorTop, float anchorBottom, float height, float parentHeight, float margin, boolean preferBelow) {
    float below = anchorBottom + margin, above = anchorTop - margin - height;
    boolean fitsBelow = below >= margin && below + height <= parentHeight - margin;
    boolean fitsAbove = above >= margin && above + height <= parentHeight - margin;
    float desired = preferBelow ? (fitsBelow || !fitsAbove ? below : above) : (fitsAbove || !fitsBelow ? above : below);
    return clamp(desired, height, parentHeight, margin);
  }
}
