"""Exercise the production overlay with TextView gravity and compound padding."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class CustomEmojiAlignmentTest(unittest.TestCase):
    def test_overlay_follows_text_origin_and_restores_its_bounds(self):
        source = (ROOT / "app/src/main/java/org/thunderdog/challegram/emoji/CustomEmojiSpanImpl.java").read_text()
        draw = method(source, "public void onOverlayDraw (").replace("android.widget.TextView", "TextView")
        fixture = FIXTURE.replace("PRODUCTION_METHOD", draw)
        with tempfile.TemporaryDirectory(prefix="frogram-emoji-alignment-") as directory:
            target = Path(directory) / "EmojiAlignmentRegression.java"
            target.write_text(fixture)
            compiled = subprocess.run([shutil.which("javac"), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run([shutil.which("java"), "-cp", directory, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r"""
public class EmojiAlignmentRegression {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static class Canvas {}
  static class Layout {}
  static class View {int getPaddingLeft(){return 3;}int getPaddingTop(){return 5;}}
  static class TextView extends View {
    int top;TextView(int top){this.top=top;}
    int getTotalPaddingLeft(){return 13;}int getTotalPaddingTop(){return top;}
  }
  static class Rect {
    int left=20,top=30,right=40,bottom=50;
    void offset(int x,int y){left+=x;right+=x;top+=y;bottom+=y;}
  }
  static class Entry {boolean missing;boolean isNotFound(){return missing;}}
  static class Size {int getSize(){return 20;}}
  static class Span {
    Entry customEmoji=new Entry();boolean isDestroyed,fail;Size size=new Size();
    View lastHost;int customEmojiSize=20,draws,x,y;Rect drawRect=new Rect();
    void prepareCustomEmoji(int size){customEmojiSize=size;}
    void drawCustomEmoji(Canvas c,View host){lastHost=host;draws++;x=drawRect.left;y=drawRect.top;if(fail)throw new IllegalStateException();}
    PRODUCTION_METHOD
  }
  public static void main(String[] args){
    Canvas c=new Canvas();Layout layout=new Layout();
    for(int offset:new int[]{5,25,45}){
      Span s=new Span();TextView view=new TextView(offset);
      for(int i=0;i<3;i++){
        s.onOverlayDraw(c,view,layout);
        check(s.lastHost==view,"overlay must forward its TextView for tinting");
        check(s.x==33&&s.y==30+offset,"icon must follow compound padding and vertical gravity");
        check(s.drawRect.left==20&&s.drawRect.top==30,"overlay draws must not accumulate offsets");
      }
      s.fail=true;
      try{s.onOverlayDraw(c,view,layout);throw new AssertionError("expected draw failure");}catch(IllegalStateException expected){}
      check(s.drawRect.left==20&&s.drawRect.top==30,"failed draws must restore layout coordinates too");
    }
    Span generic=new Span();generic.onOverlayDraw(c,new View(),layout);
    check(generic.x==23&&generic.y==35,"non-TextView surfaces retain plain padding");
    Span noLayout=new Span();noLayout.onOverlayDraw(c,new TextView(25),null);
    check(noLayout.draws==0,"do not reuse stale positions without a text layout");
    Span missing=new Span();missing.customEmoji.missing=true;missing.onOverlayDraw(c,new TextView(25),layout);
    check(missing.draws==0,"unavailable emoji must not draw a stale overlay");
    Span destroyed=new Span();destroyed.isDestroyed=true;destroyed.onOverlayDraw(c,new TextView(25),layout);
    check(destroyed.draws==0,"destroyed emoji must not draw");
  }
}
"""
