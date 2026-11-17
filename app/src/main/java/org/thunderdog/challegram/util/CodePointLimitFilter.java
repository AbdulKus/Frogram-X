package org.thunderdog.challegram.util;

import android.text.InputFilter;
import android.text.Spanned;

/** Limits pasted/replaced text by code points, without splitting emoji pairs. */
public final class CodePointLimitFilter implements InputFilter {
  private final int limit;

  public CodePointLimitFilter (int limit) { this.limit = limit; }

  @Override public CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    int remaining = limit - Character.codePointCount(dest, 0, dstart) - Character.codePointCount(dest, dend, dest.length());
    if (remaining <= 0) return "";
    if (Character.codePointCount(source, start, end) <= remaining) return null;
    return source.subSequence(start, Character.offsetByCodePoints(source, start, remaining));
  }
}
