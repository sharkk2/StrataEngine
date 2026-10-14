package org.sharkk2.game;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.AssetLoader;
import org.sharkk2.sengine.core.systems.CollisionService;
import org.sharkk2.sengine.core.systems.InputService;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class PlayerController extends Component {

    private float velocityX, velocityZ, velocityY;
    private float speedMultiplier = 1f;
    private boolean flyMode = false;
    private boolean grounded = false;
    private boolean camLock = false;
    private boolean moveLock = false;
    private boolean sprinting = false;

    private static final float DRAG_SENSITIVITY_MULTIPLIER = 1.2f;
    private static final float ROLL_VELOCITY_FACTOR = 0.08f;
    private static final float MAX_LOOK_ROLL = 2.3f;
    private static final float ROLL_SMOOTH_SPEED = 8f;
    private static final float MAX_LEAN_ROLL = 12f;
    private static final float MAX_LEAN_OFFSET = 0.6f;
    private static final float LEAN_SMOOTH_SPEED = 6f;
    private static final float AXIS_EPS = 1e-4f;
    private static final float MIN_MOVE_SPEED = 0.0001f;
    private static final float FOOTSTEP_PITCH = 1f;
    private static final float GROUNDED_LOSS_GRACE = 0.15f;
    private static final float STICK_DEADZONE = 0.25f;

    private float roll = 0f;
    private float leanAmount = 0f;
    private float speedRatio = 0f;
    private float ungroundedTimer = 0f;

    private boolean footSwitch = true;
    private int lastBobStep = 0;
    private float bobTimer = 0f;
    private float bobOffsetY = 0f;


    private AssetLoader.AudioData leftFoot;
    private AssetLoader.AudioData rightFoot;

    @Override
    protected void onObjectAttach() {
        owner.getEngine().getInputService().lockMouse(true);
        leftFoot = owner.getEngine().getAssetLoader().loadAudioFile("assets/audio/leftFootRug.wav");
        rightFoot = owner.getEngine().getAssetLoader().loadAudioFile("assets/audio/rightFootRug.wav");
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
        Scene scene = engine.getSceneManager().getActiveScene();
        CameraComponent camera = owner.getComponent(CameraComponent.class);
        if (camera == null || scene.getPrimaryCamera() == null) return;
        if (!scene.getPrimaryCamera().equals(camera)) return;

        double scroll = input.getScrollDY();
        if (scroll != 0 && input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
            speedMultiplier *= (float) Math.pow(1.1f, scroll);
        }
        speedMultiplier = Math.clamp(speedMultiplier, 0.1f, 100f);

        if (input.isKeyPressed(GLFW_KEY_Y)) flyMode = !flyMode;
        sprinting = !flyMode && (input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isGamepadBtnDown(GLFW_GAMEPAD_BUTTON_LEFT_THUMB));


        float sprintMultiplier = sprinting ? engine.getValue("controls.sprint_multiplier") : 1f;
        float maxSpeed = engine.getValue("controls.max_speed") * dt * speedMultiplier * sprintMultiplier;
        float accel = engine.getValue("controls.acceleration") * dt;
        float friction = (float) Math.pow(engine.getValue("controls.friction"), dt * 60);

        boolean wasGrounded = grounded;
        grounded = false;

        float targetLean = 0f;
        if (input.isKeyDown(GLFW_KEY_Q) || input.isGamepadBtnDown(GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) targetLean -= 1f;
        if (input.isKeyDown(GLFW_KEY_E) || input.isGamepadBtnDown(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) targetLean += 1f;
        leanAmount += (targetLean - leanAmount) * Math.min(1f, LEAN_SMOOTH_SPEED * dt);

        float sensitivity = engine.getValue("controls.mouse_sensitivity");
        boolean mouseVisible = engine.getIO("mouse_visible");
        float dragScale = mouseVisible ? DRAG_SENSITIVITY_MULTIPLIER : 1f;

        float dx, dy;
        if (!input.isGamepadConnected()) {
            dx = input.getMouseDX() * sensitivity * dragScale;
            dy = input.getMouseDY() * sensitivity * dragScale;
        } else {
            float rightStickX = input.getRightStickX();
            float rightStickY = -input.getRightStickY();
            if (Math.abs(rightStickX) < STICK_DEADZONE) rightStickX = 0;
            if (Math.abs(rightStickY) < STICK_DEADZONE) rightStickY = 0;

            float gamepadLookSpeed = engine.getValue("controls.gamepad_look_speed");
            dx = rightStickX * gamepadLookSpeed * dt;
            dy = rightStickY * gamepadLookSpeed * dt;
        }


        float nyaw = ((camera.getYaw() + dx) % 360f + 360f) % 360f;
        float npitch = Math.clamp(camera.getPitch() + dy, -89f, 89f);

        float rollScale = sprinting ? sprintMultiplier : 1f;
        float yawSpeed = dx / dt;
        float targetRoll = Math.clamp(-yawSpeed * ROLL_VELOCITY_FACTOR * rollScale, -MAX_LOOK_ROLL * rollScale, MAX_LOOK_ROLL * rollScale);
        roll += (targetRoll - roll) * Math.min(1f, ROLL_SMOOTH_SPEED * dt);
        float totalRoll = roll + leanAmount * MAX_LEAN_ROLL * rollScale;

        boolean allowedToLook = !mouseVisible || input.isMouseBtnDown(GLFW_MOUSE_BUTTON_MIDDLE);
        boolean isPrimary = scene.getPrimaryCamera().getID().equals(camera.getID());
        if (!camLock && isPrimary && allowedToLook) {
            camera.setDirection(npitch, nyaw, totalRoll);
        }

        float radYaw = (float) Math.toRadians(nyaw);
        float forwardX = (float) Math.cos(radYaw);
        float forwardZ = (float) Math.sin(radYaw);
        float rightX = (float) -Math.sin(radYaw);
        float rightZ = (float) Math.cos(radYaw);

        boolean ctrl = input.isKeyDown(GLFW_KEY_LEFT_CONTROL);
        float moveX = 0, moveZ = 0;
        if (!input.isGamepadConnected()) {
            if (!ctrl && input.isKeyDown(GLFW_KEY_W)) { moveX += forwardX; moveZ += forwardZ; }
            if (!ctrl && input.isKeyDown(GLFW_KEY_S)) { moveX -= forwardX; moveZ -= forwardZ; }
            if (!ctrl && input.isKeyDown(GLFW_KEY_A)) { moveX -= rightX; moveZ -= rightZ; }
            if (!ctrl && input.isKeyDown(GLFW_KEY_D)) { moveX += rightX; moveZ += rightZ; }
        } else {
            float stickX = input.getLeftStickX();
            float stickY = input.getLeftStickY();
            if (Math.abs(stickX) < STICK_DEADZONE) stickX = 0;
            if (Math.abs(stickY) < STICK_DEADZONE) stickY = 0;

            if (!ctrl && stickY < 0) { moveX += forwardX * -stickY; moveZ += forwardZ * -stickY; }
            if (!ctrl && stickY > 0) { moveX -= forwardX * stickY; moveZ -= forwardZ * stickY; }
            if (!ctrl && stickX != 0) { moveX += rightX * stickX; moveZ += rightZ * stickX; }
        }


        float moveMag = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moveMag > 0) {
            moveX /= moveMag;
            moveZ /= moveMag;
        }

        velocityX = (velocityX + moveX * accel) * friction;
        velocityZ = (velocityZ + moveZ * accel) * friction;
        float horizontalSpeed = (float) Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        if (horizontalSpeed > maxSpeed) {
            float scale = maxSpeed / horizontalSpeed;
            velocityX *= scale;
            velocityZ *= scale;
            horizontalSpeed = maxSpeed;
        }

        float rampTime = Math.max(engine.getValue("controls.speed_ramp_time"), 0.0001f);
        float rampStep = dt / rampTime;
        speedRatio += moveMag > 0 ? rampStep : -rampStep;
        speedRatio = Math.clamp(speedRatio, 0f, 1f);
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

            if (wasGrounded && (input.isKeyPressed(GLFW_KEY_SPACE) || input.isGamepadBtnDown(GLFW_GAMEPAD_BUTTON_CROSS))) {
                velocityY = jumpForce;
            } else {
                velocityY -= gravity;
            }
        }

        float bobAmplitude = engine.getValue("controls.bob_amplitude");
        float dynamicBobFrequency = engine.getValue("controls.bob_frequency") * speedRatio;
        boolean shouldBob = !flyMode && horizontalSpeed > MIN_MOVE_SPEED;

        if (shouldBob && wasGrounded) {
            ungroundedTimer = 0f;
        } else {
            ungroundedTimer += dt;
        }

        if (shouldBob && ungroundedTimer < GROUNDED_LOSS_GRACE) {
            bobTimer += dt * dynamicBobFrequency;
            float phase = bobTimer % 1f;
            float raw = (float) (0.5f - 0.5f * Math.cos(phase * Math.PI * 2f));
            float shaped = (float) Math.pow(raw, 2.2f);
            bobOffsetY = -shaped * bobAmplitude;

            // one footstep per full bob cycle, phase-locked instead of using a separate clock
            int currentBobStep = (int) bobTimer;
            if (currentBobStep != lastBobStep) {
                lastBobStep = currentBobStep;
                float walkVolume = engine.getValue("controls.walk_footstep_volume");
                float sprintVolume = engine.getValue("controls.sprint_footstep_volume");
                float volume = sprinting ? sprintVolume : walkVolume;
                AssetLoader.AudioData clip = footSwitch ? leftFoot : rightFoot;
                engine.getAudioService().playAudio(clip, volume, FOOTSTEP_PITCH);
                footSwitch = !footSwitch;
            }
        } else {
            bobTimer = 0f;
            lastBobStep = 0;
            bobOffsetY *= (float) Math.pow(0.001f, dt);
        }

        Vector3f leanOffset = new Vector3f(rightX, 0f, rightZ).mul(leanAmount * MAX_LEAN_OFFSET);
        camera.setPositionOffset(new Vector3f(leanOffset.x, bobOffsetY + 2, leanOffset.z));


        if (!moveLock) move(velocityX, velocityY, velocityZ);

        if (input.isKeyPressed(GLFW_KEY_K)) {
            engine.getAudioService().playAudio(engine.getAssetLoader().loadAudioFile("assets/audio/ripmygranny.wav"), 1f, FOOTSTEP_PITCH);
        }
    }


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
    public boolean isSprinting() { return sprinting; }
}