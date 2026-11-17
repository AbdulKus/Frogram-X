"""Floating settings may paint above the header without moving their viewport."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/org/thunderdog/challegram/navigation'


class GlassOverflowTest(unittest.TestCase):
    def test_insets_preserve_geometry_and_extra_rows_are_scoped_to_pages(self):
        content = (JAVA / 'GlassPageContent.java').read_text()
        manager = (JAVA / 'GlassLayoutManager.java').read_text()
        code = FIXTURE.replace('CONTENT_METHODS', '\n'.join(method(content, name) for name in [
            'private void updateInset (', 'public static int extraLayoutSpace ('
        ])).replace('MANAGER_METHOD', method(manager, 'protected void calculateExtraLayoutSpace (').replace('@Override', ''))
        with tempfile.TemporaryDirectory(prefix='frogram-header-overflow-') as directory:
            path = Path(directory) / 'OverflowTest.java'
            path.write_text(code)
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_row_clips_resize_and_restore_on_recycle(self):
        source = (JAVA / 'GlassPageContent.java').read_text()
        methods = '\n'.join(method(source, name) for name in [
            'private void clip (', 'public void onChildViewAttachedToWindow (',
            'public void onChildViewDetachedFromWindow (', 'public void onLayoutChange ('
        ]).replace('@Override', '')
        code = ROW_FIXTURE.replace('ROW_METHODS', methods)
        with tempfile.TemporaryDirectory(prefix='frogram-row-clips-') as directory:
            path = Path(directory) / 'RowClipsTest.java'
            path.write_text(code)
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class OverflowTest {
 @interface NonNull {}
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 static class ViewGroup {static class MarginLayoutParams {}}
 static class View {Object parent;int margin,padding;Object getParent(){return parent;}Object getLayoutParams(){return new ViewGroup.MarginLayoutParams();}}
 static class HeaderView {static int size;static int getSize(boolean offset){return size;}}
 static class Views {static void setTopMargin(View view,int n){view.margin=n;}}
 static class GlassPageContent extends View {
  int overflow,changes;Runnable invalidation=()->changes++;
  void setPadding(int l,int t,int r,int b){padding=t;}
  CONTENT_METHODS
 }
 static class RecyclerView {static class State {}}
 static class BaseManager {protected void calculateExtraLayoutSpace(RecyclerView.State state,int[] space){space[0]=23;space[1]=47;}}
 static class GlassLayoutManager extends BaseManager {View list;MANAGER_METHOD}
 public static void main(String[] args){
  for(float density:new float[]{1,2.75f,3,3.5f}){
   HeaderView.size=Math.round(56*density);
   GlassPageContent page=new GlassPageContent();View group=new View(),list=new View();list.parent=group;group.parent=page;
   GlassLayoutManager manager=new GlassLayoutManager();manager.list=list;
   for(int status:new int[]{0,24,80,0}){
    page.updateInset(status);int originalTop=HeaderView.size+status,originalHeight=2000-originalTop;
    check(originalTop+page.margin+page.padding==originalTop,"original rows moved");
    check(originalHeight-page.margin-page.padding==originalHeight,"original viewport height changed");
    int changes=page.changes;page.updateInset(status);check(changes==page.changes,"unchanged inset schedules another layout");
    int[] space={0,0};manager.calculateExtraLayoutSpace(new RecyclerView.State(),space);
    check(space[0]>=originalTop&&space[1]==47,"not enough rows behind header or changed trailing prefetch");
   }
   group.parent=new View();int[] space={0,0};manager.calculateExtraLayoutSpace(new RecyclerView.State(),space);
   check(space[0]==23&&space[1]==47,"chat or ordinary list acquired settings overflow");
  }
 }
}'''


ROW_FIXTURE = """public class RowClipsTest {
 @interface NonNull {}
 static void check(boolean v){if(!v)throw new AssertionError();}
 static class Rect {
  int l,t,r,b;Rect(){}Rect(Rect other){set(other.l,other.t,other.r,other.b);}void set(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}void setEmpty(){set(0,0,0,0);}
  boolean intersect(Rect other){int left=Math.max(l,other.l),top=Math.max(t,other.t),right=Math.min(r,other.r),bottom=Math.min(b,other.b);if(left>=right||top>=bottom)return false;set(left,top,right,bottom);return true;}
 }
 static class View {int width=100,height=50;Rect clip;Object listener;int getWidth(){return width;}int getHeight(){return height;}Rect getClipBounds(){return clip==null?null:new Rect(clip);}void setClipBounds(Rect r){clip=r==null?null:new Rect(r);}void addOnLayoutChangeListener(Object l){listener=l;}void removeOnLayoutChangeListener(Object l){check(listener==l);listener=null;}}
 static class RowClips {java.util.WeakHashMap<View,Rect> originals=new java.util.WeakHashMap<>();Rect bounds=new Rect();ROW_METHODS}
 public static void main(String[] args){
  RowClips clips=new RowClips();View row=new View();clips.onChildViewAttachedToWindow(row);check(row.clip.r==100&&row.clip.b==50);
  row.width=80;row.height=30;clips.onLayoutChange(row,0,0,80,30,0,0,100,50);check(row.clip.r==80&&row.clip.b==30);
  clips.onChildViewDetachedFromWindow(row);check(row.clip==null&&row.listener==null&&clips.originals.isEmpty());
  Rect original=new Rect();original.set(10,5,90,45);row.clip=new Rect(original);clips.onChildViewAttachedToWindow(row);check(row.clip.l==10&&row.clip.t==5&&row.clip.r==80&&row.clip.b==30);
  row.width=40;row.height=20;clips.onLayoutChange(row,0,0,40,20,0,0,80,30);check(row.clip.l==10&&row.clip.r==40&&row.clip.b==20);
  clips.onChildViewDetachedFromWindow(row);check(row.clip.l==10&&row.clip.r==90&&row.clip.b==45&&clips.originals.isEmpty());
 }
}"""
