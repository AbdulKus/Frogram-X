"""Reserved expanded tab width must not crop a capsule into a square end."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class GlassFeedbackTest(unittest.TestCase):
    def test_mask_tracks_visible_width_during_label_expansion(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/widget/GlassRippleDrawable.java').read_text()
        methods = '\n'.join(method(source, signature) for signature in [
            'public void setVisibleWidth (', 'private void updateMask ('])
        code = FIXTURE.replace('PRODUCTION_METHODS', methods)
        with tempfile.TemporaryDirectory(prefix='frogram-feedback-') as directory:
            path = Path(directory) / 'FeedbackTest.java'
            path.write_text(code)
            compile_result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(compile_result.returncode, 0, compile_result.stderr)
            run_result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(run_result.returncode, 0, run_result.stderr)


FIXTURE = '''public class FeedbackTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static class GlassStyle {static final float CONTROL_SIZE_DP=44;}
 static class Screen {static float density;static int dp(float n){return (int)Math.ceil(n*density);}}
 static class Rect {int left,top,right,bottom;Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}int width(){return right-left;}int height(){return bottom-top;}}
 static class Mask {float radius;Rect bounds;void setCornerRadius(float value){radius=value;}void setBounds(int l,int t,int r,int b){bounds=new Rect(l,t,r,b);}}
 static class Feedback {
  static final float TAB_INSET_X_DP=4,TAB_INSET_Y_DP=7;
  Mask mask=new Mask();boolean compact,alignRight;int visibleWidth=-1;Rect bounds,hotspot;
  Feedback(boolean compact){this.compact=compact;}
  Rect getBounds(){return bounds;}void setHotspotBounds(int l,int t,int r,int b){hotspot=new Rect(l,t,r,b);}
  void resize(Rect value){bounds=value;updateMask(value);}
  PRODUCTION_METHODS
 }
 public static void main(String[] args){
  for(float density:new float[]{1,2.75f,3})for(boolean rtl:new boolean[]{false,true}){
   Screen.density=density;Feedback tab=new Feedback(false);int full=Screen.dp(240),height=Screen.dp(56);tab.resize(new Rect(11,19,11+full,19+height));
   // A real BackgroundView stays expanded while its visible label grows/shrinks.
   for(int widthDp:new int[]{56,57,72,103,180,240,180,103,72,56}){
    int visible=Screen.dp(widthDp);tab.setVisibleWidth(visible,rtl);Rect b=tab.mask.bounds;int x=Screen.dp(4),y=Screen.dp(7);
    check(b.width()==visible-2*x,"mask still uses expanded layout width");
    check(b.left==(rtl?11+full-visible:11)+x,"mask follows wrong RTL edge");
    check(b.top==19+y&&b.height()==height-2*y,"feedback differs from selection insets");
    check(tab.mask.radius==Math.min(b.width(),b.height())/2f,"capsule end became square");
    check(tab.hotspot.left==b.left&&tab.hotspot.right==b.right,"ripple hotspot escaped visible tab");
    // Android can reassign background bounds during View.draw / layout.
    tab.resize(new Rect(11,19,11+full,19+height));check(tab.mask.bounds.width()==visible-2*x,"bounds reset lost visible geometry");
   }
   Feedback icon=new Feedback(true);icon.resize(new Rect(0,0,Screen.dp(56),Screen.dp(56)));
   check(icon.mask.bounds.width()==Screen.dp(44)&&icon.mask.radius==Screen.dp(44)/2f,"header feedback is not circular");
  }
 }
}'''
