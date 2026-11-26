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

    def test_navigation_and_fullscreen_transition_keep_status_bar_icons_visible(self):
        base_src = (ROOT / 'app/src/main/java/org/thunderdog/challegram/BaseActivity.java').read_text()
        nav_src = (ROOT / 'app/src/main/java/org/thunderdog/challegram/navigation/NavigationStack.java').read_text()
        media_src = (ROOT / 'app/src/main/java/org/thunderdog/challegram/mediaview/MediaViewController.java').read_text()
        web_src = (ROOT / 'app/src/main/java/org/thunderdog/challegram/component/preview/WebViewPreviewLayout.java').read_text()
        ui_src = (ROOT / 'app/src/main/java/org/thunderdog/challegram/tool/UI.java').read_text()

        # Verify navigation stack listener & replace() trigger updates
        self.assertIn('updateWindowDecorSystemUiVisibility();', method(base_src, 'private final NavigationStack.ChangeListener navigationStackChangeListener'))
        self.assertIn('notifyStackChanged();', method(nav_src, 'public void replace ('))
        self.assertIn('Build.VERSION.SDK_INT < Build.VERSION_CODES.R', method(media_src, 'private void setLowProfile ('))
        self.assertIn('removeHideNavigationView(this);', method(media_src, 'private void setFullScreen ('))
        self.assertIn('Build.VERSION.SDK_INT < Build.VERSION_CODES.R', method(web_src, 'private void processFullscreen ('))

        reveal_finish = method(media_src, 'public void onFactorChangeFinished (').replace('@Override', '')
        self.assertIn('setFullScreen(false);\n          setLowProfile(false);', reveal_finish)

        base_methods = '\n'.join([
            method(base_src, 'private boolean useLightStatusBar ('),
            method(base_src, 'public void setWindowDecorSystemUiVisibility (').replace('@SuppressWarnings("deprecation")', ''),
            method(base_src, 'public void setFullScreenFlag ('),
            method(base_src, 'private int computeUiVisibility ('),
            method(base_src, 'private void setHideNavigation ('),
            method(base_src, 'private void updateNavigationBarColor (').replace('@SuppressWarnings("deprecation")', ''),
        ])
        set_light_code = method(ui_src, 'public static void setLightSystemBars (').replace('@SuppressWarnings("deprecation")', '')
        set_light_code = set_light_code.replace('android.view.WindowInsetsController', 'WindowInsetsController').replace('android.view.WindowInsets.Type', 'WindowInsets.Type')

        with tempfile.TemporaryDirectory(prefix='frogram-base-bars-') as folder:
            target = Path(folder) / 'BaseSystemBarsRegression.java'
            target.write_text(
                BASE_FIXTURE
                .replace('SET_LIGHT_SYSTEM_BARS', set_light_code)
                .replace('BASE_METHODS', base_methods)
            )
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


