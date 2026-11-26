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

    def test_folder_editor_bottom_offset_preserves_padded_viewport(self):
        editor_src = (JAVA / 'ui/EditChatFolderController.java').read_text()
        decoration = method(editor_src, 'public void getItemOffsets (').replace('@Override', '').replace('@NonNull ', '')
        with tempfile.TemporaryDirectory(prefix='frogram-folder-offset-') as directory:
            target = Path(directory) / 'Regression.java'
            target.write_text(FOLDER_OFFSET.replace('PRODUCTION', decoration))
            compiled = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            ran = subprocess.run([shutil.which('java'), '-cp', directory, 'Regression'], capture_output=True, text=True)
            self.assertEqual(ran.returncode, 0, ran.stderr)

    def test_interface_chats_card_starts_with_rounded_top_and_omits_empty_card(self):
        theme_src = (JAVA / 'ui/SettingsThemeController.java').read_text()
        start = theme_src.index('items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Chats));')
        end = theme_src.index('items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_chatSwipes', start)
        end = theme_src.index(';', end) + 1
        snippet = (
            theme_src[start:end]
            .replace('AppInstallationUtil.isAppSideLoaded(UI.getAppContext())', 'sideLoadedValue')
            .replace('tdlib.canIgnoreSensitiveContentRestriction()', 'canIgnoreSensitive')
            .replace('tdlib.ignoreSensitiveContentRestrictions()', 'ignoreSensitive')
            .replace('Settings.instance().needRestrictContent()', 'needRestrict')
            .replace('Settings.instance().getNewSetting(Settings.SETTING_FLAG_EXPLICIT_DICE)', 'explicitDice')
        )
        with tempfile.TemporaryDirectory(prefix='frogram-theme-chats-') as directory:
            target = Path(directory) / 'Regression.java'
            target.write_text(THEME_CHATS.replace('PRODUCTION', snippet))
            compiled = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            ran = subprocess.run([shutil.which('java'), '-cp', directory, 'Regression'], capture_output=True, text=True)
            self.assertEqual(ran.returncode, 0, ran.stderr)


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
 static class Cards {Adapter adapter=new Adapter();RectF bounds=new RectF();float[] radii=new float[8];Path clip=new Path();boolean reactionTile(int position){return false;} boolean partAt(int p){return true;}PRODUCTION}
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

FOLDER_OFFSET = '''public class Regression {
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 static final boolean DECORATION_LOGGING_ENABLED=false;
 static class Log {static void v(String m,Object... args){}}
 static class Screen {static int dp(float v){return Math.round(v*2.75f);}}
 static class Rect {int bottom;void setEmpty(){bottom=0;}}
 static class View {
  int top,bottom,measuredHeight;
  View(int t,int b){top=t;bottom=b;measuredHeight=b-t;}
  int getTop(){return top;}
  int getBottom(){return bottom;}
  int getMeasuredHeight(){return measuredHeight;}
 }
 static class ListItem {}
 static class SettingHolder {static int measureHeightForType(ListItem item){return 100;}}
 static class Adapter {int count=15;int getItemCount(){return count;}ListItem getItem(int i){return new ListItem();}}
 static class LinearLayoutManager {
  int firstPos=0,lastPos=14;View firstView,lastView;
  int findFirstVisibleItemPosition(){return firstPos;}
  int findLastVisibleItemPosition(){return lastPos;}
  View findViewByPosition(int i){if(i==0)return firstView;if(i==lastPos)return lastView;return new View(0,100);}
 }
 static class RecyclerView {
  static final int NO_POSITION=-1;
  static class State {}
  static class ItemDecoration {}
  LinearLayoutManager manager;int measuredHeight=2340,paddingTop=345,paddingBottom=66;
  RecyclerView(LinearLayoutManager m){manager=m;}
  int getChildAdapterPosition(View v){return manager.lastPos;}
  LinearLayoutManager getLayoutManager(){return manager;}
  int getMeasuredHeight(){return measuredHeight;}
  int getPaddingTop(){return paddingTop;}
  int getPaddingBottom(){return paddingBottom;}
 }
 Adapter adapter=new Adapter();
 RecyclerView recyclerView;
 boolean doneVisible=false;
 boolean isDoneVisible(){return doneVisible;}
 PRODUCTION
 public static void main(String[] args){
  Regression r=new Regression();
  LinearLayoutManager lm=new LinearLayoutManager();
  lm.firstView=new View(345,420);
  lm.lastView=new View(1650,1795); // contentHeight = 1450
  r.recyclerView=new RecyclerView(lm);
  Rect out=new Rect();
  r.getItemOffsets(out,lm.lastView,r.recyclerView,new RecyclerView.State());
  int decoratedBottom=lm.lastView.getBottom()+out.bottom;
  int paddedEnd=r.recyclerView.getMeasuredHeight()-r.recyclerView.getPaddingBottom();
  check(decoratedBottom==paddedEnd,"short list overflowed padded viewport: "+decoratedBottom+" != "+paddedEnd);
  r.doneVisible=true;
  lm.firstPos=1; // partially visible long list (> availableHeight)
  lm.lastPos=29;
  r.adapter.count=30;
  r.getItemOffsets(out,lm.lastView,r.recyclerView,new RecyclerView.State());
  check(out.bottom==Screen.dp(76f),"done button inset missing: "+out.bottom);
 }
}'''

