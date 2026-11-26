package org.thunderdog.challegram.util;

/** Optical vertical centering for single-line tab labels inside pill indicators. */
public final class TabLabelGeometry {
  private TabLabelGeometry () { }

  public static boolean isAllCaps (CharSequence text) {
    if (text == null || text.length() == 0) return false;
    boolean hasUpper = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLowerCase(c)) return false;
      if (Character.isUpperCase(c)) hasUpper = true;
    }
    return hasUpper;
  }

  public static int verticalTop (int viewHeight, int layoutHeight, int lineBaseline, float ascent, float descent, float capHeight, boolean allCaps) {
    if (viewHeight <= 0) return 0;
    if (allCaps && capHeight > 0f && lineBaseline > 0) {
      return Math.round(viewHeight * 0.5f - lineBaseline + capHeight * 0.5f);
    }
    if (!allCaps && lineBaseline > 0 && descent > ascent) {
      return Math.round(viewHeight * 0.5f - lineBaseline - (ascent + descent) * 0.5f);
    }
    return (viewHeight - layoutHeight) / 2;
  }
}
