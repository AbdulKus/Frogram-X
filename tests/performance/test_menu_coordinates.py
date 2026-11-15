"""Run the production view-coordinate mapper against translated/scrolled/scaled trees."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class MenuCoordinateTest(unittest.TestCase):
    def test_animated_menu_uses_the_drawn_coordinate_system(self):
        source = (ROOT / 'app/src/main/java/org/thunderdog/challegram/util/ViewCoordinateMapper.java').read_text()
        source = re.sub(r'^(?:package|import) .*;\n', '', source, flags=re.M)
        source = source.replace('public final class ViewCoordinateMapper', 'static final class ViewCoordinateMapper', 1)
        with tempfile.TemporaryDirectory(prefix='frogram-menu-coordinates-') as directory:
            target = Path(directory) / 'MenuCoordinatesRegression.java'
            target.write_text(FIXTURE.replace('PRODUCTION_SOURCE', source))
            result = subprocess.run([shutil.which('javac'), str(target)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([shutil.which('java'), '-cp', directory, target.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


FIXTURE = r'''
import java.awt.geom.*;
public class MenuCoordinatesRegression {
  static void equal(float a,float b){if(Math.abs(a-b)>.002)throw new AssertionError(a+" != "+b);}
  interface ViewParent {}
  static class RectF {float left,top,right,bottom;RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
  static class Matrix {
    AffineTransform m=new AffineTransform();void reset(){m.setToIdentity();}
    void preTranslate(float x,float y){m.translate(x,y);}void preConcat(Matrix other){m.concatenate(other.m);}
    boolean isIdentity(){return m.isIdentity();}
    boolean invert(Matrix inverse){try{inverse.m=m.createInverse();return true;}catch(NoninvertibleTransformException e){return false;}}
    void mapPoints(float[] p){m.transform(p,0,p,0,p.length/2);}
    void mapRect(RectF r){float[] p={r.left,r.top,r.right,r.top,r.right,r.bottom,r.left,r.bottom};mapPoints(p);r.left=r.right=p[0];r.top=r.bottom=p[1];for(int i=2;i<p.length;i+=2){r.left=Math.min(r.left,p[i]);r.right=Math.max(r.right,p[i]);r.top=Math.min(r.top,p[i+1]);r.bottom=Math.max(r.bottom,p[i+1]);}}
    void scale(float x,float y,float px,float py){reset();m.translate(px,py);m.scale(x,y);m.translate(-px,-py);}
  }
  static class View implements ViewParent {
    View parent;int left,top,scrollX,scrollY;Matrix matrix=new Matrix();
    View(View p,int l,int t){parent=p;left=l;top=t;}ViewParent getParent(){return parent;}
    int getLeft(){return left;}int getTop(){return top;}int getScrollX(){return scrollX;}int getScrollY(){return scrollY;}
    Matrix getMatrix(){return matrix;}View getRootView(){return parent==null?this:parent.getRootView();}
  }
  PRODUCTION_SOURCE
  public static void main(String[] args){
    View root=new View(null,100,80);root.scrollX=5;root.scrollY=7;
    View header=new View(root,40,60);header.scrollX=12;header.scrollY=20;header.matrix.preTranslate(15,-10);
    View tab=new View(header,22,16);tab.matrix.scale(2,3,5,7);
    View overlay=new View(root,0,0);overlay.scrollX=3;overlay.scrollY=9;overlay.matrix.preTranslate(10,15);overlay.matrix.m.scale(.5,.5);
    View menu=new View(overlay,30,40);ViewCoordinateMapper mapper=new ViewCoordinateMapper();
    for(float scale:new float[]{.8f,.94f,.97f,1f}){
      menu.matrix.scale(scale,scale,100,50);float[] point={10,20};
      if(!mapper.mapPoint(tab,menu,point))throw new AssertionError("same tree rejected");
      equal(100+13/scale,point[0]);equal(50+73/scale,point[1]);
      if(!mapper.mapPoint(menu,tab,point))throw new AssertionError("inverse mapping rejected");
      equal(10,point[0]);equal(20,point[1]);
    }
    menu.matrix.scale(.8f,.8f,100,50);RectF bounds=new RectF(0,0,10,20);
    mapper.mapRect(tab,menu,bounds);equal(66.25f,bounds.left);equal(-8.75f,bounds.top);equal(116.25f,bounds.right);equal(141.25f,bounds.bottom);
    header.top+=37;float[] shifted={10,20};mapper.mapPoint(tab,menu,shifted);equal(233.75f,shifted[1]);
    menu.matrix.scale(0,0,0,0);float[] unchanged={10,20};if(mapper.mapPoint(tab,menu,unchanged))throw new AssertionError("singular target accepted");equal(10,unchanged[0]);equal(20,unchanged[1]);
    if(mapper.mapPoint(tab,new View(null,0,0),unchanged))throw new AssertionError("different windows accepted");
    View simpleRoot=new View(null,0,0),source=new View(simpleRoot,10,20),rtl=new View(simpleRoot,0,0);rtl.matrix.scale(-1,1,200,0);
    float[] mirrored={10,30};mapper.mapPoint(source,rtl,mirrored);equal(380,mirrored[0]);equal(50,mirrored[1]);
    source.left=20;source.top=40;source.matrix.m.rotate(Math.PI/2);float[] rotated={10,5};mapper.mapPoint(source,simpleRoot,rotated);equal(15,rotated[0]);equal(50,rotated[1]);
  }
}
'''
