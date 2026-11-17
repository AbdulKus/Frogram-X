"""A recycled card divider stays thin, centred and resolves the current theme."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class CardSeparatorTest(unittest.TestCase):
    def test_reused_separator_keeps_length_and_cannot_repaint_the_canvas(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/widget/SeparatorView.java').read_text()
        methods = '\n'.join(method(source, name).replace('@Override', '') for name in [
            'public void setCardStyle (', 'public void setOffsets (', 'protected void onDraw ('
        ])
        with tempfile.TemporaryDirectory(prefix='frogram-card-divider-') as directory:
            path = Path(directory) / 'DividerTest.java'
            path.write_text(FIXTURE.replace('METHODS', methods))
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class DividerTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class Paint {int color;void setColor(int c){color=c;}}
 static class Canvas {float[][] rectangles=new float[2][];int[] colors=new int[2];int count;void drawColor(int c){throw new AssertionError("unbounded fill");}void drawRect(float l,float t,float r,float b,int color){rectangles[count]=new float[]{l,t,r,b};colors[count++]=color;}void drawRect(float l,float t,float r,float b,Paint p){drawRect(l,t,r,b,p.color);}}
 static class Theme {static int fill=0xffeeeeee,text=0xff223344;static int getColor(int id){return text;}static int fillingColor(){return fill;}}
 static class ColorId {static final int glassText=1;}
 static class ProfileCardBackground {static int fillingColor(){return Theme.fill;}}
 static class Lang {static boolean mirror;static boolean rtl(){return mirror;}}
 static class me {static class vkryl {static class core {static class ColorUtils {static int alphaColor(float a,int c){return ((int)(255*a)<<24)|(c&0xffffff);}}}}}
 static class org {static class thunderdog {static class challegram {static class tool {static class Paints {static int fillingPaint(int c){return c;}}}}}}
 static class Divider {Paint paint=new Paint();float left,right,top;int height=2,forcedFillingColor=0xff000000,forcedColor=0xff000000,colorId,invalidations;boolean cardStyle,useFilling=true;int getMeasuredWidth(){return 300;}int getHeight(){return 4;}void invalidate(){invalidations++;}METHODS}
 public static void main(String[] args){
  Divider divider=new Divider();divider.setOffsets(64,20);divider.setCardStyle();check(divider.left==42&&divider.right==42);int changed=divider.invalidations;divider.setOffsets(64,20);divider.setCardStyle();check(changed==divider.invalidations);
  for(boolean rtl:new boolean[]{false,true})for(int fill:new int[]{0xff202020,0xfff7f7f7,0xff182733}){
   Lang.mirror=rtl;Theme.fill=fill;Theme.text=fill==0xfff7f7f7?0xff223344:0xffddeeff;Canvas canvas=new Canvas();divider.onDraw(canvas);check(canvas.count==2&&canvas.colors[0]==fill&&canvas.rectangles[0][0]==0&&canvas.rectangles[0][2]==300&&canvas.rectangles[0][3]==4);float[] line=canvas.rectangles[1];check(line[0]==42&&line[2]==258&&line[3]-line[1]==2&&(line[0]+line[2])/2==150);check((canvas.colors[1]>>>24)==25);
  }
  divider.setOffsets(16,20);check(divider.left==18&&divider.right==18);Canvas canvas=new Canvas();divider.onDraw(canvas);check(canvas.rectangles[1][2]-canvas.rectangles[1][0]==264);
 }
}'''
