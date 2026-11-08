/*
 * This file is a part of Frogram X
 * Copyright © 2026 Frogram X contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.Td;

public final class PageBlockButtons extends PageBlock implements Destroyable {
  public interface Action {
    void run (TdApi.InlineButton button);
  }

  private final Button[] buttons;
  private final TdApi.PageBlockHorizontalAlignment alignment;
  private final ClickHelper clickHelper;
  private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final @Nullable Action action;

  PageBlockButtons (ViewController<?> context, TdApi.PageBlockButtonRow row, int quoteLevel, @Nullable Action action) {
    super(context, row, quoteLevel);
    this.action = action;
    alignment = row.align;
    buttons = new Button[row.buttons.length];
    for (int i = 0; i < buttons.length; i++) {
      TdApi.InlineButton button = row.buttons[i];
      buttons[i] = new Button(button, FormattedText.parseRichText(context, button.text, null));
    }
    clickHelper = new ClickHelper(new ClickHelper.Delegate() {
      @Override
      public boolean needClickAt (View view, float x, float y) {
        Button button = findButton(x, y);
        return button != null && isInteractive(button.source);
      }

      @Override
      public void onClickAt (View view, float x, float y) {
        Button button = findButton(x, y);
        if (button != null && isInteractive(button.source)) {
          if (PageBlockButtons.this.action != null) {
            PageBlockButtons.this.action.run(button.source);
          } else {
            open(context, null, button.source);
          }
        }
      }
    });
    arrowPaint.setStrokeWidth(Screen.dp(1.5f));
    arrowPaint.setStrokeCap(Paint.Cap.ROUND);
  }

  private @Nullable Button findButton (float x, float y) {
    for (Button button : buttons) {
      if (button.bounds.contains((int) x, (int) y)) {
        return button;
      }
    }
    return null;
  }

  private boolean isInteractive (TdApi.InlineButton button) {
    if (button.type == null) {
      return false;
    }
    switch (button.type.getConstructor()) {
      case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR:
      case TdApi.InlineKeyboardButtonTypeCopyText.CONSTRUCTOR:
      case TdApi.InlineKeyboardButtonTypeUser.CONSTRUCTOR:
        return true;
      case TdApi.InlineKeyboardButtonTypeCallback.CONSTRUCTOR:
      case TdApi.InlineKeyboardButtonTypeWebApp.CONSTRUCTOR:
        return action != null;
      default:
        return false;
    }
  }

  public static void open (ViewController<?> context, @Nullable TdApi.Message message, TdApi.InlineButton button) {
    if (button.type == null) {
      return;
    }
    TdlibUi.UrlOpenParameters urlParameters = new TdlibUi.UrlOpenParameters().requireOpenPrompt();
    switch (button.type.getConstructor()) {
      case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR:
        context.tdlib().ui().openUrl(context, ((TdApi.InlineKeyboardButtonTypeUrl) button.type).url, urlParameters);
        break;
      case TdApi.InlineKeyboardButtonTypeUser.CONSTRUCTOR:
        context.tdlib().ui().openPrivateProfile(context, ((TdApi.InlineKeyboardButtonTypeUser) button.type).userId, urlParameters);
        break;
      case TdApi.InlineKeyboardButtonTypeCopyText.CONSTRUCTOR:
        UI.copyText(((TdApi.InlineKeyboardButtonTypeCopyText) button.type).text, R.string.CopiedText);
        break;
      case TdApi.InlineKeyboardButtonTypeWebApp.CONSTRUCTOR: {
        if (message != null) {
          String url = ((TdApi.InlineKeyboardButtonTypeWebApp) button.type).url;
          long botId = message.viaBotUserId != 0 ? message.viaBotUserId : Td.getSenderUserId(message);
          if (botId != 0) {
            context.tdlib().ui().openInlineWebApp(context, message.chatId, botId, url, message.topicId);
          } else {
            context.tdlib().ui().openUrl(context, url, urlParameters);
          }
        }
        break;
      }
      case TdApi.InlineKeyboardButtonTypeCallback.CONSTRUCTOR: {
        if (message == null || message.schedulingState != null) {
          return;
        }
        byte[] data = ((TdApi.InlineKeyboardButtonTypeCallback) button.type).data;
        context.tdlib().send(new TdApi.GetCallbackQueryAnswer(message.chatId, message.id, new TdApi.CallbackQueryPayloadData(data)), (answer, error) -> {
          context.tdlib().ui().post(() -> {
            if (context.isDestroyed()) {
              return;
            }
            if (error != null) {
              UI.showError(error);
              return;
            }
            if (!answer.url.isEmpty()) {
              context.tdlib().ui().openUrl(context, answer.url, urlParameters);
            }
            if (!answer.text.isEmpty()) {
              if (answer.showAlert) {
                context.openOkAlert(Lang.getString(R.string.AppName), answer.text);
              } else if (context instanceof MessagesController) {
                ((MessagesController) context).showCallbackToast(answer.text);
              } else {
                UI.showToast(answer.text, Toast.LENGTH_SHORT);
              }
            }
          });
        });
        break;
      }
    }
  }

  @Override
  public int getRelatedViewType () {
    return ListItem.TYPE_PAGE_BLOCK;
  }

  @Override
  protected int computeHeight (View view, int width) {
    if (buttons.length == 0) {
      return 0;
    }
    int margin = inline ? 0 : Screen.dp(16);
    int gap = Screen.dp(4);
    int rowWidth = Math.max(1, width - 2 * margin - (buttons.length - 1) * gap);
    int buttonWidth = Math.max(1, rowWidth / buttons.length);
    if (buttons.length > 1) {
      int preferred = Screen.dp(72);
      for (Button button : buttons) {
        if (button.label != null) {
          preferred = Math.max(preferred, Screen.dp(44) + (int) Math.ceil(
            PageBlockRichText.getCaptionProvider().getTextPaint().measureText(button.label.text)));
        }
      }
      buttonWidth = Math.min(buttonWidth, preferred);
    }
    int remainingWidth = rowWidth - buttonWidth * buttons.length;
    int x = margin;
    if (alignment instanceof TdApi.PageBlockHorizontalAlignmentCenter) {
      x += remainingWidth / 2;
    } else if (alignment instanceof TdApi.PageBlockHorizontalAlignmentRight) {
      x += remainingWidth;
    }
    int top = getContentTop();
    for (Button button : buttons) {
      button.bounds.set(x, top, x + buttonWidth, top + getContentHeight());
      if (button.text != null) {
        button.text.performDestroy();
      }
      if (button.label != null) {
        int padding = Screen.dp(button.isUrl() ? 40 : 16);
        button.text = new Text.Builder(button.label.text, Math.max(1, buttonWidth - padding),
          PageBlockRichText.getCaptionProvider(), TextColorSets.WHITE)
          .entities(button.label.entities, this::updateIconMedia)
          .addFlags(Text.FLAG_ALIGN_CENTER)
          .maxLineCount(1)
          .viewProvider(currentViews)
          .build();
      }
      x += buttonWidth + gap;
    }
    return top + getContentHeight() + Screen.dp(4);
  }

  @Override
  public void requestIcons (ComplexReceiver receiver) {
    receiver.clearReceiversWithHigherKey(requestInlineIcons(receiver, 0));
  }

  @Override
  int requestInlineIcons (ComplexReceiver receiver, int offset) {
    for (Button button : buttons) {
      if (button.text != null) {
        int count = button.text.getMediaCount();
        button.text.requestMedia(receiver, offset, count);
        offset += count;
      }
    }
    return offset;
  }

  @Override
  protected boolean handleTouchEvent (View view, MotionEvent event) {
    return clickHelper.onTouchEvent(view, event);
  }

  @Override
  protected int getContentTop () {
    return Screen.dp(4);
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(36);
  }

  private int buttonColor (TdApi.InlineButton button) {
    if (button.style instanceof TdApi.ButtonStyleDanger) {
      return 0xffdb4646;
    }
    if (button.style instanceof TdApi.ButtonStyleSuccess) {
      return 0xff40a535;
    }
    return 0xff2589bd;
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver icons) {
    int radius = Screen.dp(18);
    for (Button button : buttons) {
      Rect bounds = button.bounds;
      float alpha = isInteractive(button.source) ? 1f : .55f;
      c.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius,
        Paints.fillingPaint(ColorUtils.alphaColor(alpha, buttonColor(button.source))));
      if (button.text != null) {
        int left = bounds.left + Screen.dp(8);
        int right = bounds.right - Screen.dp(button.isUrl() ? 24 : 8);
        button.text.draw(c, left, right, 0, bounds.centerY() - button.text.getHeight() / 2, null, alpha, icons);
      }
      if (button.isUrl()) {
        int x = bounds.right - Screen.dp(12);
        int y = bounds.top + Screen.dp(11);
        int size = Screen.dp(5);
        arrowPaint.setColor(ColorUtils.alphaColor(alpha, Theme.getColor(ColorId.white)));
        c.drawLine(x - size, y + size, x, y, arrowPaint);
        c.drawLine(x - size, y, x, y, arrowPaint);
        c.drawLine(x, y, x, y + size, arrowPaint);
      }
    }
  }

  @Override
  public void performDestroy () {
    for (Button button : buttons) {
      if (button.text != null) {
        button.text.performDestroy();
      }
    }
  }

  private static final class Button {
    final TdApi.InlineButton source;
    final @Nullable FormattedText label;
    final Rect bounds = new Rect();
    @Nullable Text text;

    Button (TdApi.InlineButton source, @Nullable FormattedText label) {
      this.source = source;
      this.label = label;
    }

    boolean isUrl () {
      return source.type instanceof TdApi.InlineKeyboardButtonTypeUrl ||
        source.type instanceof TdApi.InlineKeyboardButtonTypeWebApp;
    }
  }
}
