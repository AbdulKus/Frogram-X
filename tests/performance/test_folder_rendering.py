"""Regressions found while exercising folder editing and scrolling on HWUI."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/org/thunderdog/challegram'


class FolderRenderingTest(unittest.TestCase):
    def run_java(self, fixture, source, signature):
        with tempfile.TemporaryDirectory(prefix='frogram-folder-') as directory:
            target = Path(directory) / 'Regression.java'
            target.write_text(fixture.replace('PRODUCTION', method((JAVA / source).read_text(), signature).replace('@Override', '')))
            compiled = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            ran = subprocess.run([shutil.which('java'), '-cp', directory, 'Regression'], capture_output=True, text=True)
            self.assertEqual(ran.returncode, 0, ran.stderr)

    def test_hardware_overflow_is_not_culled_by_recording_bounds(self):
        self.run_java(CLIPPING, 'widget/SettingsCards.java', 'public boolean clipChild (')

    def test_folder_paste_and_selection_replacement_preserve_codepoints(self):
        self.run_java(LIMIT, 'util/CodePointLimitFilter.java', 'public CharSequence filter (')

    def test_connection_indicator_keeps_modern_system_icons_visible(self):
        self.run_java(BARS, 'widget/NetworkStatusBarView.java', 'private void checkLowProfile (')

    def test_chip_touch_coordinates_follow_the_padded_drawing_origin(self):
        self.run_java(TOUCHES, 'ui/SelectChatsController.java', 'public boolean onTouchEvent (')


CLIPPING = '''import java.util.Arrays;
public class Regression {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static class NonNull {}
 static class Canvas {enum EdgeType{AA}boolean hardware;int rejected,clips;boolean isHardwareAccelerated(){return hardware;}boolean quickReject(RectF r,EdgeType t){rejected++;return true;}void clipPath(Path p){clips++;}}
 static class RectF {void set(float l,float t,float r,float b){}}
 static class Path {enum Direction{CW}void reset(){}void addRoundRect(RectF r,float[] radii,Direction d){}}
 static class View {float getX(){return 8;}float getY(){return -80;}int getWidth(){return 384;}int getHeight(){return 68;}}
 static class SeparatorView extends View {void setCardStyle(){}void setOffsets(float l,float r){}}
 static class RecyclerView {int position=1;int getChildAdapterPosition(View v){return position;}}
 static class ListItem {static final int TYPE_SHADOW_TOP=1,TYPE_SHADOW_BOTTOM=2,TYPE_EMPTY_OFFSET=3,TYPE_SEPARATOR_FULL=4;int getIconResource(){return 1;}}
 static class Adapter {int type=9;int getItemCount(){return 4;}int getItemViewType(int p){return type;}ListItem getItem(int p){return new ListItem();}}
 static class Screen {static int dp(float v){return (int)v;}}
 static class GlassStyle {static final float RADIUS_DP=26;}
 static class Cards {Adapter adapter=new Adapter();RectF bounds=new RectF();float[] radii=new float[8];Path clip=new Path();boolean partAt(int p){return true;}PRODUCTION}
 public static void main(String[] args){
  Cards cards=new Cards();View row=new View();RecyclerView list=new RecyclerView();Canvas hardware=new Canvas();hardware.hardware=true;
  check(cards.clipChild(hardware,row,list)&&hardware.rejected==0&&hardware.clips==1);
  Canvas software=new Canvas();check(!cards.clipChild(software,row,list)&&software.rejected==1&&software.clips==0);
  cards.adapter.type=ListItem.TYPE_SHADOW_TOP;check(!cards.clipChild(hardware,row,list));
  list.position=-1;check(cards.clipChild(hardware,row,list));
 }
}'''

LIMIT = '''public class Regression {
 interface Spanned extends CharSequence {}
 static class Dest implements Spanned {String s;Dest(String s){this.s=s;}public int length(){return s.length();}public char charAt(int p){return s.charAt(p);}public CharSequence subSequence(int a,int b){return s.subSequence(a,b);}}
 static class Filter {int limit;Filter(int n){limit=n;}PRODUCTION}
 static void same(String expected,CharSequence actual){if(actual==null||!expected.contentEquals(actual))throw new AssertionError(expected+" != "+actual);}
 public static void main(String[] args){
  Filter folder=new Filter(12);same("QA Folder ed",folder.filter("QA Folder edited",0,16,new Dest(""),0,0));
  Filter shortName=new Filter(3);same("a😀b",shortName.filter("a😀b😁c",0,7,new Dest(""),0,0));
  same("🙂",shortName.filter("🙂🙂",0,4,new Dest("abc"),2,3));
  same("XY",new Filter(4).filter("0XYZa",1,4,new Dest("abc"),1,2));
  same("",shortName.filter("z",0,1,new Dest("abc"),1,1));
  if(folder.filter("QA Renamed",0,10,new Dest("old name"),0,8)!=null)throw new AssertionError("valid replacement changed");
 }
}'''

BARS = '''public class Regression {
 static class Build {static class VERSION {static int SDK_INT;}static class VERSION_CODES{static final int R=30;}}
 static class Status {boolean isPaused,isVisible,low;float factor;void setLowProfile(boolean v){low=v;}PRODUCTION}
 public static void main(String[] args){
  for(int api:new int[]{29,30,35,36,37})for(boolean paused:new boolean[]{false,true})for(boolean visible:new boolean[]{false,true})for(float factor:new float[]{0,.5f,1}){
   Build.VERSION.SDK_INT=api;Status status=new Status();status.isPaused=paused;status.isVisible=visible;status.factor=factor;status.checkLowProfile();
   if(status.low!=(api<30&&!paused&&(visible||factor!=0)))throw new AssertionError("system icons dimmed on "+api);
  }
 }
}'''

TOUCHES = '''public class Regression {
 static class MotionEvent {float x,y;MotionEvent(float x,float y){this.x=x;this.y=y;}void offsetLocation(float dx,float dy){x+=dx;y+=dy;}}
 static class ClickHelper {float x,y;boolean fail;boolean onTouchEvent(Object view,MotionEvent event){x=event.x;y=event.y;if(fail)throw new IllegalStateException();return x>=0&&y>=0;}}
 static class Group {Object delegate=new Object();ClickHelper clickHelper=new ClickHelper();int getPaddingLeft(){return 16;}int getPaddingTop(){return 12;}PRODUCTION}
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  Group group=new Group();MotionEvent event=new MotionEvent(41,28);check(group.onTouchEvent(event));check(group.clickHelper.x==25&&group.clickHelper.y==16&&event.x==41&&event.y==28);
  event=new MotionEvent(10,14);check(!group.onTouchEvent(event)&&event.x==10&&event.y==14);
  group.delegate=null;check(!group.onTouchEvent(event)&&event.x==10&&event.y==14);
  group.delegate=new Object();group.clickHelper.fail=true;try{group.onTouchEvent(event);throw new AssertionError();}catch(IllegalStateException expected){}check(event.x==10&&event.y==14);
 }
}'''
