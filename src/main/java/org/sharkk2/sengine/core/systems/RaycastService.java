package org.sharkk2.sengine.core.systems;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;

import java.util.List;

public class RaycastService {
    private final Engine engine;
    private final Vector3f axisX = new Vector3f(), axisY = new Vector3f(), axisZ = new Vector3f(), rDir = new Vector3f();
    private final Vector4f clipScratch = new Vector4f();
    private final Matrix4f invProjMatScratch = new Matrix4f();
    private final Matrix4f invViewMatScratch = new Matrix4f();


    public RaycastService(Engine engine) {
        this.engine = engine;
    }

    public float rayIntersects(Vector3f origin, Vector3f dir, Bounds bounds) {
        float tmin = 0;
        float tmax = Float.MAX_VALUE;
        bounds.obbAxes.getColumn(0, axisX);
        bounds.obbAxes.getColumn(1, axisY);
        bounds.obbAxes.getColumn(2, axisZ);

        Vector3f toOrigin = new Vector3f(origin).sub(bounds.worldCenter);
        float[] o = {toOrigin.dot(axisX), toOrigin.dot(axisY), toOrigin.dot(axisZ)};
        float[] d = {dir.dot(axisX), dir.dot(axisY), dir.dot(axisZ)};                   // handles rotation
        float[] h = {bounds.obbHalfExtents.x, bounds.obbHalfExtents.y, bounds.obbHalfExtents.z};
        // we use ts formula "point = origin + direction * t", where 't' is how far along the axis
        // so we just solve for t: (point - origin) / d, and with that we basically "crushed" the obb boundaries on the ray's axis
        // now for an intersection to happen the latest entry "highest min" should be less than the earliest exit "smallest max" and ofc these are the t's we just found
        // ts is called a slab test

        // you can try to visualize ts by imagining a line in ur head pointed at a box from an angle, then imagine a big square at each side,
        // where each square is a plane for x, y, z axes, and the opposite side of that side(the min side) is the max plane
        // now you can imagine which square the line went through, you'd find that everytime the line indeed intersected, it always went through a max square
        // after it went through the last min square, if it didn't then ur line never touched the box

        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-6f) { // what if the ray is parallel? it may not cross the boundaries of the obb!
                if (o[i] < -h[i] || o[i] > h[i]) return -1; // we can simply check if its not between the boundary planes of the axis, if so, its not intersecting and we do an early return
            } else {
                float t1 = (-h[i] - o[i]) / d[i];
                float t2 = (h[i] - o[i]) / d[i];
                if (t1 > t2) {float tmp = t1; t1 = t2; t2 = tmp;} // t1 should always be min and t2 always max
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return -1;
            }
        }

        return tmin; // distance along the ray to the first hit
    }


    public GameObject castRay(Vector3f position, Vector3f direction, List<GameObject> scanPool, float maxDistance, boolean onlyVisible) {
        direction.normalize();
        GameObject closest = null;
        float closestT = maxDistance;
        for (GameObject go : scanPool) {
            if (!go.hasComponent(ModelComponent.class)) continue;
            ModelComponent model = go.getComponent(ModelComponent.class);
            if (!model.isVisible() && onlyVisible) continue;
            float t = rayIntersects(position, direction, model.bounds);
            if (t >= 0.01f && t < closestT) {
                closestT = t;
                closest = go;
            }
        }
        return closest;
    }

    public Vector3f calculateScreenRay(float sX, float sY, Matrix4f projMatrix, Matrix4f viewMatrix) {
        // reverses the transformations we apply for 3D coordinates to crush it into 2D "just uncrushes it back"
        float x = (2.0f * sX) / engine.getWindowWidth() - 1.0f;
        float y = 1.0f - (2.0f * sY) / engine.getWindowHeight();
        // NDC Space -> Clip Space
        clipScratch.set(x, y, -1.0f, 1.0f);
        // Clip Space -> Eye Space
        invProjMatScratch.set(projMatrix).invert();
        Vector4f eyeCoords = invProjMatScratch.transform(clipScratch);
        eyeCoords.set(eyeCoords.x, eyeCoords.y, -1.0f, 0.0f);

        invViewMatScratch.set(viewMatrix).invert();
        Vector4f rayWorld = invViewMatScratch.transform(eyeCoords);
        return new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z).normalize();
    }



    public GameObject castScreenRay(float x, float y, Vector3f position, Matrix4f projMatrix, Matrix4f viewMatrix, List<GameObject> scanPool, float maxDistance, boolean onlyVisible) {
        rDir.set(calculateScreenRay(x, y, projMatrix, viewMatrix));
        GameObject closest = null;
        float closestT = maxDistance;
        for (GameObject go : scanPool) {
            if (!go.hasComponent(ModelComponent.class)) continue;
            ModelComponent model = go.getComponent(ModelComponent.class);
            if (!model.isVisible() && onlyVisible) continue;
            float t = rayIntersects(position, rDir, model.bounds);
            if (t >= 0.01f && t < closestT) {
                closestT = t;
                closest = go;
            }
        }
        return closest;
    }

}
