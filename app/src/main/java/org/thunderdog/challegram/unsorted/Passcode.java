/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 04/08/2015 at 11:34
 */
package org.thunderdog.challegram.unsorted;

import android.content.SharedPreferences;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.BiometricAuthentication;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.reference.ReferenceList;

public class Passcode implements UI.StateListener {
  public interface LockListener {
    void onPasscodeLocked (boolean isLocked);
  }

  private static Passcode instance;

  public static Passcode instance () {
    if (instance == null) {
      instance = new Passcode();
    }
    return instance;
  }

  public static final int MODE_NONE = 0x00;
  public static final int MODE_PINCODE  = 0x01;
  public static final int MODE_PASSWORD = 0x02;
  public static final int MODE_PATTERN  = 0x03;
  public static final int MODE_GESTURE  = 0x04;
  public static final int MODE_BIOMETRICS = 0x05;

  public static boolean isValidMode (int mode) {
    switch (mode) {
      case MODE_PINCODE:
      case MODE_PASSWORD:
      case MODE_PATTERN:
      case MODE_GESTURE:
      case MODE_BIOMETRICS:
        return true;
    }
    return false;
  }

  public static final int AUTOLOCK_MODE_NONE = 0x00;
  public static final int AUTOLOCK_MODE_1MIN = 0x01;
  public static final int AUTOLOCK_MODE_5MINS = 0x02;
  public static final int AUTOLOCK_MODE_1HOUR = 0x03;
  public static final int AUTOLOCK_MODE_5HOURS = 0x04;
  public static final int AUTOLOCK_MODE_INSTANT = 0x05;

  public static final int STATE_UNLOCK  = 0x01;
  public static final int STATE_CHOOSE  = 0x02;
  public static final int STATE_CONFIRM = 0x03;

  public static final int MIN_PASSWORD_SIZE = 1;
  public static final int MIN_PATTERN_SIZE = 4;
  public static final int PINCODE_SIZE = 4;

  @SuppressWarnings("SpellCheckingInspection")
  private static final String SALT_OLD = "VGhpcyBpcyBhIHZlcnkgc2VjdXJlIHNhbHQgb2YgQ2hhbGxlZ3JhbS4=";
  private static final String SALT_NEW = "VGhpcyBpcyB0aGUgbW9yZSBzZWN1cmUgc2FsdCBvZiBUZWxlZ3JhbSBY";

  private int mode;
  private Boolean _visible, _allowScreenshots, _displayNotifications;

  private int autolockMode;
  private long autolockTime;

  private String passcodeHash, biometricsHash;
  private int biometricsOptions;

  private boolean isLocked;
  private int hiddenPasscodeMode;
  private String hiddenPasscodeHash;
  private boolean hiddenAccountsUnlocked;
  private int hiddenNotificationMode;
  private final Set<Integer> hiddenAccountIds = new HashSet<>();
  private final ReferenceList<LockListener> listeners = new ReferenceList<>(true);

  public static final String KEY_PASSCODE_MODE = "pc_mode";
  public static final String KEY_PASSCODE_HASH = "pc_hash";
  private static final String KEY_PASSCODE_LOCKED = "pc_locked";
  private static final String KEY_PASSCODE_VISIBLE = "pc_visible";
  private static final String KEY_PASSCODE_ALLOW_SCREENSHOTS = "pc_allow_ss";
  private static final String KEY_PASSCODE_AUTOLOCK_MODE = "pc_al_mode";
  private static final String KEY_PASSCODE_AUTOLOCK_TIME = "pc_time";
  private static final String KEY_PASSCODE_DISPLAY_NOTIFICATIONS = "pc_notifications";
  public static final String KEY_PASSCODE_BIOMETRICS_HASH = "pc_finger_hash";
  private static final String KEY_HIDDEN_PASSCODE_MODE = "pc_hidden_mode";
  private static final String KEY_HIDDEN_PASSCODE_HASH = "pc_hidden_hash";
  private static final String KEY_HIDDEN_ACCOUNT_IDS = "pc_hidden_accounts";
  private static final String KEY_HIDDEN_NOTIFICATION_MODE = "pc_hidden_notifications";

