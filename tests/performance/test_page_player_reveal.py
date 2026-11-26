"""A reserved glass header must not expose clickable fragments of a hidden player."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class PagePlayerRevealTest(unittest.TestCase):
    def test_reveal_and_touch_follow_the_visible_player(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/navigation/HeaderFilling.java').read_text()
        methods = '\n'.join(method(source, name) for name in ['private float pagePlayerAlpha (', 'private boolean isFloatingPlayerContext (', 'public boolean needClickAt (', 'public boolean onTouchEvent ('])
        code = FIXTURE.replace('PRODUCTION_METHODS', methods.replace('@Override', ''))
        with tempfile.TemporaryDirectory(prefix='frogram-player-reveal-') as directory:
            path = Path(directory) / 'PlayerRevealTest.java'
            path.write_text(code)
            style = (ROOT / 'app/src/main/java/org/thunderdog/challegram/widget/GlassStyle.java').read_text()
            (Path(directory) / 'GlassStyle.java').write_text(style)
            geometry = (ROOT / 'app/src/main/java/org/thunderdog/challegram/util/FloatingPlayerGeometry.java').read_text()
            (Path(directory) / 'FloatingPlayerGeometry.java').write_text(geometry)
            sources = [str(path), str(Path(directory) / 'GlassStyle.java'), str(Path(directory) / 'FloatingPlayerGeometry.java')]
            result = subprocess.run([shutil.which('javac'), '-d', directory, *sources], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class PlayerRevealTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static class HeaderView {static int getPlayerSize(){return 36;}}
 static class MathUtils {static float clamp(float value){return Math.max(0,Math.min(1,value));}}
 static class View {}
 static class MotionEvent {float y;boolean recycled;MotionEvent(float y){this.y=y;}static MotionEvent obtain(MotionEvent other){return new MotionEvent(other.y);}void offsetLocation(float x,float y){this.y+=y;}void recycle(){recycled=true;}}
 static class Screen {static int dp(float value){return Math.round(value);}}
 static class Helper {Filling filling;MotionEvent received;boolean onTouchEvent(View view,MotionEvent event){received=event;return filling.needClickAt(view,15,event.y);}}
 static class ViewController<T> {boolean glass=true;boolean usesGlassPageHeader(){return glass;}boolean hasFloatingPlayer(){return false;}}
 static class Navigation {ViewController<?> owner=new ViewController<>();boolean animating;ViewController<?> getCurrentStackItem(){return owner;}boolean isAnimating(){return animating;}}
 static class Filling {
  int width=1080,playerTop,playerBottom;float fillingBottom=100,hideFactor,dropShadowAlpha=1,showFactor=1,playerAllowance=1,touchDownX,touchDownY;
  boolean showOngoingBar=true,handlingFloatingPlayerTouch;Navigation navigationController=new Navigation();
  Helper helper=new Helper();View headerView=new View();Filling(){helper.filling=this;}
  PRODUCTION_METHODS
 }
 public static void main(String[] args){
  Filling f=new Filling();float last=0;
  for(int visible=0;visible<=36;visible++){
   f.playerBottom=100+visible;f.playerTop=f.playerBottom-36;
   float alpha=f.pagePlayerAlpha();check(alpha>=last&&alpha<=1,"player reveal is not monotone");last=alpha;
   if(visible<=28){check(alpha==0,"expanded header exposes a player fragment");check(!f.needClickAt(new View(),15,100+visible*.5f),"hidden player intercepts page touches");}
  }
  check(last==1,"compact player did not become opaque");
  check(f.needClickAt(new View(),15,118),"visible player stopped accepting input");
  f.playerAllowance=0;check(!f.needClickAt(new View(),15,118),"zero allowance player remains clickable");
  f.playerAllowance=1;
  check(!f.needClickAt(new View(),5,118)&&!f.needClickAt(new View(),1075,118),"floating player accepts clicks outside pill margins");
  check(!f.needClickAt(new View(),15,99)&&!f.needClickAt(new View(),15,137),"player accepts input outside its visible row");
  f.playerTop=100;f.playerBottom=136;
  MotionEvent touch=new MotionEvent(136);
  check(f.onTouchEvent(touch),"shifted visible player rejects touch");
  check(touch.y==136&&!touch.recycled&&f.helper.received.recycled,"player translation changed original input or leaked event");
  check(!f.onTouchEvent(new MotionEvent(104)),"old player location still intercepts touches");
  f.navigationController.owner.glass=false;
  check(f.onTouchEvent(new MotionEvent(104)),"classic player touch was shifted");
  f.navigationController.owner.glass=true;
  f.hideFactor=1;check(f.pagePlayerAlpha()==0&&!f.needClickAt(new View(),15,118),"hidden player remains clickable");
  f.hideFactor=0;f.playerBottom=108;f.playerTop=72;
  f.navigationController.owner.glass=false;check(f.needClickAt(new View(),15,104),"classic player interaction was changed");
  f.navigationController.owner.glass=true;f.navigationController.animating=true;check(!f.needClickAt(new View(),15,104),"navigation should block player input");
 }
}'''
