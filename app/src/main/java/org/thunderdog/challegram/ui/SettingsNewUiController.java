package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

public final class SettingsNewUiController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public SettingsNewUiController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_newUi; }
  @Override public CharSequence getName () { return Lang.getString(R.string.FrogramNewUi); }

  @Override protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_newChatHeader) {
          view.getToggler().setRadioEnabled(Settings.instance().useNewChatHeader(), isUpdate);
        } else if (item.getId() == R.id.btn_newChatInput) {
          view.getToggler().setRadioEnabled(Settings.instance().useNewChatInput(), isUpdate);
        }
      }
    };
    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_newChatHeader, 0, R.string.FrogramNewChatHeader),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_newChatInput, 0, R.string.FrogramNewChatInput),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
    }, false);
    recyclerView.setAdapter(adapter);
  }

  @Override public void onClick (View view) {
    if (view.getId() == R.id.btn_newChatHeader) {
      Settings.instance().setUseNewChatHeader(adapter.toggleView(view));
    } else if (view.getId() == R.id.btn_newChatInput) {
      Settings.instance().setUseNewChatInput(adapter.toggleView(view));
    }
  }
}
