"""Regression for production AssertionError:36 in rich message page previews."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/org/thunderdog/challegram'


class PageSpacerTest(unittest.TestCase):
    def test_all_simple_page_blocks_have_measurable_heights(self):
        holder = (JAVA / 'ui/SettingHolder.java').read_text()
        page = (JAVA / 'data/PageBlock.java').read_text()
        items = (JAVA / 'ui/ListItem.java').read_text()
        constants = '\n'.join(re.findall(r'public static final int TYPE_\w+ = -?\d+;', items))
        # Check every spacer actually constructed by the parser, including ternaries.
        types = set()
        for call in re.findall(r'new PageBlockSimple\([^;\n]+', page):
            types.update(re.findall(r'ListItem\.(TYPE_\w+)', call))
        self.assertIn('TYPE_EMPTY_OFFSET_NO_HEAD', types)
        cases = ','.join('ListItem.' + name for name in sorted(types))
        source = '''
public class SpacerRegression {
 static class ListItem { CONSTANTS }
 static class Screen { static float density; static int dp(float n){return Math.round(n*density);} }
 static class Size { static int getMaximumHeaderSizeDifference(){return Screen.dp(88f);} }
 static class DetachedChatHeaderView { static int getViewHeight(){return Screen.dp(72f);} }
 static class GiftHeaderView { static int getDefaultHeight(){return Screen.dp(96f);} }
 MEASURE
 public static void main(String[] args) {
   for(float d:new float[]{1f,2.75f,3f,3.5f}) {
     Screen.density=d;
     for(int type:new int[]{CASES}) if(measureHeightForType(type)<0) throw new AssertionError(type);
     if(measureHeightForType(36)!=Screen.dp(12f)) throw new AssertionError("preview spacer height");
     if(measureHeightForType(ListItem.TYPE_EMPTY_OFFSET)!=Screen.dp(12f)+Size.getMaximumHeaderSizeDifference()) throw new AssertionError("header spacer changed");
   }
   try {measureHeightForType(100000);throw new IllegalStateException("unknown types silently accepted");} catch(AssertionError expected){}
 }
}
'''.replace('CONSTANTS', constants).replace('MEASURE', method(holder, 'public static int measureHeightForType (int viewType)')).replace('CASES', cases)
        with tempfile.TemporaryDirectory(prefix='frogram-page-spacers-') as temp:
            target = Path(temp) / 'SpacerRegression.java'
            target.write_text(source)
            result = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', temp, 'SpacerRegression'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
