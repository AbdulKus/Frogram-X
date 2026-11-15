import org.thunderdog.challegram.util.AnchoredMenuGeometry;

public final class AnchoredMenuTest {
  private static void check (boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  public static void main (String[] args) {
    for (int density = 1; density <= 4; density++) {
      float margin = 8 * density;
      for (int w : new int[] {240, 360, 600, 900}) {
        float width = w * density, height = 500 * density;
        float menuWidth = Math.min(208 * density, width - 2 * margin), menuHeight = 288 * density;
        for (float center : new float[] {-40, 0, 30, 150, 400, 1000}) {
          float left = AnchoredMenuGeometry.left(center * density, menuWidth, width, margin);
          check(left >= margin && left + menuWidth <= width - margin, "menu escaped horizontal viewport");
        }
        float below = AnchoredMenuGeometry.top(-60 * density, 6 * density, menuHeight, height, margin, true);
        check(below == 14 * density, "floating header overlap or lost gap");
        float above = AnchoredMenuGeometry.top(height - 50 * density, height, menuHeight, height, margin, false);
        check(above + menuHeight == height - 58 * density, "bottom tab anchor drifted");
        float flipped = AnchoredMenuGeometry.top(height - 30 * density, height, menuHeight, height, margin, true);
        check(flipped == height - 38 * density - menuHeight, "menu did not flip when space below ran out");
        float fallback = AnchoredMenuGeometry.top(50 * density, 100 * density, 180 * density, 196 * density, margin, true);
        check(fallback >= margin && fallback + 180 * density <= 196 * density - margin, "short viewport clipping");
      }
    }
    check(AnchoredMenuGeometry.left(5, 250, 200, 8) == 0, "oversized legacy menu yielded a negative origin");
    System.out.println("Anchored menu placement: densities, header overflow, bottom tabs, edge clamps and short viewports passed");
  }
}
