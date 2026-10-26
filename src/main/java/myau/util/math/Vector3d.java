package myau.util.math;

/** Minimal mutable 3D vector; replaces javax.vecmath.Vector3d, which Forge shipped but vanilla/Fabric does not. */
public class Vector3d {
    public double x, y, z;

    public Vector3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
