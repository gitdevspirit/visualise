package myau.util.math;

/** Minimal mutable 4D vector; replaces javax.vecmath.Vector4d, which Forge shipped but vanilla/Fabric does not. */
public class Vector4d {
    public double x, y, z, w;

    public Vector4d(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
}
