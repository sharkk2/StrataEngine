package org.sharkk2.sengine.core.systems.gui;

import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import org.joml.Matrix4f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.InputService;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.ColliderComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.debug.HardwareMonitor;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F4;

public class Imgui {
    private final Engine engine;
    private boolean initialized = false;
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private final Map<String, ImBoolean> ioRefs = new HashMap<>();
    private final Map<String, ImFloat> valueRefs = new HashMap<>();
    private final Map<String, Float> defaultValueConfig = new HashMap<>();
    private int manipulationMode = Operation.TRANSLATE;
    private GameObject selectedObject;
    private record HistoryEntry(GameObject object, Matrix4f matrix) {};
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>();
    private boolean gizmoActive = false;
    private HistoryEntry pendingGizmoEntry;

    public Imgui(Engine engine) {
        this.engine = engine;
    }

    public void initialize() {
        if (initialized) {
            Logger.warning("ImGUI is already initialized!");
            return;
        }

        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        io.setIniFilename(null);

        ImFontAtlas atlas = ImGui.getIO().getFonts();
        atlas.addFontFromFileTTF("src/main/resources/fonts/Geomini.ttf", 16);
        ImFontConfig config = new ImFontConfig();
        config.setMergeMode(true);
        config.setPixelSnapH(true);

        imGuiGlfw.init(engine.getWindowHandle(), true);
        imGuiGl3.init("#version 150 core");
        engine.getInputService().setMapping("wireframe", GLFW_KEY_F1);
        engine.getInputService().setMapping("changeRenderingMode", GLFW_KEY_F2);
        engine.getInputService().setMapping("toggleSSAO", GLFW_KEY_F3);
        engine.getInputService().setMapping("toggleMouse", GLFW_KEY_F4);
        engine.getInputService().setMapping("toggleGUI", GLFW_KEY_F9);

        defaultValueConfig.putAll(new TreeMap<>(engine.valueConfig));
        initialized = true;

    }

    public void destroy() {
        ImGui.destroyContext();
    }




    public void renderImGui() {
        tickGUI();
        if (!engine.getIO("imgui.enabled")) return;

        imGuiGlfw.newFrame();
        ImGui.newFrame();
        ImGuizmo.beginFrame();
        ImGuizmo.setDrawList(ImGui.getBackgroundDrawList());

        ImGuizmo.setOrthographic(false);
        ImGuizmo.setRect(
                0,
                0,
                engine.getWindowWidth(),
                engine.getWindowHeight()
        );

        if (engine.getIO("imgui.debug_overlay")) {
            renderDebugOverlay();
        }

        if (engine.getIO("imgui.full_settings")) {
            renderAllEngineSettings();
        }

        if (selectedObject != null) {
            engine.getDebugger().visualizeDirection(selectedObject, 3);

            if (selectedObject.hasComponent(ModelComponent.class)) {
                engine.getDebugger().visualizeBounds(selectedObject.getComponent(ModelComponent.class).bounds);
            }


            CameraComponent cam = engine.getCameraService().getPrimaryCamera();
            float[] matrix = new float[16];

            Matrix4f world = selectedObject.transform.calculateWorldMatrix();
            Matrix4f view = cam.getViewMatrix();
            Matrix4f proj = cam.getProjectionMatrix(engine.getWindowAspectRatio());

            world.get(matrix);

            float[] viewData = new float[16];
            float[] projData = new float[16];

            view.get(viewData);
            proj.get(projData);
            ImGuizmo.manipulate(viewData, projData, matrix, manipulationMode, Mode.WORLD);

            boolean isUsingGizmo = ImGuizmo.isUsing();

            if (isUsingGizmo && !gizmoActive) {
                pendingGizmoEntry = new HistoryEntry(selectedObject, new Matrix4f(world));
            }

            if (isUsingGizmo) {
                Matrix4f result = new Matrix4f().set(matrix);
                selectedObject.transform.applyWorldMatrix(result);
            }

            if (!isUsingGizmo && gizmoActive && pendingGizmoEntry != null) {
                undoStack.push(pendingGizmoEntry);
                redoStack.clear();
                pendingGizmoEntry = null;
            }

            gizmoActive = isUsingGizmo;
        }
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }


