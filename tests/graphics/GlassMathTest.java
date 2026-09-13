import java.util.Arrays;
import java.util.Random;
import org.thunderdog.challegram.util.GlassBlur;
import org.thunderdog.challegram.util.GlassFrameCache;
import org.thunderdog.challegram.util.GlassSampling;
import org.thunderdog.challegram.util.DampedSpring;
import org.thunderdog.challegram.util.ThreadHeaderVisibility;

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
    int[] fine = new int[96 * 24 * 4], coarse = new int[96 * 24];
    for (int phase = 0; phase < 2; phase++) {
      for (int y = 0; y < 48; y++) for (int x = 0; x < 192; x++) {
        fine[y * 192 + x] = ((x + y + phase) & 1) == 0 ? 0xff000000 : 0xffffffff;
      }
      GlassSampling.downsample2x(fine, coarse, 96, 24);
      for (int pixel : coarse) check(pixel == 0xff7f7f7f, "subpixel phase changed the glass brightness");
    }
    int[] tiny = new int[1];
    GlassSampling.downsample2x(new int[] {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffffff}, tiny, 1, 1);
    check(tiny[0] == 0xff7f7f7f, "RGB area averaging mixed channels");
    int previous = 255;
    for (int line = -2; line <= 20; line++) {
      Arrays.fill(coarse, 0xffffffff);
      for (int y = Math.max(0, line); y < Math.min(24, line + 2); y++) Arrays.fill(coarse, y * 96, (y + 1) * 96, 0xff000000);
      blur.blur(coarse, 96, 24);
      int edge = coarse[6 * 96 + 48] & 255;
      check(Math.abs(edge - previous) <= 37, "glyph entering overscan caused a dark flash");
      previous = edge;
    }
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
    GlassFrameCache cache = new GlassFrameCache();
    int[] raw = new int[48 * 14];
    for (int i = 0; i < raw.length; i++) raw[i] = i % 2 == 0 ? 0xff000000 : 0xffffffff;
    int[] original = raw.clone();
    for (int reopen = 0; reopen < 20; reopen++) {
      cache.clear();
      check(cache.update(raw, 48, 14, false), "reopened surface skipped its first upload");
      check(Arrays.equals(cache.pixels(), reference(raw, 48, 14)), "reopened surface lost its blur");
      check(!cache.update(raw, 48, 14, false), "idle scene keeps uploading");
      check(Arrays.equals(raw, original), "capture buffer modified by display blur");
      check(cache.update(raw, 24, 28, false), "same pixels with new dimensions reused stale geometry");
      check(Arrays.equals(cache.pixels(), reference(raw, 24, 28)), "resize reused old blur");
      check(cache.update(raw, 24, 28, true), "geometry refresh ignored");
    }
    raw[0] = 0xffff0000;
    check(cache.update(raw, 24, 28, false), "new message pixels ignored");
    check(Arrays.equals(cache.pixels(), reference(raw, 24, 28)), "changed scene not blurred");
    // The post crosses a fixed edge, then the preview animates without another
    // scroll. It must never toggle just because a different row becomes 'last visible'.
    boolean preview = false;
    int transitions = 0;
    for (int bottom = 180; bottom >= -60; bottom--) {
      boolean next = ThreadHeaderVisibility.shouldShow(preview, bottom >= 80, bottom, 80, 8);
      if (next != preview) transitions++;
      preview = next;
      for (int frame = 0; frame < 24; frame++) {
        check(ThreadHeaderVisibility.shouldShow(preview, bottom >= 80, bottom, 80, 8) == preview,
          "stationary post oscillates during preview animation");
      }
    }
    check(preview && transitions == 1, "post did not pin exactly once");
    for (int bottom : new int[] {79, 81, 80, 83, 79, 87, 82}) {
      check(ThreadHeaderVisibility.shouldShow(true, true, bottom, 80, 8), "edge rounding unpinned post");
    }
    transitions = 0;
    for (int bottom = -60; bottom <= 180; bottom++) {
      boolean next = ThreadHeaderVisibility.shouldShow(preview, bottom >= 80, bottom, 80, 8);
      if (next != preview) transitions++;
      preview = next;
    }
    check(!preview && transitions == 1, "return scroll did not unpin exactly once");
    check(!ThreadHeaderVisibility.shouldShow(false, true, 400, 0, 8), "visible large post was pinned");
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
    System.out.println("Glass sampling: phase stability, RGB averaging, overscan edge; blur: " + cases + " sizes, solid colors, reuse; cache: 20 reopens, resize, unchanged/changed frames; thread post: forward/return, stationary frames and edge jitter; spring: 30/60/90/120 Hz, return and teardown passed");
  }
}
