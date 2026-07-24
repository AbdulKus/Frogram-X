/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverProvider;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.lang.ref.WeakReference;

public final class ForumTopicIconModifier implements DrawModifier, TdlibEmojiManager.Watcher {
  private static final long IMAGE_RECEIVER_KEY = 1;
  private static final long GIF_RECEIVER_KEY = 2;

  private final Tdlib tdlib;
  private final long customEmojiId;
  private final int color;
  private final Drawable forumIcon;
  private final Drawable pinIcon;
  private final Drawable lockIcon;
  private final boolean general;

  private TGStickerObj sticker;
  private WeakReference<View> boundView;
  private boolean postponedRequestSent;
  private boolean pinned;
  private boolean closed;
  private boolean centered;
  private boolean selected;

  public ForumTopicIconModifier (Tdlib tdlib, TdApi.ForumTopicIcon icon, boolean pinned, boolean closed, boolean general) {
    this.tdlib = tdlib;
    this.customEmojiId = !general && icon != null ? icon.customEmojiId : 0;
    this.color = icon != null && icon.color != 0 ? 0xff000000 | icon.color : Theme.getColor(ColorId.icon);
    this.pinned = pinned;
    this.closed = closed;
    this.general = general;
    this.forumIcon = Drawables.get(R.drawable.baseline_forum_16);
    this.pinIcon = Drawables.get(R.drawable.deproko_baseline_pin_14);
    this.lockIcon = Drawables.get(R.drawable.baseline_lock_14);
    if (customEmojiId != 0) {
      TdlibEmojiManager.Entry entry = tdlib.emoji().findOrPostponeRequest(customEmojiId, this);
      if (entry != null && !entry.isNotFound()) {
        setSticker(entry);
      }
    }
  }

  public void bind (View view) {
    boundView = new WeakReference<>(view);
    if (customEmojiId == 0) {
      view.invalidate();
      return;
    }
    if (sticker == null) {
      if (!postponedRequestSent) {
        postponedRequestSent = true;
        tdlib.emoji().performPostponedRequests();
      }
      return;
    }
    requestFiles(view);
    view.invalidate();
  }

  public ForumTopicIconModifier setCentered (boolean centered) {
    this.centered = centered;
    return this;
  }

  public void setSelected (boolean selected) {
    if (this.selected != selected) {
      this.selected = selected;
      invalidateBoundView();
    }
  }

  public void setPinned (boolean pinned) {
    if (this.pinned != pinned) {
      this.pinned = pinned;
      invalidateBoundView();
    }
  }

  public void setClosed (boolean closed) {
    if (this.closed != closed) {
      this.closed = closed;
      invalidateBoundView();
    }
  }

  private void invalidateBoundView () {
    View view = boundView != null ? boundView.get() : null;
    if (view != null) {
      view.invalidate();
    }
  }

  private void setSticker (@NonNull TdlibEmojiManager.Entry entry) {
    if (entry.value != null) {
      sticker = new TGStickerObj(tdlib, entry.value, null, new TdApi.StickerTypeCustomEmoji());
    }
  }

  private void requestFiles (View view) {
    if (!(view instanceof ComplexReceiverProvider) || sticker == null) {
      return;
    }
    ComplexReceiver receiver = ((ComplexReceiverProvider) view).getComplexReceiver();
    receiver.getImageReceiver(IMAGE_RECEIVER_KEY).requestFile(sticker.isAnimated() ? sticker.getImage() : sticker.getFullImage());
    receiver.getGifReceiver(GIF_RECEIVER_KEY).requestFile(sticker.getPreviewAnimation());
  }

  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    if (entry.customEmojiId != customEmojiId || entry.isNotFound()) {
      return;
    }
    tdlib.ui().post(() -> {
      setSticker(entry);
      View view = boundView != null ? boundView.get() : null;
      if (view != null) {
        requestFiles(view);
        view.invalidate();
      }
    });
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    int size = Screen.dp(30f);
    int left = centered ? (view.getMeasuredWidth() - size) / 2 : Screen.dp(15f);
    if (org.thunderdog.challegram.core.Lang.rtl()) {
      left = centered ? left : view.getMeasuredWidth() - left - size;
    }
    int top = (view.getMeasuredHeight() - size) / 2;
    float centerX = left + size / 2f;
    float centerY = top + size / 2f;

    if (selected) {
      c.drawCircle(centerX, centerY, size / 2f + Screen.dp(3f),
        Paints.getProgressPaint(Theme.getColor(ColorId.iconActive), Screen.dp(2f)));
    }
    c.drawCircle(centerX, centerY, size / 2f, Paints.fillingPaint(color));
    if (sticker != null && view instanceof ComplexReceiverProvider) {
      ComplexReceiver receiver = ((ComplexReceiverProvider) view).getComplexReceiver();
      ImageReceiver imageReceiver = receiver.getImageReceiver(IMAGE_RECEIVER_KEY);
      GifReceiver gifReceiver = receiver.getGifReceiver(GIF_RECEIVER_KEY);
      float scale = sticker.getDisplayScale();
      DrawAlgorithms.drawReceiver(c, imageReceiver, gifReceiver, false, true,
        left, top, left + size, top + size, scale, scale);
    } else if (general) {
      Paint paint = Paints.getMediumTextPaint(19f, 0xffffffff, false);
      Paint.FontMetrics metrics = paint.getFontMetrics();
      c.drawText("#", centerX - paint.measureText("#") / 2f,
        centerY - (metrics.ascent + metrics.descent) / 2f, paint);
    } else {
      Drawables.drawCentered(c, forumIcon, centerX, centerY, Paints.whitePorterDuffPaint());
    }

    if (pinned) {
      float pinX = left + size - Screen.dp(1f);
      float pinY = top + Screen.dp(2f);
      c.drawCircle(pinX, pinY, Screen.dp(8f), Paints.fillingPaint(Theme.fillingColor()));
      Drawables.drawCentered(c, pinIcon, pinX, pinY, Paints.getIconGrayPorterDuffPaint());
    }
    if (closed) {
      float lockX = left + size - Screen.dp(1f);
      float lockY = top + size - Screen.dp(1f);
      c.drawCircle(lockX, lockY, Screen.dp(8f), Paints.fillingPaint(Theme.fillingColor()));
      Drawables.drawCentered(c, lockIcon, lockX, lockY, Paints.getIconGrayPorterDuffPaint());
    }
  }
}
