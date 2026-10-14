"""Execute the production list/preview methods with small surrounding JVM fixtures."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[2]
CHAT = REPO / "app/src/main/java/org/thunderdog/challegram/component/chat"


def method(source, signature):
    start = source.index(signature)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end].replace("@Nullable ", "")


class PreviewRegressionTest(unittest.TestCase):
    def test_repeated_updates_reuse_rows_and_update_retained_title(self):
        pinned = (CHAT / "PinnedMessagesBar.java").read_text(encoding="utf-8")
        preview = (CHAT / "MessagePreviewView.java").read_text(encoding="utf-8")
        pinned_methods = "\n".join(method(pinned, signature) for signature in [
            "private void clearMessageList (", "public void setMessageList (",
            "public void setStaticMessageList (",
        ])
        source = FIXTURE.replace("PINNED_METHODS", pinned_methods)
        source = source.replace("DISPLAY_METHOD", method(preview, "private void setDisplayData ("))
        source = source.replace("TITLE_METHOD", method(preview, "public boolean setForcedTitle ("))
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK required")
        with tempfile.TemporaryDirectory(prefix="frogram-preview-regression-") as directory:
            target = Path(directory) / "PreviewRegression.java"
            target.write_text(source, encoding="utf-8")
            compiled = subprocess.run([javac, "-encoding", "UTF-8", str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run([java, "-cp", directory, "PreviewRegression"], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("Preview regression passed", result.stdout)


FIXTURE = r"""
import java.util.*;
public class PreviewRegression {
  static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
  static class RecyclerView { static final int NO_POSITION=-1; LinearLayoutManager manager=new LinearLayoutManager(); Object getLayoutManager(){return manager;} }
  static class LinearLayoutManager { int scrolls; void scrollToPositionWithOffset(int p,int o){scrolls++;} }
  static class TdApi { static class Message {long chatId=1;} }
  static class Entry {int id,subscribed,destroyed;Entry(int id){this.id=id;}void subscribeToUpdates(){subscribed++;}void performDestroy(){destroyed++;}public boolean equals(Object o){return o instanceof Entry&&((Entry)o).id==id;} }
  static class ListItem { }
  static class MessageListManager extends ArrayList<TdApi.Message> {
    int added,removed,loaded;int getTotalCount(){return size();}int getCount(){return size();}Object tdlib(){return null;}
    void addChangeListener(Object o){added++;}void removeChangeListener(Object o){removed++;}void loadInitialChunk(Object o){loaded++;}
  }
  static class Animator {void setValue(boolean v,boolean a){}void forceFactor(float f){} }
  static class Adapter {int changes,count;void setItems(List<ListItem> l,boolean a){changes++;count=l.size();}void setItems(ListItem[] l,boolean a){changes++;count=l.length;} }
  static class Pinned {
    List<Entry> staticList;MessageListManager messageList;int focusIndex;RecyclerView recyclerView=new RecyclerView();
    Adapter messagesAdapter=new Adapter();Animator canExpand=new Animator(),countAnimator=new Animator();
    void collapse(boolean b){} ListItem itemOf(Entry e){return new ListItem();}ListItem itemOf(Object t,TdApi.Message m){return new ListItem();}
    PINNED_METHODS
  }
  static class StringUtils {static boolean equalsOrBothEmpty(String a,String b){return Objects.equals(a,b)||(a==null||a.isEmpty())&&(b==null||b.isEmpty());} }
  static class BitwiseUtils {static boolean hasFlag(int n,int f){return (n&f)!=0;} }
  static class Options {static final int NO_UPDATES=1,DISABLE_MESSAGE_PREVIEW=2;}
  static class Link {void removeReference(Object o){}void addReference(Object o){}boolean isLoading(){return false;} }
  static class MessageId {MessageId(TdApi.Message m){} }
  static class Media {void replace(Object o,boolean a){}void measure(boolean b){} }
  static class DisplayData {
    TdApi.Message message=new TdApi.Message();Object quote,forcedLocalPickedFile,filter;int options;Link linkPreview;String forcedTitle;
    DisplayData(String title){forcedTitle=title;}boolean equalsTo(TdApi.Message m,Object q,int o,Link l,Object f){return true;}void performDestroy(){}
    TITLE_METHOD
  }
  static class Preview {
    DisplayData data;int titleUpdates,builds;Object contentPreview;Media mediaPreview=new Media();
    void updateTitleText(){titleUpdates++;}void unsubscribeFromUpdates(TdApi.Message m){}void subscribeToUpdates(TdApi.Message m){}
    void buildPreview(){builds++;}void setPreviewChatId(Object a,long c,Object b,MessageId m,Object f){}void clearPreviewChat(){}void invalidate(){}
    DISPLAY_METHOD
  }
  public static void main(String[] args) {
    Pinned p=new Pinned();Entry first=new Entry(1);
    p.setStaticMessageList(Collections.singletonList(first),-1);
    for(int n=0;n<100;n++)p.setStaticMessageList(Collections.singletonList(new Entry(1)),-1);
    check(p.messagesAdapter.changes==1,"unchanged previews rebuild adapter");check(first.subscribed==1,"retained entry subscribed repeatedly");
    p.setStaticMessageList(Collections.singletonList(new Entry(1)),3);
    check(p.recyclerView.manager.scrolls==1&&p.messagesAdapter.changes==1,"explicit focus must scroll without rebuilding");
    p.setStaticMessageList(Collections.singletonList(new Entry(2)),-1);
    check(first.destroyed==1&&p.messagesAdapter.changes==2,"changed entries replace and clear old state");
    p.setMessageList(null);
    check(p.staticList==null&&p.messagesAdapter.count==0,"clearing static preview must bypass dynamic identity shortcut");
    MessageListManager dynamic=new MessageListManager();dynamic.add(new TdApi.Message());p.setMessageList(dynamic);int changes=p.messagesAdapter.changes;
    p.setMessageList(dynamic);check(p.messagesAdapter.changes==changes&&dynamic.added==1&&dynamic.loaded==1,"same dynamic manager must be reused");
    p.setStaticMessageList(Collections.singletonList(new Entry(3)),-1);check(dynamic.removed==1,"switching list kind detaches dynamic listener");
    p.setStaticMessageList(Collections.emptyList(),-1);check(p.staticList==null&&p.messagesAdapter.count==0,"empty static list clears rows");
    Preview preview=new Preview();DisplayData retained=new DisplayData("before");preview.setDisplayData(retained);int builds=preview.builds;
    preview.setDisplayData(new DisplayData("after"));check(preview.data==retained&&"after".equals(retained.forcedTitle),"changed title must update retained data");
    check(preview.titleUpdates==1&&preview.builds==builds,"title update must not rebuild equal content");
    preview.setDisplayData(new DisplayData("after"));check(preview.titleUpdates==1,"unchanged title must not animate again");
    System.out.println("Preview regression passed: 100 duplicate updates reused rows; replacement, clearing, dynamic lists and retained titles verified");
  }
}
"""


if __name__ == "__main__":
    unittest.main()
