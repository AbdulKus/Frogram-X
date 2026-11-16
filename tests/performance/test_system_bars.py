"""Keep modern Insets appearance separate from legacy visibility and fullscreen."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class SystemBarsTest(unittest.TestCase):
    def test_modern_appearance_owner_and_legacy_fallback(self):
        code = method((ROOT / 'app/src/main/java/org/thunderdog/challegram/tool/UI.java').read_text(),
                      'public static void setLightSystemBars (')
        code = code.replace('android.view.WindowInsetsController', 'WindowInsetsController').replace('android.view.WindowInsets.Type', 'WindowInsets.Type')
        with tempfile.TemporaryDirectory(prefix='frogram-system-bars-') as folder:
            target = Path(folder) / 'SystemBarsRegression.java'
            target.write_text(FIXTURE.replace('PRODUCTION_METHOD', code))
            result = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', folder, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = '''public class SystemBarsRegression {
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  static class Build {static class VERSION {static int SDK_INT;}static class VERSION_CODES {static final int R=30;}}
  static class BitwiseUtils {static int optional(int flag,boolean on){return on?flag:0;}static int setFlag(int v,int f,boolean on){return on?v|f:v&~f;}}
  static class Settings {static Settings instance(){return new Settings();}boolean useEdgeToEdge(){return true;}}
  static class WindowManager {static class LayoutParams {static final int FLAG_FULLSCREEN=1024;int flags;}}
  static class WindowInsets {static class Type {static int statusBars(){return 1;}}}
  static class View {
    static final int SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR=16,SYSTEM_UI_FLAG_LIGHT_STATUS_BAR=8192,SYSTEM_UI_FLAG_FULLSCREEN=4;
    int visibility,appearanceMutations;
    int getSystemUiVisibility(){return visibility;}
    void setSystemUiVisibility(int value){if(((value^visibility)&(16|8192))!=0)appearanceMutations++;visibility=value;}
  }
  static class WindowInsetsController {
    static final int APPEARANCE_LIGHT_NAVIGATION_BARS=16,APPEARANCE_LIGHT_STATUS_BARS=8;
    int appearance,shows,hides;boolean visible;
    void setSystemBarsAppearance(int value,int mask){appearance=(appearance&~mask)|(value&mask);}
    void show(int types){check(types==1,"unrelated bars shown");shows++;visible=true;}
    void hide(int types){check(types==1,"unrelated bars hidden");hides++;visible=false;}
  }
  static class Window {
    View decor=new View();WindowInsetsController insets=new WindowInsetsController();WindowManager.LayoutParams params=new WindowManager.LayoutParams();
    View getDecorView(){return decor;}WindowInsetsController getInsetsController(){return insets;}WindowManager.LayoutParams getAttributes(){return params;}
  }
  PRODUCTION_METHOD
  public static void main(String[] args){
    for(int api:new int[]{30,35,36}){
      Build.VERSION.SDK_INT=api;Window window=new Window();window.decor.visibility=8192|4;
      setLightSystemBars(window,true,true,256,true);
      check(window.insets.appearance==(8|16),"modern foreground flags missing");
      check(window.decor.appearanceMutations==0,"modern appearance overwritten through deprecated flags");
      check(window.decor.visibility==(8192|256)&&window.insets.visible,"return to normal content did not restore status bar");
      setLightSystemBars(window,false,false,0,true);
      check(window.insets.appearance==0&&window.decor.appearanceMutations==0,"dark theme did not exclusively update Insets appearance");
      setLightSystemBars(window,true,true,4,true);
      check(!window.insets.visible&&window.insets.hides==1,"fullscreen visibility lost");
      int shows=window.insets.shows;
      setLightSystemBars(window,false,false,0,false);
      check(window.insets.shows==shows&&!window.insets.visible,"styling-only call changed fullscreen visibility");
      window.params.flags=1024;
      setLightSystemBars(window,false,false,0,true);
      check(window.insets.shows==shows,"window fullscreen flag was ignored");
      window.params.flags=0;window.insets=null;
      setLightSystemBars(window,true,true,0,true);
      window.insets=new WindowInsetsController();setLightSystemBars(window,true,true,0,true);
      check(window.insets.visible&&window.insets.appearance==24,"late window ownership was not recoverable");
    }
    Build.VERSION.SDK_INT=29;Window legacy=new Window();
    setLightSystemBars(legacy,true,true,256,true);
    check(legacy.decor.visibility==(256|16|8192),"legacy icon flags missing");
    setLightSystemBars(legacy,false,false,0,false);
    check(legacy.decor.visibility==256,"legacy layout visibility lost");
  }
}'''