  public static final String KEY_PASSCODE_BIOMETRICS_OPTIONS = "pc_biometrics";
  public static final int BIOMETRICS_OPTION_ONLY_STRONG = 1;

  public static final int HIDDEN_NOTIFICATIONS_NONE = 0;
  public static final int HIDDEN_NOTIFICATIONS_GENERIC = 1;
  public static final int HIDDEN_NOTIFICATIONS_ALL = 2;

  private Passcode () {
    Settings prefs = Settings.instance();
    mode = prefs.getInt(KEY_PASSCODE_MODE, MODE_NONE);
    autolockMode = prefs.getInt(KEY_PASSCODE_AUTOLOCK_MODE, AUTOLOCK_MODE_NONE);

    biometricsHash = prefs.getString(KEY_PASSCODE_BIOMETRICS_HASH, null);
    biometricsOptions = prefs.getInt(KEY_PASSCODE_BIOMETRICS_OPTIONS, 0);

    if (mode != MODE_NONE) {
      isLocked = prefs.getBoolean(KEY_PASSCODE_LOCKED, false);
      if (autolockMode != AUTOLOCK_MODE_NONE) {
        autolockTime = prefs.getLong(KEY_PASSCODE_AUTOLOCK_TIME, 0);
        checkAutoLock();
      }
      passcodeHash = prefs.getString(KEY_PASSCODE_HASH, null);
    }
    hiddenPasscodeMode = prefs.getInt(KEY_HIDDEN_PASSCODE_MODE, MODE_NONE);
    hiddenPasscodeHash = prefs.getString(KEY_HIDDEN_PASSCODE_HASH, null);
    hiddenNotificationMode = prefs.getInt(KEY_HIDDEN_NOTIFICATION_MODE, HIDDEN_NOTIFICATIONS_GENERIC);
    parseHiddenAccountIds(prefs.getString(KEY_HIDDEN_ACCOUNT_IDS, null));

    UI.addStateListener(this);
  }

  private void parseHiddenAccountIds (String value) {
    hiddenAccountIds.clear();
    if (value == null || value.isEmpty()) {
      return;
    }
    for (String part : value.split(",")) {
      try {
        hiddenAccountIds.add(Integer.parseInt(part));
      } catch (NumberFormatException ignored) { }
    }
  }

  private String serializeHiddenAccountIds () {
    StringBuilder builder = new StringBuilder();
    for (int accountId : hiddenAccountIds) {
      if (builder.length() > 0) {
        builder.append(',');
      }
      builder.append(accountId);
    }
    return builder.toString();
  }

  public boolean displayNotifications () {
    if (_displayNotifications == null)
      _displayNotifications = Settings.instance().getBoolean(KEY_PASSCODE_DISPLAY_NOTIFICATIONS, false);
    return _displayNotifications;
  }

  public void setDisplayNotifications (boolean display) {
    this._displayNotifications = display;
    if (display)
      Settings.instance().putBoolean(KEY_PASSCODE_DISPLAY_NOTIFICATIONS, display);
    else
      Settings.instance().remove(KEY_PASSCODE_DISPLAY_NOTIFICATIONS);
  }

  @Override
  public void onUiStateChanged (int newState) {
    if (newState == UI.State.PAUSED) {
      trackUserActivity(true);
    }
  }

  public void addLockListener (LockListener listener) {
    listeners.add(listener);
  }

  public void removeLockListener (LockListener listener) {
    listeners.remove(listener);
  }

  private void notifyLockListeners (boolean isLocked) {
    for (LockListener listener : listeners) {
      listener.onPasscodeLocked(isLocked);
    }
  }

