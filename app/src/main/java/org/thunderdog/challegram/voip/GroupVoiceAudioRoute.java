package org.thunderdog.challegram.voip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.util.Arrays;
import java.util.List;

final class GroupVoiceAudioRoute {
  enum Route { SPEAKER, EARPIECE, HEADSET, BLUETOOTH }

  private final Context context;
  private final AudioManager audio;
  private final Runnable changed;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private PowerManager.WakeLock proximity;
  private Route desired = Route.SPEAKER;
  private boolean active;
  private boolean connected;
  private boolean legacySco;
  private boolean previousBluetooth;
  private boolean previousWired;
  private boolean scoReceiverRegistered;
  private Object communicationListener;

  private final AudioDeviceCallback devices = new AudioDeviceCallback() {
    @Override public void onAudioDevicesAdded (AudioDeviceInfo[] ignored) { devicesChanged(); }
    @Override public void onAudioDevicesRemoved (AudioDeviceInfo[] ignored) { devicesChanged(); }
  };

  private final BroadcastReceiver scoReceiver = new BroadcastReceiver() {
    @Override public void onReceive (Context context, Intent intent) { update(); }
  };

  GroupVoiceAudioRoute (Context context, AudioManager audio, Runnable changed) {
    this.context = context;
    this.audio = audio;
    this.changed = changed;
  }

  void start () {
    active = true;
    PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    if (power != null && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
      proximity = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "FrogramX:GroupVoiceProximity");
      proximity.setReferenceCounted(false);
    }
    audio.registerAudioDeviceCallback(devices, handler);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      AudioManager.OnCommunicationDeviceChangedListener listener = ignored -> update();
      audio.addOnCommunicationDeviceChangedListener(context.getMainExecutor(), listener);
      communicationListener = listener;
    } else {
      context.registerReceiver(scoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
      scoReceiverRegistered = true;
    }
    previousBluetooth = device(Route.BLUETOOTH) != null;
    previousWired = device(Route.HEADSET) != null;
    select(previousBluetooth ? Route.BLUETOOTH : previousWired ? Route.HEADSET : Route.SPEAKER);
  }

  void setConnected (boolean connected) {
    this.connected = connected;
    update();
  }

  void cycle () {
    switch (desired) {
      case SPEAKER:
        if (device(Route.HEADSET) != null) select(Route.HEADSET);
        else if (device(Route.EARPIECE) != null) select(Route.EARPIECE);
        else if (device(Route.BLUETOOTH) != null) select(Route.BLUETOOTH);
        break;
      case HEADSET:
      case EARPIECE:
        select(device(Route.BLUETOOTH) != null ? Route.BLUETOOTH : Route.SPEAKER);
        break;
      case BLUETOOTH:
        select(Route.SPEAKER);
        break;
    }
  }

  Route current () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      AudioDeviceInfo selected = audio.getCommunicationDevice();
      Route route = selected != null ? routeOf(selected) : null;
      if (route != null) return route;
    } else {
      if (audio.isBluetoothScoOn()) return Route.BLUETOOTH;
      if (audio.isSpeakerphoneOn()) return Route.SPEAKER;
      if (device(Route.HEADSET) != null) return Route.HEADSET;
      if (device(Route.EARPIECE) != null && !legacySco) return Route.EARPIECE;
    }
    return desired;
  }

  private AudioDeviceInfo device (Route route) {
    List<AudioDeviceInfo> available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
      audio.getAvailableCommunicationDevices() : Arrays.asList(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
    for (AudioDeviceInfo candidate : available) {
      if (routeOf(candidate) == route) return candidate;
    }
    return null;
  }

  private static Route routeOf (AudioDeviceInfo device) {
    return switch (device.getType()) {
      case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Route.EARPIECE;
      case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> Route.SPEAKER;
      case AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID -> Route.BLUETOOTH;
      case AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE -> Route.HEADSET;
      default -> null;
    };
  }

  private void devicesChanged () {
    if (!active) return;
    boolean bluetooth = device(Route.BLUETOOTH) != null;
    boolean wired = device(Route.HEADSET) != null;
    boolean newBluetooth = bluetooth && !previousBluetooth;
    boolean newWired = wired && !previousWired;
    previousBluetooth = bluetooth;
    previousWired = wired;
    if (newBluetooth) select(Route.BLUETOOTH);
    else if (newWired && desired != Route.BLUETOOTH) select(Route.HEADSET);
    else if (device(desired) == null) select(bluetooth ? Route.BLUETOOTH : wired ? Route.HEADSET : Route.SPEAKER);
    else update();
  }

  private void select (Route route) {
    desired = route;
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AudioDeviceInfo selected = device(route);
        if (selected == null || !audio.setCommunicationDevice(selected)) audio.clearCommunicationDevice();
      } else {
        boolean bluetooth = route == Route.BLUETOOTH;
        if (bluetooth != legacySco) {
          if (bluetooth) audio.startBluetoothSco();
          else audio.stopBluetoothSco();
          legacySco = bluetooth;
          audio.setBluetoothScoOn(bluetooth);
        }
        audio.setSpeakerphoneOn(route == Route.SPEAKER);
      }
    } catch (RuntimeException error) {
      android.util.Log.w("FrogramGroupVoice", "Unable to switch audio output", error);
    }
    update();
  }

  private void update () {
    if (!active) return;
    if (proximity != null) {
      boolean earpiece = connected && desired == Route.EARPIECE && current() == Route.EARPIECE;
      if (earpiece && !proximity.isHeld()) proximity.acquire();
      else if (!earpiece && proximity.isHeld()) proximity.release();
    }
    changed.run();
  }

  void stop () {
    active = false;
    if (proximity != null && proximity.isHeld()) proximity.release();
    audio.unregisterAudioDeviceCallback(devices);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (communicationListener != null) {
        audio.removeOnCommunicationDeviceChangedListener((AudioManager.OnCommunicationDeviceChangedListener) communicationListener);
      }
      audio.clearCommunicationDevice();
    } else {
      if (scoReceiverRegistered) context.unregisterReceiver(scoReceiver);
      if (legacySco) {
        audio.setBluetoothScoOn(false);
        audio.stopBluetoothSco();
      }
    }
  }
}
