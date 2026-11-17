"""Exercise joined-card geometry and theme/visibility gates in the real renderer."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class SettingsSurfaceTest(unittest.TestCase):
    def test_light_group_edges_do_not_create_internal_borders(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/widget/SettingsCardSurface.java').read_text()
        draw = method(source, 'public void draw (') + '\n' + method(source, 'private void paintSurface (')
        with tempfile.TemporaryDirectory(prefix='frogram-settings-surface-') as directory:
            path = Path(directory) / 'SurfaceTest.java'
            path.write_text(FIXTURE.replace('DRAW', draw))
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class SurfaceTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class RectF {float left,top,right,bottom;void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
 static class Paint {static final int ANTI_ALIAS_FLAG=1;enum Style {FILL,STROKE} Style style;float blur,dy,width;Paint(int flags){}void setAlpha(int a){}void clearShadowLayer(){blur=dy=0;}void setStyle(Style s){style=s;}void setStrokeWidth(float w){width=w;}void setColor(int c){}void setShadowLayer(float r,float x,float y,int c){blur=r;dy=y;}}
 static class Canvas {int draws,saves;float top,bottom,clipTop,clipBottom,radius,blur,dy;Paint.Style style;int save(){return ++saves;}void clipRect(float l,float t,float r,float b){clipTop=t;clipBottom=b;}void drawRoundRect(RectF r,float x,float y,Paint p){draws++;top=r.top;bottom=r.bottom;radius=x;style=p.style;blur=p.blur;dy=p.dy;}void restoreToCount(int n){saves--;}}
 static class View {float alpha=1;float getAlpha(){return alpha;}float getX(){return 8;}float getY(){return 100;}int getWidth(){return 384;}int getHeight(){return 68;}}
 static class Theme {static boolean dark;static boolean isDark(){return dark;}static int getColor(int id){return 0xff202020;}}
 static class ColorId {static final int glassText=1;}
 static class Screen {static float dp(float v){return v;}}
 static class GlassStyle {static final float RADIUS_DP=26;}
 static class ColorUtils {static int alphaColor(float alpha,int color){return color;}}
 static class ProfileCardBackground {static int fillingColor(){return 0xfffafafa;}}
 static class Surface {final RectF bounds=new RectF();final Paint paint=new Paint(1);DRAW}
 public static void main(String[] args){
  Surface surface=new Surface();Canvas canvas=new Canvas();View row=new View();
  surface.draw(canvas,row,true,true,false);check(canvas.draws==1&&canvas.top==100&&canvas.bottom==168&&canvas.blur==2&&canvas.dy==1&&canvas.clipTop<100&&canvas.clipBottom>168&&canvas.saves==0);
  surface.draw(canvas,row,false,false,true);check(canvas.draws==2&&canvas.top==74&&canvas.bottom==194&&canvas.clipTop==100&&canvas.clipBottom==168&&canvas.style==Paint.Style.STROKE&&canvas.radius==26&&canvas.blur==0&&canvas.saves==0);
  surface.draw(canvas,row,true,false,true);check(canvas.top==100&&canvas.bottom==194&&canvas.clipBottom==168);
  surface.draw(canvas,row,false,true,true);check(canvas.top==74&&canvas.bottom==168&&canvas.clipTop==100);
  int drawn=canvas.draws;Theme.dark=true;surface.draw(canvas,row,true,true,false);surface.draw(canvas,row,true,true,true);check(canvas.draws==drawn);
  Theme.dark=false;row.alpha=0;surface.draw(canvas,row,true,true,false);surface.draw(canvas,row,true,true,true);check(canvas.draws==drawn&&canvas.saves==0);
 }
}'''
