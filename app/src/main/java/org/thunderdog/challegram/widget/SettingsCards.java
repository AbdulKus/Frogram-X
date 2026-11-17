package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import me.vkryl.core.ColorUtils;

/** Opt-in page styling: sheet/dialog adapters and chat lists keep their own layout. */
public final class SettingsCards extends RecyclerView.ItemDecoration {
  private final SettingsAdapter adapter;
  private final Path clip = new Path();
  private final RectF bounds = new RectF();
  private final float[] radii = new float[8];
  private final SettingsCardSurface surface = new SettingsCardSurface();

  private SettingsCards (SettingsAdapter adapter) { this.adapter = adapter; }

  public static void install (View view) { install(view, null); }

  public static void install (View view, ViewController<?> themeProvider) {
    if (view instanceof CustomRecyclerView) {
      CustomRecyclerView list = (CustomRecyclerView) view;
      if (list.getLayoutManager() instanceof LinearLayoutManager &&
          ((LinearLayoutManager) list.getLayoutManager()).getOrientation() == RecyclerView.VERTICAL) {
        list.enableSettingsCards();
        // Row invalidation alone does not redraw the list's decoration display list.
        if (themeProvider != null && list.getSettingsCards() == null) themeProvider.addThemeInvalidateListener(list);
      }
      if (list.getAdapter() instanceof SettingsAdapter && list.getLayoutManager() instanceof LinearLayoutManager &&
          ((LinearLayoutManager) list.getLayoutManager()).getOrientation() == RecyclerView.VERTICAL && list.getSettingsCards() == null) {
        SettingsAdapter adapter = (SettingsAdapter) list.getAdapter();
        adapter.setCardStyle();
        SettingsCards cards = new SettingsCards(adapter);
        list.setSettingsCards(cards);
        list.addItemDecoration(cards);
        if ((adapter.getItems().isEmpty() || adapter.getItemViewType(0) != ListItem.TYPE_EMPTY_OFFSET) && list.getPaddingTop() < Screen.dp(12f)) {
          list.setPadding(list.getPaddingLeft(), Screen.dp(12f), list.getPaddingRight(), list.getPaddingBottom());
        }
        for (int i = 0; i < list.getChildCount(); i++) {
          View child = list.getChildAt(i);
          int position = list.getChildAdapterPosition(child);
          if (position >= 0 && position < adapter.getItemCount()) applyRow(child, adapter.getItemViewType(position));
        }
      }
      return; // Nested carousels manage their own cards and widths.
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) install(group.getChildAt(i), themeProvider);
    }
  }

  public static boolean isRow (int type) {
    if (type <= ListItem.TYPE_CUSTOM || type == ListItem.TYPE_CUSTOM_SINGLE) return true;
    switch (type) {
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_VALUED_SETTING:
      case ListItem.TYPE_INFO_SETTING:
      case ListItem.TYPE_INFO_MULTILINE:
      case ListItem.TYPE_VALUED_SETTING_RED_STUPID:
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE:
      case ListItem.TYPE_CHECKBOX_OPTION:
      case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
      case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
      case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE:
      case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
      case ListItem.TYPE_RADIO_OPTION:
      case ListItem.TYPE_RADIO_OPTION_LEFT:
      case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
      case ListItem.TYPE_VALUED_SETTING_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX:
      case ListItem.TYPE_SLIDER:
      case ListItem.TYPE_SLIDER_BRIGHTNESS:
      case ListItem.TYPE_EDITTEXT:
      case ListItem.TYPE_EDITTEXT_COUNTERED:
      case ListItem.TYPE_EDITTEXT_NO_PADDING:
      case ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE:
      case ListItem.TYPE_EDITTEXT_REUSABLE:
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO:
      case ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION:
      case ListItem.TYPE_SESSION:
      case ListItem.TYPE_SESSION_WITH_AVATAR:
      case ListItem.TYPE_USER:
      case ListItem.TYPE_USER_SMALL:
      case ListItem.TYPE_STICKER_SET:
      case ListItem.TYPE_ARCHIVED_STICKER_SET:
      case ListItem.TYPE_BUTTON:
      case ListItem.TYPE_REACTION_CHECKBOX:
      case ListItem.TYPE_COLOR_PICKER:
      case ListItem.TYPE_COUNTRY:
        return true;
    }
    return false;
  }

  public static boolean isSeparator (int type) { return type == ListItem.TYPE_SEPARATOR || type == ListItem.TYPE_SEPARATOR_FULL; }

  /** Fixed SettingView heights only; editors, sliders and sessions keep their own measurements. */
  public static int fixedHeight (int type) {
    switch (type) {
      case ListItem.TYPE_VALUED_SETTING:
      case ListItem.TYPE_INFO_SETTING:
      case ListItem.TYPE_VALUED_SETTING_WITH_RADIO:
      case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE:
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX:
        return Screen.dp(68f);
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE:
      case ListItem.TYPE_CHECKBOX_OPTION:
      case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
      case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
      case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
      case ListItem.TYPE_RADIO_OPTION:
      case ListItem.TYPE_RADIO_OPTION_LEFT:
      case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
        return Screen.dp(56f);
    }
    return -1;
  }
  private boolean rowAt (int position) { return position >= 0 && position < adapter.getItemCount() && isRow(adapter.getItemViewType(position)); }
  private boolean partAt (int position) {
    return position >= 0 && position < adapter.getItemCount() &&
      (rowAt(position) || isSeparator(adapter.getItemViewType(position)) && (rowAt(position - 1) || rowAt(position + 1)));
  }

  public static void applyRow (View view, int type) {
    if (type == ListItem.TYPE_EMPTY_OFFSET) { view.setBackground(null); return; }
    if (!isRow(type)) return;
    if (view instanceof SettingView) {
      SettingView setting = (SettingView) view;
      setting.setProfileInformationStyle(true);
      setting.setIconColorId(ColorId.glassIcon);
    }
    android.graphics.drawable.Drawable background = view.getBackground();
    if (!(background instanceof ProfileCardBackground) &&
        (background == null || background instanceof android.graphics.drawable.ColorDrawable ||
         background instanceof android.graphics.drawable.RippleDrawable || background instanceof FillingDrawable)) {
      view.setBackground(new ProfileCardBackground());
    }
  }

  @Override public void getItemOffsets (@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    int position = parent.getChildAdapterPosition(view);
    if (partAt(position)) out.left = out.right = Screen.dp(GlassStyle.SIDE_DP);
    else if (position >= 0 && position < adapter.getItemCount()) {
      switch (adapter.getItemViewType(position)) {
        case ListItem.TYPE_HEADER:
        case ListItem.TYPE_HEADER_PADDED:
        case ListItem.TYPE_HEADER_MULTILINE:
        case ListItem.TYPE_DESCRIPTION:
        case ListItem.TYPE_DESCRIPTION_SMALL:
        case ListItem.TYPE_DESCRIPTION_CENTERED:
          out.left = out.right = Screen.dp(GlassStyle.SIDE_DP);
      }
    }
  }

  /** Called by both the screen renderer and software backdrop capture. */
  public boolean clipChild (Canvas canvas, View view, RecyclerView parent) {
    int position = parent.getChildAdapterPosition(view);
    if (position < 0 || position >= adapter.getItemCount()) return true;
    int type = adapter.getItemViewType(position);
    if (type == ListItem.TYPE_SHADOW_TOP || type == ListItem.TYPE_SHADOW_BOTTOM || type == ListItem.TYPE_EMPTY_OFFSET) return false;
    if (!partAt(position)) return true;
    bounds.set(view.getX(), view.getY(), view.getX() + view.getWidth(), view.getY() + view.getHeight());
    if (canvas.quickReject(bounds, Canvas.EdgeType.AA)) return false;
    float radius = Math.min(Screen.dp(GlassStyle.RADIUS_DP), view.getHeight() / 2f);
    java.util.Arrays.fill(radii, 0, 4, partAt(position - 1) ? 0 : radius);
    java.util.Arrays.fill(radii, 4, 8, partAt(position + 1) ? 0 : radius);
    clip.reset();
    clip.addRoundRect(bounds, radii, Path.Direction.CW);
    canvas.clipPath(clip);
    if (view instanceof SeparatorView) {
      SeparatorView separator = (SeparatorView) view;
      separator.forceFillingColor(ProfileCardBackground.fillingColor());
      separator.forceColor(ColorUtils.alphaColor(.10f, Theme.getColor(ColorId.glassText)));
      ListItem next = adapter.getItem(position + 1);
      separator.setOffsets(Screen.dp(type == ListItem.TYPE_SEPARATOR_FULL || next == null || next.getIconResource() == 0 ? 16f : 64f), Screen.dp(20f));
    }
    return true;
  }

  private void drawSurfaces (Canvas canvas, RecyclerView parent, boolean foreground) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      View row = parent.getChildAt(i);
      int position = parent.getChildAdapterPosition(row);
      if (partAt(position)) surface.draw(canvas, row, !partAt(position - 1), !partAt(position + 1), foreground);
    }
  }
  @Override public void onDraw (@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) { drawSurfaces(canvas, parent, false); }
  @Override public void onDrawOver (@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) { drawSurfaces(canvas, parent, true); }
}