THEME_CHATS = '''import java.util.*;
public class Regression {
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 static class R {
  static class string {static final int Chats=1,DisplaySensitiveContent=2,IgnoreRestrictions=3,UseExplicitDice=4,ChatQuickActions=5;}
  static class id {static final int btn_restrictSensitiveContent=10,btn_ignoreContentRestrictions=11,btn_toggleNewSetting=12,btn_chatSwipes=13;}
 }
 static class Settings {static final long SETTING_FLAG_EXPLICIT_DICE=1L;}
 static class ListItem {
  static final int TYPE_HEADER=1,TYPE_SHADOW_TOP=2,TYPE_SHADOW_BOTTOM=3,TYPE_SEPARATOR_FULL=4,TYPE_RADIO_SETTING=5,TYPE_VALUED_SETTING_COMPACT=6;
  int type,id;
  ListItem(int type){this.type=type;}
  ListItem(int type,int id,int icon,int str){this.type=type;this.id=id;}
  ListItem setLongId(long l){return this;}
 }
 static List<ListItem> build(boolean sideLoadedValue,boolean canIgnoreSensitive,boolean ignoreSensitive,boolean needRestrict,boolean explicitDice){
  List<ListItem> items=new ArrayList<>();
  PRODUCTION
  return items;
 }
 public static void main(String[] args){
  List<ListItem> none=build(false,false,false,true,false);
  check(none.size()==3,"empty card leaked: "+none.size());
  check(none.get(0).type==ListItem.TYPE_HEADER&&none.get(1).type==ListItem.TYPE_SHADOW_TOP&&none.get(2).id==R.id.btn_chatSwipes,"unexpected header/card when hidden");

  for(int mask=1;mask<8;mask++){
   boolean sens=(mask&1)!=0,ign=(mask&2)!=0,dice=(mask&4)!=0;
   List<ListItem> items=build(false,sens,sens,!ign,dice);
   check(items.get(0).type==ListItem.TYPE_HEADER,"missing header");
   check(items.get(1).type==ListItem.TYPE_SHADOW_TOP,"first conditional item must follow TYPE_SHADOW_TOP");
   check(items.get(2).type==ListItem.TYPE_RADIO_SETTING,"missing radio setting after SHADOW_TOP");
   int expectedCount=3+(Integer.bitCount(mask)-1)*2+3;
   check(items.size()==expectedCount,"wrong item count for mask "+mask+": "+items.size());
   check(items.get(items.size()-3).type==ListItem.TYPE_SHADOW_BOTTOM,"missing bottom shadow before chatSwipes");
   check(items.get(items.size()-2).type==ListItem.TYPE_SHADOW_TOP,"missing top shadow for chatSwipes");
   check(items.get(items.size()-1).id==R.id.btn_chatSwipes,"missing chatSwipes");
  }
 }
}'''

