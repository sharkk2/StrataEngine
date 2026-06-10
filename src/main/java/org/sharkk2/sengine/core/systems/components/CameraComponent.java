package org.sharkk2.sengine.core.systems.components;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.sharkk2.sengine.core.classes.Component;

public class CameraComponent extends Component {
    private float fov = 60f;
    private float near = 0.01f;
    private float far = 1000f;

    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f right = new Vector3f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();
    private final Vector3f direction = new Vector3f(0, 0, -1);

    public Matrix4f getViewMatrix() {
        Vector3f center = new Vector3f(owner.transform.getPosition()).add(direction);
        viewMatrix.identity().lookAt(owner.transform.getPosition(), center, up);
        return viewMatrix;
    }

    public void getViewMatrix(Matrix4f out) {
        Vector3f center = new Vector3f(owner.transform.getPosition()).add(direction);
        out.identity().lookAt(owner.transform.getPosition(), center, up);
    }

    public Matrix4f getProjectionMatrix(float aspectRatio) {
        projMatrix.identity().perspective((float) Math.toRadians(fov), aspectRatio, near, far);
        return projMatrix;
    }

    public void getProjectionMatrix(Matrix4f out, float aspectRatio) {
        out.identity().perspective((float) Math.toRadians(fov), aspectRatio, near, far);
    }

    private void calculateDirection() {
        float radYaw = (float) Math.toRadians(getYaw());
        float radPitch = (float) Math.toRadians(getPitch());
        direction.set((float) (Math.cos(radPitch) * Math.cos(radYaw)), (float) Math.sin(radPitch), (float) (Math.cos(radPitch) * Math.sin(radYaw))).normalize();
        right.set(direction).cross(new Vector3f(0, 1, 0)).normalize();
        up.set(right).cross(direction).normalize();
    }

    public void setPitchYaw(float pitch, float yaw) {
        owner.transform.pitch = pitch;
        owner.transform.yaw = yaw;
        calculateDirection();
    }


    public float getYaw() { return owner.transform.yaw; }
    public float getPitch() { return owner.transform.pitch; }
    public void setFov(float fov) { this.fov = fov; }
    public float getFov() { return fov; }
    public void setNear(float near) { this.near = near; }
    public float getNear() { return near; }
    public void setFar(float far) { this.far = far; }
    public float getFar() { return far; }
    public Vector3f getDirection() {return new Vector3f(direction);}
    public void getDirection(Vector3f out) {out.set(direction);}

    @Override
    protected void onObjectAttach() {}

    @Override
    protected void onObjectDetach() {}

    @Override
    protected void onUpdate() {

    }
}