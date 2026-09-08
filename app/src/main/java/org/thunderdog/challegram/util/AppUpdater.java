/* Copyright © Frogram X contributors. Licensed under GPL-3.0-or-later. */
package org.thunderdog.challegram.util;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.FileProvider;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.AppUpdateDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.vkryl.android.AppInstallationUtil;
import me.vkryl.core.reference.ReferenceList;

/** GitHub release updates. DownloadManager owns transfers across activity/process death. */
public class AppUpdater {
  public interface Listener {
    void onAppUpdateStateChanged (int state, int oldState, boolean isApk);
    default void onAppUpdateDownloadProgress (long bytesDownloaded, long totalBytesToDownload) { }
  }

  public @interface State {
    int NONE = 0, CHECKING = 1, AVAILABLE = 2, DOWNLOADING = 3, READY_TO_INSTALL = 4, INSTALLING = 5;
  }

  public @interface FlowType {
    int NONE = 0, TELEGRAM_CHANNEL = 1, GOOGLE_PLAY = 2, GITHUB = 3;
  }

  private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;
  private static final long RETRY_INTERVAL = 15 * 60 * 1000L;
  private final BaseActivity activity;
  private final Context context;
  private final SharedPreferences prefs;
  private final DownloadManager downloads;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final ReferenceList<Listener> listeners = new ReferenceList<>();
  private GitHubRelease release;
  private int state = State.NONE;
  private long downloaded, downloadId;
  private int generation;
  private boolean resumed, destroyed, polling, verifying;
  private String lastError;
  private AppUpdateDialog dialog;

  public AppUpdater (BaseActivity activity) {
    this.activity = activity;
    this.context = activity.getApplicationContext();
    prefs = context.getSharedPreferences("frogram_updates", Context.MODE_PRIVATE);
    downloads = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    try {
      String cached = prefs.getString("release", null);
      String manifest = prefs.getString("manifest", null);
      if (cached != null && manifest != null) release = select(new JSONObject(cached), new JSONObject(manifest));
    } catch (Exception ignored) { }
    downloadId = prefs.getLong("download_id", 0);
    if (release != null) {
      state = State.AVAILABLE;
      if (prefs.getLong("download_asset", 0) == release.assetId) {
        if (prefs.getLong("verified_asset", 0) == release.assetId && verifiedFile().isFile() && verifiedFile().length() == release.size) {
          downloaded = release.size;
          state = State.READY_TO_INSTALL;
        } else if (downloadId != 0) {
          state = State.DOWNLOADING;
        }
      } else {
        clearDownload();
      }
    } else {
      clearDownload();
    }
  }

  public int state () { return state; }
  public int flowType () { return FlowType.GITHUB; }
  public long totalBytesToDownload () { return release != null ? release.size : 0; }
  public long bytesDownloaded () { return downloaded; }
  @Nullable public String displayVersion () { return release != null ? release.versionName : null; }
  @Nullable public String commit () { return null; }
  @Nullable public GitHubRelease release () { return release; }
  @Nullable public String lastError () { return lastError; }
  public boolean isVerifying () { return verifying; }
  public boolean shouldShowUpdateNotice () {
    return release != null && (prefs.getLong("notice_asset", 0) != release.assetId ||
      prefs.getLong("notice_after", 0) <= System.currentTimeMillis());
  }
  public void postponeUpdate () {
    if (release == null) return;
    prefs.edit().putLong("notice_asset", release.assetId)
      .putLong("notice_after", System.currentTimeMillis() + 24 * 60 * 60 * 1000L).apply();
    setState(state);
  }
  public boolean automaticChecksEnabled () { return prefs.getBoolean("automatic", true); }
  public void setAutomaticChecksEnabled (boolean enabled) {
    prefs.edit().putBoolean("automatic", enabled).apply();
    if (enabled) checkForUpdates();
  }
  public void addListener (Listener listener) { listeners.add(listener); }
  public void removeListener (Listener listener) { listeners.remove(listener); }

