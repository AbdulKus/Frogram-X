"""Counters follow their own tab's animated selection, including skipped tabs."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

JAVA = Path(__file__).resolve().parents[2] / 'app/src/main/java/org/thunderdog/challegram'


class FolderCounterColorsTest(unittest.TestCase):
    def test_labels_and_counters_share_selection(self):
        main = (JAVA / 'ui/MainController.java').read_text()
        top = (JAVA / 'navigation/ViewPagerTopView.java').read_text()
        colors = method(main, 'public static class UnreadCounterColorSet implements TextColorSet')
        methods = '\n'.join(method(top, sig) for sig in [
            'public int getItemTextColor (', 'private int getTabTextColor (', 'public float getItemSelectionFactor (',
            'private float getItemSelectionFactor (',
        ])
        source = FIXTURE.replace('COLORS', colors).replace('METHODS', methods)
        source = re.sub(r'@(?:Nullable|ColorInt|Override)\s*', '', source)
        source = re.sub(r'@FloatRange\([^)]*\)\s*', '', source)
        with tempfile.TemporaryDirectory(prefix='frogram-folder-colors-') as temp:
            path = Path(temp) / 'MainController.java'
            path.write_text(source)
            result = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', temp, 'MainController'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r'''
public class MainController {
 static final int NO_POSITION=-1;
 interface FutureBool {boolean getBoolValue();}
 interface TextColorSet {}
 static class Color {static final int TRANSPARENT=0;}
 static class ColorId {static final int NONE=0,filling=1,glassIcon=2,glassActive=3,headerLightBackground=4,headerBackground=5,headerLightText=6,headerText=7;}
 static class PropertyId {static final int NONE=0;}
 static class Theme {static boolean isDark(){return dark;}static boolean dark,blue; static int getColor(int id){return id==1?(dark?(blue?0xff1c2733:0xff000000):0xffffffff):id==2?(dark?0xffaeb8c0:0xff454b51):(dark?0xff64b5ef:0xff194b70);}static float getProperty(int id){return 1;}static float getSubtitleAlpha(){return .7f;} }
 static class ColorUtils {
  static int alphaColor(float f,int c){return (Math.round(((c>>>24)&255)*f)<<24)|(c&0xffffff);}
  static int fromToArgb(int a,int b,float f){int c=0;for(int s:new int[]{0,8,16,24}) c|=Math.round(((a>>>s)&255)*(1-f)+((b>>>s)&255)*f)<<s;return c;}
 }
 static class Counter {float mute;float getMuteFactor(){return mute;}}
 static class Top {
  int fromTextColorId=2,toTextColorId=3,fromTextColorAlphaId=0,fromIndex=-1,toIndex=-1;
  float disabledFactor,selectionFactor;boolean glassSelection=true;
  METHODS
 }
 static class Header {Top top=new Top();Top getTopView(){return top;}}
 Header headerCell=new Header();boolean displayTabsAtBottom(){return false;}boolean hasFloatingHeader(){return true;}int getPagerItemPosition(long id){return id>=10&&id<=12?(int)id-10:NO_POSITION;}
 COLORS
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  MainController parent=new MainController();
  for(boolean dark:new boolean[]{false,true})for(boolean blue:new boolean[]{false,true}){
   Theme.dark=dark;Theme.blue=blue;parent.headerCell.top.selectionFactor=0;
   check(parent.headerCell.top.getItemTextColor(0)==Theme.getColor(dark&&!blue?ColorId.glassIcon:ColorId.glassActive),"selected glass tab has the wrong role");
   check(parent.headerCell.top.getItemTextColor(1)==Theme.getColor(dark&&!blue?ColorId.glassActive:ColorId.glassIcon),"inactive glass tab has the wrong role");
  }
  for(boolean dark:new boolean[]{false,true})for(boolean blue:new boolean[]{false,true}){
   Theme.dark=dark;Theme.blue=blue;
   for(boolean skip:new boolean[]{false,true}){
    parent.headerCell.top.fromIndex=skip?0:-1;parent.headerCell.top.toIndex=skip?2:-1;
    for(float progress:new float[]{0,.25f,.5f,.75f,1,1.5f,2}){
     parent.headerCell.top.selectionFactor=progress;
     for(int index=0;index<3;index++)for(float mute:new float[]{0,.5f,1}){
      UnreadCounterColorSet colors=UnreadCounterColorSet.create(parent,10+index);Counter c=new Counter();c.mute=mute;colors.setCounter(c);
      float selected=parent.headerCell.top.getItemSelectionFactor(index);int label=parent.headerCell.top.getItemTextColor(index);
      check(colors.backgroundColor()==label,"counter belongs to another tab's colour");
      check(colors.defaultTextColor()==Theme.getColor(ColorId.filling),"all badge numbers must contrast with their own fill");check(colors.backgroundColor(false)==label,"inactive or muted tab lost its matching badge circle");
      if(selected==1&&mute==0){check(colors.defaultTextColor()==Theme.getColor(ColorId.filling),"selected number must contrast with accent fill");check(colors.backgroundColor(false)==label,"selected fill must match selected label");}
      check(colors.outlineColor(false)==0,"floating badges must not add an unrelated outline");
     }
    }
   }
  }
 }
}
'''

if __name__ == '__main__':
    unittest.main()
