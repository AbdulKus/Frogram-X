"""Regression coverage for stationary holds and content-sized filter surfaces."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2] / 'app/src/main/java/org/thunderdog/challegram'


class FilterHoldTest(unittest.TestCase):
    def test_main_hold_folder_options_release_and_width(self):
        tabs = (ROOT / 'navigation/ViewPagerTopView.java').read_text()
        menu = (ROOT / 'widget/ChatFilterMenu.java').read_text()
        controller = (ROOT / 'ui/MainController.java').read_text()
        code = FIXTURE.replace('LONG_CLICK', method(tabs, 'public boolean onLongClick ('))
        code = code.replace('HOLD_METHODS', '\n'.join(method(tabs, signature) for signature in [
            'public boolean startSlideOffOnHold (', 'public void cancelSlideOff (', 'public void finishSlideOff (',
        ]))
        code = code.replace('MEASURE_METHOD', method(menu, 'protected void onMeasure ('))
        code = code.replace('MENU_FINISH', method(controller, 'public void onSlideOffFinish ('))
        code = code.replace('MAIN_PREPARE', method(controller, 'public boolean onSlideOffPrepare ('))
        code = code.replace('FOLDER_OPTIONS', method(controller, 'public boolean onPagerItemLongClick (').replace('@Override', ''))
        with tempfile.TemporaryDirectory(prefix='frogram-filter-hold-') as folder:
            target = Path(folder) / 'FilterHoldRegression.java'
            target.write_text(code)
            compiled = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', folder, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r'''
public class FilterHoldRegression {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  @interface Nullable {}
  static class SystemClock {static long uptimeMillis(){return 1000;}}
  static class MotionEvent {
    static final int ACTION_MOVE=2,ACTION_CANCEL=3;static MotionEvent last;boolean recycled;float x=42,y=20;float getX(){return x;}float getY(){return y;}
    static MotionEvent obtain(long down,long time,int action,float x,float y,int meta){return last=new MotionEvent();}
    void recycle(){recycled=true;}
  }
  static class ViewParent {boolean locked;void requestDisallowInterceptTouchEvent(boolean b){locked=b;}}
  static class LayoutParams {int height=48;}
  static class View {
    static final int VISIBLE=0,GONE=8;ViewParent parent=new ViewParent();boolean pressed=true;int naturalWidth,visibility,measuredWidth;
    int clicks;boolean performClick(){clicks++;return true;}int getWidth(){return 100;}int getHeight(){return 48;}
    LayoutParams params=new LayoutParams();ViewParent getParent(){return parent;}void setPressed(boolean p){pressed=p;}
    int getVisibility(){return visibility;}LayoutParams getLayoutParams(){return params;}int getMeasuredWidth(){return measuredWidth;}
    void measure(int width,int height){int mode=MeasureSpec.getMode(width);measuredWidth=mode==MeasureSpec.EXACTLY?MeasureSpec.getSize(width):mode==MeasureSpec.AT_MOST?Math.min(naturalWidth,MeasureSpec.getSize(width)):naturalWidth;}
  }
  static class OnSlideOffListener {
    boolean prepare=true,consume=true,fail;int opens,finishes;boolean applied;
    boolean onSlideOffPrepare(View v,MotionEvent e,int i){return prepare;}
    boolean onSlideOffLongPress(View v,MotionEvent e,int i){if(fail)throw new IllegalStateException();if(consume)opens++;return consume;}
    void onSlideOffFinish(View v,MotionEvent e,int i,boolean apply){finishes++;applied=apply;}
  }
  interface PagerListener {boolean onPagerItemLongClick(int index);}
  static class Top {
    OnSlideOffListener onSlideOffListener=new OnSlideOffListener();int options;
    PagerListener listener=i->{options++;return true;};
    LONG_CLICK
  }
  static class BackgroundView extends View {
    Top topView;long touchDownTime=99;float touchX=42,touchY=20;int index;boolean inSlideOff,startedByHold;ViewParent lockedParent;
    BackgroundView(Top t){topView=t;}boolean inSlideOff(){return inSlideOff;}
    HOLD_METHODS
  }
  static class MeasureSpec {
    static final int EXACTLY=0x40000000,AT_MOST=0x80000000,UNSPECIFIED=0;
    static int getSize(int spec){return spec&0x3fffffff;}static int getMode(int spec){return spec&0xc0000000;}
    static int makeMeasureSpec(int size,int mode){return size|mode;}
  }
  static class Screen {static int density=1;static int dp(float value){return Math.round(value*density);}}
  static class Items {View[] rows;Items(View... rows){this.rows=rows;}int getChildCount(){return rows.length;}View getChildAt(int i){return rows[i];}}
  static class Frame {
    static final int GONE=8;
    int widthSpec,heightSpec;int getPaddingLeft(){return Screen.dp(6);}int getPaddingRight(){return Screen.dp(6);}
    protected void onMeasure(int w,int h){widthSpec=w;heightSpec=h;}
  }
  static class Menu extends Frame {Items items;Menu(Items items){this.items=items;}MEASURE_METHOD}
  static class TrackingMenu {
    boolean shown=true;int selected=-1,tracks,stops;View row=new View();
    boolean isBubbleVisible(){return shown;}
    int trackPointer(View view,float x,float y){tracks++;return selected;}
    void stopTracking(){stops++;}View getItemAt(int index){return row;}
  }
  static class MenuCallback {
    static final long INVALID_PAGER_ITEM_ID=-1;long selectedPagerItemId=123,closedItem;
    // Also model the former keep-open branch so this release assertion catches it.
    boolean openedByHold=true;TrackingMenu menu=new TrackingMenu();int closes;
    void setMenuVisible(View anchor,long id,boolean show){check(!show,"finish reopened menu");closes++;closedItem=id;if(menu!=null)menu.shown=show;}
    MENU_FINISH
  }
  static class TdApi {static class ChatList {}}
  static class Routing {
    static final long MAIN_PAGER_ITEM_ID=10;long[] ids={10,20,30,40};boolean folders;int options;
    java.util.List<TdApi.ChatList> pagerChatLists=java.util.Arrays.asList(new TdApi.ChatList(),new TdApi.ChatList(),new TdApi.ChatList(),new TdApi.ChatList());
    long getPagerItemId(int index){return ids[index];}boolean hasFolders(){return folders;}void showChatListOptions(TdApi.ChatList list){options++;}
    MAIN_PREPARE
    FOLDER_OPTIONS
  }
  public static void main(String[] args){
    Routing routing=new Routing();
    for(boolean folders:new boolean[]{false,true}){routing.folders=folders;for(int i=0;i<4;i++)check(routing.onSlideOffPrepare(new View(),new MotionEvent(),i)==(i==0),"filters intercepted a folder/archive/calls tab");}
    routing.folders=true;check(routing.onPagerItemLongClick(1)&&routing.options==1,"folder settings did not receive the hold");
    routing.folders=false;check(!routing.onPagerItemLongClick(1)&&routing.options==1,"non-folder page opened folder options");
    MenuCallback release=new MenuCallback();
    release.onSlideOffFinish(new View(),new MotionEvent(),0,true);
    check(!release.menu.shown&&release.closes==1&&release.menu.row.clicks==0,"release over held tab kept filters open");
    check(release.menu.stops==1&&release.closedItem==123&&release.selectedPagerItemId==-1,"release did not clean up the menu target");
    MenuCallback choose=new MenuCallback();choose.menu.selected=3;
    choose.onSlideOffFinish(new View(),new MotionEvent(),0,true);
    check(choose.menu.row.clicks==1&&!choose.menu.shown,"release over row must select once and close");
    MenuCallback cancel=new MenuCallback();cancel.menu.selected=3;
    cancel.onSlideOffFinish(new View(),new MotionEvent(),0,false);
    check(cancel.menu.row.clicks==0&&cancel.menu.tracks==0&&!cancel.menu.shown,"cancel selected a row or retained filters");
    MenuCallback hiddenMenu=new MenuCallback();hiddenMenu.menu.shown=false;hiddenMenu.menu.selected=3;
    hiddenMenu.onSlideOffFinish(new View(),new MotionEvent(),0,true);
    check(hiddenMenu.menu.row.clicks==0,"hidden menu selected a filter");
    MenuCallback absent=new MenuCallback();absent.menu=null;
    absent.onSlideOffFinish(new View(),new MotionEvent(),0,true);
    check(absent.closes==1&&absent.selectedPagerItemId==-1,"missing menu broke gesture cleanup");
    Top top=new Top();BackgroundView tab=new BackgroundView(top);
    check(top.onLongClick(tab),"stationary hold not consumed");
    check(top.onSlideOffListener.opens==1&&top.options==0,"folder options took priority over filters");
    check(tab.inSlideOff&&tab.startedByHold&&tab.parent.locked&&!tab.pressed,"hold did not acquire the held gesture");
    check(MotionEvent.last.recycled,"hold event leaked");
    check(!top.onLongClick(tab)&&top.onSlideOffListener.opens==1,"duplicate hold reopened the menu");
    tab.finishSlideOff(new MotionEvent(),top.onSlideOffListener,true);
    check(!tab.startedByHold&&!tab.inSlideOff&&!tab.parent.locked&&top.onSlideOffListener.applied,"release failed to reset the hold");
    Top blocked=new Top();blocked.onSlideOffListener.prepare=false;BackgroundView noFilter=new BackgroundView(blocked);
    check(blocked.onLongClick(noFilter)&&blocked.options==1&&blocked.onSlideOffListener.opens==0,"ineligible tabs lost their existing long-click behavior");
    Top old=new Top();old.onSlideOffListener.consume=false;check(old.onLongClick(new BackgroundView(old))&&old.options==1,"unhandled hold lost fallback");
    Top bad=new Top();bad.onSlideOffListener.fail=true;try{new BackgroundView(bad).startSlideOffOnHold();throw new AssertionError();}catch(IllegalStateException expected){}
    check(MotionEvent.last.recycled,"exceptional hold leaked its event");
    for(int density:new int[]{1,2,3,4}){
      Screen.density=density;View shortRow=new View(),longRow=new View(),hidden=new View();
      shortRow.naturalWidth=120*density;longRow.naturalWidth=165*density;hidden.naturalWidth=500*density;hidden.visibility=View.GONE;
      Menu menu=new Menu(new Items(shortRow,longRow,hidden));menu.onMeasure(MeasureSpec.makeMeasureSpec(390*density,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(600*density,MeasureSpec.AT_MOST));
      check(MeasureSpec.getMode(menu.widthSpec)==MeasureSpec.EXACTLY,"single scroll/content child can leave a blank strip");
      check(MeasureSpec.getSize(menu.widthSpec)==177*density,"fixed minimum or hidden row widened the menu");
      longRow.naturalWidth=800*density;menu.onMeasure(MeasureSpec.makeMeasureSpec(390*density,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(600*density,MeasureSpec.AT_MOST));
      check(MeasureSpec.getSize(menu.widthSpec)==268*density,"long translation escaped the width cap");
      menu.onMeasure(MeasureSpec.makeMeasureSpec(140*density,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(200*density,MeasureSpec.AT_MOST));
      check(MeasureSpec.getSize(menu.widthSpec)==124*density,"narrow viewport lost outer clearance");
    }
  }
}
'''