  private void setLocked (boolean isLocked) {
    boolean hiddenAccessChanged = false;
    if (isLocked && hiddenAccountsUnlocked) {
      hiddenAccountsUnlocked = false;
      hiddenAccessChanged = true;
    }
    if (this.isLocked != isLocked) {
      this.isLocked = isLocked;
      SharedPreferences.Editor editor = Settings.instance().edit();
      editor.putBoolean(KEY_PASSCODE_LOCKED, isLocked);
      if (!isLocked) {
        autolockTime = System.currentTimeMillis() + getAutolockTime();
        editor.putLong(KEY_PASSCODE_AUTOLOCK_TIME, autolockTime);
      }
      editor.apply();
      UI.checkDisallowScreenshots();
      notifyLockListeners(isLocked);
    }
    if (hiddenAccessChanged) {
      onHiddenAccountAccessChanged();
    }
  }

  public boolean toggleLock () {
    setLocked(!isLocked);
    return isLocked;
  }

  public void setAutolockMode (int mode) {
    if (this.autolockMode != mode) {
      this.autolockMode = mode;
      this.autolockTime = mode == AUTOLOCK_MODE_NONE ? 0l : System.currentTimeMillis() + getAutolockTime();
      SharedPreferences.Editor prefs = Settings.instance().edit();
      prefs.putInt(KEY_PASSCODE_AUTOLOCK_MODE, mode);
      prefs.putLong(KEY_PASSCODE_AUTOLOCK_TIME, autolockTime);
      prefs.apply();
      UI.checkDisallowScreenshots();
    }
  }

  public long getAutolockTime () {
    switch (autolockMode) {
      case AUTOLOCK_MODE_1MIN: return TimeUnit.MINUTES.toMillis(1);
      case AUTOLOCK_MODE_5MINS: return TimeUnit.MINUTES.toMillis(5);
      case AUTOLOCK_MODE_1HOUR: return TimeUnit.HOURS.toMillis(1);
      case AUTOLOCK_MODE_5HOURS: return TimeUnit.HOURS.toMillis(5);
      case AUTOLOCK_MODE_INSTANT: return TimeUnit.MILLISECONDS.toMillis(170);
    }
    return 0;
  }

  public boolean needUnlockWithBiometrics () {
    return biometricsHash != null;
  }

  public void disableUnlockByBiometrics () {
    if (biometricsHash != null) {
      biometricsHash = null;
      biometricsOptions = 0;
      Settings.instance().edit()
        .remove(KEY_PASSCODE_BIOMETRICS_HASH)
        .remove(KEY_PASSCODE_BIOMETRICS_OPTIONS)
        .apply();
    }
  }

  public void enableUnlockByBiometrics (long biometricsId, boolean strong) {
    this.biometricsHash = getPasscodeHashOld(String.valueOf(biometricsId));
    this.biometricsOptions = BitwiseUtils.optional(BIOMETRICS_OPTION_ONLY_STRONG, strong);
    Settings.instance().edit()
      .putString(KEY_PASSCODE_BIOMETRICS_HASH, biometricsHash)
      .putInt(KEY_PASSCODE_BIOMETRICS_OPTIONS, biometricsOptions)
      .apply();
  }

  public boolean useStrongBiometrics () {
    return BitwiseUtils.hasFlag(biometricsOptions, BIOMETRICS_OPTION_ONLY_STRONG);
  }

  public int getAutolockMode () {
    return autolockMode;
  }

  private long lastAutolockTraceTime;

  public void trackUserActivity (boolean force) {
    if (autolockMode != AUTOLOCK_MODE_NONE) {
      long time = System.currentTimeMillis();
      if (force || lastAutolockTraceTime == 0 || time - lastAutolockTraceTime >= 10000l) {
        lastAutolockTraceTime = time;
        autolockTime = time + getAutolockTime();
        Settings.instance().putLong(KEY_PASSCODE_AUTOLOCK_TIME, autolockTime);
        if (force) {
          checkAutoLock();
        }
      }
    }
  }

  public boolean checkAutoLock () {
    if (autolockMode != AUTOLOCK_MODE_NONE && autolockTime > 0l && System.currentTimeMillis() >= autolockTime) {
      setLocked(true);
      return true;
    }
    return false;
  }

  public boolean allowScreenshots () {
    if (_allowScreenshots == null)
      _allowScreenshots = Settings.instance().getBoolean(KEY_PASSCODE_ALLOW_SCREENSHOTS, false);
    return _allowScreenshots;
  }

