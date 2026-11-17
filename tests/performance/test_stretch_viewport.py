"""Stretch-safe viewports preserve geometry, touch ownership and scroll anchors."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

SOURCE = Path(__file__).resolve().parents[2] / 'app/src/main/java/org/thunderdog/challegram/navigation/GlassPageContent.java'


class StretchViewportTest(unittest.TestCase):
    def test_inset_changes_preserve_row_origin_extent_and_header_touches(self):
        source = SOURCE.read_text()
        methods = method(source, 'private void extendViewport (') + '\n' + method(source, 'public boolean dispatchTouchEvent (').replace('@Override', '')
        with tempfile.TemporaryDirectory(prefix='frogram-stretch-') as directory:
            target = Path(directory) / 'StretchTest.java'
            target.write_text(FIXTURE.replace('METHODS', methods))
            compiled = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            ran = subprocess.run([shutil.which('java'), '-cp', directory, 'StretchTest'], capture_output=True, text=True)
            self.assertEqual(ran.returncode, 0, ran.stderr)

    def test_scroll_anchors_round_trip_with_padding_and_row_decoration(self):
        source = (SOURCE.parent.parent / 'ui/RecyclerViewController.java').read_text()
        methods = '\n'.join(method(source, signature) for signature in (
            'protected void saveRecyclerPosition (', 'protected void applySavedRecyclerPosition (',
            'public boolean saveInstanceState (', 'protected int getViewTop ('))
        with tempfile.TemporaryDirectory(prefix='frogram-scroll-anchor-') as directory:
            target = Path(directory) / 'ScrollTest.java'
            target.write_text(SCROLL_FIXTURE.replace('METHODS', methods))
            compiled = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            ran = subprocess.run([shutil.which('java'), '-cp', directory, 'ScrollTest'], capture_output=True, text=True)
            self.assertEqual(ran.returncode, 0, ran.stderr)


FIXTURE = '''import java.util.*;
public class StretchTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class Build {static class VERSION{static int SDK_INT=36;}static class VERSION_CODES{static final int S=31;}}
 static class View {}
 static class ViewGroup {static class LayoutParams {static final int MATCH_PARENT=-1;int height=-1;}static class MarginLayoutParams extends LayoutParams {int topMargin=17,bottomMargin=12;}}
 static class RecyclerView extends View {ViewGroup.LayoutParams params=new ViewGroup.MarginLayoutParams();int padding=12,bottom=24,layouts,childTop=12;ViewGroup.LayoutParams getLayoutParams(){return params;}void setLayoutParams(ViewGroup.LayoutParams p){params=p;layouts++;}void offsetChildrenVertical(int delta){childTop+=delta;}int getPaddingLeft(){return 8;}int getPaddingRight(){return 8;}int getPaddingTop(){return padding;}int getPaddingBottom(){return bottom;}void setPadding(int l,int t,int r,int b){padding=t;bottom=b;}}
 static class ViewController<T> {boolean cards=true;boolean usesSettingsCards(){return cards;}}
 static class MotionEvent {static final int ACTION_DOWN=0;int action;float y;MotionEvent(int a,float y){action=a;this.y=y;}int getActionMasked(){return action;}float getY(){return y;}}
 static class Parent {int touches;boolean dispatchTouchEvent(MotionEvent e){touches++;return true;}}
 static class Page extends Parent {Object tag=new ViewController<>();int overflow,padding=80;Map<View,Integer> viewportInsets=new IdentityHashMap<>();Object getTag(){return tag;}int getPaddingTop(){return padding;}METHODS}
 static void geometry(RecyclerView list){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)list.params;int height=800-p.topMargin-p.bottomMargin;check(p.topMargin+list.padding==29);check(p.topMargin+list.childTop==29);check(height-list.padding-list.bottom==735);}
 public static void main(String[] args){
  Page page=new Page();RecyclerView list=new RecyclerView();geometry(list);
  for(int inset:new int[]{80,80,104,64,0,88}){page.overflow=inset;int calls=list.layouts;page.extendViewport(list);geometry(list);check(list.padding==12+inset);page.extendViewport(list);check(list.layouts==(inset==80&&calls>0?calls:calls+1));}
  RecyclerView profile=new RecyclerView();((ViewController<?>)page.tag).cards=false;page.extendViewport(profile);geometry(profile);check(profile.layouts==0);
  ((ViewController<?>)page.tag).cards=true;RecyclerView wrap=new RecyclerView();wrap.params.height=-2;page.extendViewport(wrap);check(wrap.layouts==0&&wrap.padding==12);
  RecyclerView pager=new RecyclerView();pager.params=new ViewGroup.LayoutParams();page.extendViewport(pager);check(pager.layouts==0);
  Build.VERSION.SDK_INT=30;RecyclerView legacy=new RecyclerView();page.extendViewport(legacy);geometry(legacy);check(legacy.layouts==0);Build.VERSION.SDK_INT=36;
  check(!page.dispatchTouchEvent(new MotionEvent(0,40))&&page.touches==0);
  check(page.dispatchTouchEvent(new MotionEvent(0,80))&&page.touches==1);
  check(page.dispatchTouchEvent(new MotionEvent(2,40))&&page.touches==2);
  page.padding=0;check(page.dispatchTouchEvent(new MotionEvent(0,0))&&page.touches==3);
 }
}'''


SCROLL_FIXTURE = '''import java.util.*;
public class ScrollTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class View {int top;int getTop(){return top;}}
 static class LinearLayoutManager {View view=new View();int position=8,padding,decoration=7;int findFirstVisibleItemPosition(){return position;}View findViewByPosition(int p){return p==position?view:null;}int getDecoratedTop(View v){return v.top-decoration;}void scrollToPositionWithOffset(int p,int offset){position=p;view.top=padding+offset+decoration;}}
 static class RecyclerView {static final int NO_POSITION=-1;LinearLayoutManager manager=new LinearLayoutManager();LinearLayoutManager getLayoutManager(){return manager;}int getPaddingTop(){return manager.padding;}}
 static class Bundle {Map<String,Integer> ints=new HashMap<>();void putInt(String key,int value){ints.put(key,value);}int getInt(String key){return ints.get(key);}}
 static class Parent {boolean saveInstanceState(Bundle state,String prefix){return true;}}
 static class Controller extends Parent {RecyclerView recyclerView=new RecyclerView();int savedRecyclerPosition=-1,savedRecyclerPositionOffset;RecyclerView getRecyclerView(){return recyclerView;}METHODS}
 public static void main(String[] args){
  for(int padding:new int[]{0,33,88,121,323}){
   Controller c=new Controller();LinearLayoutManager lm=c.recyclerView.manager;lm.padding=padding;lm.view.top=padding-19;
   int originalTop=lm.view.top,originalPosition=lm.position;
   for(int pass=0;pass<3;pass++){
    c.saveRecyclerPosition();lm.scrollToPositionWithOffset(11,-51);c.applySavedRecyclerPosition();
    check(lm.position==originalPosition&&lm.view.top==originalTop);
    Bundle state=new Bundle();check(c.saveInstanceState(state,"qa_"));
    lm.scrollToPositionWithOffset(state.getInt("qa_base_scroll_position"),state.getInt("qa_base_scroll_offset"));
    check(lm.position==originalPosition&&lm.view.top==originalTop);
    int offset=c.getViewTop(originalPosition);lm.scrollToPositionWithOffset(originalPosition,offset);check(lm.view.top==originalTop);
   }
  }
 }
}'''
