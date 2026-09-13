package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import org.thunderdog.challegram.tool.Screen;

/** One outline for the composer, microphone bulge, elastic neck and lock. */
public final class RecordingGlassView extends View {
  private ChatGlassDrawable glass;
  private final Path shape = new Path(), part = new Path();
  private final RectF bounds = new RectF();
  public RecordingGlassView (Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
  public void setGlass (ChatGlassDrawable glass) { this.glass = glass; invalidate(); }

  public void setGeometry (RectF island, float cx, float cy, float radius, RectF lock) {
    shape.reset();
    shape.addRoundRect(island, Screen.dp(26f), Screen.dp(26f), Path.Direction.CW);
    if (radius > 0f) {
      part.reset(); part.addCircle(cx, cy, radius, Path.Direction.CW); shape.op(part, Path.Op.UNION);
      float gap = island.top - (cy + radius);
      // The neck narrows continuously, then separates. The same path reconnects on return.
      if (cy < island.centerY() && gap < Screen.dp(18f)) {
        float strength = Math.max(0f, Math.min(1f, 1f - gap / Screen.dp(18f)));
        float neck = radius * .55f * strength;
        float bottom = island.top + Screen.dp(12f);
        float top = cy + radius * .55f;
        if (top < bottom && neck > .5f) {
          part.reset(); part.moveTo(cx - radius * .75f, bottom);
          part.cubicTo(cx - neck, bottom - Screen.dp(10f), cx - neck, top + Screen.dp(10f), cx - radius * .7f, top);
          part.lineTo(cx + radius * .7f, top);
          part.cubicTo(cx + neck, top + Screen.dp(10f), cx + neck, bottom - Screen.dp(10f), cx + radius * .75f, bottom);
          part.close(); shape.op(part, Path.Op.UNION);
        }
      }
    }
    if (lock != null && !lock.isEmpty()) {
      part.reset(); part.addRoundRect(lock, lock.width() / 2f, lock.width() / 2f, Path.Direction.CW);
      shape.op(part, Path.Op.UNION);
    }
    shape.computeBounds(bounds, true);
    if (glass != null) {
      glass.setBounds((int) Math.floor(bounds.left), (int) Math.floor(bounds.top), (int) Math.ceil(bounds.right), (int) Math.ceil(bounds.bottom));
      glass.setShape(shape);
    }
    invalidate();
  }
  @Override protected void onDraw (Canvas canvas) { if (glass != null) glass.draw(canvas); }
}