  public void setAllowScreenshots (boolean allowScreenshots) {
    this._allowScreenshots = allowScreenshots;
    if (allowScreenshots) {
      Settings.instance().putBoolean(KEY_PASSCODE_ALLOW_SCREENSHOTS, true);
    } else {
      Settings.instance().remove(KEY_PASSCODE_ALLOW_SCREENSHOTS);
    }
  }

  public boolean shouldDisallowScreenshots () {
    return !allowScreenshots() && (isLocked() || (autolockMode != AUTOLOCK_MODE_NONE && mode != MODE_NONE));
  }

  public boolean isLocked () {
    return isEnabled() && (checkAutoLock() || isLocked);
  }

  public boolean isLockedAndVisible () {
    if (isLocked()) {
      BaseActivity activity = UI.getUiContext();
      return UI.getUiState() != UI.State.RESUMED || activity == null || activity.isPasscodeShowing();
    }
    return false;
  }

  public void setVisible (boolean visible) {
    this._visible = visible;
    Settings.instance().putBoolean(KEY_PASSCODE_VISIBLE, visible);
  }

  public boolean isVisible () {
    if (_visible == null)
      _visible = Settings.instance().getBoolean(KEY_PASSCODE_VISIBLE, true);
    return _visible;
  }

  public boolean canBeInvisible () {
    return canBeInvisible(mode);
  }

  public static boolean canBeInvisible (int mode) {
    return mode == MODE_PINCODE || (!Config.DISABLE_PASSWORD_INVISIBILITY && mode == MODE_PASSWORD) || mode == MODE_PATTERN || mode == MODE_GESTURE;
  }

  public boolean isEnabled () {
    return mode != MODE_NONE;
  }

  public void disable () {
    if (mode != MODE_NONE) {
      disableDoubleBottom();
      mode = MODE_NONE;
      Settings.instance().putInt(KEY_PASSCODE_MODE, MODE_NONE);
      setLocked(false);
    }
  }

  public int getMode () {
    return mode;
  }

  public static String getPasscodeHash (String passcode) {
    return passcode != null ? U.sha256(U.sha256(passcode + SALT_NEW)) : null;
  }

  private static String getPasscodeHashOld (String passcode) {
    return passcode != null ? U.md5(U.md5(passcode + SALT_OLD)) : null;
  }

  public void setPasscodeHash (int mode, String passcode, int extraOptions) {
    boolean turnedOn = this.mode == MODE_NONE && mode != MODE_NONE;
    if (isDoubleBottomEnabled() &&
        (this.mode != mode || (hiddenPasscodeMode == mode && hiddenPasscodeHash.equals(getPasscodeHash(passcode))))) {
      disableDoubleBottom();
    }
    this.mode = mode;
    this.passcodeHash = getPasscodeHashOld(passcode);
    SharedPreferences.Editor edit = Settings.instance().edit();
    edit.putInt(KEY_PASSCODE_MODE, mode);
    if (passcodeHash != null) {
      edit.putString(KEY_PASSCODE_HASH, passcodeHash);
    } else {
      edit.remove(KEY_PASSCODE_HASH);
    }
    if (mode == MODE_BIOMETRICS && passcodeHash != null) {
      this.biometricsOptions = extraOptions;
      edit.putInt(KEY_PASSCODE_BIOMETRICS_OPTIONS, extraOptions);
    }
    edit.apply();
    if (turnedOn) {
      TdlibManager.instance().onUpdateAllNotifications();
    }
  }

  public boolean comparePincode (String pincode) {
    return passcodeHash != null && pincode != null && pincode.length() == PINCODE_SIZE && passcodeHash.equals(getPasscodeHashOld(pincode));
  }

  public boolean comparePassword (String password) {
    return passcodeHash != null && password != null && password.length() >= MIN_PASSWORD_SIZE && passcodeHash.equals(getPasscodeHashOld(password));
  }

  public boolean comparePattern (String pattern) {
    return passcodeHash != null && pattern != null && pattern.length() >= MIN_PATTERN_SIZE && passcodeHash.equals(getPasscodeHashOld(pattern));
  }

