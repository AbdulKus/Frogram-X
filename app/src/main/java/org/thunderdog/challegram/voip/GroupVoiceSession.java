package org.thunderdog.challegram.voip;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.GroupVoiceService;
import org.thunderdog.challegram.telegram.GroupCallListener;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.AuthorizationListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import java.util.LinkedHashMap;

/** Process-level owner; the dialog may close while audio continues in the foreground service. */
public final class GroupVoiceSession implements GroupCallListener, GroupVoiceEngine.Listener, CallManager.CurrentCallListener, AuthorizationListener {
  private static GroupVoiceSession current;
  public static GroupVoiceSession current () { return current; }
  public final Tdlib tdlib;
  public final int callId;
  public final LinkedHashMap<String, TdApi.GroupCallParticipant> participants = new LinkedHashMap<>();
  public boolean muted = true;
  public boolean connected;
  public boolean ended;
  public String status = Lang.getString(R.string.FrogramCallConnecting);
  public Runnable changed;
  private GroupVoiceEngine engine;
  private AudioManager audio;
  private int previousMode;
  private boolean previousSpeaker;
  private boolean audioConfigured;
  private PowerManager.WakeLock wakeLock;
  private boolean joining;
  private boolean joined;
  private long connectionGeneration;
  private int muteGeneration;
  private TdApi.MessageSender self;
  private final AudioManager.OnAudioFocusChangeListener focus = change -> UI.post(() -> {
    if (change == AudioManager.AUDIOFOCUS_LOSS) stop(null);
    else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) setMuted(true);
  });

  private GroupVoiceSession (Tdlib tdlib, int callId) { this.tdlib = tdlib; this.callId = callId; }

  public static void start (ViewController<?> owner, int callId, java.util.function.Consumer<GroupVoiceSession> ready) {
    if (owner.isDestroyed()) return;
    if (current != null) {
      if (current.tdlib == owner.tdlib() && current.callId == callId) ready.accept(current);
      else owner.openAlert(R.string.FrogramGroupCall, Lang.getString(R.string.SomeCallInProgressDesc));
      return;
    }
    if (owner.tdlib().context().calls().promptActiveCall()) return;
    if (owner.context().permissions().requestRecordAudioPermissions(granted -> {
      if (granted) start(owner, callId, ready);
    })) return;
    GroupVoiceSession session = new GroupVoiceSession(owner.tdlib(), callId);
    current = session;
    session.tdlib.incrementCallReferenceCount();
    session.tdlib.listeners().subscribeForGlobalUpdates(session);
    session.tdlib.context().calls().addCurrentCallListener(session);
    session.tdlib.listeners().addAuthorizationChangeListener(session);
    ready.accept(session);
    Intent intent = new Intent(owner.context(), GroupVoiceService.class);
    try {
      if (Build.VERSION.SDK_INT >= 26) owner.context().startForegroundService(intent);
      else owner.context().startService(intent);
    } catch (RuntimeException e) { session.stop(Lang.getString(R.string.FrogramCallFailed)); }
  }

  public void connect () {
    if (ended || engine != null) return;
    tdlib.client().send(new TdApi.GetGroupCall(callId), response -> UI.post(() -> {
      if (ended) return;
      if (!(response instanceof TdApi.GroupCall) || !((TdApi.GroupCall) response).isActive || !((TdApi.GroupCall) response).isVideoChat || ((TdApi.GroupCall) response).isRtmpStream) {
        stop(Lang.getString(R.string.FrogramCallFailed));
        return;
      }
      try {
        audio = (AudioManager) UI.getAppContext().getSystemService(Context.AUDIO_SERVICE);
        previousMode = audio.getMode();
        previousSpeaker = audio.isSpeakerphoneOn();
        if (audio.requestAudioFocus(focus, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IllegalStateException("Audio focus unavailable");
        audioConfigured = true;
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audio.setSpeakerphoneOn(true);
        PowerManager power = (PowerManager) UI.getAppContext().getSystemService(Context.POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FrogramX:GroupVoice");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        engine = new GroupVoiceEngine(this);
        engine.joinPayload();
        connectionTimeout();
      } catch (RuntimeException | LinkageError error) { stop(Lang.getString(R.string.FrogramCallFailed)); }
    }));
  }

  @Override public void onPayload (int source, String payload) {
    if (ended || joining) return;
    joining = true;
    TdApi.GroupCallJoinParameters parameters = new TdApi.GroupCallJoinParameters(source, payload, true, false);
    tdlib.client().send(new TdApi.JoinVideoChat(callId, null, parameters, ""), response -> UI.post(() -> {
      joining = false;
      if (ended) {
        if (response instanceof TdApi.Text) tdlib.client().send(new TdApi.LeaveGroupCall(callId), ignored -> { });
        return;
      }
      if (!(response instanceof TdApi.Text)) { stop(Lang.getString(R.string.FrogramCallFailed)); return; }
      joined = true;
      muted = true;
      engine.mute(true);
      engine.response(((TdApi.Text) response).text);
      tdlib.client().send(new TdApi.LoadGroupCallParticipants(callId, 100), ignored -> { });
      notifyChanged();
    }));
  }

  @Override public void onState (boolean value) {
    if (ended) return;
    connected = value;
    status = Lang.getString(value ? R.string.FrogramCallConnected : R.string.FrogramCallConnecting);
    if (!value) connectionTimeout();
    else connectionGeneration++;
    notifyChanged();
  }

  private void connectionTimeout () {
    long generation = ++connectionGeneration;
    UI.post(() -> {
      if (!ended && !connected && generation == connectionGeneration) stop(Lang.getString(R.string.FrogramCallFailed));
    }, 45000);
  }

  public void setMuted (boolean value) {
    if (ended || engine == null || !joined || self == null) return;
    if (value) { muted = true; engine.mute(true); notifyChanged(); }
    int generation = ++muteGeneration;
    tdlib.client().send(new TdApi.ToggleGroupCallParticipantIsMuted(callId, self, value), response -> UI.post(() -> {
      if (ended || generation != muteGeneration) return;
      if (response instanceof TdApi.Ok) { muted = value; engine.mute(value); }
      notifyChanged();
    }));
  }

  public void toggleSpeaker () {
    if (audioConfigured && !ended) audio.setSpeakerphoneOn(!audio.isSpeakerphoneOn());
  }

  @Override public void onGroupCallUpdated (TdApi.GroupCall call) {
    if (call.id != callId) return;
    UI.post(() -> {
      if (ended) return;
      if (!call.isActive || (joined && !call.isJoined && !call.needRejoin)) { stop(null); return; }
      if (call.needRejoin && engine != null && !joining) {
        connected = false;
        muted = true;
        engine.mute(true);
        engine.joinPayload();
        connectionTimeout();
      }
      notifyChanged();
    });
  }

  @Override public void onGroupCallParticipantUpdated (int id, TdApi.GroupCallParticipant participant) {
    if (id != callId) return;
    UI.post(() -> {
      if (ended) return;
      String key = participant.participantId instanceof TdApi.MessageSenderUser ? "u" + ((TdApi.MessageSenderUser) participant.participantId).userId : "c" + ((TdApi.MessageSenderChat) participant.participantId).chatId;
      if (participant.order.isEmpty()) participants.remove(key);
      else participants.put(key, participant);
      if (participant.isCurrentUser) {
        self = participant.participantId;
        if (participant.isMutedForAllUsers && !participant.canUnmuteSelf && engine != null) { muteGeneration++; muted = true; engine.mute(true); }
      }
      notifyChanged();
    });
  }

  public void stop (String error) {
    if (ended) return;
    ended = true;
    connected = false;
    status = error != null ? error : Lang.getString(R.string.FrogramCallEnded);
    if (engine != null) { engine.stop(); engine = null; }
    tdlib.client().send(new TdApi.LeaveGroupCall(callId), ignored -> { });
    tdlib.listeners().unsubscribeFromGlobalUpdates(this);
    tdlib.context().calls().removeCurrentCallListener(this);
    tdlib.listeners().removeAuthorizationChangeListener(this);
    tdlib.decrementCallReferenceCount();
    if (audioConfigured) {
      audio.abandonAudioFocus(focus);
      audio.setSpeakerphoneOn(previousSpeaker);
      audio.setMode(previousMode);
      audioConfigured = false;
    }
    if (wakeLock != null) {
      if (wakeLock.isHeld()) wakeLock.release();
      wakeLock = null;
    }
    if (current == this) current = null;
    UI.getAppContext().stopService(new Intent(UI.getAppContext(), GroupVoiceService.class));
    notifyChanged();
  }

  private void notifyChanged () { if (changed != null) changed.run(); }

  @Override public void onCurrentCallChanged (Tdlib account, TdApi.Call call) {
    if (call != null) UI.post(() -> stop(null));
  }

  @Override public void onAuthorizationStateChanged (TdApi.AuthorizationState state) {
    if (!(state instanceof TdApi.AuthorizationStateReady)) UI.post(() -> stop(null));
  }
}
