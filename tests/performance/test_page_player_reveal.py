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
        methods = '\n'.join(method(source, name) for name in ['private float pagePlayerAlpha (', 'public boolean needClickAt ('])
        code = FIXTURE.replace('PRODUCTION_METHODS', methods.replace('@Override', ''))
        with tempfile.TemporaryDirectory(prefix='frogram-player-reveal-') as directory:
            path = Path(directory) / 'PlayerRevealTest.java'
            path.write_text(code)
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class PlayerRevealTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static class HeaderView {static int getPlayerSize(){return 36;}}
 static class MathUtils {static float clamp(float value){return Math.max(0,Math.min(1,value));}}
 static class View {}
 static class ViewController<T> {boolean glass=true;boolean usesGlassPageHeader(){return glass;}}
 static class Navigation {ViewController<?> owner=new ViewController<>();boolean animating;ViewController<?> getCurrentStackItem(){return owner;}boolean isAnimating(){return animating;}}
 static class Filling {
  int playerTop,playerBottom;float fillingBottom=100,hideFactor,dropShadowAlpha=1,showFactor=1,touchDownX,touchDownY;
  boolean showOngoingBar=true,handlingFloatingPlayerTouch;Navigation navigationController=new Navigation();
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
  check(!f.needClickAt(new View(),15,99)&&!f.needClickAt(new View(),15,137),"player accepts input outside its visible row");
  f.hideFactor=1;check(f.pagePlayerAlpha()==0&&!f.needClickAt(new View(),15,118),"hidden player remains clickable");
  f.hideFactor=0;f.playerBottom=108;f.playerTop=72;
  f.navigationController.owner.glass=false;check(f.needClickAt(new View(),15,104),"classic player interaction was changed");
  f.navigationController.owner.glass=true;f.navigationController.animating=true;check(!f.needClickAt(new View(),15,104),"navigation should block player input");
 }
}'''
