/*
 * This file is a part of Frogram X
 * Copyright © 2014-2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.annotation.Keep;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

/** A small, native Telegram Mini App host built on top of the existing in-app WebView. */
public class MiniAppController extends WebkitController<MiniAppController.Args> {
  public static class Args {
    public final long botUserId;
    public final String title;
    public final String subtitle;
    public final TdApi.WebAppUrl webAppUrl;
    public final long launchId;
    public final String keyboardButtonText;

    public Args (long botUserId, String title, String subtitle, TdApi.WebAppUrl webAppUrl, long launchId, String keyboardButtonText) {
      this.botUserId = botUserId;
      this.title = title;
      this.subtitle = subtitle;
      this.webAppUrl = webAppUrl;
      this.launchId = launchId;
      this.keyboardButtonText = keyboardButtonText;
    }
  }

  private String origin;
  private boolean backButtonVisible;
  private boolean needCloseConfirmation;
  private TextView mainButton;

  public MiniAppController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @SuppressLint({"AddJavascriptInterface", "SetJavaScriptEnabled"})
  @Override
  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    Args args = getArgumentsStrict();
    headerCell.setTitle(StringUtils.isEmpty(args.title) ? Lang.getString(R.string.MiniApp) : args.title);
    headerCell.setSubtitle(args.subtitle);

    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      settings.setAllowFileAccessFromFileURLs(false);
      settings.setAllowUniversalAccessFromFileURLs(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
      settings.setMediaPlaybackRequiresUserGesture(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      webView.addJavascriptInterface(new TelegramWebviewProxy(), "TelegramWebviewProxy");
    }

    Uri initial = Uri.parse(args.webAppUrl.url);
    origin = initial.getScheme() + "://" + initial.getAuthority();
    webView.loadUrl(args.webAppUrl.url);
  }

