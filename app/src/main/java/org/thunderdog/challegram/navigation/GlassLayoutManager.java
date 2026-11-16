package org.thunderdog.challegram.navigation;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Keeps only the additional rows needed behind a floating page header. */
public class GlassLayoutManager extends LinearLayoutManager {
  private final RecyclerView list;
  public GlassLayoutManager (Context context, RecyclerView list) {
    super(context, RecyclerView.VERTICAL, false);
    this.list = list;
  }
  @Override protected void calculateExtraLayoutSpace (@NonNull RecyclerView.State state, @NonNull int[] space) {
    super.calculateExtraLayoutSpace(state, space);
    space[0] = Math.max(space[0], GlassPageContent.extraLayoutSpace(list));
  }
}
