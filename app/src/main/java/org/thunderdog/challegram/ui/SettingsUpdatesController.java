package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.v.CustomRecyclerView;

public final class SettingsUpdatesController extends RecyclerViewController<Void> implements View.OnClickListener, AppUpdater.Listener {
  private SettingsAdapter adapter;

  public SettingsUpdatesController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_frogramUpdates; }
  @Override public CharSequence getName () { return Lang.getString(R.string.FrogramUpdates); }

  @Override protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        AppUpdater updater = context().appUpdater();
        if (item.getId() == R.id.btn_frogramAutoUpdates) {
          view.getToggler().setRadioEnabled(updater.automaticChecksEnabled(), isUpdate);
        } else if (item.getId() == R.id.btn_frogramUpdateVersion) {
          view.setData(BuildConfig.VERSION_NAME);
        } else if (item.getId() == R.id.btn_checkUpdates) {
          view.setName(updater.release() != null ? R.string.FrogramWhatsNew : R.string.CheckForUpdates);
          view.setEnabledAnimated(updater.state() != AppUpdater.State.CHECKING, isUpdate);
          if (updater.lastError() != null) view.setData(updater.lastError());
          else if (updater.isVerifying()) view.setData(R.string.FrogramUpdateVerifying);
          else if (updater.state() == AppUpdater.State.DOWNLOADING) view.setData(Strings.buildSize(updater.bytesDownloaded()) + " / " + Strings.buildSize(updater.totalBytesToDownload()));
          else if (updater.state() == AppUpdater.State.READY_TO_INSTALL) view.setData(R.string.FrogramUpdateReady);
          else if (updater.state() == AppUpdater.State.AVAILABLE) view.setData(updater.displayVersion());
          else if (updater.state() == AppUpdater.State.CHECKING) view.setData(R.string.CheckingForUpdates);
          else view.setData("");
        }
      }
    };
    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_frogramUpdateVersion, 0, R.string.FrogramInstalledVersion),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_frogramAutoUpdates, 0, R.string.FrogramAutoCheckUpdates),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_checkUpdates, 0, R.string.CheckForUpdates),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
    }, false);
    recyclerView.setAdapter(adapter);
    context().appUpdater().addListener(this);
  }

  @Override public void onClick (View view) {
    AppUpdater updater = context().appUpdater();
    if (view.getId() == R.id.btn_frogramAutoUpdates) {
      updater.setAutomaticChecksEnabled(adapter.toggleView(view));
    } else if (view.getId() == R.id.btn_checkUpdates) {
      if (updater.state() == AppUpdater.State.NONE) updater.checkForUpdatesNow();
      else updater.showUpdateDetails();
    }
  }
  @Override public void onAppUpdateStateChanged (int state, int oldState, boolean isApk) { adapter.updateValuedSettingById(R.id.btn_checkUpdates); }
  @Override public void onAppUpdateDownloadProgress (long downloaded, long total) { adapter.updateValuedSettingById(R.id.btn_checkUpdates); }
  @Override public void destroy () {
    context().appUpdater().removeListener(this);
    super.destroy();
  }
}
