package org.sharkk2.sengine.core.classes;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Bounds {
    public final Vector3f localMin = new Vector3f();
    public final Vector3f localMax = new Vector3f();
    public final Vector3f localCenters = new Vector3f();
    public final Vector3f localExtents = new Vector3f();
    public final Vector3f worldCenter = new Vector3f();
    public final Matrix3f obbAxes = new Matrix3f();
    public final Vector3f obbHalfExtents = new Vector3f();
    public final Vector3f worldMin = new Vector3f();
    public final Vector3f worldMax = new Vector3f();
    public float boundingRadius = 1.0f; // *moved it from model component to here

    private final Matrix4f lastWorldMatrix = new Matrix4f().zero();
    private boolean initialized = false;

    public void computeLocals(float[] vertices) {
        localMin.set(Float.MAX_VALUE);
        localMax.set(-Float.MAX_VALUE);
        float maxDistSq = 0;
        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
            if (x < localMin.x) localMin.x = x;
            if (y < localMin.y) localMin.y = y;
            if (z < localMin.z) localMin.z = z;
            if (x > localMax.x) localMax.x = x;
            if (y > localMax.y) localMax.y = y;
            if (z > localMax.z) localMax.z = z;
            float d = x*x + y*y + z*z;
            if (d > maxDistSq) maxDistSq = d;
        }

        boundingRadius = (float) Math.sqrt(maxDistSq);

        float cx = (localMin.x + localMax.x) * 0.5f; //midpoint formula
        float cy = (localMin.y + localMax.y) * 0.5f;
        float cz = (localMin.z + localMax.z) * 0.5f;
        float ex = (localMax.x - localMin.x) * 0.5f; // extents
        float ey = (localMax.y - localMin.y) * 0.5f;
        float ez = (localMax.z - localMin.z) * 0.5f;
        localCenters.set(cx, cy, cz);
        localExtents.set(ex, ey, ez);

    }

    public void update(Matrix4f worldMatrix) {
        if (initialized && worldMatrix.equals(lastWorldMatrix)) return;
        lastWorldMatrix.set(worldMatrix);
        initialized = true;

        // world center
        worldMatrix.transformPosition(localCenters.x, localCenters.y, localCenters.z, worldCenter);

        // extract rotation+scale (3x3 upper left), split into unit axes + scale-adjusted extents
        Vector3f col0 = new Vector3f(worldMatrix.m00(), worldMatrix.m01(), worldMatrix.m02());
        Vector3f col1 = new Vector3f(worldMatrix.m10(), worldMatrix.m11(), worldMatrix.m12());
        Vector3f col2 = new Vector3f(worldMatrix.m20(), worldMatrix.m21(), worldMatrix.m22());
        float sx = col0.length(); float sy = col1.length(); float sz = col2.length();

        // sep the baked axis and scale so we can put the raw directions in obbAxes
        col0.div(sx == 0 ? 1:sx);
        col1.div(sy == 0 ? 1:sy);
        col2.div(sz == 0 ? 1:sz);

        obbAxes.set(col0, col1, col2);
        obbHalfExtents.set(localExtents.x * sx, localExtents.y * sy, localExtents.z * sz); // stretch them extents

        // for each world axis, extent = sum of projection of each OBB axis * that axis's half extent
        float wx = Math.abs(col0.x) * obbHalfExtents.x + Math.abs(col1.x) * obbHalfExtents.y + Math.abs(col2.x) * obbHalfExtents.z;

        float wy = Math.abs(col0.y) * obbHalfExtents.x + Math.abs(col1.y) * obbHalfExtents.y + Math.abs(col2.y) * obbHalfExtents.z;

        float wz = Math.abs(col0.z) * obbHalfExtents.x + Math.abs(col1.z) * obbHalfExtents.y + Math.abs(col2.z) * obbHalfExtents.z;

        worldMin.set(worldCenter.x - wx, worldCenter.y - wy, worldCenter.z - wz);
        worldMax.set(worldCenter.x + wx, worldCenter.y + wy, worldCenter.z + wz);

    }


}