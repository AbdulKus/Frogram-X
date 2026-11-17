"""A page may be moved from a margin host into a ViewPager's non-margin host."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class PagerInsetsTest(unittest.TestCase):
    def test_margin_and_pager_hosts_preserve_content_origin(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/navigation/GlassPageContent.java').read_text()
        update = method(source, 'private void updateInset (')
        with tempfile.TemporaryDirectory(prefix='frogram-pager-insets-') as directory:
            path = Path(directory) / 'InsetTest.java'
            path.write_text(FIXTURE.replace('UPDATE', update))
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class InsetTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class ViewGroup {static class LayoutParams{}static class MarginLayoutParams extends LayoutParams{int top;}}
 static class HeaderView {static int getSize(boolean b){return 56;}}
 static class Views {static void setTopMargin(Page page,int top){((ViewGroup.MarginLayoutParams)page.params).top=top;}}
 static class Page {Object params;int overflow,padding,invalidations;Runnable invalidation=()->invalidations++;Page(Object p){params=p;}Object getLayoutParams(){return params;}void setPadding(int l,int t,int r,int b){padding=t;}UPDATE}
 public static void main(String[] args){
  ViewGroup.MarginLayoutParams margins=new ViewGroup.MarginLayoutParams();Page page=new Page(margins);page.updateInset(24);check(page.overflow==80&&margins.top+page.padding==0&&page.invalidations==1);
  page.updateInset(24);check(page.invalidations==1);page.updateInset(32);check(page.overflow==88&&margins.top+page.padding==0&&page.invalidations==2);
  page.params=new ViewGroup.LayoutParams();page.updateInset(32);check(page.overflow==0&&page.padding==0&&page.invalidations==3);page.updateInset(48);check(page.invalidations==3);
  page.params=margins;page.updateInset(20);check(page.overflow==76&&margins.top+page.padding==0&&page.invalidations==4);
  Page directPager=new Page(new ViewGroup.LayoutParams());directPager.updateInset(24);check(directPager.overflow==0&&directPager.padding==0&&directPager.invalidations==0);
 }
}'''
