package org.thunderdog.challegram.ui;

import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.MessageRevisionStore.Revision;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import me.vkryl.core.ColorUtils;

final class MessageHistoryDialog {
  static void show (MessagesController owner, List<Revision> revisions) {
    MessageDialogStyle panel = new MessageDialogStyle(owner, Lang.getString(R.string.FrogramEditHistory), false);
    LinearLayout list = new LinearLayout(owner.context());
    list.setOrientation(LinearLayout.VERTICAL);
    DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Lang.dateFormatLocale());
    for (int i = 0; i < revisions.size(); i++) {
      Revision revision = revisions.get(i);
      LinearLayout card = new LinearLayout(owner.context());
      card.setOrientation(LinearLayout.VERTICAL);
      int padding = Screen.dp(12);
      card.setPadding(padding, padding, padding, padding);
      card.setBackground(MessageDialogStyle.rounded(ColorUtils.alphaColor(.07f, Theme.textAccentColor()), 14));
      String date = format.format(new Date(revision.observed));
      card.addView(panel.label(i == 0 ? Lang.getString(R.string.FrogramHistoryLatest, date) : date, 12, Theme.textDecentColor(), true));
      TextView text = panel.label(revision.text, 16, Theme.textAccentColor(), false);
      text.setTextIsSelectable(true);
      LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
      textParams.topMargin = Screen.dp(6);
      card.addView(text, textParams);
      LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
      if (i > 0) cardParams.topMargin = Screen.dp(8);
      list.addView(card, cardParams);
    }
    panel.addScrollable(list, 14, 420);
    panel.add(panel.button(Lang.getOK(), Theme.textAccentColor(), false, panel::dismiss), 12);
    panel.show();
  }
}
