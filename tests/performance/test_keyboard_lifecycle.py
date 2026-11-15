"""Exercise production keyboard listeners and custom-emoji receiver lifetimes."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_pinned_previews import method

ROOT = Path(__file__).resolve().parents[2] / "app/src/main/java/org/thunderdog/challegram"


class KeyboardLifecycleTest(unittest.TestCase):
    def test_hidden_and_detached_keyboard_releases_work_and_can_reopen(self):
        emoji = (ROOT / "widget/CustomEmojiTextView.java").read_text()
        keyboard = (ROOT / "component/chat/CommandKeyboardLayout.java").read_text()
        emoji_methods = "\n".join(method(emoji, signature) for signature in [
            "private void setMediaAttached (", "private void updateMediaAttachment (",
            "protected void onAttachedToWindow (", "protected void onDetachedFromWindow (",
            "protected void onVisibilityChanged (", "protected void onWindowVisibilityChanged (",
            "public void performDestroy (",
        ])
        keyboard_methods = "\n".join(method(keyboard, signature) for signature in [
            "public void onViewAttachedToWindow (", "private void removePreDrawListener (",
            "public void onViewDetachedFromWindow (", "public void performDestroy (",
        ])
        fixture = FIXTURE.replace("EMOJI_METHODS", emoji_methods).replace("KEYBOARD_METHODS", keyboard_methods)
        with tempfile.TemporaryDirectory(prefix="frogram-keyboard-lifecycle-") as directory:
            target = Path(directory) / "KeyboardLifecycleRegression.java"
            target.write_text(fixture)
            result = subprocess.run([shutil.which("javac"), str(target)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which("java"), "-cp", directory, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r"""
import java.util.*;
public class KeyboardLifecycleRegression {
  static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
  interface Destroyable {void performDestroy();}
  static class View {static final int VISIBLE=0;}
  static class Base extends View {
    Object token;boolean shown=true;int windowVisibility=VISIBLE,baseDestroys;
    Object getWindowToken(){return token;}boolean isShown(){return shown;}int getWindowVisibility(){return windowVisibility;}
    protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}
    protected void onVisibilityChanged(View v,int n){}protected void onWindowVisibilityChanged(int n){}
    public void performDestroy(){baseDestroys++;}
  }
  static class Receiver {boolean attached;int attaches,detaches;void attach(){attached=true;attaches++;}void detach(){attached=false;detaches++;}}
  static class Holder {Receiver receiver=new Receiver();int destroys;void performDestroy(){destroys++;}}
  static class Emoji extends Base {
    Holder mediaHolder=new Holder();boolean mediaAttached,destroyed;
    EMOJI_METHODS
  }
  static class ViewTreeObserver {
    boolean alive=true;Set<Object> listeners=new HashSet<>();boolean isAlive(){return alive;}
    void addOnPreDrawListener(Object o){check(listeners.add(o),"duplicate frame listener");}
    void removeOnPreDrawListener(Object o){listeners.remove(o);}
  }
  static class Child extends View implements Destroyable {int destroys;public void performDestroy(){destroys++;}}
  static class Keyboard {
    ViewTreeObserver observer,current=new ViewTreeObserver();boolean destroyed;int keyboardState=2,framesDropped=7,removed;
    Object callback=new Object();Child[] children={new Child(),new Child()};
    ViewTreeObserver getViewTreeObserver(){return current;}int getChildCount(){return children.length;}View getChildAt(int i){return children[i];}
    void removeOnAttachStateChangeListener(Object o){removed++;}
    KEYBOARD_METHODS
  }
  public static void main(String[] args){
    Emoji e=new Emoji();e.onWindowVisibilityChanged(0);check(!e.mediaHolder.receiver.attached,"unattached labels must stay inactive");
    e.token=new Object();e.onAttachedToWindow();check(e.mediaHolder.receiver.attached,"visible labels attach");
    e.shown=false;e.onVisibilityChanged(new View(),8);check(!e.mediaHolder.receiver.attached,"closing the keyboard pauses receivers");
    e.shown=true;e.onVisibilityChanged(new View(),0);check(e.mediaHolder.receiver.attached,"reopening resumes receivers");
    e.windowVisibility=8;e.onWindowVisibilityChanged(8);check(!e.mediaHolder.receiver.attached,"background windows pause receivers");
    e.windowVisibility=0;e.onWindowVisibilityChanged(0);check(e.mediaHolder.receiver.attached,"foreground windows resume receivers");
    e.onDetachedFromWindow();check(!e.mediaHolder.receiver.attached,"detach must pause before the window token is cleared");
    e.onAttachedToWindow();e.performDestroy();e.performDestroy();e.onAttachedToWindow();
    check(!e.mediaHolder.receiver.attached&&e.mediaHolder.destroys==1&&e.baseDestroys==1,"destroy must be final and idempotent");
    Emoji early=new Emoji();early.mediaHolder=null;early.token=new Object();early.onVisibilityChanged(new View(),0);
    Keyboard k=new Keyboard();k.onViewAttachedToWindow(new View());ViewTreeObserver old=k.current;
    k.onViewAttachedToWindow(new View());check(old.listeners.size()==1,"reattach must replace its listener");
    k.current=new ViewTreeObserver();k.onViewAttachedToWindow(new View());check(old.listeners.isEmpty()&&k.current.listeners.size()==1,"moving trees must release the old observer");
    k.onViewDetachedFromWindow(new View());check(k.current.listeners.isEmpty()&&k.observer==null&&k.keyboardState==0&&k.framesDropped==0,"leaving a chat releases its observer and pending keyboard work");
    k.onViewAttachedToWindow(new View());k.performDestroy();k.performDestroy();k.onViewAttachedToWindow(new View());
    check(k.current.listeners.isEmpty()&&k.callback==null&&k.removed==1,"destroyed keyboard must not retain callbacks or reattach");
    for(Child child:k.children)check(child.destroys==1,"removed keyboard releases each emoji label once");
  }
}
"""