  @Override
  protected void onCreateContentView (FrameLayoutFix contentView, WebView webView) {
    mainButton = new TextView(context());
    mainButton.setGravity(Gravity.CENTER);
    mainButton.setTextSize(15f);
    mainButton.setTextColor(Color.WHITE);
    mainButton.setTypeface(mainButton.getTypeface(), android.graphics.Typeface.BOLD);
    mainButton.setVisibility(View.GONE);
    mainButton.setOnClickListener(v -> notifyEvent("main_button_pressed", null));
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f), Gravity.BOTTOM);
    params.leftMargin = params.rightMargin = params.bottomMargin = Screen.dp(8f);
    contentView.addView(mainButton, params);
  }

  @Override
  protected boolean hasSpecialProcessing () {
    return true;
  }

  @Override
  protected boolean processSpecial (Uri uri) {
    if (uri == null) {
      return false;
    }
    String scheme = uri.getScheme();
    if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) &&
        (!getArgumentsStrict().webAppUrl.requireSameOrigin || sameOrigin(uri))) {
      return false;
    }
    tdlib.ui().openUrl(this, uri.toString(), new TdlibUi.UrlOpenParameters());
    return true;
  }

  private boolean sameOrigin (Uri uri) {
    return origin != null && origin.equalsIgnoreCase(uri.getScheme() + "://" + uri.getAuthority());
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (backButtonVisible) {
      if (commit) {
        notifyEvent("back_button_pressed", null);
      }
      return true;
    }
    if (needCloseConfirmation) {
      if (commit) {
        new AlertDialog.Builder(context(), Theme.dialogTheme())
          .setTitle(Lang.getString(R.string.MiniAppClose))
          .setMessage(Lang.getString(R.string.MiniAppCloseConfirm))
          .setPositiveButton(Lang.getString(R.string.MiniAppCloseAction), (dialog, which) -> navigateBack())
          .setNegativeButton(Lang.getString(R.string.Cancel), null)
          .show();
      }
      return true;
    }
    return false;
  }

  private void notifyEvent (String event, JSONObject data) {
    String payload = data == null ? "null" : data.toString();
    evaluateJavascript("window.Telegram&&Telegram.WebView&&Telegram.WebView.receiveEvent(" + JSONObject.quote(event) + "," + payload + ");");
  }

  private JSONObject themeParams () {
    JSONObject json = new JSONObject();
    try {
      json.put("bg_color", color(Theme.fillingColor()));
      json.put("secondary_bg_color", color(Theme.backgroundColor()));
      json.put("header_bg_color", color(Theme.headerColor()));
      json.put("bottom_bar_bg_color", color(Theme.fillingColor()));
      json.put("text_color", color(Theme.textAccentColor()));
      json.put("hint_color", color(Theme.textDecentColor()));
      json.put("link_color", color(Theme.textLinkColor()));
      json.put("button_color", color(Theme.getColor(ColorId.fillingPositive)));
      json.put("button_text_color", color(Theme.getColor(ColorId.fillingPositiveContent)));
      json.put("accent_text_color", color(Theme.textLinkColor()));
      json.put("destructive_text_color", "#db4646");
    } catch (Throwable ignored) { }
    return json;
  }

  private static String color (int color) {
    return String.format("#%06x", color & 0x00ffffff);
  }

  private void sendViewport () {
    JSONObject data = new JSONObject();
    try {
      float density = context().getResources().getDisplayMetrics().density;
      data.put("height", webView() != null ? webView().getHeight() / density : 0);
      data.put("width", webView() != null ? webView().getWidth() / density : 0);
      data.put("is_state_stable", true);
      data.put("is_expanded", true);
    } catch (Throwable ignored) { }
    notifyEvent("viewport_changed", data);
  }

  private void setupMainButton (JSONObject data) {
    if (mainButton == null) return;
    boolean visible = data.optBoolean("is_visible", false);
    mainButton.setText(data.optString("text", ""));
    mainButton.setEnabled(data.optBoolean("is_active", true));
    mainButton.setAlpha(mainButton.isEnabled() ? 1f : .55f);
    mainButton.setBackgroundColor(parseColor(data.optString("color", null), Theme.getColor(ColorId.fillingPositive)));
    mainButton.setTextColor(parseColor(data.optString("text_color", null), Theme.getColor(ColorId.fillingPositiveContent)));
    mainButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    setWebViewBottomInset(visible ? Screen.dp(68f) : 0);
  }

  private static int parseColor (String value, int fallback) {
    try {
      return StringUtils.isEmpty(value) ? fallback : Color.parseColor(value);
    } catch (Throwable ignored) {
      return fallback;
    }
  }

  private void showPopup (JSONObject data) {
    JSONArray buttons = data.optJSONArray("buttons");
    AlertDialog.Builder builder = new AlertDialog.Builder(context(), Theme.dialogTheme())
      .setTitle(data.optString("title", ""))
      .setMessage(data.optString("message", ""));
    final String[] ids = new String[3];
    for (int i = 0; buttons != null && i < Math.min(3, buttons.length()); i++) {
      JSONObject button = buttons.optJSONObject(i);
      if (button == null) continue;
      ids[i] = button.optString("id", "");
      String text = button.optString("text", Lang.getOK());
      final int index = i;
      android.content.DialogInterface.OnClickListener listener = (dialog, which) -> {
        JSONObject result = new JSONObject();
        try { result.put("button_id", ids[index]); } catch (Throwable ignored) { }
        notifyEvent("popup_closed", result);
      };
      if (i == 0) builder.setPositiveButton(text, listener);
      else if (i == 1) builder.setNegativeButton(text, listener);
      else builder.setNeutralButton(text, listener);
    }
    builder.setOnCancelListener(dialog -> notifyEvent("popup_closed", new JSONObject()));
    builder.show();
  }

  private void processEvent (String eventType, String eventData) {
    JSONObject data;
    try {
      data = StringUtils.isEmpty(eventData) ? new JSONObject() : new JSONObject(eventData);
    } catch (Throwable ignored) {
      data = new JSONObject();
    }
    switch (eventType) {
      case "web_app_ready":
        notifyEvent("theme_changed", wrapTheme());
        sendViewport();
        break;
      case "web_app_request_theme": notifyEvent("theme_changed", wrapTheme()); break;
      case "web_app_request_viewport": sendViewport(); break;
      case "web_app_setup_back_button": backButtonVisible = data.optBoolean("is_visible", false); break;
      case "web_app_setup_closing_behavior": needCloseConfirmation = data.optBoolean("need_confirmation", false); break;
      case "web_app_setup_main_button": setupMainButton(data); break;
      case "web_app_close": navigateBack(); break;
      case "web_app_data_send": {
        Args args = getArgumentsStrict();
        if (!StringUtils.isEmpty(args.keyboardButtonText)) {
          tdlib.send(new TdApi.SendWebAppData(args.botUserId, args.keyboardButtonText, data.optString("data", "")), (result, error) -> UI.post(this::navigateBack));
        }
        break;
      }
      case "web_app_open_link": {
        String url = data.optString("url", "");
        if (!StringUtils.isEmpty(url)) tdlib.ui().openUrl(this, url, new TdlibUi.UrlOpenParameters());
        break;
      }
      case "web_app_open_tg_link": {
        String path = data.optString("path_full", "");
        if (!StringUtils.isEmpty(path)) tdlib.ui().openUrl(this, "https://t.me" + path, new TdlibUi.UrlOpenParameters());
        break;
      }
      case "web_app_open_popup": showPopup(data); break;
      case "web_app_request_fullscreen":
        notifyFailure("fullscreen_failed", "UNSUPPORTED");
        break;
      case "web_app_request_file_download":
        notifyFailure("file_download_requested", "UNSUPPORTED");
        break;
    }
  }

  private JSONObject wrapTheme () {
    JSONObject result = new JSONObject();
    try { result.put("theme_params", themeParams()); } catch (Throwable ignored) { }
    return result;
  }

  private void notifyFailure (String event, String error) {
    JSONObject result = new JSONObject();
    try { result.put("error", error); } catch (Throwable ignored) { }
    notifyEvent(event, result);
  }

  @Keep
  private final class TelegramWebviewProxy {
    @Keep
    @JavascriptInterface
    public void postEvent (String eventType, String eventData) {
      UI.post(() -> {
        if (!isDestroyed()) processEvent(eventType, eventData);
      });
    }
  }

  @Override
  public void destroy () {
    Args args = getArguments();
    if (args != null && args.launchId != 0) {
      tdlib.send(new TdApi.CloseWebApp(args.launchId), (result, error) -> { });
    }
    super.destroy();
    mainButton = null;
  }
}
