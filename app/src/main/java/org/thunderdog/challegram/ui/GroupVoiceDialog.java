package org.thunderdog.challegram.ui;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.GroupVoiceSession;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ColorUtils;

public final class GroupVoiceDialog {
  public static void show (ViewController<?> owner, int callId) {
    if (!owner.isDestroyed() && callId != 0) new GroupVoiceDialog(owner, callId).show();
  }

  private final ViewController<?> owner;
  private final int callId;
  private final MessageDialogStyle panel;
  private final TextView status;
  private final TextView join;
  private final TextView leave;
  private final TextView microphone;
  private final TextView speaker;
  private final LinearLayout actions;
  private final LinearLayout participantList;
  private GroupVoiceSession session;
  private boolean closed;
  private final Runnable changed = this::scheduleRender;
  private final Runnable renderTask = this::render;

  private GroupVoiceDialog (ViewController<?> owner, int callId) {
    this.owner = owner;
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
    GroupVoiceSession current = GroupVoiceSession.current();
    if (current != null && current.callId == callId && current.tdlib == owner.tdlib()) bind(current);
    panel.setOnDismiss(() -> {
      closed = true;
      UI.removePendingRunnable(renderTask);
      if (session != null && session.changed == changed) session.changed = null;
    });
    render();
    panel.show();
  }

  private void join () {
    GroupVoiceSession.start(owner, callId, value -> {
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
    participantList.setVisibility(active && !session.participants.isEmpty() ? View.VISIBLE : View.GONE);
    if (!active) return;

    microphone.setEnabled(session.canToggleMute());
    microphone.setAlpha(microphone.isEnabled() ? 1f : .45f);
    microphone.setText(Lang.getString(session.muted ? R.string.FrogramCallUnmute : R.string.FrogramCallMute));
    speaker.setEnabled(session.connected);
    speaker.setAlpha(speaker.isEnabled() ? 1f : .45f);
    speaker.setText(Lang.getString(session.isSpeakerOn() ? R.string.FrogramCallSpeakerOn : R.string.FrogramCallSpeaker));

    participantList.removeAllViews();
    List<TdApi.GroupCallParticipant> participants = new ArrayList<>(session.participants.values());
    participants.sort((a, b) -> b.order.compareTo(a.order));
    for (TdApi.GroupCallParticipant participant : participants) addParticipant(participant);
  }

  private void addParticipant (TdApi.GroupCallParticipant participant) {
    String name = owner.tdlib().senderName(participant.participantId);
    LinearLayout row = new LinearLayout(owner.context());
    row.setGravity(Gravity.CENTER_VERTICAL);
    int padding = Screen.dp(10);
    row.setPadding(padding, Screen.dp(6), padding, Screen.dp(6));
    row.setBackground(MessageDialogStyle.rounded(ColorUtils.alphaColor(.07f, Theme.textAccentColor()), 12));
    String initial = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1));
    TextView avatar = panel.label(initial, 14, Theme.textLinkColor(), true);
    avatar.setGravity(Gravity.CENTER);
    avatar.setBackground(MessageDialogStyle.rounded(ColorUtils.alphaColor(.15f, Theme.textLinkColor()), 20));
    row.addView(avatar, new LinearLayout.LayoutParams(Screen.dp(32), Screen.dp(32)));
    TextView title = panel.label(name, 15, Theme.textAccentColor(), false);
    title.setSingleLine(true);
    title.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
    nameParams.setMarginStart(Screen.dp(10));
    row.addView(title, nameParams);
    if (participant.isMutedForAllUsers) {
      TextView muted = panel.label(Lang.getString(R.string.FrogramCallMuted), 12, Theme.textDecentColor(), false);
      row.addView(muted);
    }
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    if (participantList.getChildCount() > 0) params.topMargin = Screen.dp(4);
    participantList.addView(row, params);
  }
}
