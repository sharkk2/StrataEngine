package org.sharkk2.sengine.core.systems.components;

import imgui.extension.imguizmo.flag.Mode;
import org.joml.*;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

import static org.sharkk2.sengine.Logger.getCallerClassName;

public class CameraComponent extends Component {
    private float fov = 60f;
    private float near = 0.01f;
    private float far = 1000f;

    private float pitch = 0f;
    private float yaw = 0f;
    private float roll = 0f;
    public boolean depthOfField = false;
    private float focusDistance = 15;
    private float apertureFNumber = 1.8f;
    private float focalLengthMillim = 50;
    private float vignette = 0.5f;

    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f right = new Vector3f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();
    private final Vector3f direction = new Vector3f(0, 0, -1);
    private final Vector3f positionOffset = new Vector3f();
    private final FrustumIntersection frustumIntersection = new FrustumIntersection();
    public final List<ModelComponent> invisibleModels = new ArrayList<>();
    private int colorGradingLUT = -1;
    private int lensDirtTexture = -1;

    public void setColorGradingLUT(int cubemapTex) {
        if (cubemapTex >= 0) colorGradingLUT = cubemapTex;
    }
    public int getColorGradingLUT() {return colorGradingLUT;}
    public void setLensDirtTexture(int texture) {
        if (texture >= 0) lensDirtTexture = texture;
    }
    public int getLensDirtTexture() {return lensDirtTexture;}

    public enum CompassDirection {
        NORTH, SOUTH, EAST, WEST
    }

    public Matrix4f getViewMatrix() {
        Vector3f eye = new Vector3f(owner.transform.getPosition()).add(positionOffset);
        Vector3f center = new Vector3f(eye).add(direction);
        viewMatrix.identity().lookAt(eye, center, up);
        return viewMatrix;
    }

    public void getViewMatrix(Matrix4f out) {
        Vector3f eye = new Vector3f(owner.transform.getPosition()).add(positionOffset);
        Vector3f center = new Vector3f(eye).add(direction);
        out.identity().lookAt(eye, center, up);
    }

    public Matrix4f getProjectionMatrix(float aspectRatio) {
        projMatrix.identity().perspective((float) Math.toRadians(fov), aspectRatio, near, far);
        return projMatrix;
    }

    public void getProjectionMatrix(Matrix4f out, float aspectRatio) {
        out.identity().perspective((float) Math.toRadians(fov), aspectRatio, near, far);
    }

    public Vector3f getEyePosition() {
        return new Vector3f(owner.transform.getPosition()).add(positionOffset);
    }

    public void getEyePosition(Vector3f out) {
        out.set(owner.transform.getPosition()).add(positionOffset);
    }

    private void calculateDirection() {
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float radRoll = (float) Math.toRadians(roll);

        direction.set(
                (float) (Math.cos(radPitch) * Math.cos(radYaw)),
                (float) Math.sin(radPitch),
                (float) (Math.cos(radPitch) * Math.sin(radYaw))
        ).normalize();

        right.set(direction).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f worldUp = new Vector3f(right).cross(direction).normalize();

        Quaternionf rollRotation = new Quaternionf().rotationAxis(radRoll, direction);
        rollRotation.transform(worldUp, up);
        rollRotation.transform(right);

        owner.transform.applyOrientation(direction, up);
    }


    public void setDirection(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;
        calculateDirection();
    }

    public void setDirection(float pitch, float yaw, float roll) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        calculateDirection();
    }

    public void setPositionOffset(Vector3f offset) {
        this.positionOffset.set(offset);
    }

    public Vector3f getPositionOffset() {
        return new Vector3f(positionOffset);
    }

    public boolean inFrustum(GameObject object, float aspectRatio, float radius) {
        Matrix4f proj = getProjectionMatrix(aspectRatio);
        Matrix4f view = getViewMatrix();
        frustumIntersection.set(proj.mul(view, new Matrix4f()));

        Matrix4f world = object.transform.calculateWorldMatrix();
        float wx = world.m30(), wy = world.m31(), wz = world.m32();
        float maxScale = Math.max(object.transform.getWidth(), Math.max(object.transform.getHeight(), object.transform.getDepth()));
        return frustumIntersection.testSphere(wx, wy, wz, radius * maxScale);
    }

    public CompassDirection getCompass() {
        float normalized = ((getYaw() % 360) + 360) % 360;
        int sector = (int)((normalized + 45) / 90) % 4;
        return CompassDirection.values()[sector];
    }

    public float getCompassFloat() {
        float normalized = getYaw() % 360;
        if (normalized < 0) normalized += 360;
        int[] order = {0, 1, 3, 2};
        int sector = (int)(normalized / 90f) % 4;
        float frac = (normalized % 90f) / 90f;
        return order[sector] + frac;
    }

    public void lookAt(Vector3f position) {
        float dx = position.x - owner.transform.getX();
        float dy = position.y - owner.transform.getY();
        float dz = position.z - owner.transform.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) Math.toDegrees(Math.atan2(dy, distance));
        setDirection(pitch, yaw);
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setFov(float fov) { this.fov = fov; }
    public float getFov() { return fov; }
    public void setNear(float near) { this.near = near; }
    public float getNear() { return near; }
    public void setFar(float far) { this.far = far; }
    public float getFar() { return far; }
    public Vector3f getDirection() {return new Vector3f(direction);}
    public void getDirection(Vector3f out) {out.set(direction);}
    public Vector3f getUp() {return new Vector3f(up);}
    public void getUp(Vector3f out) {out.set(up);}
    public float getRoll() {return roll;}

    public void setFocusDistance(float dist) {this.focusDistance = dist;}
    public void setFocalLength(float millimeters) {this.focalLengthMillim = millimeters;}
    public void setApertureFNumber(float fnum) {this.apertureFNumber = fnum;}
    public float getApertureFNumber() {return apertureFNumber;}
    public float getFocusDistance() {return focusDistance;}
    public float getFocalLength() {return focalLengthMillim;}

    public float getVignette() {return vignette;}
    public void setVignette(float vignette) {this.vignette = Math.clamp(vignette, 0, 1);}


    @Override
    protected void onObjectAttach() {
        calculateDirection();
    }

    @Override
    protected void onObjectDetach() {
        Scene scene = owner.getEngine().getSceneManager().getActiveScene();
        if (scene != null && scene.getPrimaryCamera() != null && scene.getPrimaryCamera().equals(this)) {
           scene.setPrimaryCamera(null);
        }
    }

    @Override
    protected void onUpdate() {

    }

    @Override
    public Component copy() {
        CameraComponent copy = new CameraComponent();
        copy.name = name + "_copy";
        copy.pitch = pitch;
        copy.yaw = yaw;
        copy.setFar(far);
        copy.setFov(fov);
        copy.setNear(near);
        copy.setPositionOffset(positionOffset);
        copy.setLensDirtTexture(lensDirtTexture);
        copy.setColorGradingLUT(colorGradingLUT);
        copy.setVignette(vignette);
        copy.depthOfField = depthOfField;
        copy.setApertureFNumber(apertureFNumber);
        copy.setFocusDistance(focusDistance);
        copy.setFocalLength(focalLengthMillim);
        return copy;
    }
}