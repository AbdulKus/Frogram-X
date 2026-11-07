package org.thunderdog.challegram.ui;

import android.text.TextUtils;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.GroupVoiceSession;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.vkryl.core.ColorUtils;

public final class GroupVoiceDialog {
  private static GroupVoiceDialog visible;
  public static void show (ViewController<?> owner, int callId) {
    if (!owner.isDestroyed() && callId != 0) new GroupVoiceDialog(owner, owner.tdlib(), callId).show();
  }

  public static void openCurrent (ViewController<?> owner) {
    GroupVoiceSession session = GroupVoiceSession.current();
    if (owner != null && !owner.isDestroyed() && session != null) {
      new GroupVoiceDialog(owner, session.tdlib, session.callId).show();
    }
  }

  private final ViewController<?> owner;
  private final Tdlib tdlib;
  private final int callId;
  private final MessageDialogStyle panel;
  private final TextView status;
  private final TextView join;
  private final TextView leave;
  private final TextView minimize;
  private final TextView microphone;
  private final TextView speaker;
  private final LinearLayout actions;
  private final LinearLayout participantList;
  private final HashMap<String, ParticipantRow> rows = new HashMap<>();
  private final List<String> orderedKeys = new ArrayList<>();
  private String volumeKey;
  private GroupVoiceSession session;
  private boolean closed;
  private final Runnable changed = this::scheduleRender;
  private final Runnable renderTask = this::render;

  private GroupVoiceDialog (ViewController<?> owner, Tdlib tdlib, int callId) {
    this.owner = owner;
    this.tdlib = tdlib;
    this.callId = callId;
    panel = new MessageDialogStyle(owner, Lang.getString(R.string.FrogramGroupCall), false);
    status = panel.label("", 14, Theme.textDecentColor(), false);
    panel.add(status, 6);
    participantList = new LinearLayout(owner.context());
    participantList.setOrientation(LinearLayout.VERTICAL);
    panel.addScrollable(participantList, 12, 220);

    actions = new LinearLayout(owner.context());
    actions.setGravity(Gravity.CENTER);
    panel.add(actions, 12);
    microphone = addAction(R.string.FrogramCallUnmute, R.drawable.baseline_mic_24,
      () -> session.setMuted(!session.muted), 0);
    speaker = addAction(R.string.FrogramCallSpeaker, R.drawable.baseline_volume_up_24,
      () -> session.toggleSpeaker(), 8);
    leave = panel.button(Lang.getString(R.string.FrogramCallLeave), Theme.getColor(ColorId.textNegative), false,
      () -> session.stop(null));
    panel.add(leave, 10);
    minimize = panel.button(Lang.getString(R.string.FrogramCallMinimize), Theme.textAccentColor(), false, panel::dismiss);
    panel.add(minimize, 6);
    join = panel.button(Lang.getString(R.string.FrogramCallJoin), Theme.textLinkColor(), true, this::join);
    panel.add(join, 12);
  }

  private TextView addAction (int label, int icon, Runnable action, int margin) {
    TextView button = panel.button(Lang.getString(label), Theme.textAccentColor(), false, action);
    button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
    button.setCompoundDrawablePadding(Screen.dp(4));
    button.getCompoundDrawables()[1].mutate().setColorFilter(Paints.getColorFilter(Theme.textAccentColor()));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
    params.setMarginStart(Screen.dp(margin));
    actions.addView(button, params);
    return button;
  }

  private void show () {
    if (visible != null && visible.owner.context() == owner.context()) {
      if (visible.callId == callId && visible.tdlib == tdlib) return;
      visible.panel.dismiss();
    }
    GroupVoiceSession current = GroupVoiceSession.current();
    if (current != null && current.callId == callId && current.tdlib == tdlib) bind(current);
    panel.setOnDismiss(() -> {
      closed = true;
      if (visible == this) visible = null;
      UI.removePendingRunnable(renderTask);
      if (session != null && session.changed == changed) session.changed = null;
      for (ParticipantRow row : rows.values()) row.avatar.receiver.destroy();
      rows.clear();
    });
    render();
    panel.show();
    visible = this;
  }

