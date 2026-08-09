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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

import java.util.ArrayList;
import java.util.List;

public final class RichMessageUtils {
  private static final int MESSAGE_PREVIEW_LENGTH = 700;
  private static final int CHAT_LIST_PREVIEW_LENGTH = 180;
  private static final int MAX_PREVIEW_BLOCKS = 3;

  private RichMessageUtils () { }

  public static TdApi.FormattedText buildMessagePreview (@Nullable TdApi.RichMessage richMessage) {
    List<BlockText> blocks = collect(richMessage, MESSAGE_PREVIEW_LENGTH, MAX_PREVIEW_BLOCKS);
    StringBuilder text = new StringBuilder();
    List<TdApi.TextEntity> entities = new ArrayList<>();
    for (BlockText block : blocks) {
      if (text.length() > 0) {
        text.append("\n\n");
      }
      int start = text.length();
      text.append(block.text);
      if (block.heading && block.text.length() > 0) {
        entities.add(new TdApi.TextEntity(start, block.text.length(), new TdApi.TextEntityTypeBold()));
      }
    }
    if (text.length() == 0) {
      text.append(Lang.getString(R.string.RichMessage));
    }
    text.append("\n\n");
    int actionStart = text.length();
    String action = Lang.getString(R.string.OpenRichMessage);
    text.append(action);
    entities.add(new TdApi.TextEntity(actionStart, action.length(), new TdApi.TextEntityTypeBold()));
    return new TdApi.FormattedText(text.toString(), entities.toArray(new TdApi.TextEntity[0]));
  }

  public static String buildChatListPreview (@Nullable TdApi.RichMessage richMessage) {
    List<BlockText> blocks = collect(richMessage, CHAT_LIST_PREVIEW_LENGTH, 2);
    StringBuilder text = new StringBuilder();
    for (BlockText block : blocks) {
      if (text.length() > 0) {
        text.append(" — ");
      }
      text.append(block.text);
    }
    return text.length() > 0 ? text.toString() : Lang.getString(R.string.RichMessage);
  }

  private static List<BlockText> collect (@Nullable TdApi.RichMessage richMessage, int maxLength, int maxBlocks) {
    List<BlockText> out = new ArrayList<>(maxBlocks);
    if (richMessage != null && richMessage.blocks != null) {
      for (TdApi.PageBlock block : richMessage.blocks) {
        collect(block, out, maxLength, maxBlocks);
        if (out.size() >= maxBlocks || totalLength(out) >= maxLength) {
          break;
        }
      }
    }
    if (!out.isEmpty() && totalLength(out) > maxLength) {
      BlockText last = out.get(out.size() - 1);
      int overflow = totalLength(out) - maxLength;
      int end = Math.max(1, last.text.length() - overflow - 1);
      out.set(out.size() - 1, new BlockText(last.text.substring(0, end).trim() + "…", last.heading));
    }
    return out;
  }

  private static int totalLength (List<BlockText> blocks) {
    int length = 0;
    for (BlockText block : blocks) {
      length += block.text.length() + (length == 0 ? 0 : 2);
    }
    return length;
  }

