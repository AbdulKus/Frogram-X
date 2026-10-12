package org.thunderdog.challegram.util;

/** Reversible phases shared by header, pinned rows and composer navigation. */
public final class GlassNavigationMotion {
  private GlassNavigationMotion () { }

  public static float outgoing (float progress) {
    return 1f - smooth(progress / .5f);
  }

  public static float incoming (float progress) {
    return smooth((progress - .35f) / .65f);
  }

  public static float geometry (float progress) {
    return smooth(progress / .58f);
  }

  /** A late pinned row retargets the shell and keeps incoming text behind the gate. */
  public static final class Geometry {
    private Segment curve;

    public Geometry (float from, float to) { curve = new Segment(from, to, 0f, .58f, null); }

    public void retarget (float target, float progress) {
      if (target == curve.to) return;
      curve = new Segment(value(progress), target, progress, Math.min(1f, Math.max(.58f, progress + .18f)), curve);
    }

    public float value (float progress) {
      return curve.value(progress);
    }

    public float incoming (float progress) {
      float start = Math.min(.35f, curve.end);
      return smooth((progress - start) / (1f - start));
    }

    private static final class Segment {
      final float from, to, start, end;
      final Segment previous;
      Segment (float from, float to, float start, float end, Segment previous) {
        this.from = from; this.to = to; this.start = start; this.end = end; this.previous = previous;
      }
      float value (float progress) {
        if (previous != null && progress < start) return previous.value(progress);
        float factor = end <= start ? 1f : smooth((progress - start) / (end - start));
        return from + (to - from) * factor;
      }
    }
  }

  private static float smooth (float factor) {
    factor = Math.max(0f, Math.min(1f, factor));
    return factor * factor * (3f - 2f * factor);
  }
}
