package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.voip.GroupVoiceSession;
import me.vkryl.core.lambda.Destroyable;

public final class GroupVoiceDialog {
  public static void show (ViewController<?> owner, int callId) {
    if (owner.isDestroyed() || callId == 0) return;
    new GroupVoiceDialog(owner, callId).show();
  }
  private final ViewController<?> owner;
  private final int callId;
  private GroupVoiceSession session;
  private TextView status;
  private TextView participants;
  private Button join;
  private Button microphone;
  private Button speaker;
  private boolean closed;
  private final Runnable changed = this::render;

  private GroupVoiceDialog (ViewController<?> owner, int callId) { this.owner = owner; this.callId = callId; }

  private void show () {
    LinearLayout content = new LinearLayout(owner.context());
    content.setOrientation(LinearLayout.VERTICAL);
    int padding = Screen.dp(20);
    content.setPadding(padding, padding, padding, padding);
    status = label();
    content.addView(status);
    join = new Button(owner.context());
    join.setText(Lang.getString(R.string.FrogramCallJoin));
    content.addView(join);
    join.setOnClickListener(v -> {
      if (session != null && !session.ended) session.stop(null);
      else GroupVoiceSession.start(owner, callId, value -> {
        if (closed || owner.isDestroyed()) { value.stop(null); return; }
        session = value;
        session.changed = changed;
        render();
      });
    });
    microphone = new Button(owner.context());
    microphone.setOnClickListener(v -> { if (session != null) session.setMuted(!session.muted); });
    content.addView(microphone);
    speaker = new Button(owner.context());
    speaker.setText(Lang.getString(R.string.FrogramCallSpeaker));
    speaker.setOnClickListener(v -> { if (session != null) session.toggleSpeaker(); });
    content.addView(speaker);
    participants = label();
    ScrollView scroll = new ScrollView(owner.context());
    scroll.addView(participants);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, Screen.dp(200)));
    AlertDialog dialog = owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.FrogramGroupCall)).setView(content)
      .setPositiveButton(Lang.getOK(), null));
    if (owner instanceof MessagesController) MessageDialogStyle.apply((MessagesController) owner, dialog);
    Destroyable destroy = dialog::dismiss;
    owner.addDestroyListener(destroy);
    dialog.setOnDismissListener(d -> {
      closed = true;
      owner.removeDestroyListener(destroy);
      if (session != null && session.changed == changed) session.changed = null;
    });
    GroupVoiceSession existing = GroupVoiceSession.current();
    if (existing != null && existing.callId == callId && existing.tdlib == owner.tdlib()) { session = existing; session.changed = changed; }
    render();
  }

  private TextView label () {
    TextView view = new TextView(owner.context());
    view.setTextColor(Theme.textAccentColor());
    view.setTextSize(16);
    view.setPadding(0, Screen.dp(8), 0, Screen.dp(12));
    return view;
  }

  private void render () {
    if (closed || owner.isDestroyed()) return;
    boolean active = session != null && !session.ended;
    status.setText(session != null ? session.status : Lang.getString(R.string.FrogramCallJoinHint));
    join.setText(Lang.getString(active ? R.string.FrogramCallLeave : R.string.FrogramCallJoin));
    microphone.setEnabled(active && session.connected);
    microphone.setText(Lang.getString(active && !session.muted ? R.string.FrogramCallMute : R.string.FrogramCallUnmute));
    speaker.setEnabled(active);
    if (session != null) {
      StringBuilder list = new StringBuilder();
      for (TdApi.GroupCallParticipant participant : session.participants.values()) {
        if (list.length() != 0) list.append('\n');
        list.append(owner.tdlib().senderName(participant.participantId));
        if (participant.isMutedForAllUsers) list.append(" · ").append(Lang.getString(R.string.FrogramCallMuted));
      }
      participants.setText(list);
    }
  }
}
