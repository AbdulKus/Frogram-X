"""Popular pack groups keep their boundaries when pages are appended or replaced."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2]


class TrendingPackCardsTest(unittest.TestCase):
    def test_pack_boundaries_and_pagination(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/widget/TrendingPackCards.java').read_text()
        update = method(source, 'private void updateGroups (')
        with tempfile.TemporaryDirectory(prefix='frogram-pack-groups-') as directory:
            path = Path(directory) / 'PackTest.java'
            path.write_text(FIXTURE.replace('UPDATE', update))
            compiled = subprocess.run([shutil.which('javac'), str(path)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            ran = subprocess.run([shutil.which('java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(ran.returncode, 0, ran.stderr)


FIXTURE = '''import java.util.*;
public class PackTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class MediaStickersAdapter {
  static class StickerHolder {static final int TYPE_HEADER_TRENDING=1,TYPE_STICKER=2,TYPE_EMPTY=3;}
  int[] types={};int queries;
  int getItemCount(){return types.length;}
  int getItemViewType(int p){queries++;return types[p];}
 }
 static class Cards {
  MediaStickersAdapter adapter=new MediaStickersAdapter();ArrayList<int[]> groups=new ArrayList<>();
  int[] groupForPosition={};boolean dirty=true;
  UPDATE
  void expect(int[] membership,int[][] ranges){
   updateGroups();check(Arrays.equals(groupForPosition,membership)&&groups.size()==ranges.length);
   for(int i=0;i<ranges.length;i++)check(Arrays.equals(groups.get(i),ranges[i]));
  }
 }
 public static void main(String[] args){
  Cards cards=new Cards();cards.expect(new int[]{},new int[][]{});
  // Progress/separators/help and orphan cells are outside the rounded packs.
  cards.adapter.types=new int[]{9,2,1,2,2,3,8,1,2,9};
  cards.expect(new int[]{-1,-1,0,0,0,0,-1,1,1,-1},new int[][]{{2,5},{7,8}});
  int queries=cards.adapter.queries;cards.updateGroups();check(queries==cards.adapter.queries);
  cards.adapter.types=new int[]{1,2,8,1,2,2,8,1,3};cards.dirty=true;
  cards.expect(new int[]{0,0,-1,1,1,1,-1,2,2},new int[][]{{0,1},{3,5},{7,8}});
  // Same item count with new section boundaries must also invalidate the cache.
  cards.adapter.types=new int[]{1,2,2,8,1,2,8,1,3};cards.dirty=true;
  cards.expect(new int[]{0,0,0,-1,1,1,-1,2,2},new int[][]{{0,2},{4,5},{7,8}});
  cards.adapter.types=new int[]{9};cards.dirty=true;cards.expect(new int[]{-1},new int[][]{});
 }
}'''
