package org.thunderdog.challegram.ui;

import android.content.Context;
import org.thunderdog.challegram.telegram.Tdlib;

/** Settings pages share the glass shell while retaining their own rows and actions. */
public abstract class SettingsPageController<T> extends RecyclerViewController<T> {
  protected SettingsPageController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override protected boolean usesGlassPageHeader () { return true; }
}