  private void join () {
    GroupVoiceSession.start(owner, tdlib, callId, value -> {
      if (closed || owner.isDestroyed()) {
        value.stop(null);
        return;
      }
      bind(value);
      render();
    });
  }

  private void bind (GroupVoiceSession session) {
    this.session = session;
    session.changed = changed;
  }

  private void scheduleRender () {
    UI.removePendingRunnable(renderTask);
    UI.post(renderTask, 16);
  }

  private void render () {
    if (closed || owner.isDestroyed()) return;
    boolean active = session != null && !session.ended;
    status.setText(session != null ? session.status : Lang.getString(R.string.FrogramCallJoinHint));
    join.setVisibility(active ? View.GONE : View.VISIBLE);
    actions.setVisibility(active ? View.VISIBLE : View.GONE);
    leave.setVisibility(active ? View.VISIBLE : View.GONE);
    minimize.setVisibility(active ? View.VISIBLE : View.GONE);
    participantList.setVisibility(active && !session.participants.isEmpty() ? View.VISIBLE : View.GONE);
    if (!active) return;

    microphone.setEnabled(session.canToggleMute());
    microphone.setAlpha(microphone.isEnabled() ? 1f : .45f);
    microphone.setText(Lang.getString(session.muted ? R.string.FrogramCallUnmute : R.string.FrogramCallMute));
    speaker.setEnabled(session.connected);
    speaker.setAlpha(speaker.isEnabled() ? 1f : .45f);
    speaker.setText(Lang.getString(session.audioRouteLabel()));

    if (volumeKey != null && !session.participants.containsKey(volumeKey)) volumeKey = null;
    List<String> keys = new ArrayList<>(session.participants.keySet());
    keys.sort((a, b) -> session.participants.get(b).order.compareTo(session.participants.get(a).order));
    if (!orderedKeys.equals(keys)) {
      participantList.removeAllViews();
      rows.entrySet().removeIf(entry -> {
        if (session.participants.containsKey(entry.getKey())) return false;
        entry.getValue().avatar.receiver.destroy();
        return true;
      });
      for (String key : keys) {
        ParticipantRow row = rows.get(key);
        if (row == null) {
          row = new ParticipantRow(key, session.participants.get(key));
          rows.put(key, row);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (participantList.getChildCount() > 0) params.topMargin = Screen.dp(4);
        participantList.addView(row, params);
      }
      orderedKeys.clear();
      orderedKeys.addAll(keys);
    }
    for (String key : keys) rows.get(key).update(session.participants.get(key));
  }

  private final class ParticipantRow extends LinearLayout {
    final String key;
    final SpeakerAvatar avatar;
    final TextView title;
    final ImageView muted;
    final ImageView closeVolume;
    final LinearLayout volumeControl;
    final SeekBar slider;
    final TextView percent;

    ParticipantRow (String key, TdApi.GroupCallParticipant participant) {
      super(owner.context());
      this.key = key;
      setGravity(Gravity.CENTER_VERTICAL);
      int padding = Screen.dp(10);
      setPadding(padding, Screen.dp(5), padding, Screen.dp(5));
      setBackground(MessageDialogStyle.rounded(ColorUtils.alphaColor(.07f, Theme.textAccentColor()), 12));
      avatar = new SpeakerAvatar(participant.participantId);
      addView(avatar, new LinearLayout.LayoutParams(Screen.dp(38), Screen.dp(38)));

      FrameLayout body = new FrameLayout(owner.context());
      LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, Screen.dp(40), 1f);
      bodyParams.setMarginStart(Screen.dp(8));
      addView(body, bodyParams);
      title = panel.label(tdlib.senderName(participant.participantId), 15, Theme.textAccentColor(), false);
      title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      body.addView(title, new FrameLayout.LayoutParams(-1, -1));
      if (!participant.isCurrentUser) title.setOnClickListener(v -> toggleVolume());

      volumeControl = new LinearLayout(owner.context());
      volumeControl.setGravity(Gravity.CENTER_VERTICAL);
      body.addView(volumeControl, new FrameLayout.LayoutParams(-1, -1));
      slider = new SeekBar(owner.context());
      slider.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
      slider.setMax(200);
      slider.setContentDescription(Lang.getString(R.string.FrogramCallParticipantVolume));
      volumeControl.addView(slider, new LinearLayout.LayoutParams(0, -1, 1f));
      percent = panel.label("", 13, Theme.textLinkColor(), true);
      percent.setGravity(Gravity.CENTER);
      volumeControl.addView(percent, new LinearLayout.LayoutParams(Screen.dp(46), -1));
      slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override public void onProgressChanged (SeekBar bar, int value, boolean fromUser) {
          percent.setText(Lang.getString(R.string.FrogramCallVolumePercent, value));
          if (fromUser) session.setVolume(key, value);
        }
        @Override public void onStartTrackingTouch (SeekBar bar) { bar.getParent().requestDisallowInterceptTouchEvent(true); }
        @Override public void onStopTrackingTouch (SeekBar bar) { bar.getParent().requestDisallowInterceptTouchEvent(false); }
      });
      muted = new ImageView(owner.context());
      muted.setImageResource(R.drawable.frogram_mic_off_24);
      muted.setColorFilter(Paints.getColorFilter(Theme.textDecentColor()));
      muted.setContentDescription(Lang.getString(R.string.FrogramCallMuted));
      addView(muted, new LinearLayout.LayoutParams(Screen.dp(36), Screen.dp(36)));
      muted.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      int inset = Screen.dp(8);
      muted.setPadding(inset, inset, inset, inset);

