/*
 * This file is a part of Frogram X.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DoubleBottomSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private CustomRecyclerView recyclerView;
  private boolean primarySetupOffered;
  private boolean hiddenSetupOffered;
  private boolean accessPromptOffered;
  private Runnable pendingSecureNavigation;

  public DoubleBottomSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_doubleBottom;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.DoubleBottom);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.recyclerView = recyclerView;
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setCenterIcon(true);
        if (item.getId() == R.id.btn_doubleBottomNotifications) {
          view.setData(getNotificationModeName());
        } else if (item.getId() == R.id.btn_doubleBottomAutoLock) {
          view.setData(Passcode.instance().getAutolockModeNames()[Passcode.instance().getAutolockMode()]);
        } else if (item.getId() == R.id.btn_notificationContent) {
          view.getToggler().setRadioEnabled(Passcode.instance().displayNotifications(), isUpdate);
        } else if (item.getId() == R.id.btn_passcodeNotificationActions) {
          view.getToggler().setRadioEnabled(Passcode.instance().allowNotificationActions(), isUpdate);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    rebuildCells();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter == null) {
      return;
    }
    Passcode passcode = Passcode.instance();
    if (passcode.isDoubleBottomEnabled()) {
      if (passcode.isHiddenAccountsUnlocked()) {
        accessPromptOffered = false;
        rebuildCells();
      } else if (!accessPromptOffered) {
        accessPromptOffered = true;
        navigateWhenReady(this::openHiddenAccessPrompt);
      }
    } else if (passcode.isEnabled() && Passcode.isValidHiddenMode(passcode.getMode())) {
      if (!hiddenSetupOffered) {
        hiddenSetupOffered = true;
        navigateWhenReady(this::openHiddenPasscodeSetup);
      } else {
        rebuildCells();
      }
    } else if (!primarySetupOffered) {
      primarySetupOffered = true;
      navigateWhenReady(this::offerPrimaryPasscodeType);
    }
  }

  private void navigateWhenReady (Runnable action) {
    if (pendingSecureNavigation != null) {
      return;
    }
    pendingSecureNavigation = new Runnable() {
      @Override
      public void run () {
        if (pendingSecureNavigation != this) {
          return;
        }
        if (isDestroyed() || !isFocused()) {
          pendingSecureNavigation = null;
          primarySetupOffered = false;
          hiddenSetupOffered = false;
          accessPromptOffered = false;
          return;
        }
        if (isNavigationAnimating()) {
          UI.post(this, 50l);
          return;
        }
        pendingSecureNavigation = null;
        action.run();
      }
    };
    UI.post(pendingSecureNavigation, 180l);
  }

  private void rebuildCells () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    Passcode passcode = Passcode.instance();
    if (!passcode.isDoubleBottomEnabled() || !passcode.isHiddenAccountsUnlocked()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_doubleBottomSetup, R.drawable.baseline_lock_24, R.string.DoubleBottomSetup));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DoubleBottomSetupInfo));
      adapter.setItems(items, true);
      return;
    }

    ArrayList<TdlibAccount> accounts = TdlibManager.instance().getActiveAccounts();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.DoubleBottomPrimaryAccounts));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    int primaryCount = 0;
    for (TdlibAccount account : accounts) {
      if (!passcode.isAccountHidden(account.id)) {
        if (primaryCount++ > 0) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(newAccountItem(account, false));
      }
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.DoubleBottomHiddenAccounts));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    int hiddenCount = 0;
    for (TdlibAccount account : accounts) {
      if (passcode.isAccountHidden(account.id)) {
        if (hiddenCount++ > 0) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(newAccountItem(account, true));
      }
    }
    if (hiddenCount == 0) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DoubleBottomHiddenAccounts));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DoubleBottomAccountsInfo));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Notifications));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_notificationContent, R.drawable.baseline_visibility_24, R.string.AllowNotifications));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_passcodeNotificationActions, R.drawable.baseline_reply_24, R.string.PasscodeNotificationActions));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_doubleBottomNotifications, R.drawable.baseline_notifications_24, R.string.DoubleBottomNotifications));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SecurityTitle));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_doubleBottomAutoLock, R.drawable.baseline_schedule_24, R.string.AutoLock));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_doubleBottomChangePasscode, R.drawable.baseline_vpn_key_24, R.string.DoubleBottomChangeHiddenPasscode));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, true);
  }

  private ListItem newAccountItem (TdlibAccount account, boolean hidden) {
    return new ListItem(
      ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR,
      R.id.btn_doubleBottomAccount,
      0,
      account.getLongName(),
      hidden
    ).setData(account).setLongValue(account.getKnownUserId());
  }

  private CharSequence getNotificationModeName () {
    switch (Passcode.instance().getHiddenNotificationMode()) {
      case Passcode.HIDDEN_NOTIFICATIONS_NONE:
        return Lang.getString(R.string.DoubleBottomNotificationsNone);
      case Passcode.HIDDEN_NOTIFICATIONS_ALL:
        return Lang.getString(R.string.DoubleBottomNotificationsAll);
      case Passcode.HIDDEN_NOTIFICATIONS_GENERIC:
      default:
        return Lang.getString(R.string.DoubleBottomNotificationsGeneric);
    }
  }

  private void offerPrimaryPasscodeType () {
    int[] ids = {
      Passcode.MODE_PINCODE,
      Passcode.MODE_PASSWORD,
      Passcode.MODE_PATTERN
    };
    String[] names = {
      Passcode.getModeName(Passcode.MODE_PINCODE).toString(),
      Passcode.getModeName(Passcode.MODE_PASSWORD).toString(),
      Passcode.getModeName(Passcode.MODE_PATTERN).toString()
    };
    showOptions(
      Lang.getString(Passcode.instance().isEnabled() ? R.string.DoubleBottomUnsupportedPasscode : R.string.DoubleBottomSetupInfo),
      ids,
      names,
      (itemView, id) -> {
        openPrimaryPasscodeSetup(id);
        return true;
      }
    );
  }

  private void openPrimaryPasscodeSetup (int mode) {
    PasscodeController controller = new PasscodeController(context, tdlib);
    controller.setPasscodeMode(PasscodeController.MODE_SETUP);
    controller.forceMode(mode);
    navigateTo(controller);
  }

  private void openHiddenPasscodeSetup () {
    Passcode passcode = Passcode.instance();
    if (!Passcode.isValidHiddenMode(passcode.getMode())) {
      primarySetupOffered = false;
      offerPrimaryPasscodeType();
      return;
    }
    PasscodeController controller = new PasscodeController(context, tdlib);
    controller.setPasscodeMode(PasscodeController.MODE_SETUP);
    controller.setHiddenPasscodeSetup();
    controller.forceMode(passcode.getMode());
    navigateTo(controller);
  }

  private void openHiddenAccessPrompt () {
    PasscodeController controller = new PasscodeController(context, tdlib);
    controller.setPasscodeMode(PasscodeController.MODE_UNLOCK);
    controller.requireHiddenAccess();
    controller.setAfterStandaloneUnlock(this::navigateBack, this::rebuildCells);
    navigateTo(controller);
  }

  private void showNotificationOptions () {
    int[] ids = {
      Passcode.HIDDEN_NOTIFICATIONS_NONE,
      Passcode.HIDDEN_NOTIFICATIONS_GENERIC,
      Passcode.HIDDEN_NOTIFICATIONS_ALL
    };
    String[] names = {
      Lang.getString(R.string.DoubleBottomNotificationsNone),
      Lang.getString(R.string.DoubleBottomNotificationsGeneric),
      Lang.getString(R.string.DoubleBottomNotificationsAll)
    };
    showOptions(ids, names, (itemView, id) -> {
      Passcode.instance().setHiddenNotificationMode(id);
      adapter.updateValuedSettingById(R.id.btn_doubleBottomNotifications);
      return true;
    });
  }

  private void showAutoLockOptions () {
    String[] names = Passcode.instance().getAutolockModeNames();
    int[] ids = new int[names.length];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = i;
    }
    showOptions(ids, names, (itemView, id) -> {
      Passcode.instance().setAutolockMode(id);
      adapter.updateValuedSettingById(R.id.btn_doubleBottomAutoLock);
      return true;
    });
  }

  @Override
  public void onClick (View v) {
    ListItem item = (ListItem) v.getTag();
    if (item == null) {
      return;
    }
    if (item.getId() == R.id.btn_doubleBottomSetup) {
      if (Passcode.instance().isDoubleBottomEnabled() && !Passcode.instance().isHiddenAccountsUnlocked()) {
        accessPromptOffered = true;
        openHiddenAccessPrompt();
      } else if (Passcode.instance().isEnabled() && Passcode.isValidHiddenMode(Passcode.instance().getMode())) {
        hiddenSetupOffered = true;
        openHiddenPasscodeSetup();
      } else {
        primarySetupOffered = true;
        offerPrimaryPasscodeType();
      }
    } else if (item.getId() == R.id.btn_doubleBottomAccount) {
      TdlibAccount account = (TdlibAccount) item.getData();
      boolean hidden = Passcode.instance().isAccountHidden(account.id);
      if (!hidden) {
        int primaryCount = 0;
        for (TdlibAccount activeAccount : TdlibManager.instance().getActiveAccounts()) {
          if (!Passcode.instance().isAccountHidden(activeAccount.id)) {
            primaryCount++;
          }
        }
        if (primaryCount <= 1) {
          UI.showToast(R.string.DoubleBottomKeepPrimaryAccount, Toast.LENGTH_SHORT);
          return;
        }
      }
      Passcode.instance().setAccountHidden(account.id, !hidden);
      rebuildCells();
    } else if (item.getId() == R.id.btn_doubleBottomNotifications) {
      showNotificationOptions();
    } else if (item.getId() == R.id.btn_notificationContent) {
      Passcode.instance().setDisplayNotifications(adapter.toggleView(v, item));
      TdlibManager.instance().onUpdateAllNotifications();
    } else if (item.getId() == R.id.btn_passcodeNotificationActions) {
      Passcode.instance().setAllowNotificationActions(adapter.toggleView(v, item));
      TdlibManager.instance().onUpdateAllNotifications();
    } else if (item.getId() == R.id.btn_doubleBottomAutoLock) {
      showAutoLockOptions();
    } else if (item.getId() == R.id.btn_doubleBottomChangePasscode) {
      openHiddenPasscodeSetup();
    }
  }
}
