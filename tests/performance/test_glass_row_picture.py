"""Execute production row recording: invalidation, reuse, recycling and failure."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class GlassRowPictureTest(unittest.TestCase):
    def test_recording_tracks_content_instead_of_scroll_position(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/component/dialogs/ChatView.java').read_text()
        signatures = ['public void drawForGlass (', 'public void invalidate ()',
                      'public void invalidate (Rect dirty)',
                      'public void invalidate (int left, int top, int right, int bottom)',
                      'public boolean invalidateContent (', 'public void invalidateEmojiStatusReceiver (']
        methods = '\n'.join(method(source, signature) for signature in signatures).replace('@Nullable ', '')
        with tempfile.TemporaryDirectory(prefix='frogram-glass-row-') as temp:
            target = Path(temp) / 'GlassRowRegression.java'
            target.write_text(FIXTURE.replace('PRODUCTION', methods))
            result = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', temp, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r'''
public class GlassRowRegression {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static class Rect{}
 static class Text{}static class TextMedia{}
 static class Canvas{boolean hardware;int pictures;Picture last;boolean isHardwareAccelerated(){return hardware;}void drawPicture(Picture p){check(!p.open,"unfinished recording");pictures++;last=p;}}
 static class Picture{static int allocations,ends;int width,height;boolean open;Picture(){allocations++;}int getWidth(){return width;}int getHeight(){return height;}Canvas beginRecording(int w,int h){width=w;height=h;open=true;return new Canvas();}void endRecording(){open=false;ends++;}}
 static class View{void invalidate(){}void invalidate(Rect r){}void invalidate(int l,int t,int r,int b){}}
 static class Row extends View{
  Picture glassPicture;boolean glassPictureDirty=true,updateDuringDraw,fail;int width=1080,height=216,records;Object chat=new Object();
  void requestTextContent(){}void requestEmojiStatus(){}void requestReactionFiles(){}
  int getMeasuredWidth(){return width;}int getMeasuredHeight(){return height;}
  void onDraw(Canvas c){records++;if(updateDuringDraw)invalidate();if(fail)throw new IllegalStateException("source draw failed");}
  PRODUCTION
 }
 public static void main(String[] args){
  Row r=new Row();Canvas c=new Canvas();r.drawForGlass(c);Picture first=c.last;
  for(int i=0;i<100;i++)r.drawForGlass(c);
  check(r.records==1&&Picture.allocations==1&&c.pictures==101,"scrolling re-shaped an unchanged row");
  r.invalidate();r.drawForGlass(c);check(r.records==2&&c.last!=first,"avatar/text invalidation kept stale contents");
  r.invalidate(new Rect());r.drawForGlass(c);check(r.records==3,"drawable rectangle invalidation ignored");
  r.invalidate(1,2,3,4);r.drawForGlass(c);check(r.records==4,"partial invalidation ignored");
  r.width=800;r.drawForGlass(c);check(r.records==5&&c.last.width==800,"resize replayed old geometry");
  r.glassPicture=null;r.drawForGlass(c);check(r.records==6,"reattached or rebound row reused detached images");
  r.updateDuringDraw=true;r.invalidate();r.drawForGlass(c);r.drawForGlass(c);
  check(r.records==8&&r.glassPictureDirty,"image/animation update during recording was lost");
  r.updateDuringDraw=false;r.fail=true;int draws=c.pictures,ends=Picture.ends;
  try{r.drawForGlass(c);throw new AssertionError("failure swallowed");}catch(IllegalStateException expected){}
  check(Picture.ends==ends+1&&r.glassPictureDirty&&c.pictures==draws,"failed recording leaked or became visible");
  r.fail=false;r.drawForGlass(c);check(!r.glassPictureDirty,"recording did not recover");
  c.hardware=true;int before=Picture.allocations;r.drawForGlass(c);check(before==Picture.allocations,"hardware draw must preserve receiver/rendering behavior");
  c.hardware=false;r.width=0;draws=c.pictures;r.drawForGlass(c);check(c.pictures==draws,"unmeasured rows must not allocate recordings");
  Row content=new Row();content.drawForGlass(c);Picture original=c.last;
  check(!content.invalidateContent(new Object()),"unrelated chat content must not invalidate this row");content.drawForGlass(c);check(c.last==original,"unrelated chat discarded this row's recording");
  check(content.invalidateContent(content.chat),"content update was not handled");content.drawForGlass(c);check(c.last!=original,"text-only update with unchanged media kept stale glyphs");
  original=c.last;content.invalidateEmojiStatusReceiver(new Text(),null);content.drawForGlass(c);check(c.last!=original,"emoji status refresh with unchanged receiver kept stale content");
 }
}
'''
