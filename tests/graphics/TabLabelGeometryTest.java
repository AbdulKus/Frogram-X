import org.thunderdog.challegram.util.TabLabelGeometry;

public final class TabLabelGeometryTest {
  private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }

  public static void main(String[] args) {
    check(TabLabelGeometry.isAllCaps("MESSAGES"), "MESSAGES should be detected as all caps");
    check(TabLabelGeometry.isAllCaps("GIFs".toUpperCase()), "uppercased label should be all caps");
    check(!TabLabelGeometry.isAllCaps("123"), "numeric string is not all caps");
    check(!TabLabelGeometry.isAllCaps("Messages"), "mixed case should not be all caps");
    check(!TabLabelGeometry.isAllCaps("Ссылки"), "Cyrillic mixed case should not be all caps");
    check(TabLabelGeometry.isAllCaps("ССЫЛКИ"), "Cyrillic uppercase should be all caps");

    // Simulate typical Roboto Medium 13sp StaticLayout in 48dp (144px) pill:
    // layoutHeight=53, lineBaseline=39, ascent=-36, descent=10, capHeight=28.
    // Old (144 - 53) / 2 = 45 puts ink [56..84] -> top space 56, bottom space 60 (4px high!).
    int allCapsTop = TabLabelGeometry.verticalTop(144, 53, 39, -36f, 10f, 28f, true);
    int inkTop = allCapsTop + 39 - 28;
    int inkBottom = allCapsTop + 39;
    check(Math.abs(inkTop - (144 - inkBottom)) <= 1, "all-caps ink box was not vertically centered: top=" + inkTop + " bottomGap=" + (144 - inkBottom));

    // Mixed case balances ascent/descent around baseline.
    int mixedTop = TabLabelGeometry.verticalTop(144, 53, 39, -36f, 10f, 28f, false);
    float visualCenter = mixedTop + 39 + (-36f + 10f) * 0.5f;
    check(Math.abs(visualCenter - 72f) <= 0.6f, "mixed-case optical center deviated: " + visualCenter);

    // Fallback when metrics unavailable
    check(TabLabelGeometry.verticalTop(144, 50, 0, 0f, 0f, 0f, true) == 47, "fallback centering mismatch");

    System.out.println("TabLabelGeometryTest: caps detection, optical all-caps/mixed centering, and fallback passed");
  }
}
