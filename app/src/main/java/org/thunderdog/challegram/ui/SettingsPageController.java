package org.thunderdog.challegram.ui;

import android.content.Context;
import org.thunderdog.challegram.telegram.Tdlib;

/** Settings pages share the glass shell while retaining their own rows and actions. */
public abstract class SettingsPageController<T> extends RecyclerViewController<T> {
  protected SettingsPageController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override protected boolean usesGlassPageHeader () { return true; }
  @Override protected boolean usesSettingsCards () { return true; }
  @Override protected android.view.View onCreateView (Context context) {
    android.view.View view = super.onCreateView(context);
    android.view.View recycler = getRecyclerView();
    recycler.setPadding(recycler.getPaddingLeft(), recycler.getPaddingTop() + org.thunderdog.challegram.tool.Screen.dp(12f), recycler.getPaddingRight(), recycler.getPaddingBottom());
    return view;
  }
}