  public boolean compareBiometrics (long biometricsId, boolean strong) {
    final boolean ok;
    if (mode == MODE_BIOMETRICS) {
      ok = passcodeHash != null && passcodeHash.equals(getPasscodeHashOld(String.valueOf(biometricsId)));
    } else {
      ok = biometricsHash != null && biometricsHash.equals(getPasscodeHashOld(String.valueOf(biometricsId)));
    }
    if (ok) {
      return strong || !BitwiseUtils.hasFlag(biometricsOptions, BIOMETRICS_OPTION_ONLY_STRONG);
    }
    return false;
  }

  public static boolean isValidPincode (String pincode) {
    return pincode != null && pincode.length() == PINCODE_SIZE;
  }

  public void setPincode (String pincode) {
    setPasscodeHash(MODE_PINCODE, pincode, 0);
  }

  public static boolean isValidPassword (String password) {
    return password != null && password.length() >= MIN_PASSWORD_SIZE;
  }

  public void setPassword (String password) {
    setPasscodeHash(MODE_PASSWORD, password,0 );
  }

  public void setGesture () {
    setPasscodeHash(MODE_GESTURE, "", 0);
  }

  public static boolean isValidPattern (String pattern) {
    return pattern != null && pattern.length() >= MIN_PATTERN_SIZE;
  }

  public void setPattern (String pattern) {
    setPasscodeHash(MODE_PATTERN, pattern, 0);
  }

  public void setBiometrics (long biometricsId, boolean strong) {
    int newOptions = BitwiseUtils.optional(BIOMETRICS_OPTION_ONLY_STRONG, strong);
    setPasscodeHash(MODE_BIOMETRICS, String.valueOf(biometricsId), newOptions);
  }

  public boolean unlockByPassword (String password) {
    return unlockByPassword(password, false);
  }

  public boolean unlockByPassword (String password, boolean requireHiddenAccess) {
    return unlockWithPasscode(MODE_PASSWORD, password, requireHiddenAccess);
  }

  public boolean unlockByPincode (String pincode) {
    return unlockByPincode(pincode, false);
  }

  public boolean unlockByPincode (String pincode, boolean requireHiddenAccess) {
    return unlockWithPasscode(MODE_PINCODE, pincode, requireHiddenAccess);
  }

  public void unlock () {
    setHiddenAccountsUnlocked(false);
    setLocked(false);
  }

  public boolean unlockByPattern (String pattern) {
    return unlockByPattern(pattern, false);
  }

  public boolean unlockByPattern (String pattern, boolean requireHiddenAccess) {
    return unlockWithPasscode(MODE_PATTERN, pattern, requireHiddenAccess);
  }

  public boolean unlockByBiometrics (long biometricsId, boolean strong) {
    if (compareBiometrics(biometricsId, strong)) {
      setHiddenAccountsUnlocked(false);
      setLocked(false);
      return true;
    }
    return false;
  }

  public boolean isDoubleBottomEnabled () {
    return hiddenPasscodeHash != null && isValidHiddenMode(hiddenPasscodeMode);
  }

  public static boolean isValidHiddenMode (int mode) {
    return mode == MODE_PINCODE || mode == MODE_PASSWORD || mode == MODE_PATTERN;
  }

  public int getHiddenPasscodeMode () {
    return hiddenPasscodeMode;
  }

  public boolean isHiddenAccountsUnlocked () {
    return isDoubleBottomEnabled() && hiddenAccountsUnlocked && !isLocked();
  }

  public boolean isAccountHidden (int accountId) {
    return isDoubleBottomEnabled() && hiddenAccountIds.contains(accountId);
  }

  public boolean isAccountVisible (int accountId) {
    return !isAccountHidden(accountId) || isHiddenAccountsUnlocked();
  }

  public Set<Integer> getHiddenAccountIds () {
    return Collections.unmodifiableSet(new HashSet<>(hiddenAccountIds));
  }

