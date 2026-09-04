package org.sharkk2.game;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.CollisionService;
import org.sharkk2.sengine.core.systems.InputService;
import org.sharkk2.sengine.core.systems.components.ColliderComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.Renderer;
import org.sharkk2.sengine.core.systems.components.CameraComponent;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class PlayerController extends Component {
    private float velocityX, velocityZ, velocityY;
    private float speedMultiplier = 1f;
    private float dragSensitivityMultiplier = 1.2f;
    private boolean camLock = false;
    private boolean moveLock = false;
    private boolean flyMode = true;

    private boolean grounded = false;
    private float bobTimer = 0f;
    private float bobOffsetY = 0f;



    private final List<GameObject> droppedCameras = new ArrayList<>();
    private int cameraIndex = -1; // -1 = the player's own camera, 0..n-1 = index into droppedCameras

    @Override
    protected void onObjectAttach() {
        owner.getEngine().getInputService().lockMouse(true);
    }

    @Override
    protected void onObjectDetach() {}

    @Override
    public Component copy() { return new PlayerController(); }

    @Override
    protected void onUpdate() {
        Engine engine = owner.getEngine();
        InputService input = engine.getInputService();
        float dt = engine.getDeltaTime();

        CameraComponent camera = owner.getComponent(CameraComponent.class);
        if (camera == null) return;

        if (input.isKeyPressed(GLFW_KEY_F7)) {
            dropCamera(engine, camera);
        }

        if (input.isKeyPressed(GLFW_KEY_F8)) {
            cycleCamera(engine, camera);
        }

        if (input.isKeyPressed(GLFW_KEY_F6) && cameraIndex != -1 && !droppedCameras.isEmpty()) {
            engine.getSceneManager().getActiveScene().removeObject(droppedCameras.get(cameraIndex));
            droppedCameras.remove(cameraIndex);
            cameraIndex--;
            if (cameraIndex == -1) {engine.getCameraService().setPrimaryCamera(camera);}
            else {
                engine.getCameraService().setPrimaryCamera(droppedCameras.get(cameraIndex).getComponent(CameraComponent.class));
            }


        }

        if (cameraIndex != -1) {return;}
        double scroll = input.getScrollDY();
        if (scroll != 0) {
            speedMultiplier *= (float) Math.pow(1.1f, scroll);
        }
        speedMultiplier = Math.clamp(speedMultiplier, 0.1f, 100f);

        float maxSpeed = engine.getValue("controls.max_speed") * dt * speedMultiplier;
        float accel = engine.getValue("controls.acceleration") * dt;
        float friction = (float) Math.pow(engine.getValue("controls.friction"), dt * 60);

        // snapshot last frame's grounded state; move() will recompute the real one below
        boolean wasGrounded = grounded;
        grounded = false;

        if (input.isKeyPressed(GLFW_KEY_Y)) {
            flyMode = !flyMode;
        }

        float nyaw;
        float npitch;
        if (!engine.getIO("mouse_visible")) {
            float dx = input.getMouseDX() * engine.getValue("controls.mouse_sensitivity");
            float dy = input.getMouseDY() * engine.getValue("controls.mouse_sensitivity");

            nyaw = ((camera.getYaw() + dx) % 360f + 360f) % 360f;
            npitch = Math.clamp(camera.getPitch() + dy, -89f, 89f);

            if (!camLock && engine.getCameraService().getPrimaryCamera().getID().equals(camera.getID())) {
                camera.setDirection(npitch, nyaw);
            }
        } else {
            float dx = input.getMouseDX() * engine.getValue("controls.mouse_sensitivity") * dragSensitivityMultiplier;
            float dy = input.getMouseDY() * engine.getValue("controls.mouse_sensitivity") * dragSensitivityMultiplier;

            nyaw = ((camera.getYaw() + dx) % 360f + 360f) % 360f;
            npitch = Math.clamp(camera.getPitch() + dy, -89f, 89f);
            if (input.isMouseDown(GLFW_MOUSE_BUTTON_MIDDLE)) {
                if (!camLock && engine.getCameraService().getPrimaryCamera().getID().equals(camera.getID())) {
                    camera.setDirection(npitch, nyaw);
                }
            }
        }

        float radYaw = (float) Math.toRadians(nyaw);
        float forwardX = (float) Math.cos(radYaw);
        float forwardZ = (float) Math.sin(radYaw);
        float rightX = (float) -Math.sin(radYaw);
        float rightZ = (float) Math.cos(radYaw);

        float moveX = 0, moveZ = 0;
        if (input.isKeyDown(GLFW_KEY_W) && !input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) { moveX += forwardX; moveZ += forwardZ; }
        if (input.isKeyDown(GLFW_KEY_S) && !input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) { moveX -= forwardX; moveZ -= forwardZ; }
        if (input.isKeyDown(GLFW_KEY_A) && !input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) { moveX -= rightX; moveZ -= rightZ; }
        if (input.isKeyDown(GLFW_KEY_D) && !input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) { moveX += rightX; moveZ += rightZ; }
        float moveMag = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moveMag > 0) {
            moveX /= moveMag;
            moveZ /= moveMag;
        }

        velocityX = (velocityX + moveX * accel) * friction;
        velocityZ = (velocityZ + moveZ * accel) * friction;
        float speed = (float) Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        if (speed > maxSpeed) {
            float scale = maxSpeed / speed;
            velocityX *= scale;
            velocityZ *= scale;
        }

        if (flyMode) {
            if (input.isKeyDown(GLFW_KEY_SPACE)) {
                velocityY = maxSpeed;
            } else if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
                velocityY = -maxSpeed;
            } else {
                velocityY *= friction;
            }
        } else {
            float gravity = engine.getValue("controls.gravity") * dt;
            float jumpForce = engine.getValue("controls.jump_force") * dt;

            if (wasGrounded && input.isKeyPressed(GLFW_KEY_SPACE)) {
                velocityY = jumpForce;
            } else {
                velocityY -= gravity;
            }
        }

        float bobFrequency = engine.getValue("controls.bob_frequency");
        float bobAmplitude = engine.getValue("controls.bob_amplitude");
        float horizontalSpeed = (float) Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);

        if (!flyMode && wasGrounded && horizontalSpeed > 0.0001f) {
            bobTimer += dt * bobFrequency * (horizontalSpeed / Math.max(maxSpeed, 0.0001f));
            bobOffsetY = (float) Math.sin(bobTimer * Math.PI * 2f) * bobAmplitude;
        } else {
            bobTimer = 0f;
            bobOffsetY *= (float) Math.pow(0.001f, dt);
        }

        camera.setPositionOffset(new Vector3f(0f, bobOffsetY, 0f));

        if (!moveLock) {
            move(velocityX, velocityY, velocityZ);
        }

        if (input.isKeyPressed(GLFW_KEY_K)) {
            engine.getAudioService().playAudio(engine.getAssetLoader().loadAudioFile("src/main/resources/audio/ripmygranny.wav"));
        }

        if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            System.exit(0);
        }
    }

    private void dropCamera(Engine engine, CameraComponent mainCamera) {
        Vector3f pos = owner.transform.getPosition();

        GameObject camObject = new GameObject(engine);
        camObject.setName("dropped_camera_" + (droppedCameras.size() + 1));
        camObject.transform.setPosition(pos.x, pos.y, pos.z);

        CameraComponent newCamera = new CameraComponent();
        newCamera.name = "dropped_camera_" + (droppedCameras.size() + 1);
        camObject.attachComponent(newCamera);
        newCamera.setDirection(mainCamera.getPitch(), mainCamera.getYaw());
        engine.getSceneManager().getActiveScene().addObject(camObject);
        droppedCameras.add(camObject);

        Logger.info("Dropped camera #" + droppedCameras.size() + " at " + pos);
    }

    private void cycleCamera(Engine engine, CameraComponent mainCamera) {
        if (droppedCameras.isEmpty()) return;

        cameraIndex++;
        if (cameraIndex >= droppedCameras.size()) {
            cameraIndex = -1;
            engine.getCameraService().setPrimaryCamera(mainCamera);
            return;
        }

        CameraComponent nextCamera = droppedCameras.get(cameraIndex).getComponent(CameraComponent.class);
        engine.getCameraService().setPrimaryCamera(nextCamera);
    }

    private static final float AXIS_EPS = 1e-4f;

    private void move(float dx, float dy, float dz) {
        CollisionService col = owner.getEngine().getCollisionService();

        if (dx != 0f) {
            owner.transform.transformPos(dx, 0f, 0f);
            float corr = col.maxAxisPenetration(col.checkCollisionAll(owner), 0);
            if (Math.abs(corr) > AXIS_EPS) {
                owner.transform.transformPos(corr, 0f, 0f);
                if (opposes(dx, corr)) velocityX = 0f;
            }
        }

        if (dy != 0f) {
            owner.transform.transformPos(0f, dy, 0f);
            float corr = col.maxAxisPenetration(col.checkCollisionAll(owner), 1);
            if (Math.abs(corr) > AXIS_EPS) {
                owner.transform.transformPos(0f, corr, 0f);
                if (corr > 0f) grounded = true;
                if (opposes(dy, corr)) velocityY = 0f;
            }
        }

        if (dz != 0f) {
            owner.transform.transformPos(0f, 0f, dz);
            float corr = col.maxAxisPenetration(col.checkCollisionAll(owner), 2);
            if (Math.abs(corr) > AXIS_EPS) {
                owner.transform.transformPos(0f, 0f, corr);
                if (opposes(dz, corr)) velocityZ = 0f;
            }
        }
    }

    private boolean opposes(float delta, float corr) {
        return (delta > 0f && corr < 0f) || (delta < 0f && corr > 0f);
    }

    public void setCamLock(boolean locked) { this.camLock = locked; }
    public void setMoveLock(boolean locked) { this.moveLock = locked; }
    public boolean isCamLocked() { return camLock; }
    public boolean isMoveLock() { return moveLock; }
    public void setFlyMode(boolean flyMode) { this.flyMode = flyMode; }
    public boolean isFlyMode() { return flyMode; }
}