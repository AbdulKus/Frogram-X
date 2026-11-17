package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.Arrays;

/** One rounded surface per popular pack, retaining the grid's real cell hit targets. */
public final class TrendingPackCards extends RecyclerView.ItemDecoration {
  private final MediaStickersAdapter adapter;
  private final ArrayList<int[]> groups = new ArrayList<>();
  private final RectF bounds = new RectF();
  private final Path clip = new Path();
  private final SettingsCardSurface surface = new SettingsCardSurface();
  private int[] groupForPosition = new int[0];
  private boolean dirty = true;

  public TrendingPackCards (MediaStickersAdapter adapter) {
    this.adapter = adapter;
    adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override public void onChanged () { dirty = true; }
      @Override public void onItemRangeChanged (int start, int count) { dirty = true; }
      @Override public void onItemRangeInserted (int start, int count) { dirty = true; }
      @Override public void onItemRangeRemoved (int start, int count) { dirty = true; }
      @Override public void onItemRangeMoved (int from, int to, int count) { dirty = true; }
    });
  }

  private void updateGroups () {
    if (!dirty && groupForPosition.length == adapter.getItemCount()) return;
    dirty = false;
    groups.clear();
    groupForPosition = new int[adapter.getItemCount()];
    Arrays.fill(groupForPosition, -1);
    int current = -1;
    for (int p = 0; p < adapter.getItemCount(); p++) {
      int type = adapter.getItemViewType(p);
      if (type == MediaStickersAdapter.StickerHolder.TYPE_HEADER_TRENDING) {
        current = groups.size();
        groups.add(new int[] {p, p});
      } else if (type != MediaStickersAdapter.StickerHolder.TYPE_STICKER && type != MediaStickersAdapter.StickerHolder.TYPE_EMPTY) {
        current = -1;
      }
      if (current >= 0) { groupForPosition[p] = current; groups.get(current)[1] = p; }
    }
  }

  private void groupBounds (RecyclerView parent, int group) {
    int[] range = groups.get(group);
    View first = parent.getLayoutManager().findViewByPosition(range[0]);
    View last = parent.getLayoutManager().findViewByPosition(range[1]);
    float radius = Screen.dp(GlassStyle.RADIUS_DP);
    bounds.set(parent.getPaddingLeft(), first != null ? first.getY() : -radius,
      parent.getWidth() - parent.getPaddingRight(), last != null ? last.getY() + last.getHeight() : parent.getHeight() + radius);
  }

  public boolean clipChild (Canvas canvas, View child, RecyclerView parent) {
    int p = parent.getChildAdapterPosition(child);
    if (p < 0 || p >= adapter.getItemCount()) return true;
    if (adapter.getItemViewType(p) == MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR) return false;
    updateGroups();
    int group = groupForPosition[p];
    if (group < 0) return true;
    groupBounds(parent, group);
    float radius = Screen.dp(GlassStyle.RADIUS_DP);
    float left = child.getX(), top = child.getY(), right = left + child.getWidth(), bottom = top + child.getHeight();
    if (left >= bounds.left && right <= bounds.right && top >= bounds.top && bottom <= bounds.bottom &&
        ((left >= bounds.left + radius && right <= bounds.right - radius) ||
         (top >= bounds.top + radius && bottom <= bounds.bottom - radius))) return true;
    clip.reset();
    clip.addRoundRect(bounds, Screen.dp(GlassStyle.RADIUS_DP), Screen.dp(GlassStyle.RADIUS_DP), Path.Direction.CW);
    canvas.clipPath(clip);
    return true;
  }

  @Override public void getItemOffsets (@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    int p = parent.getChildAdapterPosition(view);
    if (p >= 0 && p < adapter.getItemCount() && adapter.getItemViewType(p) == MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR) out.bottom = Screen.dp(7f);
  }

  private void drawGroups (Canvas canvas, RecyclerView parent, boolean foreground) {
    updateGroups();
    int previous = -1;
    for (int i = 0; i < parent.getChildCount(); i++) {
      int p = parent.getChildAdapterPosition(parent.getChildAt(i));
      if (p < 0 || p >= groupForPosition.length) continue;
      int group = groupForPosition[p];
      if (group < 0 || group == previous) continue;
      previous = group;
      groupBounds(parent, group);
      surface.drawGroup(canvas, bounds, foreground);
    }
  }

  @Override public void onDraw (@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) { drawGroups(canvas, parent, false); }
  @Override public void onDrawOver (@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) { drawGroups(canvas, parent, true); }
}
