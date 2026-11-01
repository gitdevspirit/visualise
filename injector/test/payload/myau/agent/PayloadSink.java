package myau.agent;
import visualise.boot.Sink;
public class PayloadSink implements Sink {
    private int clicks;
    public void startGameHead()   { System.out.println("  payload: startGameHead"); }
    public void startGameReturn() { System.out.println("  payload: startGameReturn"); myau.mod.Thing.poke(); }
    public void runTickHead()     { System.out.println("  payload: runTickHead"); }
    public void runTickReturn()   { System.out.println("  payload: runTickReturn"); }
    public void loadWorld()       { System.out.println("  payload: loadWorld"); }
    public boolean clickMouse()   { boolean cancel = ++clicks == 2; System.out.println("  payload: clickMouse -> cancel=" + cancel); return cancel; }
    public void resize()          { System.out.println("  payload: resize"); }
    public void render3D(float p) { System.out.println("  payload: render3D " + p); }
    public void render2D(float p) { System.out.println("  payload: render2D " + p); }
    public void keyState(int k, boolean s) { System.out.println("  payload: keyState " + k + " " + s); }
}
