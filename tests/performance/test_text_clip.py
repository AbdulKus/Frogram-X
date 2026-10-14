"""Run the production clipping prelude and retain draw-coordinate bookkeeping."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[2]
SOURCE = REPO / "app/src/main/java/org/thunderdog/challegram/util/text/Text.java"


def clipping_prelude(source):
    start = source.index("    final boolean needRestore = BitwiseUtils.hasFlag(textFlags, FLAG_NEED_CLIP_TEXT_AREA);")
    end = source.index("    final boolean center =", start)
    return source[start:end]


class TextClipRegressionTest(unittest.TestCase):
    def test_empty_software_clip_skips_drawing_and_preserves_coordinates(self):
        source = FIXTURE.replace("PRODUCTION_PRELUDE", clipping_prelude(SOURCE.read_text(encoding="utf-8")))
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK required")
        with tempfile.TemporaryDirectory(prefix="frogram-text-clip-") as directory:
            path = Path(directory) / "TextClipRegression.java"
            path.write_text(source, encoding="utf-8")
            result = subprocess.run([javac, "-encoding", "UTF-8", str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([java, "-cp", directory, "TextClipRegression"], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("Text clipping regression passed", result.stdout)


FIXTURE = r"""
import java.util.*;
public class TextClipRegression {
  static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
  static class Canvas {
    boolean visible,hardware;int saves,clips,draws;float left,top,right,bottom;
    Canvas(boolean v,boolean h){visible=v;hardware=h;}
    boolean clipRect(float l,float t,float r,float b){clips++;left=l;top=t;right=r;bottom=b;return visible;}
    boolean isHardwareAccelerated(){return hardware;}
  }
  static class Views {static int save(Canvas c){return ++c.saves;}static void restore(Canvas c,int n){check(c.saves==n,"balanced restore");c.saves--;}}
  static class Screen {static int dp(float n){return Math.round(n);}}
  static class BitwiseUtils {static boolean hasFlag(int n,int flag){return (n&flag)!=0;}}
  static class QuoteBackground {int draws;void draw(Canvas c,int x,int end,int pad,int y){draws++;}}
  static class Text {
    static final int FLAG_NEED_CLIP_TEXT_AREA=4096;
    int textFlags=FLAG_NEED_CLIP_TEXT_AREA,lastStartX,lastStartY,lastEndX,lastEndXBottomPadding,cleared;
    boolean rtl;Object lineMarginProvider,backgrounds=new Object();ArrayList<Object> pressHighlights=new ArrayList<>();
    List<QuoteBackground> quotes=Arrays.asList(new QuoteBackground());
    int getLineCount(){return 2;}int getLineStartMargin(int line,int y){return 10+line;}int getWidth(){return 120;}int getHeight(){return 40;}boolean isFullyRtl(){return rtl;}
    void clearSpoilers(){cleared++;}
    void draw(Canvas c,int startX,int endX,int endXBottomPadding,int startY){
      PRODUCTION_PRELUDE
      c.draws++;
      if(needRestore)Views.restore(c,saveCount);
    }
  }
  public static void main(String[] args){
    for(boolean rtl:new boolean[]{false,true})for(boolean hardware:new boolean[]{false,true})for(boolean visible:new boolean[]{false,true}){
      Text t=new Text();t.rtl=rtl;t.lineMarginProvider=new Object();t.pressHighlights.add(new Object());
      Canvas c=new Canvas(visible,hardware);t.draw(c,20,200,7,30);
      check(c.draws==((visible||hardware)?1:0),"only empty software clips skip draw");
      check(c.saves==0&&c.clips==1,"clip save must always be restored");
      check(t.lastStartX==20&&t.lastEndX==200&&t.lastEndXBottomPadding==7&&t.lastStartY==30,"touch and tooltip coordinates remain current");
      check(t.backgrounds==null&&t.pressHighlights.isEmpty()&&t.cleared==1,"coordinate-dependent caches must still reset");
      check(t.quotes.get(0).draws==1,"quote bookkeeping remains current");
      check(c.left==(rtl?65:16)&&c.right==(rtl?204:155)&&c.top==26&&c.bottom==74,"original RTL/LTR clip geometry is preserved");
      t.draw(c,20,200,7,31);check(t.cleared==1&&t.lastStartY==31,"vertical-only move updates coordinates without extra cache clearing");
    }
    Text t=new Text();t.textFlags=0;Canvas c=new Canvas(false,false);t.draw(c,1,2,3,4);
    check(c.draws==1&&c.clips==0&&c.saves==0,"text without its own clip keeps the original rendering path");
    System.out.println("Text clipping regression passed");
  }
}
"""
