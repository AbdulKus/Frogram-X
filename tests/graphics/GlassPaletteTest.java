import org.thunderdog.challegram.util.GlassPalette;
import java.util.Random;

public final class GlassPaletteTest {
  static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
  public static void main(String[] args) {
    check(Math.abs(GlassPalette.ratio(0xff000000, 0xffffffff) - 21) < .000001, "WCAG black/white reference");
    check(Math.abs(GlassPalette.luminance(0xff808080) - .2158605) < .000001, "sRGB transfer reference");
    Random random = new Random(174);
    for (int i = 0; i < 1000; i++) {
      int accent = 0xff000000 | random.nextInt(0x1000000);
      GlassPalette p = new GlassPalette(0xffffffff, accent, accent, 0xff111111, 0xff888888, 0xff777777, false);
      check(p.icon == p.secondary, "ordinary icons and secondary labels must share a role");
      for (int media : new int[] {0xff000000, 0xffffffff, 0xffff0000, 0xff00ff00, 0xff0000ff}) {
        int surface = GlassPalette.blend(media, p.surface, GlassPalette.LIGHT_VEIL);
        for (float state : new float[] {0, .06f, .12f, .14f}) {
          int background = GlassPalette.blend(surface, p.active, state);
          for (int content : new int[] {p.text, p.secondary, p.icon, p.active, p.placeholder}) {
            check((content >>> 24) == 255, "content opacity must not depend on the underlying photo");
            check(GlassPalette.ratio(content, background) >= 4.5, "light glass contrast below text threshold");
          }
        }
      }
    }
    GlassPalette blue = new GlassPalette(-1, 0xff507fa2, 0xff569ace, 0xff111111, 0xff777777, 0xff888888, false);
    GlassPalette red = new GlassPalette(-1, 0xffb34b4b, 0xffdd4b4b, 0xff111111, 0xff777777, 0xff888888, false);
    check(blue.icon != red.icon && blue.active != red.active, "accent changes must propagate to ordinary and selected actions");
    GlassPalette dark = new GlassPalette(0xff111111, 0xff222222, 0xffaabbcc, -1, 0xffaaaaaa, 0xffbbbbbb, true);
    check(dark.text == -1 && dark.icon == 0xffbbbbbb && dark.surface == 0xff111111, "dark theme roles changed unexpectedly");
    System.out.println("Glass palette: WCAG references, 1000 accents, media extremes, selected states and dark roles passed");
  }
}