  public boolean setAccountHidden (int accountId, boolean hidden) {
    boolean changed = hidden ? hiddenAccountIds.add(accountId) : hiddenAccountIds.remove(accountId);
    if (changed) {
      Settings.instance().putString(KEY_HIDDEN_ACCOUNT_IDS, serializeHiddenAccountIds());
      onHiddenAccountAccessChanged();
    }
    return changed;
  }

  public void forgetHiddenAccount (int accountId) {
    if (hiddenAccountIds.remove(accountId)) {
      Settings.instance().putString(KEY_HIDDEN_ACCOUNT_IDS, serializeHiddenAccountIds());
    }
  }

  public int getHiddenNotificationMode () {
    return hiddenNotificationMode;
  }

  public void setHiddenNotificationMode (int mode) {
    if (mode < HIDDEN_NOTIFICATIONS_NONE || mode > HIDDEN_NOTIFICATIONS_ALL) {
      throw new IllegalArgumentException("mode == " + mode);
    }
    if (hiddenNotificationMode != mode) {
      hiddenNotificationMode = mode;
      Settings.instance().putInt(KEY_HIDDEN_NOTIFICATION_MODE, mode);
      TdlibManager.instance().onUpdateAllNotifications();
    }
  }

  public boolean shouldHideNotifications (int accountId) {
    return isAccountHidden(accountId) && !isHiddenAccountsUnlocked() &&
      hiddenNotificationMode == HIDDEN_NOTIFICATIONS_NONE;
  }

  public boolean shouldUseGenericNotifications (int accountId) {
    return isAccountHidden(accountId) && !isHiddenAccountsUnlocked() &&
      hiddenNotificationMode == HIDDEN_NOTIFICATIONS_GENERIC;
  }

  public boolean setHiddenPasscode (int mode, String passcode) {
    if (!isValidHiddenMode(mode) || passcode == null || matchesPrimaryPasscode(mode, passcode)) {
      return false;
    }
    hiddenPasscodeMode = mode;
    hiddenPasscodeHash = getPasscodeHash(passcode);
    hiddenAccountsUnlocked = true;
    Settings.instance().edit()
      .putInt(KEY_HIDDEN_PASSCODE_MODE, mode)
      .putString(KEY_HIDDEN_PASSCODE_HASH, hiddenPasscodeHash)
      .apply();
    onHiddenAccountAccessChanged();
    return true;
  }

  public void disableDoubleBottom () {
    if (!isDoubleBottomEnabled() && hiddenAccountIds.isEmpty()) {
      return;
    }
    hiddenPasscodeMode = MODE_NONE;
    hiddenPasscodeHash = null;
    hiddenAccountsUnlocked = false;
    hiddenAccountIds.clear();
    Settings.instance().edit()
      .remove(KEY_HIDDEN_PASSCODE_MODE)
      .remove(KEY_HIDDEN_PASSCODE_HASH)
      .remove(KEY_HIDDEN_ACCOUNT_IDS)
      .apply();
    onHiddenAccountAccessChanged();
  }

  private boolean matchesPrimaryPasscode (int mode, String passcode) {
    switch (mode) {
      case MODE_PINCODE:
        return comparePincode(passcode);
      case MODE_PASSWORD:
        return comparePassword(passcode);
      case MODE_PATTERN:
        return comparePattern(passcode);
      default:
        return false;
    }
  }

  private boolean compareHiddenPasscode (int mode, String passcode) {
    return isDoubleBottomEnabled() && hiddenPasscodeMode == mode && passcode != null &&
      hiddenPasscodeHash.equals(getPasscodeHash(passcode));
  }

  private boolean unlockWithPasscode (int mode, String passcode, boolean requireHiddenAccess) {
    if (compareHiddenPasscode(mode, passcode)) {
      setHiddenAccountsUnlocked(true);
      setLocked(false);
      return true;
    }
    if (!requireHiddenAccess && matchesPrimaryPasscode(mode, passcode)) {
      setHiddenAccountsUnlocked(false);
      setLocked(false);
      return true;
    }
    return false;
  }

  private void setHiddenAccountsUnlocked (boolean unlocked) {
    boolean value = unlocked && isDoubleBottomEnabled();
    if (hiddenAccountsUnlocked != value) {
      hiddenAccountsUnlocked = value;
      onHiddenAccountAccessChanged();
    }
  }