  private static void collect (@Nullable TdApi.PageBlock block, List<BlockText> out, int maxLength, int maxBlocks) {
    if (block == null || out.size() >= maxBlocks || totalLength(out) >= maxLength) {
      return;
    }
    switch (block.getConstructor()) {
      case TdApi.PageBlockTitle.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockTitle) block).title, true);
        break;
      case TdApi.PageBlockSubtitle.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockSubtitle) block).subtitle, true);
        break;
      case TdApi.PageBlockHeader.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockHeader) block).header, true);
        break;
      case TdApi.PageBlockSubheader.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockSubheader) block).subheader, true);
        break;
      case TdApi.PageBlockSectionHeading.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockSectionHeading) block).text, true);
        break;
      case TdApi.PageBlockKicker.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockKicker) block).kicker, true);
        break;
      case TdApi.PageBlockParagraph.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockParagraph) block).text, false);
        break;
      case TdApi.PageBlockPreformatted.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockPreformatted) block).text, false);
        break;
      case TdApi.PageBlockFooter.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockFooter) block).footer, false);
        break;
      case TdApi.PageBlockThinking.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockThinking) block).text, false);
        break;
      case TdApi.PageBlockMathematicalExpression.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockMathematicalExpression) block).expression, false);
        break;
      case TdApi.PageBlockPullQuote.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockPullQuote) block).text, false);
        break;
      case TdApi.PageBlockDetails.CONSTRUCTOR: {
        TdApi.PageBlockDetails details = (TdApi.PageBlockDetails) block;
        add(out, details.header, true);
        collect(details.blocks, out, maxLength, maxBlocks);
        break;
      }
      case TdApi.PageBlockBlockQuote.CONSTRUCTOR:
        collect(((TdApi.PageBlockBlockQuote) block).blocks, out, maxLength, maxBlocks);
        break;
      case TdApi.PageBlockList.CONSTRUCTOR: {
        for (TdApi.PageBlockListItem item : ((TdApi.PageBlockList) block).items) {
          collect(item.blocks, out, maxLength, maxBlocks);
          if (out.size() >= maxBlocks) {
            break;
          }
        }
        break;
      }
      case TdApi.PageBlockTable.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockTable) block).caption, true);
        break;
      case TdApi.PageBlockRelatedArticles.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockRelatedArticles) block).header, true);
        break;
      case TdApi.PageBlockChatLink.CONSTRUCTOR:
        add(out, ((TdApi.PageBlockChatLink) block).title, true);
        break;
      case TdApi.PageBlockEmbeddedPost.CONSTRUCTOR:
        collect(((TdApi.PageBlockEmbeddedPost) block).blocks, out, maxLength, maxBlocks);
        break;
      case TdApi.PageBlockCover.CONSTRUCTOR:
        collect(((TdApi.PageBlockCover) block).cover, out, maxLength, maxBlocks);
        break;
      case TdApi.PageBlockPhoto.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockPhoto) block).caption);
        break;
      case TdApi.PageBlockVideo.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockVideo) block).caption);
        break;
      case TdApi.PageBlockAnimation.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockAnimation) block).caption);
        break;
      case TdApi.PageBlockAudio.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockAudio) block).caption);
        break;
      case TdApi.PageBlockVoiceNote.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockVoiceNote) block).caption);
        break;
      case TdApi.PageBlockCollage.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockCollage) block).caption);
        break;
      case TdApi.PageBlockSlideshow.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockSlideshow) block).caption);
        break;
      case TdApi.PageBlockMap.CONSTRUCTOR:
        addCaption(out, ((TdApi.PageBlockMap) block).caption);
        break;
    }
  }

  private static void collect (@Nullable TdApi.PageBlock[] blocks, List<BlockText> out, int maxLength, int maxBlocks) {
    if (blocks == null) {
      return;
    }
    for (TdApi.PageBlock block : blocks) {
      collect(block, out, maxLength, maxBlocks);
      if (out.size() >= maxBlocks || totalLength(out) >= maxLength) {
        break;
      }
    }
  }

  private static void addCaption (List<BlockText> out, @Nullable TdApi.PageBlockCaption caption) {
    if (caption != null) {
      add(out, caption.text, false);
    }
  }

  private static void add (List<BlockText> out, @Nullable TdApi.RichText richText, boolean heading) {
    add(out, richText != null ? TD.getText(richText) : null, heading);
  }

  private static void add (List<BlockText> out, @Nullable String value, boolean heading) {
    if (value == null) {
      return;
    }
    String text = value.replaceAll("\\s+", " ").trim();
    if (!text.isEmpty()) {
      out.add(new BlockText(text, heading));
    }
  }

  private static final class BlockText {
    final String text;
    final boolean heading;

    BlockText (String text, boolean heading) {
      this.text = text;
      this.heading = heading;
    }
  }
}
