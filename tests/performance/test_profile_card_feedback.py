"""Software glass capture must leave the on-screen ripple animator untouched."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class ProfileFeedbackTest(unittest.TestCase):
    def test_capture_preserves_render_thread_feedback(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/widget/ProfileCardBackground.java').read_text()
        start = source.index('  @Override public void draw (Canvas canvas) {\n    if (!canvas.isHardwareAccelerated())')
        draw = method(source[start:], 'public void draw (').replace('@Override', '')
        code = FIXTURE.replace('DRAW', draw)
        with tempfile.TemporaryDirectory(prefix='frogram-profile-feedback-') as directory:
            path = Path(directory) / 'ProfileFeedbackTest.java'
            path.write_text(code)
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class ProfileFeedbackTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static class android {static class R {static class attr {static final int state_enabled=1,state_pressed=2;}}}
 static class Canvas {boolean hardware;int fill;Canvas(boolean h){hardware=h;}boolean isHardwareAccelerated(){return hardware;}void drawRect(Object rect,int paint){fill=paint;}}
 static class ColorId {static final int glassActive=1;}
 static class Theme {static int accent=200;static int getColor(int id){return accent;}}
 static class ColorUtils {static int fromToArgb(int from,int to,float factor){return Math.round(from+(to-from)*factor);}static int alphaColor(float factor,int color){return Math.round(color*factor);}}
 static class ColorStateList {static int valueOf(int color){return color;}}
 static class org {static class thunderdog {static class challegram {static class tool {static class Paints {static int fillingPaint(int color){return color;}}}}}}
 static class Ripple {int[] state={1,2};int draws,colors;boolean animating=true;int[] getState(){return state;}Object getBounds(){return this;}void setColor(int color){colors++;}public void draw(Canvas canvas){draws++;if(!canvas.hardware)animating=false;}}
 static class Card extends Ripple {int lastAccent;int fillingColor(){return 100;}DRAW}
 public static void main(String[] args){
  Card card=new Card();Canvas screen=new Canvas(true),capture=new Canvas(false);card.draw(screen);check(card.draws==1&&card.colors==1);
  for(int frame=0;frame<20;frame++)card.draw(capture);
  check(card.animating&&card.draws==1&&card.colors==1&&capture.fill>100);
  card.state=new int[]{2};card.draw(capture);check(capture.fill==100&&card.animating);
  card.state=new int[]{1,2};Theme.accent=240;card.draw(capture);check(card.colors==1&&card.animating);
  card.draw(screen);check(card.draws==2&&card.colors==2&&card.animating);
 }
}'''
