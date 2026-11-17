"""Management tab and participant origins must agree throughout pinning."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

SOURCE = Path(__file__).resolve().parents[2] / 'app/src/main/java/org/thunderdog/challegram/ui/ProfileController.java'


class ManagementTabsTest(unittest.TestCase):
    def test_pinning_range_and_both_gaps_share_one_origin(self):
        source = SOURCE.read_text()
        methods = '\n'.join(method(source, signature) for signature in (
            'private int managementPanelTop (', 'private int managementScrollTail (', 'private int managementPinnedTop (',
            'private int maxItemsScrollY (', 'public int maxItemsScrollYOffset (', 'private int contentTop ('))
        methods = methods.replace('org.thunderdog.challegram.widget.GlassStyle.', 'GlassStyle.')
        fixture = '''public class ManagementTest {
 static class Screen {static int dp(float n){return Math.round(n);}}
 static class GlassStyle {static final float HEADER_OVERFLOW_DP=6,HEADER_EDGE_DP=3;}
 static class ShadowView {static int simpleTopShadowHeight(){return 3;}}
 static int total,pager;
 static boolean isEditing(){return true;}
 static int getTopItemsHeight(){return total;}
 static int getPagerTopViewHeight(){return pager;}
 static int getShadowBottomHeight(){return 3;}
 static int getTopViewTopPadding(){return 6;}
 METHODS
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){ManagementTest p=new ManagementTest();
  for(int height:new int[]{180,520,1400})for(int tabs:new int[]{40,48,60}){
   total=height;pager=tabs;
   check(p.managementPanelTop()-(total-pager-getShadowBottomHeight())==6);
   check(p.maxItemsScrollY()==p.maxItemsScrollYOffset());
   for(int viewport:new int[]{400,700,1000})check(total+p.managementScrollTail(viewport)-viewport==p.maxItemsScrollY());
   for(int scroll=0;scroll<=p.maxItemsScrollY();scroll++){
    int panel=p.managementPanelTop()-scroll;
    int rows=p.contentTop()-scroll;
    check(rows-(panel+pager+12)==4);
    check(panel>=p.managementPinnedTop());
   }
   check(p.managementPanelTop()-p.maxItemsScrollY()==p.managementPinnedTop());
  }
 }
}'''.replace('METHODS', methods)
        with tempfile.TemporaryDirectory(prefix='frogram-management-tabs-') as directory:
            target = Path(directory) / 'ManagementTest.java'
            target.write_text(fixture)
            compiled = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            ran = subprocess.run([shutil.which('java'), '-cp', directory, 'ManagementTest'], capture_output=True, text=True)
            self.assertEqual(ran.returncode, 0, ran.stderr)