BASE_FIXTURE = '''import java.util.*;
public class BaseSystemBarsRegression {
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  static class Build {
    static class VERSION {static int SDK_INT;}
    static class VERSION_CODES {static final int M=23,O=26,LOLLIPOP=21,R=30;}
  }
  static class BitwiseUtils {
    static int optional(int flag,boolean on){return on?flag:0;}
    static int setFlag(int v,int f,boolean on){return on?v|f:v&~f;}
    static boolean hasFlag(int v,int f){return (v&f)!=0;}
  }
  static class Config {static final boolean USE_CUSTOM_NAVIGATION_COLOR=true;}
  static class Settings {static Settings instance(){return new Settings();}boolean useEdgeToEdge(){return true;}}
  static class Theme {static boolean dark=false,needLight=false;static boolean isDark(){return dark;}static boolean needLightStatusBar(){return needLight;}static int backgroundColor(){return 0xffffffff;}static int getColor(int id){return 0;}}
  static class ColorId {static final int passcode=1;}
  static class ColorUtils {static int fromToArgb(int a,int b,float f){return b;}static int compositeColor(int a,int b){return a;}}
  static class FutureInt {int getIntValue(){return 0;}}
  static class StatusBarView {boolean floating;void setFloatingHeader(boolean v){floating=v;}}
  static class WindowManager {static class LayoutParams {static final int FLAG_FULLSCREEN=1024;int flags;}}
  static class WindowInsets {static class Type {static int statusBars(){return 1;}}}
  static class View {
    static final int SYSTEM_UI_FLAG_VISIBLE=0,SYSTEM_UI_FLAG_LOW_PROFILE=1,SYSTEM_UI_FLAG_HIDE_NAVIGATION=2,
      SYSTEM_UI_FLAG_FULLSCREEN=4,SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR=16,SYSTEM_UI_FLAG_LIGHT_STATUS_BAR=8192;
    int visibility;
    int getSystemUiVisibility(){return visibility;}
    void setSystemUiVisibility(int v){visibility=v;}
  }
  static class WindowInsetsController {
    static final int APPEARANCE_LIGHT_NAVIGATION_BARS=16,APPEARANCE_LIGHT_STATUS_BARS=8;
    int appearance;boolean visible=true;
    void setSystemBarsAppearance(int value,int mask){appearance=(appearance&~mask)|(value&mask);}
    void show(int types){visible=true;}
    void hide(int types){visible=false;}
  }
  static class Window {
    View decor=new View();WindowInsetsController insets=new WindowInsetsController();WindowManager.LayoutParams params=new WindowManager.LayoutParams();
    View getDecorView(){return decor;}WindowInsetsController getInsetsController(){return insets;}WindowManager.LayoutParams getAttributes(){return params;}
  }
  static class UI {
    static final int NAVIGATION_BAR_COLOR=0xff000000;
    static void setNavigationBarColor(Window w,int c,boolean g){}
    SET_LIGHT_SYSTEM_BARS
  }
  static class Activity {
    static final int FULLSCREEN_FLAG_PASSCODE=1<<1,FULLSCREEN_FLAG_HAS_NO_FULLSCREEN_VIEWS=1<<2,FULLSCREEN_FLAG_HAS_FULLSCREEN_VIEWS=1<<3,FULLSCREEN_FLAG_HIDE_NAVIGATION=1<<4;
    Window window=new Window();
    StatusBarView statusBar=new StatusBarView();
    boolean glassStatusBar=true,isWindowLight=true,isFullscreen=false,hideNavigation=false,isGestureNavigationEnabled=true,isPasscodeShowing=false;
    int lastWindowVisibility=0,requestedWindowVisibility=0,fullScreenFlags=0;
    float desiredIntensityFactor=0f,photoRevealFactor=0f;
    boolean desiredAllowLight=true;
    FutureInt desiredColor=null;
    Activity dismissingPasscodeController=null;
    Activity getValue(){return this;}
    float getAlpha(){return 0f;}
    Window getWindow(){return window;}
    boolean hasGlassStatusBar(){return glassStatusBar;}
    void setFullScreen(boolean fs){
      if(this.isFullscreen!=fs){
        this.isFullscreen=fs;
        this.hideNavigation=fs&&BitwiseUtils.hasFlag(fullScreenFlags,FULLSCREEN_FLAG_HIDE_NAVIGATION);
        window.params.flags=fs?(window.params.flags|WindowManager.LayoutParams.FLAG_FULLSCREEN):(window.params.flags&~WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setWindowDecorSystemUiVisibility(computeUiVisibility(),true);
      }
    }
    BASE_METHODS
  }
  public static void main(String[] args){
    for(int api:new int[]{30,35,36}){
      Build.VERSION.SDK_INT=api;
      Activity a=new Activity();
      // 1) Glass page -> non-glass dark page -> glass page flips status bar icons correctly
      a.glassStatusBar=true;a.setWindowDecorSystemUiVisibility(0,false);
      check((a.window.insets.appearance&8)!=0,"glass page must use dark status icons on light theme");
      a.glassStatusBar=false;Theme.needLight=false;a.setWindowDecorSystemUiVisibility(a.lastWindowVisibility,false);
      check((a.window.insets.appearance&8)==0,"legacy non-glass page must use white status icons");
      a.glassStatusBar=true;a.setWindowDecorSystemUiVisibility(a.lastWindowVisibility,false);
      check((a.window.insets.appearance&8)!=0,"returning to glass page left status icons white");

      // 2) Fullscreen + hideNavigation exit must never leave statusBars() hidden or LOW_PROFILE set
      a.setFullScreenFlag(Activity.FULLSCREEN_FLAG_HAS_FULLSCREEN_VIEWS,true);
      a.setFullScreenFlag(Activity.FULLSCREEN_FLAG_HIDE_NAVIGATION,true);
      check(a.isFullscreen&&a.hideNavigation&&!a.window.insets.visible,"fullscreen hide navigation failed");
      check((a.window.decor.visibility&View.SYSTEM_UI_FLAG_LOW_PROFILE)==0,"LOW_PROFILE leaked on API "+api);
      a.setFullScreenFlag(Activity.FULLSCREEN_FLAG_HAS_FULLSCREEN_VIEWS,false);
      check(!a.isFullscreen&&!a.hideNavigation,"fullscreen state remained stuck when HIDE_NAVIGATION flag lingered");
      check(a.window.insets.visible,"status bar icons remained hidden after exiting fullscreen on API "+api);
      check(a.lastWindowVisibility==0,"lastWindowVisibility remained stale after exit");
      a.setFullScreenFlag(Activity.FULLSCREEN_FLAG_HIDE_NAVIGATION,false);
      check(a.window.insets.visible,"clearing HIDE_NAVIGATION hid status bars");
    }
  }
}'''

