"""Exercise production navigation readiness with both callback orderings."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_pinned_previews import method

REPO = Path(__file__).resolve().parents[2]
JAVA = REPO / "app/src/main/java/org/thunderdog/challegram"


class ChatReadinessTest(unittest.TestCase):
    def test_early_signal_viewport_and_draft_gate(self):
        manager = (JAVA / "component/chat/MessagesManager.java").read_text(encoding="utf-8")
        controller = (JAVA / "ui/MessagesController.java").read_text(encoding="utf-8")
        view = (JAVA / "navigation/ViewController.java").read_text(encoding="utf-8")
        manager_methods = "\n".join(method(manager, signature) for signature in [
            "public boolean hasNavigationReadySignal (", "private void onChatAwaitFinish (",
            "private boolean hasInitialViewportContent (", "private void loadFromStart (MessageId",
            "private void loadFromMessage (MessageId",
        ])
        controller_methods = "\n".join(method(controller, signature) for signature in [
            "public boolean needAsynchronousAnimation (", "protected boolean canExecuteScheduledAnimation (",
            "private void finishDraftReply (",
        ])
        navigation_methods = "\n".join(method(view, signature) for signature in [
            "public final void scheduleAnimation (", "protected final void resetScheduledAnimation (",
            "public final Runnable getScheduledAnimation (", "public final void executeScheduledAnimation (",
        ]).replace("@NonNull ", "")
        source = FIXTURE.replace("MANAGER_METHODS", manager_methods).replace("CONTROLLER_METHODS", controller_methods).replace("NAVIGATION_METHODS", navigation_methods)
        source = source.replace("org.thunderdog.challegram.util.PerformanceTrace", "PerformanceTrace")
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK required")
        with tempfile.TemporaryDirectory(prefix="frogram-readiness-") as directory:
            target = Path(directory) / "ChatReadinessRegression.java"
            target.write_text(source, encoding="utf-8")
            compiled = subprocess.run([javac, "-encoding", "UTF-8", str(target)], capture_output=True, text=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            result = subprocess.run([java, "-cp", directory, "ChatReadinessRegression"], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("Readiness regression passed", result.stdout)


FIXTURE = r"""
import java.util.*;
public class ChatReadinessRegression {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static class PerformanceTrace {static void begin(String s){}static void end(){}}
  static class TGMessage {int height;TGMessage(int h){height=h;}int getHeight(){return height;}}
  static class MessageId {boolean end;MessageId(boolean e){end=e;}boolean isHistoryEnd(){return end;}}
  static class Adapter {int count;int getMessageCount(){return count;}void reset(TGMessage m){count=m==null?0:1;}}
  static class Loader {void loadFromStart(MessageId id){}void loadFromMessage(MessageId id,int mode,boolean force){}}
  static class Manager {
    Controller controller;Adapter adapter=new Adapter();Loader loader=new Loader();
    boolean navigationReadySignal,awaitingForPinnedMessages,totallyEmpty;int viewport=1000;
    Manager(Controller c){controller=c;c.manager=this;}
    Adapter getAdapter(){return adapter;}boolean isTotallyEmpty(){return totallyEmpty;}
    int getRecyclerHeight(){return viewport;}void viewMessages(boolean b){}void scrollToPositionWithOffsetImpl(int p,int o){}
    MANAGER_METHODS
  }
  static class Ui {List<Runnable> delayed=new ArrayList<>();void postDelayed(Runnable r,long t){delayed.add(r);}}
  static class Tdlib {Ui ui=new Ui();Ui ui(){return ui;}}
  static class Controller {
    Manager manager;boolean pendingDraftReply;int draftReplyRequest=1,starts;Tdlib tdlib=new Tdlib();
    Runnable scheduledAnimation;ArrayList<Runnable> animationExecuteListeners;
    boolean isDestroyed(){return false;}
    CONTROLLER_METHODS
    NAVIGATION_METHODS
    void navigate(){
      if(!needAsynchronousAnimation()){starts++;return;}
      scheduleAnimation(new Runnable(){public void run(){
        if(getScheduledAnimation()==this){resetScheduledAnimation();starts++;}
      }},500);
    }
    void fireOldTimeouts(){for(Runnable r:tdlib.ui.delayed)r.run();}
  }
  static List<TGMessage> rows(int... heights){List<TGMessage> out=new ArrayList<>();for(int h:heights)out.add(new TGMessage(h));return out;}
  public static void main(String[] args){
    Controller early=new Controller();Manager m=new Manager(early);
    check(early.needAsynchronousAnimation(),"unready history must wait");
    m.onChatAwaitFinish();early.navigate();
    check(early.starts==1&&early.getScheduledAnimation()==null,"early readiness must not be lost before runnable registration");
    m.loadFromStart(new MessageId(true),null);
    check(!m.hasNavigationReadySignal()&&early.needAsynchronousAnimation(),"new chat load must reset readiness");
    early.navigate();check(early.starts==1,"wait for the new context");
    m.onChatAwaitFinish();m.onChatAwaitFinish();early.fireOldTimeouts();
    check(early.starts==2,"late/repeated signal and obsolete timeout must start once");
    m.loadFromMessage(new MessageId(false),0,true);
    check(!m.hasNavigationReadySignal(),"forced anchor load must reset readiness");
    m.onChatAwaitFinish();m.loadFromMessage(new MessageId(false),0,false);
    check(m.hasNavigationReadySignal(),"in-place non-forced load retains readiness");

    Controller draft=new Controller();Manager dm=new Manager(draft);draft.pendingDraftReply=true;
    dm.onChatAwaitFinish();draft.navigate();
    check(draft.starts==0,"early history must not bypass draft reply");
    dm.onChatAwaitFinish();check(draft.starts==0,"late history must not bypass draft reply");
    draft.finishDraftReply(0);check(draft.starts==0&&draft.pendingDraftReply,"stale reply result must not release navigation");
    draft.finishDraftReply(1);draft.fireOldTimeouts();check(draft.starts==1,"resolved reply starts once");

    Controller empty=new Controller();Manager em=new Manager(empty);em.totallyEmpty=true;
    check(!empty.needAsynchronousAnimation(),"known empty chat must open");
    empty.manager=null;check(!empty.needAsynchronousAnimation(),"missing manager remains supported");

    check(m.hasInitialViewportContent(rows(600,400),0,null,null),"full local bottom viewport can open");
    check(m.hasInitialViewportContent(rows(1000),0,null,new MessageId(true)),"history-end anchor can open");
    check(!m.hasInitialViewportContent(rows(999),0,null,null),"short local page must keep waiting");
    check(!m.hasInitialViewportContent(rows(2000),1,null,null),"restored position must keep original policy");
    check(!m.hasInitialViewportContent(rows(2000),0,new TGMessage(1),null),"highlight target must keep original policy");
    check(!m.hasInitialViewportContent(rows(2000),0,null,new MessageId(false)),"unread/history-start anchor must keep original policy");
    check(!m.hasInitialViewportContent(rows(),0,null,null),"empty page must keep waiting");
    m.viewport=0;check(!m.hasInitialViewportContent(rows(2000),0,null,null),"unknown viewport must keep waiting");
    m.viewport=Integer.MAX_VALUE;check(m.hasInitialViewportContent(rows(Integer.MAX_VALUE-1,100),0,null,null),"height sum must not overflow");
    System.out.println("Readiness regression passed");
  }
}
"""
