"""Regression coverage for stationary holds and content-sized filter surfaces."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2] / 'app/src/main/java/org/thunderdog/challegram'


class FilterHoldTest(unittest.TestCase):
    def test_hold_precedes_folder_options_and_rows_define_width(self):
        tabs = (ROOT / 'navigation/ViewPagerTopView.java').read_text()
        menu = (ROOT / 'widget/ChatFilterMenu.java').read_text()
        code = FIXTURE.replace('LONG_CLICK', method(tabs, 'public boolean onLongClick ('))
        code = code.replace('HOLD_METHODS', '\n'.join(method(tabs, signature) for signature in [
            'public boolean startSlideOffOnHold (', 'public void cancelSlideOff (', 'public void finishSlideOff (',
        ]))
        code = code.replace('MEASURE_METHOD', method(menu, 'protected void onMeasure ('))
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
    static final int ACTION_MOVE=2,ACTION_CANCEL=3;static MotionEvent last;boolean recycled;
    static MotionEvent obtain(long down,long time,int action,float x,float y,int meta){return last=new MotionEvent();}
    void recycle(){recycled=true;}
  }
  static class ViewParent {boolean locked;void requestDisallowInterceptTouchEvent(boolean b){locked=b;}}
  static class LayoutParams {int height=48;}
  static class View {
    static final int VISIBLE=0,GONE=8;ViewParent parent=new ViewParent();boolean pressed=true;int naturalWidth,visibility,measuredWidth;
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
  public static void main(String[] args){
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
