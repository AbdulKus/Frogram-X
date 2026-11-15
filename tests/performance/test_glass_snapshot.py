"""Exercise snapshot capture, API gates, alpha and software-canvas fallback."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class GlassSnapshotTest(unittest.TestCase):
    def test_one_capture_and_hardware_software_replay(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/navigation/GlassContentSnapshot.java').read_text()
        source = re.sub(r'^(?:package|import) .*;\n', '', source, flags=re.M)
        source = re.sub(r'^\s*@android.annotation.TargetApi\([^\n]+\)\n', '\n', source, flags=re.M)
        source = source.replace('final class GlassContentSnapshot', 'static final class GlassContentSnapshot', 1)
        source = source.replace('org.thunderdog.challegram.util.PerformanceTrace', 'PerformanceTrace')
        source = source.replace('android.graphics.RenderNode', 'RenderNode')
        with tempfile.TemporaryDirectory(prefix='frogram-snapshot-') as directory:
            target = Path(directory) / 'SnapshotRegression.java'
            target.write_text(FIXTURE.replace('PRODUCTION_SOURCE', source))
            result = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r'''
import java.util.*;
public class SnapshotRegression {
  static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
  static class Build {static class VERSION {static int SDK_INT;}static class VERSION_CODES {static final int Q=29;}}
  static class Screen {static int dp(float n){return Math.round(n);}}
  static class AlphaBlur {void blur(int[] pixels,int w,int h){}}
  static class PerformanceTrace {static int depth;static void begin(String s){depth++;}static void end(){depth--;}}
  static class Paint {static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;int alpha;Paint(int flags){}void setAlpha(int n){alpha=n;}}
  static class RectF {void set(float l,float t,float r,float b){}}
  static class Bitmap {
    enum Config {ARGB_8888,HARDWARE}final int w,h;final Config config;
    Bitmap(int w,int h,Config c){this.w=w;this.h=h;config=c;}
    static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h,c);}
    static Bitmap createBitmap(Picture p,int w,int h,Config c){check(p.ended,"incomplete recording");return new Bitmap(w,h,c);}
    int getWidth(){return w;}int getHeight(){return h;}void eraseColor(int c){}
    void getPixels(int[] p,int o,int s,int x,int y,int w,int h){}void setPixels(int[] p,int o,int s,int x,int y,int w,int h){}
  }
  static class Picture {
    boolean ended;static Picture last;
    Canvas beginRecording(int w,int h){last=this;return new Canvas(false);}void endRecording(){ended=true;}
  }
  static class Canvas {
    final boolean hardware;final List<Bitmap> images=new ArrayList<>();final List<Integer> alpha=new ArrayList<>();
    Canvas(boolean hardware){this.hardware=hardware;}Canvas(Bitmap b){hardware=false;check(b.config!=Bitmap.Config.HARDWARE,"cannot rasterize into immutable hardware image");}
    void drawRenderNode(RenderNode node){check(hardware,"node on software canvas");images.add(node.bitmap);alpha.add(Math.round(node.alpha*255));}
    int save(){return 1;}void restoreToCount(int n){}
    boolean isHardwareAccelerated(){return hardware;}void translate(float x,float y){}void scale(float x,float y){}
    void clipRect(int l,int t,int r,int b){}void drawPicture(Picture p){check(p.ended,"replay before recording ended");}
    void drawBitmap(Bitmap b,Object src,RectF dst,Paint p){check(hardware||b.config!=Bitmap.Config.HARDWARE,"hardware image on software canvas");images.add(b);alpha.add(p.alpha);}
  }
  static class RenderNode {
    Bitmap bitmap;float alpha;RenderNode(String name){}void setPosition(int l,int t,int r,int b){bitmap=new Bitmap(r-l,b-t,Bitmap.Config.HARDWARE);}
    void setUseCompositingLayer(boolean force,Paint paint){}Canvas beginRecording(int w,int h){return new Canvas(true);}
    void endRecording(){}void setAlpha(float n){alpha=n;}
  }
  static class View {
    int width=400,height=100,draws;boolean hardware=true,fail;
    int getWidth(){return width;}int getHeight(){return height;}boolean isHardwareAccelerated(){return hardware;}
    void draw(Canvas c){draws++;if(fail)throw new IllegalStateException("test draw failure");}
  }
  PRODUCTION_SOURCE
  public static void main(String[] args){
    for(int api:new int[]{24,27,28,30,34})for(boolean accelerated:new boolean[]{false,true}){
      Build.VERSION.SDK_INT=api;View v=new View();v.hardware=accelerated;GlassContentSnapshot s=new GlassContentSnapshot();
      check(s.capture(v),"capture rejected visible content");check(v.draws==1,"view captured repeatedly");
      Canvas gpu=new Canvas(true);s.draw(gpu,10,20,.7f);
      check(gpu.images.size()==2&&gpu.alpha.equals(Arrays.asList(54,125)),"halo and foreground alpha changed");
      check((gpu.images.get(1).config==Bitmap.Config.HARDWARE)==(api>=29&&accelerated),"wrong API/acceleration gate");
      Canvas cpu=new Canvas(false);s.draw(cpu,10,20,.7f);s.draw(cpu,10,20,1.5f);
      check(cpu.images.get(1).config==Bitmap.Config.ARGB_8888&&cpu.images.get(1)==cpu.images.get(3),"software fallback not retained");
      check(cpu.alpha.get(2)==0&&cpu.alpha.get(3)==255,"visibility not clamped");
      check(v.draws==1,"canvas fallback recaptured live view");s.draw(cpu,0,0,0);check(cpu.images.size()==4,"invisible snapshot drew");
      check(PerformanceTrace.depth==0,"unbalanced capture trace");
    }
    Build.VERSION.SDK_INT=34;View icon=new View();GlassContentSnapshot small=new GlassContentSnapshot();small.capture(icon,icon.height,false);
    Canvas iconCanvas=new Canvas(true);small.draw(iconCanvas,0,0,1);check(iconCanvas.images.get(1).config==Bitmap.Config.ARGB_8888,"composer texture override ignored");
    View huge=new View();huge.width=4000;huge.height=4000;huge.hardware=false;GlassContentSnapshot bounded=new GlassContentSnapshot();bounded.capture(huge);
    Canvas cpu=new Canvas(false);bounded.draw(cpu,0,0,1);Bitmap foreground=cpu.images.get(1);
    check((long)foreground.w*foreground.h<502000,"unbounded legacy foreground allocation");
    View empty=new View();empty.width=0;check(!new GlassContentSnapshot().capture(empty),"empty capture accepted");
    View bad=new View();bad.fail=true;try{new GlassContentSnapshot().capture(bad);throw new AssertionError("draw failure swallowed");}catch(IllegalStateException expected){}
    check(Picture.last.ended&&PerformanceTrace.depth==0,"failed capture did not finish recording/trace");
  }
}
'''
