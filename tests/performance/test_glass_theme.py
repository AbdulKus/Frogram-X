"""Derived glass roles must follow theme edits and reuse their cached palette."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2] / 'app/src/main/java/org/thunderdog/challegram'


class GlassThemeTest(unittest.TestCase):
    def test_cached_roles_and_forced_text_theme(self):
        names = ['filling', 'headerBackground', 'textLink', 'text', 'textLight', 'icon',
                 'glassText', 'glassTextSecondary', 'glassIcon', 'glassActive', 'glassPlaceholder']
        with tempfile.TemporaryDirectory(prefix='frogram-glass-theme-') as folder:
            root = Path(folder)
            theme = root / 'org/thunderdog/challegram/theme'
            theme.mkdir(parents=True)
            util = root / 'org/thunderdog/challegram/util'
            util.mkdir(parents=True)
            shutil.copy(ROOT / 'theme/GlassTheme.java', theme)
            shutil.copy(ROOT / 'util/GlassPalette.java', util)
            (theme / 'ColorId.java').write_text('package org.thunderdog.challegram.theme; public interface ColorId {' +
                ''.join(f'int {name}={i + 1};' for i, name in enumerate(names)) + '}')
            (theme / 'ThemeDelegate.java').write_text('package org.thunderdog.challegram.theme; public interface ThemeDelegate {int getColor(int id); boolean isDark(); default int getId(){return 1;}}')
            resolve = method((ROOT / 'theme/Theme.java').read_text(), 'public static int resolveColor (')
            text = method((ROOT / 'util/text/TextColorSetThemed.java').read_text(), 'default int defaultTextColor (')
            (theme / 'Theme.java').write_text('package org.thunderdog.challegram.theme; public class Theme {' + resolve + '}')
            (theme / 'GlassRoleRegression.java').write_text(FIXTURE.replace('TEXT_METHOD', text))
            changed = method((ROOT / 'theme/ThemeManager.java').read_text(), 'public boolean hasColorChanged (').replace('@ColorId ', '')
            (theme / 'ThemeManager.java').write_text('package org.thunderdog.challegram.theme; class ThemeManager {ThemeDelegate currentThemeImpl(boolean fast){return new GlassRoleRegression.Data();}boolean isCustomTheme(int id){return false;}' + changed + '} class ThemeId {static final int TEMPORARY=-1;} class ThemeTemporary extends GlassRoleRegression.Data {ThemeDelegate getFromTheme(){return this;}ThemeDelegate getToTheme(){return this;}} class ThemeCustom extends GlassRoleRegression.Data {boolean hasRecentlyChanged(int id){return false;}}')
            sources = [str(p) for p in root.rglob('*.java')]
            compiled = subprocess.run([shutil.which('javac'), *sources], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', folder,
                'org.thunderdog.challegram.theme.GlassRoleRegression'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''package org.thunderdog.challegram.theme;
public class GlassRoleRegression {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static class Data implements ThemeDelegate {
    int accent=0xff569ace; boolean dark;
    public boolean isDark(){return dark;}
    public int getColor(int id){
      if(id==ColorId.filling)return dark?0xff111111:0xffffffff;
      if(id==ColorId.headerBackground||id==ColorId.textLink)return accent;
      if(id==ColorId.text)return dark?0xffeeeeee:0xff111111;
      if(id==ColorId.textLight)return 0xff888888;
      if(id==ColorId.icon)return 0xff999999;
      return 0; // A derived role must never fall through to the base theme.
    }
  }
  interface TextSet { int defaultTextColorId(); ThemeDelegate colorTheme(); TEXT_METHOD }
  public static void main(String[] args){
    check(new ThemeManager().hasColorChanged(ColorId.glassText),"derived roles ignored an accent/theme update");
    check(!new ThemeManager().hasColorChanged(ColorId.text),"ordinary change detection changed");
    Data first=new Data(); Object palette=GlassTheme.palette(first);
    for(int i=0;i<10000;i++)check(GlassTheme.palette(first)==palette,"unchanged theme allocated a palette");
    int before=Theme.resolveColor(first,ColorId.glassIcon);
    first.accent=0xffcf485c;
    check(GlassTheme.palette(first)!=palette,"in-place theme edit reused stale palette");
    check(Theme.resolveColor(first,ColorId.glassIcon)!=before,"accent edit did not reach ordinary icons");
    Data forced=new Data();forced.accent=0xff309445;
    TextSet text=new TextSet(){public int defaultTextColorId(){return ColorId.glassIcon;}public ThemeDelegate colorTheme(){return forced;}};
    check(text.defaultTextColor()==Theme.resolveColor(forced,ColorId.glassIcon)&&text.defaultTextColor()!=0,"forced text theme bypassed glass role resolution");
    check(Theme.resolveColor(forced,ColorId.text)==forced.getColor(ColorId.text),"ordinary theme colors changed");
    forced.dark=true;
    check(Theme.resolveColor(forced,ColorId.glassIcon)==forced.getColor(ColorId.icon),"dark icon role lost its theme value");
  }
}'''
