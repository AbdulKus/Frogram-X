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
            'private int managementPanelTop (', 'private int managementScrollTail (', 'private int pinnedTabsTop (',
            'private int maxItemsScrollY (', 'public int maxItemsScrollYOffset (', 'public int getItemsBound (',
            'private static int profileListScrollTail (', 'private int contentTop ('))
        methods = methods.replace('org.thunderdog.challegram.widget.GlassStyle.', 'GlassStyle.')
        fixture = '''public class ManagementTest {
 static class Screen {static int dp(float n){return Math.round(n);}}
 static class GlassStyle {static final float HEADER_OVERFLOW_DP=6,HEADER_EDGE_DP=3,PAGE_CLEARANCE_DP=18;}
 static class ShadowView {static int simpleTopShadowHeight(){return 3;}}
 static int total,pager,playerInset;
 static boolean editing=true,tabsVisible=true;
 static boolean isEditing(){return editing;}
 static boolean needTabs(){return tabsVisible;}
 static class ListItem {static final int TYPE_SHADOW_TOP=1;}
 static class SettingHolder {static int measureHeightForType(int type){return 6;}}
 static class Adapter {int type=ListItem.TYPE_SHADOW_TOP;int getItemCount(){return 1;}int getItemViewType(int n){return type;}}
 static class SharedBaseController<T> {Adapter adapter=new Adapter();}
 static class SharedMembersController extends SharedBaseController<Object> {}
 static int getTopItemsHeight(){return total;}
 static int getPagerTopViewHeight(){return pager;}
 static int getShadowBottomHeight(){return 3;}
 static int getTopViewTopPadding(){return 6;}
 METHODS
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){ManagementTest p=new ManagementTest(); SharedMembersController members=new SharedMembersController();
  for(int inset:new int[]{0,46}){playerInset=inset;
   for(int height:new int[]{180,520,1400})for(int tabs:new int[]{40,48,60}){
    total=height;pager=tabs;
    check(p.managementPanelTop()-(total-pager-getShadowBottomHeight())==6);
    check(p.maxItemsScrollY()==p.maxItemsScrollYOffset());
    for(int viewport:new int[]{400,700,1000})check(total+p.managementScrollTail(viewport)-viewport==p.maxItemsScrollY());
    for(int scroll=0;scroll<=p.maxItemsScrollY();scroll++){
     int panel=p.managementPanelTop()-scroll;
     int rows=p.contentTop(members)+6-scroll;
     check(rows-(panel+pager+12)==4);
     check(panel>=p.pinnedTabsTop());
    }
    check(p.managementPanelTop()-p.maxItemsScrollY()==p.pinnedTabsTop());
    for(int participants : new int[]{1,3,20}) {
     int viewport=672;
     int items=participants*68+48;
     int start=p.contentTop(members), shared=p.maxItemsScrollY();
     int tail=profileListScrollTail(viewport,items,start-shared);
     int maxScroll=Math.max(0,start+items+tail-viewport);
     if(participants<20) check(maxScroll==shared);
     else check(maxScroll>shared && tail==0);
    }
    editing=false;
    check(p.contentTop(members)+6-p.maxItemsScrollY()==p.pinnedTabsTop()+p.getItemsBound()+4);
    editing=true; tabsVisible=false;
    check(p.contentTop(members)+6-p.maxItemsScrollY()==18+playerInset);
    tabsVisible=true;
    editing=false;
    int start=p.contentTop(new SharedBaseController<>());
    int clearance=p.pinnedTabsTop()+p.getItemsBound()+4;
    int viewport=672, items=150;
    int tail=profileListScrollTail(viewport,items,Math.max(start-p.maxItemsScrollY(),clearance));
    check(viewport-items-tail>=clearance);
    editing=true;
   }
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
