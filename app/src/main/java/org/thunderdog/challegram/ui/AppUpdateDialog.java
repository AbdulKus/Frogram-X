package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.util.GitHubRelease;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A themed, scrollable release note dialog; closing it does not stop a transfer. */
public final class AppUpdateDialog implements AppUpdater.Listener {
  private final BaseActivity activity;
  private final AppUpdater updater;
  private AlertDialog dialog;
  private ProgressBar progress;
  private TextView status;
  private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)\\]\\((https://[^\\s)]+)\\)");

  public AppUpdateDialog (BaseActivity activity, AppUpdater updater) {
    this.activity = activity;
    this.updater = updater;
  }

  public void show () {
    if (dialog != null && dialog.isShowing()) return;
    GitHubRelease release = updater.release();
    if (release == null) return;
    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(Screen.dp(24), Screen.dp(8), Screen.dp(24), 0);
    TextView notes = new TextView(activity);
    notes.setTextSize(15);
    notes.setTextColor(Theme.textAccentColor());
    notes.setLinkTextColor(Theme.progressColor());
    notes.setLineSpacing(Screen.dp(3), 1);
    notes.setMovementMethod(LinkMovementMethod.getInstance());
    notes.setText(release.notes.trim().isEmpty() ? Lang.getString(R.string.FrogramNoReleaseNotes) : renderMarkdown(release.notes));
    notes.setPadding(0, 0, 0, Screen.dp(16));
    ScrollView scroll = new ScrollView(activity);
    scroll.setFillViewport(false);
    scroll.addView(notes, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    int maxHeight = Math.min(Screen.dp(320), activity.getResources().getDisplayMetrics().heightPixels / 2);
    // Cap the changelog so the action and progress remain visible even for long releases.
    notes.measure(View.MeasureSpec.makeMeasureSpec(Math.max(Screen.dp(120), activity.getResources().getDisplayMetrics().widthPixels - Screen.dp(96)), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    content.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(maxHeight, notes.getMeasuredHeight())));
    progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(1000);
    if (Build.VERSION.SDK_INT >= 21) progress.setProgressTintList(ColorStateList.valueOf(Theme.progressColor()));
    content.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(4)));
    status = new TextView(activity);
    status.setTextSize(13);
    status.setTextColor(Theme.textDecentColor());
    status.setPadding(0, Screen.dp(10), 0, Screen.dp(8));
    content.addView(status);
    String title = release.title.trim().isEmpty() ? "Frogram X " + release.versionName : release.title;
    dialog = activity.showAlert(new AlertDialog.Builder(activity, Theme.dialogTheme())
      .setTitle(title)
      .setView(content)
      .setPositiveButton(Lang.getString(R.string.FrogramUpdateDownload), null)
      .setNegativeButton(Lang.getString(R.string.FrogramUpdateLater), (ignored, which) -> updater.postponeUpdate())
      .setNeutralButton(Lang.getString(R.string.Cancel), null));
    if (dialog == null) return;
    updater.addListener(this);
    dialog.setOnDismissListener(ignored -> { updater.removeListener(this); dialog = null; });
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      if (updater.state() == AppUpdater.State.READY_TO_INSTALL) updater.installUpdate();
      else updater.downloadUpdate();
    });
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> updater.cancelDownload());
    refresh();
  }

  public void dismiss () { if (dialog != null) dialog.dismiss(); }

  private void refresh () {
    if (dialog == null) return;
    boolean downloading = updater.state() == AppUpdater.State.DOWNLOADING;
    boolean ready = updater.state() == AppUpdater.State.READY_TO_INSTALL;
    progress.setVisibility(downloading ? View.VISIBLE : View.GONE);
    progress.setIndeterminate(updater.isVerifying());
    long total = updater.totalBytesToDownload();
    long bytes = Math.min(updater.bytesDownloaded(), total);
    progress.setProgress(total > 0 ? (int) (bytes * 1000 / total) : 0);
    if (updater.lastError() != null) status.setText(updater.lastError());
    else if (updater.isVerifying()) status.setText(Lang.getString(R.string.FrogramUpdateVerifying));
    else if (downloading) status.setText(Strings.buildSize(bytes) + " / " + Strings.buildSize(total));
    else if (ready) status.setText(Lang.getString(R.string.FrogramUpdateReady));
    else status.setText(Strings.buildSize(total));
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!downloading);
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(Lang.getString(ready ? R.string.FrogramUpdateInstall : R.string.FrogramUpdateDownload));
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(downloading ? View.VISIBLE : View.GONE);
  }

  @Override public void onAppUpdateStateChanged (int state, int oldState, boolean isApk) { refresh(); }
  @Override public void onAppUpdateDownloadProgress (long downloaded, long total) { refresh(); }

  private static CharSequence renderMarkdown (String markdown) {
    SpannableStringBuilder result = new SpannableStringBuilder();
    for (String raw : markdown.replace("\r", "").split("\n", -1)) {
      boolean heading = raw.matches("^#{1,6}\\s+.*");
      String line = raw.replaceFirst("^#{1,6}\\s+", "").replaceFirst("^\\s*[-*]\\s+", "• ");
      int start = result.length();
      Matcher matcher = INLINE.matcher(line);
      int last = 0;
      while (matcher.find()) {
        result.append(line, last, matcher.start());
        int spanStart = result.length();
        Object span;
        if (matcher.group(1) != null) { result.append(matcher.group(1)); span = new StyleSpan(Typeface.BOLD); }
        else if (matcher.group(2) != null) { result.append(matcher.group(2)); span = new TypefaceSpan("monospace"); }
        else { result.append(matcher.group(3)); span = new URLSpan(matcher.group(4)); }
        result.setSpan(span, spanStart, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        last = matcher.end();
      }
      result.append(line, last, line.length());
      if (heading && result.length() > start) {
        result.setSpan(new StyleSpan(Typeface.BOLD), start, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.setSpan(new RelativeSizeSpan(1.1f), start, result.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      result.append('\n');
    }
    return result;
  }
}