  public void onResume () {
    resumed = true;
    if (state == State.DOWNLOADING) pollDownload();
    if (prefs.getBoolean("install_permission_requested", false)) {
      prefs.edit().remove("install_permission_requested").apply();
      if (Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls()) installUpdate();
    }
    checkForUpdates();
  }

  public void onPause () {
    resumed = false;
    handler.removeCallbacks(pollTask);
  }

  public void destroy () {
    destroyed = true;
    generation++;
    handler.removeCallbacksAndMessages(null);
    if (dialog != null) dialog.dismiss();
    worker.shutdownNow();
  }

  /** Calls from navigation are automatic; explicit checks bypass the interval and toggle. */
  public void checkForUpdates () { check(false); }
  public void checkForUpdatesNow () { check(true); }

  private void check (boolean manual) {
    if (destroyed || state == State.CHECKING || state == State.DOWNLOADING || state == State.READY_TO_INSTALL) return;
    long now = System.currentTimeMillis();
    long nextCheck = prefs.getLong("next_check", 0);
    if (!manual && (!automaticChecksEnabled() || (nextCheck > now && nextCheck - now <= CHECK_INTERVAL))) return;
    prefs.edit().putLong("next_check", now + RETRY_INTERVAL).apply();
    lastError = null;
    setState(State.CHECKING);
    worker.execute(() -> {
      try {
        String body = readJson(GitHubRelease.API_URL, true);
        JSONObject info = body != null ? new JSONObject(body) : null;
        String manifestBody = null;
        GitHubRelease found = null;
        if (info != null && GitHubRelease.isPublished(info)) {
          String manifestUrl = GitHubRelease.manifestUrl(info);
          if (manifestUrl != null) {
            manifestBody = readJson(manifestUrl, false);
            found = select(info, new JSONObject(manifestBody));
          }
        }
        final GitHubRelease result = found;
        final String resultManifest = manifestBody;
        handler.post(() -> {
          if (destroyed) return;
          release = result;
          prefs.edit().putString("release", result != null ? body : null)
            .putString("manifest", result != null ? resultManifest : null)
            .putLong("next_check", System.currentTimeMillis() + CHECK_INTERVAL).apply();
          setState(result != null ? State.AVAILABLE : State.NONE);
          if (manual && resumed) {
            if (result != null) showUpdateDetails();
            else UI.showToast(Lang.getString(R.string.FrogramUpToDate), Toast.LENGTH_SHORT);
          }
        });
      } catch (Exception e) {
        Log.i("GitHub update check failed", e);
        handler.post(() -> {
          if (destroyed) return;
          lastError = Lang.getString(R.string.FrogramUpdateCheckFailed);
          setState(release != null ? State.AVAILABLE : State.NONE);
          if (manual && resumed) UI.showToast(lastError, Toast.LENGTH_SHORT);
        });
      }
    });
  }