    private void tickGUI() {
        InputService input = engine.getInputService();
        CameraComponent cam = engine.getCameraService().getPrimaryCamera();
        if (input.isMousePressed(GLFW_MOUSE_BUTTON_LEFT) && !ImGuizmo.isOver() && !ImGuizmo.isUsing()) {
            GameObject clicked = engine.getRaycastService().castScreenRay(
                    input.getMouseX(),
                    input.getMouseY(),
                    cam.getOwner().transform.getPosition(),
                    cam.getProjectionMatrix(engine.getWindowAspectRatio()),
                    cam.getViewMatrix(),
                    engine.getSceneManager().getActiveScene().getAllObjects(),
                    1000,
                    true
            );
            selectedObject = clicked;
            if (selectedObject != null && input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
                selectedObject = selectedObject.getRoot();
            }
        }

        if (input.isKeyPressed(GLFW_KEY_Z) && input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
            undo();
        }

        if (input.isKeyPressed(GLFW_KEY_Y) && input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
            redo();
        }

        if (input.isKeyPressed(GLFW_KEY_DELETE) && selectedObject != null) {
            engine.getSceneManager().getActiveScene().removeObject(selectedObject.id);
            selectedObject.destroy();
            selectedObject = null;
        }

        if (input.isKeyDown(GLFW_KEY_LEFT_CONTROL) && input.isKeyPressed(GLFW_KEY_D) && selectedObject != null) {
            engine.getSceneManager().getActiveScene().addObject(selectedObject.clone());
        }

        if (input.isKeyPressed(GLFW_KEY_N)) {
            GameObject tadaa = new GameObject(engine);
            ModelComponent mccc = engine.getAssetLoader().primitives.cube();
            tadaa.attachComponent(mccc);
            tadaa.attachComponent(new ColliderComponent(mccc.bounds));
            tadaa.transform.setPosition(cam.getOwner().transform.getPosition());
            tadaa.transform.transformPos(0, 1, 0);
            tadaa.transform.applyOrientation(cam.getOwner().transform.getDirection());
            engine.getSceneManager().getActiveScene().addObject(tadaa);
        }

        if (input.isKeyPressed(GLFW_KEY_C)) {
            if (selectedObject != null) cam.lookAt(selectedObject.transform.getPosition());
        }


        Renderer renderer = engine.getRenderer();
        if (input.isKeyPressed(input.getButton("wireframe"))) {renderer.enableWireframe(!renderer.wireframeEnabled());}
        if (input.isKeyPressed(input.getButton("changeRenderingMode"))) {
            int next = renderer.getRenderingMode().ordinal() + 1;
            if (next >= Renderer.RenderMode.MODE_MAX.ordinal()) next = 0;
            renderer.setRenderingMode(Renderer.RenderMode.values()[next]);
        }

        if (input.isKeyPressed(GLFW_KEY_T)){
            manipulationMode = Operation.TRANSLATE;
        } else if (input.isKeyPressed(GLFW_KEY_B)) {
            manipulationMode = Operation.SCALE;
        } else if (input.isKeyPressed(GLFW_KEY_R)) {
            manipulationMode = Operation.ROTATE;
        }

        if (input.isKeyPressed(input.getButton("toggleSSAO"))) {
            engine.setIO("ssao", !engine.getIO("ssao"));
        }

        if (input.isKeyPressed(input.getButton("toggleMouse"))) {
            engine.setIO("mouse_visible", !engine.getIO("mouse_visible"));
            glfwSetInputMode(engine.getWindowHandle(), GLFW_CURSOR, engine.getIO("mouse_visible")? GLFW_CURSOR_NORMAL:GLFW_CURSOR_DISABLED);
        }


        if (input.isKeyPressed(input.getButton("toggleGUI"))) {
            engine.setIO("imgui.enabled", !engine.getIO("imgui.enabled"));
        }

    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        HistoryEntry entry = undoStack.pop();
        redoStack.push(new HistoryEntry(entry.object(), entry.object().transform.calculateWorldMatrix()));
        entry.object().transform.applyWorldMatrix(entry.matrix());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        HistoryEntry entry = redoStack.pop();
        undoStack.push(new HistoryEntry(entry.object(), entry.object().transform.calculateWorldMatrix()));
        entry.object().transform.applyWorldMatrix(entry.matrix());
    }


