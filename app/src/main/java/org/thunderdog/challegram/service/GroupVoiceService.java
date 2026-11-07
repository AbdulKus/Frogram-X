package org.thunderdog.challegram.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.voip.GroupVoiceSession;

public final class GroupVoiceService extends Service {
  private static final String CHANNEL = "frogram-group-audio";
  private GroupVoiceSession ownedSession;
  @Override public int onStartCommand (Intent intent, int flags, int startId) {
    GroupVoiceSession session = GroupVoiceSession.current();
    if (session == null || (intent != null && "leave".equals(intent.getAction()))) {
      if (session != null) session.stop(null);
      stopSelf();
      return START_NOT_STICKY;
    }
    ownedSession = session;
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      manager.createNotificationChannel(new NotificationChannel(CHANNEL, Lang.getString(R.string.FrogramGroupCall), NotificationManager.IMPORTANCE_LOW));
    }
    int immutable = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    PendingIntent leave = PendingIntent.getService(this, 0, new Intent(this, GroupVoiceService.class).setAction("leave"), PendingIntent.FLAG_UPDATE_CURRENT | immutable);
    Notification.Builder notification = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
    notification.setSmallIcon(R.drawable.baseline_call_24).setContentTitle(Lang.getString(R.string.FrogramGroupCall))
      .setContentText(Lang.getString(R.string.FrogramCallNotification)).setOngoing(true)
      .addAction(R.drawable.baseline_call_end_24, Lang.getString(R.string.FrogramCallLeave), leave);
    Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (launch != null) {
      launch.setAction(Intents.ACTION_OPEN_GROUP_VOICE)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("account_id", session.tdlib.id())
        .putExtra("group_call_id", session.callId);
      notification.setContentIntent(PendingIntent.getActivity(this, 7801, launch, PendingIntent.FLAG_UPDATE_CURRENT | immutable));
    }
    try {
      startForeground(7801, notification.build());
      session.connect();
    } catch (RuntimeException error) {
      session.fail(error);
      stopSelf();
    }
    return START_NOT_STICKY;
  }
  @Override public void onDestroy () {
    if (ownedSession != null && GroupVoiceSession.current() == ownedSession) ownedSession.stop(null);
    super.onDestroy();
  }
  @Override public IBinder onBind (Intent intent) { return null; }
}