      closeVolume = new ImageView(owner.context());
      closeVolume.setImageResource(R.drawable.baseline_close_20);
      closeVolume.setColorFilter(Paints.getColorFilter(Theme.textAccentColor()));
      closeVolume.setScaleType(ImageView.ScaleType.CENTER);
      closeVolume.setBackground(MessageDialogStyle.rounded(ColorUtils.alphaColor(.10f, Theme.textAccentColor()), 10));
      closeVolume.setContentDescription(Lang.getString(R.string.FrogramCallCloseVolume));
      closeVolume.setOnClickListener(v -> toggleVolume());
      addView(closeVolume, new LinearLayout.LayoutParams(Screen.dp(36), Screen.dp(36)));
    }

    void toggleVolume () {
      volumeKey = key.equals(volumeKey) ? null : key;
      render();
    }

    void update (TdApi.GroupCallParticipant participant) {
      boolean adjusting = key.equals(volumeKey) && participant.audioSourceId != 0;
      title.setEnabled(participant.isCurrentUser || participant.audioSourceId != 0);
      title.setVisibility(adjusting ? GONE : VISIBLE);
      volumeControl.setVisibility(adjusting ? VISIBLE : GONE);
      muted.setVisibility(!adjusting && participant.isMutedForAllUsers ? VISIBLE : GONE);
      closeVolume.setVisibility(adjusting ? VISIBLE : GONE);
      if (adjusting) {
        int volume = session.volume(key, participant);
        slider.setProgress(volume);
        if (percent.length() == 0) percent.setText(Lang.getString(R.string.FrogramCallVolumePercent, volume));
      }
      avatar.setSpeaking(session.isSpeaking(participant));
    }
  }

  private final class SpeakerAvatar extends View {
    final AvatarReceiver receiver = new AvatarReceiver(this);
    private boolean speaking;

    SpeakerAvatar (TdApi.MessageSender sender) {
      super(owner.context());
      receiver.requestMessageSender(tdlib, sender, AvatarReceiver.Options.FORCE_IGNORE_FORUM);
    }

    @Override protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      receiver.attach();
    }

    @Override protected void onDetachedFromWindow () {
      receiver.detach();
      super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged (int width, int height, int oldWidth, int oldHeight) {
      int inset = Screen.dp(3);
      receiver.setBounds(inset, inset, width - inset, height - inset);
    }

    @Override protected void onDraw (Canvas canvas) {
      receiver.draw(canvas);
      if (speaking) {
        int color = Theme.isDark() ? 0xff62e5a2 : Theme.getColor(ColorId.online);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f - Screen.dp(1),
          Paints.strokeBigPaint(color));
      }
    }

    void setSpeaking (boolean value) {
      if (speaking != value) {
        speaking = value;
        invalidate();
      }
    }
  }
}
