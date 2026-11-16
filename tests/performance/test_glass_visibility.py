"""Hidden glass retains invalidations without scheduling draws or walking geometry."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class GlassVisibilityTest(unittest.TestCase):
    def test_hidden_changes_are_ready_when_the_surface_reappears(self):
        source = (ROOT / "app/src/main/java/org/thunderdog/challegram/widget/ChatGlassDrawable.java").read_text()
        signatures = ["private boolean hasVisibleAlpha (", "private boolean isHostVisible (",
                      "public void invalidateBackdrop ()", "public void invalidateBackdrop (@Nullable View changed)"]
        methods = "\n".join(method(source, signature) for signature in signatures)
        start = source.index("    prepareFrame = () -> {")
        end = source.index("\n    };", start) + len("\n    };")
        setup = "void setup() {" + source[start:end] + "}"
        fixture = FIXTURE.replace("PRODUCTION_METHODS", methods + "\n" + setup)
        with tempfile.TemporaryDirectory(prefix="frogram-glass-visibility-") as directory:
            target = Path(directory) / "GlassVisibilityRegression.java"
            target.write_text(fixture)
            compiled = subprocess.run([shutil.which("javac"), str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run([shutil.which("java"), "-cp", directory, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r"""
public class GlassVisibilityRegression {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static class Matrix {boolean isIdentity(){return true;}}
  static class View {
    Object token=new Object(),parent;float alpha=1;boolean shown=true,dirty;int draws,locations;
    Object getWindowToken(){return token;}Object getParent(){return parent;}float getAlpha(){return alpha;}
    boolean isShown(){return shown;}void invalidate(){draws++;dirty=true;}boolean isDirty(){return dirty;}Matrix getMatrix(){return new Matrix();}
    int getWidth(){return 50;}int getHeight(){return 50;}
    void getLocationInWindow(int[] out){locations++;out[0]=0;out[1]=0;}
    void getLocationOnScreen(int[] out){getLocationInWindow(out);}
  }
  static class Bounds {int left=0,top=0,right=100,bottom=100;}
  static class Glass {
    View host=new View();boolean released,enabled=true,backdropDirty,geometryDirty;
    Object sample=new Object();Bounds sampleBounds=new Bounds();int[] hostPosition=new int[2],changedPosition=new int[2];
    interface PreDraw {boolean onPreDraw();}
    PreDraw prepareFrame;boolean navigationFrozen,sampleChanged=true;int prepares;
    boolean prepareBackdrop(){prepares++;boolean changed=sampleChanged;sampleChanged=false;return changed;}
    PRODUCTION_METHODS
  }
  public static void main(String[] args){
    Glass visible=new Glass();visible.invalidateBackdrop();
    check(visible.backdropDirty&&visible.host.draws==1,"visible glass must request fresh content");
    for(int mode=0;mode<4;mode++){
      Glass hidden=new Glass();
      if(mode==0)hidden.host.alpha=0;
      if(mode==1){View parent=new View();parent.alpha=0;hidden.host.parent=parent;}
      if(mode==2)hidden.host.shown=false;
      if(mode==3)hidden.host.token=null;
      hidden.invalidateBackdrop();
      check(hidden.backdropDirty&&hidden.host.draws==0,"hidden or detached glass must remember changes without drawing");
      hidden.backdropDirty=false;View changed=new View();hidden.invalidateBackdrop(changed);
      check(hidden.backdropDirty&&hidden.host.draws==0,"animated children must not wake hidden controls");
      check(hidden.host.locations==0&&changed.locations==0,"hidden controls must skip coordinate walks");
      hidden.host.alpha=1;hidden.host.parent=null;hidden.host.shown=true;hidden.host.token=new Object();
      hidden.invalidateBackdrop();
      check(hidden.backdropDirty&&hidden.host.draws==1,"revealed controls must still refresh their previously dirty sample");
    }
    Glass released=new Glass();released.released=true;released.invalidateBackdrop();
    check(!released.backdropDirty&&released.host.draws==0,"released surfaces must stay inactive");
    Glass disabled=new Glass();disabled.enabled=false;disabled.invalidateBackdrop();
    check(!disabled.backdropDirty&&disabled.host.draws==0,"disabled surfaces must stay inactive");
    Glass frame=new Glass();frame.setup();frame.invalidateBackdrop();frame.prepareFrame.onPreDraw();
    check(frame.prepares==1&&frame.host.draws==2,"new pixels must invalidate the cached display list even when the view is already dirty");
    frame.host.dirty=false;frame.prepareFrame.onPreDraw();
    check(frame.host.draws==2,"unchanged content must not schedule a frame");
    frame.sampleChanged=true;frame.prepareFrame.onPreDraw();
    check(frame.host.draws==3,"geometry-only changes must still invalidate a clean hardware display list");
    for(int mode=0;mode<5;mode++){
      Glass dormant=new Glass();dormant.setup();
      if(mode==0)dormant.navigationFrozen=true;if(mode==1)dormant.host.alpha=0;if(mode==2)dormant.host.token=null;if(mode==3)dormant.enabled=false;if(mode==4)dormant.released=true;
      check(dormant.prepareFrame.onPreDraw()&&dormant.prepares==0&&dormant.host.draws==0,"inactive surfaces must not capture or cancel the frame");
    }
    Glass changed=new Glass();changed.invalidateBackdrop(new View());
    check(changed.backdropDirty&&changed.host.draws==1,"visible intersecting changes must retain region invalidation");
  }
}
"""
