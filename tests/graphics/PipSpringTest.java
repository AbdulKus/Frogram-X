import org.thunderdog.challegram.util.DampedSpring;
import org.thunderdog.challegram.util.PipSpringMath;

public final class PipSpringTest {
  private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }

  public static void main(String[] args) {
    int parentW = 1080, parentH = 2400, pip = 333, margin = 33;
    int minX = margin, maxX = parentW - pip - margin;
    int minY = 276 + margin, maxY = parentH - pip - margin;

    // Velocity-aware horizontal edge docking
    check(PipSpringMath.pickRightEdge(200f, 3500f, pip, parentW, 0.14f), "fast rightward throw should cross to right edge");
    check(!PipSpringMath.pickRightEdge(700f, -3500f, pip, parentW, 0.14f), "fast leftward throw should cross to left edge");
    check(!PipSpringMath.pickRightEdge(100f, 0f, pip, parentW, 0.14f), "stationary left PiP drifted right");
    check(PipSpringMath.dockX(false, minX, maxX) == minX, "left dock X mismatch");
    check(PipSpringMath.dockX(true, minX, maxX) == maxX, "right dock X mismatch");

    // Continuous free vertical placement (not snapping only to top/bottom corners)
    float midY = 1050f;
    float targetY = PipSpringMath.clampTargetY(midY, 0f, minY, maxY, 0.14f);
    check(Math.abs(targetY - midY) < 0.01f, "stationary PiP at arbitrary mid-Y was snapped to corner");
    check(PipSpringMath.clampTargetY(50f, -2000f, minY, maxY, 0.14f) == minY, "upward throw escaped minY clearance");
    check(PipSpringMath.clampTargetY(2200f, 2000f, minY, maxY, 0.14f) == maxY, "downward throw escaped maxY");

    float frac = PipSpringMath.verticalFraction(midY, minY, maxY);
    check(Math.abs(PipSpringMath.verticalMargin(minY, parentH, pip, margin, frac) - Math.round(midY)) <= 1, "vertical fraction round-trip drifted");

    // Edge-swipe dismissal vs resting edge jitter
    check(PipSpringMath.shouldDismiss(-100f, pip, parentW, margin, 0.55f, 0f, 600f), "low alpha should dismiss");
    check(PipSpringMath.shouldDismiss(5f, pip, parentW, margin, 0.9f, -1500f, 600f), "leftward edge fling should dismiss");
    check(PipSpringMath.shouldDismiss(parentW - pip - 5f, pip, parentW, margin, 0.9f, 1500f, 600f), "rightward edge fling should dismiss");
    check(!PipSpringMath.shouldDismiss(margin, pip, parentW, margin, 1f, -4000f, 600f), "fling from resting inner margin should dock unless past threshold");

    // Spring convergence with drag-release velocity at 60/90/120 Hz
    for (int fps : new int[] {60, 90, 120}) {
      DampedSpring sx = new DampedSpring();
      DampedSpring sy = new DampedSpring();
      sx.reset(400f);
      sx.target = maxX;
      sx.setVelocity(1800f);
      sy.reset(midY);
      sy.target = PipSpringMath.clampTargetY(midY, 900f, minY, maxY, 0.14f);
      sy.setVelocity(900f);
      for (int i = 0; i < fps * 2; i++) {
        sx.step(1f / fps);
        sy.step(1f / fps);
        check(Float.isFinite(sx.value) && Float.isFinite(sy.value), "non-finite PiP spring position");
      }
      check(!sx.isMoving() && !sy.isMoving(), "PiP springs did not settle");
      check(sx.value == maxX, "PiP X did not settle on target edge");
    }

    System.out.println("PipSpringTest: edge picking, mid-screen Y preservation, clamping, dismiss hysteresis, and 60/90/120Hz spring convergence passed");
  }
}