    private void renderAllEngineSettings() {
        ImGui.text("IO flags");
        ImGui.separator();
        for (Map.Entry<String, Boolean> entry : new TreeMap<>(engine.ioConfig).entrySet()) {
            String key = entry.getKey();
            ImBoolean ref = ioRefs.computeIfAbsent(key, k -> new ImBoolean(entry.getValue()));
            ref.set(engine.getIO(key));
            if (ImGui.checkbox(key, ref)) {
                engine.setIO(key, ref.get());
            }
        }

        ImGui.spacing();
        ImGui.text("Values");
        ImGui.separator();
        for (Map.Entry<String, Float> entry : new TreeMap<>(engine.valueConfig).entrySet()) {
            String key = entry.getKey();
            ImFloat ref = valueRefs.computeIfAbsent(key, k -> new ImFloat(entry.getValue()));
            ref.set(engine.getValue(key));

            ImGui.setNextItemWidth(120);
            if (ImGui.inputFloat(key, ref)) {
                engine.setValue(key, ref.get());
            }
            ImGui.sameLine();
            float def = defaultValueConfig.getOrDefault(key, entry.getValue());
            ImGui.textDisabled(String.format("(ref %.3f)", def));
        }
        ImGui.separator();
        float[] time = {(float) engine.getSceneManager().getActiveScene().getSceneDayTime()};
        if (ImGui.sliderFloat("Time", time, 0, 1)) {
            engine.getSceneManager().getActiveScene().setTime(time[0]);
        }


    }


    private void renderDebugOverlay() {
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoMove;


        ImGui.setNextWindowBgAlpha(0.55f);
        ImGui.setNextWindowPos(10, 10);

        if (ImGui.begin("##debug_overlay", flags)) {
            CameraComponent mainCamera = engine.getCameraService().getPrimaryCamera();
            GameObject camObject = mainCamera.getOwner();
            Scene activeScene = engine.getSceneManager().getActiveScene();
            ImGui.text("SEngine (" + engine.version + ") - a sharkk2 project");
            ImGui.separator();
            ImGui.text(String.format("Performance: " + engine.getFps() + " FPS (%.3fms)", engine.getDeltaTime()*1000));
            ImGui.text(String.format("CPU: %d%% - GPU: %d%% (%dc)", HardwareMonitor.getCPULoad(), HardwareMonitor.getGPULoad(), HardwareMonitor.getGpuTemperature()));
            ImGui.separator();
            ImGui.text("Scene: " + activeScene.getName());
            ImGui.text("Rendered objects: " + engine.getRenderer().getRenderCount());
            ImGui.text("Lights: " + activeScene.lights.lightCount());
            ImGui.text("Rendering mode: " + engine.getRenderer().getRenderingMode().name());
            ImGui.separator();
            ImGui.text("Primary camera: " + mainCamera.name + " (owned by " + camObject.getName() + "): ");
            ImGui.text(String.format("XYZ: %.1f, %.1f, %.1f", camObject.transform.x, camObject.transform.y, camObject.transform.z));
            ImGui.text(String.format("Orientation (pitch,yaw,roll): %.1f, %.1f, %.1f", camObject.transform.pitch, camObject.transform.yaw, camObject.transform.roll));
            ImGui.text(String.format("FOV (degrees): %.2f", mainCamera.getFov()));
            ImGui.separator();
            GameObject waila = engine.getRaycastService().castRay(camObject.transform.getPosition(), mainCamera.getDirection(), activeScene.getAllObjects() ,20, true);
            if (waila!=null) {
                ImGui.text("Looking at: " + waila.getRoot().getName());
            } else {
                ImGui.text("Looking at: nothing");
            }
            if (selectedObject!=null) {
                ImGui.text("Selected object: " + selectedObject.getName());
            } else {
                ImGui.text("Selected object: nothing");
            }
        }
        ImGui.end();
    }
}