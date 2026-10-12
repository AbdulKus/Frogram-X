import java.util.Arrays;
import org.thunderdog.challegram.util.AlphaBlur;
import org.thunderdog.challegram.util.GlassNavigationMotion;

public final class GlassNavigationTest {
  private static void equal (float expected, float actual, String scenario) {
    if (Math.abs(expected - actual) > .001f) throw new AssertionError(scenario + ": " + expected + " != " + actual);
  }

  public static void main (String[] args) {
    for (int density = 1; density <= 4; density++) {
      for (int rows = 0; rows <= 4; rows++) {
        float from = 86 * density, to = (86 + 48 * rows) * density;
        GlassNavigationMotion.Geometry shape = new GlassNavigationMotion.Geometry(from, to);
        float previous = from;
        for (int frame = 0; frame <= 400; frame++) {
          float p = frame / 400f, height = shape.value(p);
          if (height < previous || height > to) throw new AssertionError("pinned shell continuity");
          previous = height;
        }
        equal(from, shape.value(0f), "cancelled opening");
        equal(to, shape.value(1f), "completed opening");
        equal(0f, shape.incoming(0f), "start incoming chrome");
        equal(1f, shape.incoming(1f), "settled incoming chrome");
      }
      GlassNavigationMotion.Geometry late = new GlassNavigationMotion.Geometry(86 * density, 134 * density);
      float before = late.value(.72f);
      late.retarget(182 * density, .72f);
      equal(before, late.value(.72f), "late pin does not jump shell");
      equal(86 * density, late.value(0f), "cancel after retarget restores original shell");
      equal(182 * density, late.value(1f), "retarget completes");
      equal(1f, late.incoming(1f), "late pin incoming completes");
    }
    AlphaBlur blur = new AlphaBlur();
    for (int[] size : new int[][] {{1, 1}, {1, 31}, {31, 1}, {37, 19}}) {
      int w = size[0], h = size[1];
      int[] solid = new int[w * h]; Arrays.fill(solid, 0x8034aaff);
      blur.blur(solid, w, h);
      for (int pixel : solid) if (pixel != 0x8034aaff) throw new AssertionError("transparent solid changed");
    }
    int[] glyph = new int[41 * 41];
    glyph[20 * 41 + 20] = 0xffffffff;
    blur.blur(glyph, 41, 41);
    int halo = 0;
    for (int pixel : glyph) {
      if (pixel >>> 24 != 0) {
        if ((pixel & 0xffffff) != 0xffffff) throw new AssertionError("dark halo around light text");
        halo++;
      }
    }
    if (halo <= 1) throw new AssertionError("foreground did not blur");
    int[] empty = new int[41 * 41]; blur.blur(empty, 41, 41);
    for (int pixel : empty) if (pixel != 0) throw new AssertionError("transparent frame contaminated");
    System.out.println("Glass navigation: 4 densities, pinned rows, smooth transitions, late geometry retargeting, transparent blur edges and halos passed");
  }
}
