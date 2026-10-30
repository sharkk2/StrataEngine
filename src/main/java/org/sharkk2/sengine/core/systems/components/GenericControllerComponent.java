package org.sharkk2.sengine.core.systems.components;

import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.InputService;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class GenericControllerComponent extends Component {

    private float speedMultiplier = 1f;
    private boolean camLock = false;
    private boolean moveLock = false;

    private final List<GameObject> droppedCameras = new ArrayList<>();
    private int cameraIndex = -1;

    @Override
    protected void onObjectAttach() {
        owner.getEngine().getInputService().lockMouse(false);
    }

    @Override
    protected void onObjectDetach() {}

    @Override
    public Component copy() { return new GenericControllerComponent(); }

    @Override
    protected void onUpdate() {
        Engine engine = owner.getEngine();
        CameraComponent camera = owner.getComponent(CameraComponent.class);
        Scene scene = engine.getSceneManager().getActiveScene();
        if (camera == null || scene == null) return;
        if (scene.getPrimaryCamera() == null) {
            scene.setPrimaryCamera(camera);
        }
        InputService input = engine.getInputService();
        float dt = engine.getDeltaTime();


        if (input.isKeyPressed(GLFW_KEY_F7)) dropCamera(engine, camera);
        if (input.isKeyPressed(GLFW_KEY_F8)) cycleCamera(engine, camera);

        if (input.isKeyPressed(GLFW_KEY_F6) && cameraIndex != -1 && !droppedCameras.isEmpty()) {
            engine.getSceneManager().getActiveScene().removeObject(droppedCameras.get(cameraIndex));
            droppedCameras.remove(cameraIndex);
            cameraIndex--;
            if (cameraIndex == -1) {
                scene.setPrimaryCamera(camera);
            } else {
                scene.setPrimaryCamera(droppedCameras.get(cameraIndex).getComponent(CameraComponent.class));
            }
        }

        if (cameraIndex != -1) return;
        if (engine.isDevMode() && !engine.getEngineEditor().isMouseInViewport() && (ImGui.getIO().getWantCaptureMouse() || ImGui.getIO().getWantCaptureKeyboard())) {
            return;
        }
        double scroll = input.getScrollDY();
        if (scroll != 0 && input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
            speedMultiplier *= (float) Math.pow(1.1f, scroll);
        }
        speedMultiplier = Math.clamp(speedMultiplier, 0.1f, 100f);

        boolean isPrimary = scene.getPrimaryCamera().getID().equals(camera.getID());
        if (input.isMouseBtnDown(GLFW_MOUSE_BUTTON_MIDDLE) && !camLock && isPrimary) {
            float sensitivity = engine.getValue("controls.mouse_sensitivity");
            float dx = input.getMouseDX() * sensitivity;
            float dy = input.getMouseDY() * sensitivity;

            float nyaw = ((camera.getYaw() + dx) % 360f + 360f) % 360f;
            float npitch = Math.clamp(camera.getPitch() + dy, -89f, 89f);

            camera.setDirection(npitch, nyaw, 0f);
        }

        float radYaw = (float) Math.toRadians(camera.getYaw());
        float forwardX = (float) Math.cos(radYaw);
        float forwardZ = (float) Math.sin(radYaw);
        float rightX = (float) -Math.sin(radYaw);
        float rightZ = (float) Math.cos(radYaw);

        float moveX = 0, moveY = 0, moveZ = 0;

        if (input.isKeyDown(GLFW_KEY_W)) { moveX += forwardX; moveZ += forwardZ; }
        if (input.isKeyDown(GLFW_KEY_S)) { moveX -= forwardX; moveZ -= forwardZ; }
        if (input.isKeyDown(GLFW_KEY_A)) { moveX -= rightX; moveZ -= rightZ; }
        if (input.isKeyDown(GLFW_KEY_D)) { moveX += rightX; moveZ += rightZ; }
        if (input.isKeyDown(GLFW_KEY_SPACE)) { moveY += 1f; }
        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) { moveY -= 1f; }
        float moveMag = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moveMag > 0) {
            moveX /= moveMag;
            moveZ /= moveMag;
        }

        float speed = engine.getValue("controls.max_speed") * dt * speedMultiplier;
        if (!moveLock) {owner.transform.transformPos(moveX * speed, moveY * speed, moveZ * speed);}
    }

    private void dropCamera(Engine engine, CameraComponent mainCamera) {
        Vector3f pos = owner.transform.getPosition();

        GameObject camObject = new GameObject(engine);
        camObject.setName("dropped_camera_" + (droppedCameras.size() + 1));
        camObject.transform.position(pos.x, pos.y, pos.z);

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
        Scene scene = engine.getSceneManager().getActiveScene();

        cameraIndex++;
        if (cameraIndex >= droppedCameras.size()) {
            cameraIndex = -1;
            scene.setPrimaryCamera(mainCamera);
            return;
        }

        CameraComponent nextCamera = droppedCameras.get(cameraIndex).getComponent(CameraComponent.class);
        scene.setPrimaryCamera(nextCamera);
    }

    public void setCamLock(boolean locked) { this.camLock = locked; }
    public void setMoveLock(boolean locked) { this.moveLock = locked; }
    public boolean isCamLocked() { return camLock; }
    public boolean isMoveLock() { return moveLock; }
}