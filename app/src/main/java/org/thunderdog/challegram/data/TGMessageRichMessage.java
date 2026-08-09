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

import android.view.MotionEvent;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.ui.InstantViewController;

import me.vkryl.android.util.ClickHelper;

public class TGMessageRichMessage extends TGMessageText {
  private TdApi.RichMessage richMessage;

  private final ClickHelper richMessageClickHelper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      return x >= getContentX() && x <= getContentX() + getContentWidth() &&
        y >= getContentY() && y <= getContentY() + getContentHeight();
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      openRichMessage();
    }
  });

  public TGMessageRichMessage (MessagesManager context, TdApi.Message msg, TdApi.MessageRichMessage content) {
    super(context, msg, RichMessageUtils.buildMessagePreview(content.message));
    this.richMessage = content.message;
  }

  private void openRichMessage () {
    InstantViewController controller = new InstantViewController(context(), tdlib());
    controller.setArguments(InstantViewController.Args.forRichMessage(msg.chatId, msg.id, richMessage));
    navigateTo(controller);
  }

  @Override
  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    return messageContent.getConstructor() == TdApi.MessageRichMessage.CONSTRUCTOR ||
      super.isSupportedMessageContent(message, messageContent);
  }

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (newContent.getConstructor() == TdApi.MessageRichMessage.CONSTRUCTOR) {
      this.msg.content = newContent;
      this.richMessage = ((TdApi.MessageRichMessage) newContent).message;
      setGeneratedMessageText(RichMessageUtils.buildMessagePreview(richMessage));
      return true;
    }
    return super.onMessageContentChanged(message, oldContent, newContent, isBottomMessage);
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent event) {
    if (super.onTouchEvent(view, event)) {
      return true;
    }
    return richMessageClickHelper.onTouchEvent(view, event);
  }
}