  private GitHubRelease select (JSONObject info, JSONObject manifest) throws Exception {
    String[] abis = Build.VERSION.SDK_INT >= 21 ? Build.SUPPORTED_ABIS : new String[] {Build.CPU_ABI, Build.CPU_ABI2};
    long installedVersion = versionCode(context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
    return GitHubRelease.select(info, manifest, context.getPackageName(), installedVersion, Build.VERSION.SDK_INT, abis);
  }

  private static String readJson (String url, boolean allowNotFound) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(20000);
    connection.setRequestProperty("Accept", "application/vnd.github+json");
    connection.setRequestProperty("User-Agent", "Frogram-X/" + BuildConfig.VERSION_NAME);
    try {
      int status = connection.getResponseCode();
      if (status == 404 && allowNotFound) return null;
      if (status != 200 || !"https".equals(connection.getURL().getProtocol())) throw new IOException("HTTP " + status);
      try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted() || out.size() + count > 1024 * 1024) throw new IOException("Update response limit");
          out.write(buffer, 0, count);
        }
        return out.toString("UTF-8");
      }
    } finally {
      connection.disconnect();
    }
  }

  public void offerUpdate () { showUpdateDetails(); }
  public void showUpdateDetails () {
    if (destroyed || release == null || activity.isFinishing()) return;
    if (dialog == null) dialog = new AppUpdateDialog(activity, this);
    dialog.show();
  }

  public void downloadUpdate () {
    if (destroyed || release == null || state != State.AVAILABLE) return;
    lastError = null;
    try {
      if (downloads == null || context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) == null) throw new IOException("Download storage unavailable");
      clearDownload();
      File destination = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/" + release.assetId + ".apk");
      if (destination.exists() && !destination.delete()) throw new IOException("Cannot replace incomplete download");
      File directory = destination.getParentFile();
      if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Download storage unavailable");
      DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.downloadUrl))
        .setTitle("Frogram X " + release.versionName)
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setAllowedOverMetered(true)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/" + release.assetId + ".apk");
      downloadId = downloads.enqueue(request);
      prefs.edit().putLong("download_id", downloadId).putLong("download_asset", release.assetId).commit();
      downloaded = 0;
      setState(State.DOWNLOADING);
      pollDownload();
    } catch (Exception e) {
      failDownload(R.string.FrogramUpdateDownloadFailed, e);
    }
  }

  public void cancelDownload () {
    if (state != State.DOWNLOADING) return;
    generation++;
    verifying = false;
    clearDownload();
    downloaded = 0;
    setState(release != null ? State.AVAILABLE : State.NONE);
  }

  private void clearDownload () {
    handler.removeCallbacks(pollTask);
    long assetId = prefs.getLong("download_asset", 0);
    if (downloadId != 0 && downloads != null) {
      try { downloads.remove(downloadId); } catch (RuntimeException ignored) { }
    }
    if (assetId > 0) new File(context.getFilesDir(), "updates/" + assetId + ".apk").delete();
    downloadId = 0;
    prefs.edit().remove("download_id").remove("download_asset").remove("verified_asset").apply();
  }

  private final Runnable pollTask = this::pollDownload;

  private void pollDownload () {
    if (destroyed || !resumed || polling || verifying || downloadId == 0 || state != State.DOWNLOADING) return;
    polling = true;
    final long id = downloadId;
    final int taskGeneration = generation;
    worker.execute(() -> {
      int status = DownloadManager.STATUS_FAILED;
      long bytes = 0;
      try (Cursor c = downloads.query(new DownloadManager.Query().setFilterById(id))) {
        if (c != null && c.moveToFirst()) {
          status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
          bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        }
      } catch (RuntimeException e) { Log.i("Update download query failed", e); }
      final int resultStatus = status;
      final long resultBytes = bytes;
      handler.post(() -> {
        polling = false;
        if (destroyed || id != downloadId || taskGeneration != generation) {
          if (!destroyed && resumed) pollDownload();
          return;
        }
        downloaded = Math.max(0, resultBytes);
        notifyProgress();
        if (resultStatus == DownloadManager.STATUS_SUCCESSFUL) verifyDownload();
        else if (resultStatus == DownloadManager.STATUS_FAILED) failDownload(R.string.FrogramUpdateDownloadFailed, null);
        else if (resumed) handler.postDelayed(pollTask, 1000);
      });
    });
  }

  private File verifiedFile () { return new File(context.getFilesDir(), "updates/" + release.assetId + ".apk"); }

  private void verifyDownload () {
    if (verifying || release == null) return;
    verifying = true;
    notifyProgress();
    final GitHubRelease target = release;
    final int taskGeneration = generation;
    worker.execute(() -> {
      File destination = new File(context.getFilesDir(), "updates/" + target.assetId + ".apk");
      File temporary = new File(destination.getPath() + "." + System.nanoTime() + ".part.apk");
      int error = R.string.FrogramUpdateInvalidFile;
      try {
        File directory = destination.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Update storage unavailable");
        File source = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/" + target.assetId + ".apk");
        if (source.length() != target.size) throw new IOException("APK length mismatch");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(temporary)) {
          byte[] buffer = new byte[65536];
          long total = 0;
          int count;
          while ((count = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted() || (total += count) > target.size) throw new IOException("Transfer interrupted");
            digest.update(buffer, 0, count);
            out.write(buffer, 0, count);
          }
          if (total != target.size) throw new IOException("APK length mismatch");
          out.getFD().sync();
        }
        if (!hex(digest.digest()).equals(target.sha256)) throw new IOException("APK checksum mismatch");
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageManager pm = context.getPackageManager();
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
        PackageInfo candidate = pm.getPackageArchiveInfo(temporary.getPath(), flags);
        if (candidate == null || candidate.applicationInfo == null || !context.getPackageName().equals(candidate.packageName) ||
            versionCode(candidate) != target.versionCode || versionCode(candidate) <= versionCode(installed) ||
            (Build.VERSION.SDK_INT >= 24 && candidate.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT)) {
          throw new IOException("APK package or version mismatch");
        }
        error = R.string.FrogramUpdateSignatureMismatch;
        Set<String> currentSigners = signers(installed);
        if (currentSigners.isEmpty() || !currentSigners.equals(signers(candidate))) throw new IOException("APK signing certificate mismatch");
        error = R.string.FrogramUpdateInvalidFile;
        handler.post(() -> {
          if (destroyed || taskGeneration != generation) { temporary.delete(); return; }
          verifying = false;
          if (!temporary.renameTo(destination)) { temporary.delete(); failDownload(R.string.FrogramUpdateInvalidFile, null); return; }
          prefs.edit().putLong("verified_asset", target.assetId).remove("notice_after").apply();
          downloaded = target.size;
          setState(State.READY_TO_INSTALL);
        });
      } catch (Exception e) {
        temporary.delete();
        final int errorRes = error;
        handler.post(() -> {
          if (!destroyed && taskGeneration == generation) failDownload(errorRes, e);
        });
      }
    });
  }

  private static long versionCode (PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
  private static Set<String> signers (PackageInfo info) {
    Signature[] signatures = Build.VERSION.SDK_INT >= 28 ? (info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : null) : info.signatures;
    Set<String> result = new HashSet<>();
    if (signatures != null) for (Signature signature : signatures) result.add(signature.toCharsString());
    return result;
  }
  private static String hex (byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b & 255));
    return result.toString();
  }

  private void failDownload (int message, Exception error) {
    verifying = false;
    clearDownload();
    lastError = Lang.getString(message);
    setState(release != null ? State.AVAILABLE : State.NONE);
    if (resumed) UI.showToast(lastError, Toast.LENGTH_LONG);
    if (error != null) Log.i("GitHub APK update failed", error);
  }

  public void installUpdate () {
    if (destroyed || state != State.READY_TO_INSTALL || release == null) return;
    File apk = verifiedFile();
    if (!apk.isFile() || apk.length() != release.size) { failDownload(R.string.FrogramUpdateInvalidFile, null); return; }
    try {
      if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
        prefs.edit().putBoolean("install_permission_requested", true).apply();
        activity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName())));
        return;
      }
      Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apk);
      activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    } catch (RuntimeException e) {
      prefs.edit().remove("install_permission_requested").apply();
      lastError = Lang.getString(R.string.FrogramUpdateInstallFailed);
      if (resumed) UI.showToast(lastError, Toast.LENGTH_LONG);
    }
  }

  private void setState (int state) {
    int old = this.state;
    this.state = state;
    for (Listener listener : listeners) listener.onAppUpdateStateChanged(state, old, true);
  }
  private void notifyProgress () {
    for (Listener listener : listeners) listener.onAppUpdateDownloadProgress(downloaded, totalBytesToDownload());
  }

  // Retained for the existing activity-result route; Frogram X never starts a Play flow.
  public void onGooglePlayFlowActivityResult (int resultCode, Intent data) { }
  public static AppInstallationUtil.PublicMarketUrls publicMarketUrls () {
    return new AppInstallationUtil.PublicMarketUrls(GitHubRelease.RELEASES_URL, "", "", "", "");
  }
  public static AppInstallationUtil.DownloadUrl getDownloadUrl (@Nullable String ignored) {
    return new AppInstallationUtil.DownloadUrl(AppInstallationUtil.InstallerId.UNKNOWN, GitHubRelease.RELEASES_URL);
  }
}
