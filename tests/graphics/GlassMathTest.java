import java.util.Arrays;
import java.util.Random;
import org.thunderdog.challegram.util.GlassBlur;
import org.thunderdog.challegram.util.DampedSpring;

public final class GlassMathTest {
  private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
  private static int[] reference(int[] input, int width, int height) {
    int[] data = input.clone();
    for (int pass = 0; pass < 4; pass++) {
      int[] out = new int[data.length];
      for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
        int r = 0, g = 0, b = 0;
        for (int k = -3; k <= 3; k++) {
          int xx = (pass & 1) == 0 ? Math.max(0, Math.min(width - 1, x + k)) : x;
          int yy = (pass & 1) != 0 ? Math.max(0, Math.min(height - 1, y + k)) : y;
          int c = data[yy * width + xx];
          r += c >>> 16 & 255; g += c >>> 8 & 255; b += c & 255;
        }
        out[y * width + x] = 0xff000000 | r / 7 << 16 | g / 7 << 8 | b / 7;
      }
      data = out;
    }
    return data;
  }
  public static void main(String[] args) {
    GlassBlur blur = new GlassBlur();
    Random random = new Random(42);
    int cases = 0;
    // Reuse one blur instance across thin composers, tall menus and differently sized chats.
    for (int[] size : new int[][] {{1,1},{1,12},{12,1},{2,3},{48,7},{96,32},{32,96},{96,96},{3,2},{48,7}}) {
      int w = size[0], h = size[1];
      int[] input = new int[w * h];
      for (int i = 0; i < input.length; i++) input[i] = 0xff000000 | random.nextInt(0x1000000);
      int[] expected = reference(input, w, h);
      blur.blur(input, w, h);
      check(Arrays.equals(input, expected), "blur differs at " + w + "x" + h);
      Arrays.fill(input, 0xff38a761); blur.blur(input, w, h);
      for (int color : input) check(color == 0xff38a761, "solid theme color changed");
      cases++;
    }
    for (int fps : new int[] {30,60,90,120}) {
      DampedSpring spring = new DampedSpring(); spring.target = -198f;
      for (int i = 0; i < fps * 2; i++) {
        spring.step(1f / fps);
        check(Float.isFinite(spring.value) && spring.value > -240 && spring.value <= 1, "unstable drag");
      }
      check(!spring.isMoving() && spring.value == -198f, "spring never settles");
      spring.target = 0f;
      for (int i = 0; i < fps * 2; i++) spring.step(1f / fps);
      check(!spring.isMoving() && spring.value == 0f, "return does not reconnect");
      spring.target = -50; spring.step(4f); check(Float.isFinite(spring.value), "long frame");
      spring.reset(0f); check(!spring.isMoving(), "callback would continue after close");
    }
    System.out.println("Glass blur: " + cases + " sizes, solid colors, reuse; spring: 30/60/90/120 Hz, return and teardown passed");
  }
}
