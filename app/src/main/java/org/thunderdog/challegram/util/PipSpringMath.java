package org.thunderdog.challegram.util;

/** Pure geometry helpers for velocity-aware PiP docking and free vertical clamping. */
public final class PipSpringMath {
  private PipSpringMath () { }

  private static float clamp (float value, float min, float max) {
    return max <= min ? min : Math.max(min, Math.min(max, value));
  }

  public static boolean pickRightEdge (float currentX, float velocityX, int pipWidth, int parentWidth, float lookaheadSec) {
    float projectedCenter = currentX + pipWidth * 0.5f + velocityX * lookaheadSec;
    return projectedCenter >= parentWidth * 0.5f;
  }

  public static float dockX (boolean rightEdge, float minX, float maxX) {
    return rightEdge ? Math.max(minX, maxX) : minX;
  }

  public static float clampTargetY (float currentY, float velocityY, float minY, float maxY, float lookaheadSec) {
    return clamp(currentY + velocityY * lookaheadSec, minY, maxY);
  }

  public static float verticalFraction (float y, float minY, float maxY) {
    if (maxY <= minY) return 0f;
    return clamp((y - minY) / (maxY - minY), 0f, 1f);
  }

  public static int verticalMargin (int minTop, int parentHeight, int pipSize, int bottomMargin, float fraction) {
    int maxTop = Math.max(minTop, parentHeight - pipSize - bottomMargin);
    float f = clamp(fraction, 0f, 1f);
    return minTop + Math.round((maxTop - minTop) * f);
  }

  public static boolean shouldDismiss (float currentX, int pipWidth, int parentWidth, int edgeMargin, float dismissAlpha, float velocityX, float minDismissVelocity) {
    if (dismissAlpha <= 0.6f) return true;
    if (velocityX <= -minDismissVelocity && currentX < edgeMargin * 0.5f) return true;
    return velocityX >= minDismissVelocity && currentX + pipWidth > parentWidth - edgeMargin * 0.5f;
  }
}