  private void onHiddenAccountAccessChanged () {
    TdlibManager.instance().onHiddenAccountAccessChanged();
  }

  // Strings

  public CharSequence getModeName () { // Used in Settings screen
    if (mode == MODE_BIOMETRICS) {
      boolean isStrong = useStrongBiometrics();
      if (BiometricAuthentication.ONLY_FINGERPRINT) {
        return Lang.getString(R.string.PasscodeFingerprint);
      } else {
        return Lang.getString(isStrong ? R.string.PasscodeBiometricsStrong : R.string.PasscodeBiometricsWeak);
      }
    } else {
      return getModeName(mode);
    }
  }

  public static CharSequence getModeName (int mode) {
    switch (mode) {
      case MODE_NONE: return Lang.getString(R.string.PasscodeSettingDisabled);
      case MODE_PINCODE: return Lang.getString(R.string.PasscodePIN);
      case MODE_PASSWORD: return Lang.getString(R.string.login_Password);
      case MODE_PATTERN: return Lang.getString(R.string.PasscodePattern);
      case MODE_GESTURE: return Lang.getString(R.string.PasscodeGesture);
      case MODE_BIOMETRICS: {
        if (BiometricAuthentication.ONLY_FINGERPRINT) {
          return Lang.getString(R.string.PasscodeFingerprint);
        } else {
          return Lang.getString(R.string.PasscodeBiometrics);
        }
      }
    }
    return "ERROR";
  }

  public String[] getAutolockModeNames () { // Auto-Lock
    return new String[] {
      Lang.getString(R.string.AutoLockDisabled),
      Lang.plural(R.string.inXMinutes, 1),
      Lang.plural(R.string.inXMinutes, 5),
      Lang.plural(R.string.inXHours, 1),
      Lang.plural(R.string.inXHours, 5),
      Lang.getString(R.string.AutoLockInstant)
    };
  }

  public static String getActionName (int mode, int state, boolean useExtraData, int extraData) {
    switch (mode) {
      case MODE_PINCODE: {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByPIN);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourPIN);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourPIN);
        }
        return null;
      }
      case MODE_PASSWORD: {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByPassword);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourPassword);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourPassword);
        }
        return null;
      }
      case MODE_PATTERN: {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByPattern);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourPattern);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourPattern);
        }
        return null;
      }
      case MODE_GESTURE:  {
        switch (state) {
          case STATE_UNLOCK: return Lang.getString(R.string.UnlockByGesture);
          case STATE_CHOOSE: return Lang.getString(R.string.ChooseYourGesture);
          case STATE_CONFIRM: return Lang.getString(R.string.ConfirmYourGesture);
        }
        return null;
      }
      case MODE_BIOMETRICS: {
        boolean isStrong = BitwiseUtils.hasFlag(extraData, Passcode.BIOMETRICS_OPTION_ONLY_STRONG);
        if (BiometricAuthentication.ONLY_FINGERPRINT) {
          switch (state) {
            case STATE_UNLOCK:
              return Lang.getString(R.string.UnlockByFingerprint);
            case STATE_CHOOSE:
              return Lang.getString(isStrong || !useExtraData ? R.string.TouchYourSensor : R.string.TouchYourSensorWeak);
            case STATE_CONFIRM:
              return Lang.getString(isStrong || !useExtraData ? R.string.ConfirmYourFingerprint : R.string.ConfirmYourFingerprintWeak);
          }
        } else {
          switch (state) {
            case STATE_UNLOCK:
              return Lang.getString(R.string.UnlockByBiometrics);
            case STATE_CHOOSE:
              return Lang.getString(isStrong || !useExtraData ? R.string.UseBiometrics : R.string.UseBiometricsWeak);
            case STATE_CONFIRM:
              return Lang.getString(!useExtraData ? R.string.ConfirmYourBiometrics : isStrong ? R.string.ConfirmYourBiometricsStrong : R.string.ConfirmYourBiometricsWeak);
          }
        }
        return null;
      }
    }
    return null;
  }
}
