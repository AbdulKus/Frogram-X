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
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.lambda.Destroyable;

public class TGMessageRichMessage extends TGMessage {
  private static final int MAX_PREVIEW_BLOCKS = 32;
  private static final int MAX_PREVIEW_TEXT_HEIGHT = 1000;
  private TdApi.RichMessage richMessage;
  private List<PageBlock> blocks;
  private final ArrayList<PreviewBlock> visibleBlocks = new ArrayList<>();
  private boolean hasMoreBlocks;
  private int contentWidth, contentHeight, actionTop;
  private @Nullable Text openAction;
  private @Nullable PreviewBlock activeBlock;
  private final ClickHelper openClickHelper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      return openAction != null &&
        x >= getContentX() && x <= getContentX() + contentWidth &&
        y >= getContentY() + actionTop && y < getContentY() + contentHeight;
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      InstantViewController controller = new InstantViewController(context(), tdlib());
      controller.setArguments(InstantViewController.Args.forRichMessage(msg, richMessage));
      navigateTo(controller);
    }
  });

  public TGMessageRichMessage (MessagesManager context, TdApi.Message msg, TdApi.MessageRichMessage content) {
    super(context, msg);
    setRichMessage(content.message);
  }

  private void setRichMessage (TdApi.RichMessage message) {
    if (blocks != null) {
      destroyBlocks();
    }
    this.richMessage = message;
    ArrayList<TdApi.PageBlock> preview = new ArrayList<>();
    hasMoreBlocks = !message.isFull;
    for (TdApi.PageBlock block : message.blocks) {
      if (preview.size() == MAX_PREVIEW_BLOCKS) {
        hasMoreBlocks = true;
        break;
      }
      if (block instanceof TdApi.PageBlockCover) {
        block = ((TdApi.PageBlockCover) block).cover;
      }
      if (isPreviewBlock(block)) {
        preview.add(block);
      } else {
        hasMoreBlocks = true;
      }
    }
    blocks = PageBlock.parsePreview(messagesController(), preview.toArray(new TdApi.PageBlock[0]), message.isRtl,
      button -> PageBlockButtons.open(messagesController(), msg, button));
    activeBlock = null;
    for (View view : currentViews) {
      if (view instanceof MessageView) {
        for (PageBlock block : blocks) {
          block.attachToView(view);
        }
      }
    }
  }

  private static boolean isPreviewBlock (TdApi.PageBlock block) {
    switch (block.getConstructor()) {
      case TdApi.PageBlockTitle.CONSTRUCTOR:
      case TdApi.PageBlockSubtitle.CONSTRUCTOR:
      case TdApi.PageBlockHeader.CONSTRUCTOR:
      case TdApi.PageBlockSubheader.CONSTRUCTOR:
      case TdApi.PageBlockSectionHeading.CONSTRUCTOR:
      case TdApi.PageBlockKicker.CONSTRUCTOR:
      case TdApi.PageBlockParagraph.CONSTRUCTOR:
      case TdApi.PageBlockPreformatted.CONSTRUCTOR:
      case TdApi.PageBlockFooter.CONSTRUCTOR:
      case TdApi.PageBlockPullQuote.CONSTRUCTOR:
      case TdApi.PageBlockExpandableBlockQuote.CONSTRUCTOR:
      case TdApi.PageBlockThinking.CONSTRUCTOR:
      case TdApi.PageBlockMathematicalExpression.CONSTRUCTOR:
      case TdApi.PageBlockPhoto.CONSTRUCTOR:
      case TdApi.PageBlockVideo.CONSTRUCTOR:
      case TdApi.PageBlockAnimation.CONSTRUCTOR:
      case TdApi.PageBlockSlideshow.CONSTRUCTOR:
      case TdApi.PageBlockCollage.CONSTRUCTOR:
      case TdApi.PageBlockButtonRow.CONSTRUCTOR:
      case TdApi.PageBlockDivider.CONSTRUCTOR:
      case TdApi.PageBlockAnchor.CONSTRUCTOR:
        return true;
      case TdApi.PageBlockBlockQuote.CONSTRUCTOR:
        return isSimpleGroup(((TdApi.PageBlockBlockQuote) block).blocks);
      case TdApi.PageBlockList.CONSTRUCTOR: {
        TdApi.PageBlockListItem[] items = ((TdApi.PageBlockList) block).items;
        if (items.length > 6) {
          return false;
        }
        for (TdApi.PageBlockListItem item : items) {
          if (!isSimpleGroup(item.blocks)) {
            return false;
          }
        }
        return true;
      }
      case TdApi.PageBlockTable.CONSTRUCTOR: {
        TdApi.PageBlockTable table = (TdApi.PageBlockTable) block;
        if (table.cells.length > 5) {
          return false;
        }
        for (TdApi.PageBlockTableCell[] row : table.cells) {
          int columns = 0;
          for (TdApi.PageBlockTableCell cell : row) {
            columns += cell.colspan;
          }
          if (columns > 4) {
            return false;
          }
        }
        return true;
      }
      default:
        return false;
    }
  }

  private static boolean isSimpleGroup (TdApi.PageBlock[] blocks) {
    if (blocks.length > 3) {
      return false;
    }
    for (TdApi.PageBlock block : blocks) {
      if (!(block instanceof TdApi.PageBlockParagraph) && !(block instanceof TdApi.PageBlockPreformatted)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void buildContent (int maxWidth) {
    visibleBlocks.clear();
    if (openAction != null) {
      openAction.performDestroy();
      openAction = null;
    }
    contentWidth = contentHeight = 0;
    activeBlock = null;
    if (maxWidth <= 0) {
      return;
    }
    boolean needsOpen = hasMoreBlocks;
    int textHeight = 0;
    long mediaOffset = 0;
    for (PageBlock block : blocks) {
      int height = block.getHeight(null, maxWidth);
      boolean countsTowardsTextHeight = block instanceof PageBlockRichText || block instanceof PageBlockTable;
      int gap = contentHeight == 0 || block.mergeTop ? 0 : Screen.dp(3);
      if (block instanceof PageBlockTable && block.getCustomWidth() > maxWidth) {
        needsOpen = true;
        continue;
      }
      if (countsTowardsTextHeight) {
        textHeight += block.getContentHeight();
        if (textHeight > Screen.dp(MAX_PREVIEW_TEXT_HEIGHT)) {
          needsOpen = true;
          break;
        }
      }
      if (height == 0) {
        continue;
      }
      int width = block instanceof PageBlockRichText ? ((PageBlockRichText) block).getInlineWidth() : maxWidth;
      contentWidth = Math.min(maxWidth, Math.max(contentWidth, width));
      contentHeight += gap;
      if (block instanceof PageBlockMedia) {
        mediaOffset -= ((PageBlockMedia) block).getInlineReceiverCount();
      }
      visibleBlocks.add(new PreviewBlock(block, contentHeight, height, mediaOffset));
      contentHeight += height;
      if (block instanceof PageBlockRichText && ((PageBlockRichText) block).isEllipsized()) {
        needsOpen = true;
        break;
      }
    }
    if (needsOpen || visibleBlocks.isEmpty()) {
      actionTop = contentHeight;
      openAction = new Text.Builder(Lang.getString(R.string.OpenRichMessage), maxWidth,
        PageBlockRichText.getCaptionProvider(), TextColorSets.Regular.LINK).allBold().maxLineCount(2).build();
      contentWidth = Math.max(contentWidth, openAction.getWidth());
      contentHeight += openAction.getHeight() + Screen.dp(12);
    }
    for (PreviewBlock item : visibleBlocks) {
      item.block.getHeight(null, contentWidth);
    }
    invalidateContentReceiver();
  }

  @Override
  protected int getContentWidth () {
    return contentWidth;
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  @Override
  public boolean needComplexReceiver () {
    return true;
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    for (PageBlock block : blocks) {
      if (attached) {
        block.attachToView(view);
      } else {
        block.detachFromView(view);
      }
    }
  }

  @Override
  public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int invalidateArg) {
    int offset = 0;
    long mediaOffset = 0;
    for (PreviewBlock item : visibleBlocks) {
      offset = item.block.requestInlineIcons(receiver, offset);
      if (item.block instanceof PageBlockMedia) {
        ((PageBlockMedia) item.block).requestInlineMedia(receiver, item.mediaKey);
        mediaOffset = item.mediaKey;
      }
    }
    receiver.clearReceiversWithHigherKey(offset);
    receiver.clearReceiversRange(Long.MIN_VALUE, mediaOffset);
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    for (PreviewBlock item : visibleBlocks) {
      if (item.block instanceof PageBlockMedia) {
        item.block.autoDownloadContent();
      }
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas canvas, int startX, int startY, int maxWidth, ComplexReceiver receiver) {
    for (PreviewBlock item : visibleBlocks) {
      int save = Views.save(canvas);
      canvas.translate(startX, startY + item.top);
      if (item.block instanceof PageBlockMedia) {
        ((PageBlockMedia) item.block).drawInlineMedia(view, canvas, receiver, item.mediaKey);
      } else {
        item.block.draw(view, canvas, null, null, receiver);
      }
      Views.restore(canvas, save);
    }
    if (openAction != null) {
      openAction.draw(canvas, startX, startX + contentWidth, 0, startY + actionTop + Screen.dp(6), null, 1f, null);
    }
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent event) {
    if (super.onTouchEvent(view, event) || openClickHelper.onTouchEvent(view, event)) {
      return true;
    }
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      activeBlock = null;
      float x = event.getX() - getContentX();
      float y = event.getY() - getContentY();
      if (x >= 0 && x < contentWidth) {
        for (PreviewBlock item : visibleBlocks) {
          if (y >= item.top && y < item.top + item.height && dispatchBlockTouch(view, event, item)) {
            activeBlock = item;
            return true;
          }
        }
      }
    } else if (activeBlock != null) {
      boolean handled = dispatchBlockTouch(view, event, activeBlock);
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        activeBlock = null;
      }
      return handled;
    }
    return false;
  }

  public boolean isTouchingGallery () {
    return activeBlock != null && activeBlock.block instanceof PageBlockMedia &&
      ((PageBlockMedia) activeBlock.block).isInlineSlideshow();
  }

  private boolean dispatchBlockTouch (MessageView view, MotionEvent event, PreviewBlock item) {
    int x = getContentX();
    int y = getContentY() + item.top;
    event.offsetLocation(-x, -y);
    try {
      return item.block.onTouchEvent(view, event);
    } finally {
      event.offsetLocation(x, y);
    }
  }

  @Override
  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent content) {
    return content instanceof TdApi.MessageRichMessage;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent content, boolean isBottomMessage) {
    if (!(content instanceof TdApi.MessageRichMessage)) {
      return false;
    }
    setRichMessage(((TdApi.MessageRichMessage) content).message);
    rebuildAndUpdateContent();
    return true;
  }

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent content, boolean isBottomMessage) {
    return updateMessageContent(message, content, isBottomMessage);
  }

  private void destroyBlocks () {
    for (PageBlock block : blocks) {
      for (View view : currentViews) {
        block.detachFromView(view);
      }
      if (block instanceof PageBlockRichText) {
        ((PageBlockRichText) block).destroyInlineText();
      } else if (block instanceof PageBlockMedia) {
        ((PageBlockMedia) block).destroyInlineMedia();
      } else if (block instanceof Destroyable) {
        ((Destroyable) block).performDestroy();
      }
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    destroyBlocks();
    if (openAction != null) {
      openAction.performDestroy();
    }
  }

  private static final class PreviewBlock {
    final PageBlock block;
    final int top, height;
    final long mediaKey;

    PreviewBlock (PageBlock block, int top, int height, long mediaKey) {
      this.block = block;
      this.top = top;
      this.height = height;
      this.mediaKey = mediaKey;
    }
  }
}
