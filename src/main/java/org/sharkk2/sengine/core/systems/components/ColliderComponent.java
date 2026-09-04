package org.sharkk2.sengine.core.systems.components;

import org.joml.Vector3f;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.classes.Component;

public class ColliderComponent extends Component {
    private final Bounds bounds;
    public ColliderComponent(Bounds bounds) {
        this.bounds = bounds;
    }

    public Bounds getBounds() {return bounds;}

    public boolean quickOverlap(Bounds other) {
        return bounds.worldMin.x <= other.worldMax.x && bounds.worldMax.x >= other.worldMin.x
                && bounds.worldMin.y <= other.worldMax.y && bounds.worldMax.y >= other.worldMin.y
                && bounds.worldMin.z <= other.worldMax.z && bounds.worldMax.z >= other.worldMin.z;
    }

    public Vector3f testCollision(Bounds other) {
        // https://dyn4j.org/2010/01/sat/
        // "If, for all axes, the shape's projections overlap, then we can conclude that the shapes are intersecting."
        // !warning: this method can be a pressure point on the GC, fix that later

        if (!quickOverlap(other)) return null; // cheap AABB reject

        Vector3f ax0 = new Vector3f(); bounds.obbAxes.getColumn(0, ax0);
        Vector3f ax1 = new Vector3f(); bounds.obbAxes.getColumn(1, ax1);
        Vector3f ax2 = new Vector3f(); bounds.obbAxes.getColumn(2, ax2);
        Vector3f bx0 = new Vector3f(); other.obbAxes.getColumn(0, bx0);
        Vector3f bx1 = new Vector3f(); other.obbAxes.getColumn(1, bx1);
        Vector3f bx2 = new Vector3f(); other.obbAxes.getColumn(2, bx2);

        Vector3f[] aAxes = {ax0, ax1, ax2};
        Vector3f[] bAxes = {bx0, bx1, bx2};

        Vector3f[] testAxes = new Vector3f[15]; // could use a List??
        int count = 0;
        for (Vector3f a : aAxes) testAxes[count++] = a;
        for (Vector3f b : bAxes) testAxes[count++] = b;
        for (Vector3f a : aAxes) {
            for (Vector3f b : bAxes) {
                Vector3f cross = new Vector3f(a).cross(b);
                if (cross.lengthSquared() < 1e-6f) continue; // edges parallel here, axis is degenerate, skip it
                testAxes[count++] = cross.normalize();
            }
        }

        float minOverlap = Float.MAX_VALUE;
        Vector3f mtvAxis = null;

        for (int i = 0; i < count; i++) {
            Vector3f axis = testAxes[i];
            float overlap = overlapOnAxis(bounds, other, axis);
            if (overlap <= 0) return null; // found a separating axis, no collision

            if (overlap < minOverlap) {
                minOverlap = overlap;
                mtvAxis = axis;
            }
        }

        if (mtvAxis == null) return null;

        Vector3f centerDelta = new Vector3f(bounds.worldCenter).sub(other.worldCenter); // this - other, not other - this
        if (centerDelta.dot(mtvAxis) < 0) mtvAxis.negate();

        return mtvAxis.mul(minOverlap);
    }

    private  float overlapOnAxis(Bounds a, Bounds b, Vector3f axis) {
        float radiusA = projectedRadius(a, axis);
        float radiusB = projectedRadius(b, axis);
        float centerA = a.worldCenter.dot(axis);
        float centerB = b.worldCenter.dot(axis);
        return (radiusA + radiusB) - Math.abs(centerA - centerB);
    }

    private float projectedRadius(Bounds box, Vector3f axis) {
        Vector3f col = new Vector3f();
        box.obbAxes.getColumn(0, col);
        float r = Math.abs(col.dot(axis)) * box.obbHalfExtents.x;
        box.obbAxes.getColumn(1, col);
        r += Math.abs(col.dot(axis)) * box.obbHalfExtents.y;
        box.obbAxes.getColumn(2, col);
        r += Math.abs(col.dot(axis)) * box.obbHalfExtents.z;
        return r;
    }

    @Override
    protected void onObjectAttach() {
        owner.getEngine().getCollisionService().register(this);
    }

    @Override
    protected void onObjectDetach() {
        owner.getEngine().getCollisionService().unregister(this);
    }

    @Override
    protected void onUpdate() {}

    @Override
    public Component copy() {
        return new ColliderComponent(bounds);
    }
}
