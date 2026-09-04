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
import imgui.type.ImInt;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
    private boolean dayTimePlaying = false;
    private final ImFloat dayTimeSpeedRef = new ImFloat(50f);
    private float dayTimeAccumulator = 0f;
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
        engine.getInputService().setMapping("wireframe", InputService.InputType.KEYBOARD, GLFW_KEY_F1);
        engine.getInputService().setMapping("changeRenderingMode", InputService.InputType.KEYBOARD, GLFW_KEY_F2);
        engine.getInputService().setMapping("toggleSSAO", InputService.InputType.KEYBOARD, GLFW_KEY_F3);
        engine.getInputService().setMapping("toggleMouse", InputService.InputType.KEYBOARD, GLFW_KEY_F4);
        engine.getInputService().setMapping("toggleGUI", InputService.InputType.KEYBOARD, GLFW_KEY_F9);

        defaultValueConfig.putAll(new TreeMap<>(engine.valueConfig));
        initialized = true;

    }

    public void destroy() {
        ImGui.destroyContext();
    }




    public void renderImGui() {
        if (engine.getSceneManager().getActiveScene() == null || !engine.getSceneManager().getActiveScene().isLoaded()) return;
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
        if (input.isMousePressed(GLFW_MOUSE_BUTTON_LEFT) && !ImGuizmo.isOver() && !ImGuizmo.isUsing() && !ImGui.getIO().getWantCaptureMouse()) {
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
            if (clicked != null && input.isKeyDown(GLFW_KEY_LEFT_ALT)) {
                selectedObject = clicked;
            } else if (clicked != null) {
                selectedObject = clicked.getRoot();
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
        if (input.isKeyPressed(input.getMapping("wireframe").code())) {renderer.enableWireframe(!renderer.wireframeEnabled());}
        if (input.isKeyPressed(input.getMapping("changeRenderingMode").code())) {
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

        if (input.isKeyPressed(input.getMapping("toggleSSAO").code())) {
            engine.setIO("ssao", !engine.getIO("ssao"));
        }

        if (input.isKeyPressed(input.getMapping("toggleMouse").code())) {
            engine.setIO("mouse_visible", !engine.getIO("mouse_visible"));
            glfwSetInputMode(engine.getWindowHandle(), GLFW_CURSOR, engine.getIO("mouse_visible")? GLFW_CURSOR_NORMAL:GLFW_CURSOR_DISABLED);
        }


        if (input.isKeyPressed(input.getMapping("toggleGUI").code())) {
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
                if (key.equals("bloom") && !ref.get()) engine.setIO("lens_dirt", false);
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
                if (key.equals("res_width")) {
                    engine.setResolution((int) ref.get(), (int) engine.getValue("res_height"));
                } else if (key.equals("res_height")) {
                    engine.setResolution((int) engine.getValue("res_width"), (int) ref.get());
                } else {
                    engine.setValue(key, ref.get());
                }
            }
            ImGui.sameLine();
            float def = defaultValueConfig.getOrDefault(key, entry.getValue());
            ImGui.textDisabled(String.format("(ref %.3f)", def));
        }
        ImGui.separator();

        Scene.Sky sky = engine.getSceneManager().getActiveScene().environment.sky;
        Scene.Lights lights = engine.getSceneManager().getActiveScene().lights;


        if (dayTimePlaying) {
            dayTimeAccumulator += dayTimeSpeedRef.get() * engine.getDeltaTime();
            int wholeSeconds = (int) Math.floor(dayTimeAccumulator);
            if (wholeSeconds != 0) {
                sky.dayTime = Math.floorMod(sky.dayTime + wholeSeconds, sky.dayLengthSeconds);
                sky.calculateDirections();
                syncLightWithSky(sky, lights);
                dayTimeAccumulator -= wholeSeconds;
            }
        }

        int[] time = {sky.dayTime};
        if (ImGui.sliderInt("Time", time, 0, sky.dayLengthSeconds)) {
            sky.dayTime = time[0];
            sky.calculateDirections();
            syncLightWithSky(sky, lights);
        }

        ImGui.sameLine();
        if (ImGui.button(dayTimePlaying ? "Pause" : "Play")) {
            dayTimePlaying = !dayTimePlaying;
            if (dayTimePlaying) {
                dayTimeAccumulator = 0f;
            }
        }

        ImGui.setNextItemWidth(120);
        ImGui.inputFloat("Day Time Speed", dayTimeSpeedRef);

        float[] ambX = {lights.globalLight.ambient.x};
        float[] ambY = {lights.globalLight.ambient.y};
        float[] ambZ = {lights.globalLight.ambient.z};

        if (ImGui.sliderFloat("Ambient Red", ambX, 0, 1)) {
            lights.globalLight.ambient.x = ambX[0];
        }

        if (ImGui.sliderFloat("Ambient Green", ambY, 0, 1)) {
            lights.globalLight.ambient.y = ambY[0];
        }

        if (ImGui.sliderFloat("Ambient Blue", ambZ, 0, 1)) {
            lights.globalLight.ambient.z = ambZ[0];
        }

        ImInt aaMode = new ImInt(engine.getRenderer().AAMode.ordinal());

        if (ImGui.radioButton("None", aaMode, 0)) {
            engine.getRenderer().AAMode = Renderer.AntiAliasingMode.NONE;
        }

        ImGui.sameLine();

        if (ImGui.radioButton("FXAA", aaMode, 1)) {
            engine.getRenderer().AAMode = Renderer.AntiAliasingMode.FXAA;
        }

        ImGui.sameLine();

        if (ImGui.radioButton("SMAA", aaMode, 2)) {
            engine.getRenderer().AAMode = Renderer.AntiAliasingMode.SMAA;
        }
    }

    private void syncLightWithSky(Scene.Sky sky, Scene.Lights lights) {
        float sunElevation = -sky.sunDirection.y;
        float moonElevation = -sky.moonDirection.y;

        // 1 = fully sunlit, 0 = fully moonlit; smooth crossfade near the horizon instead of a hard switch
        float blend = smoothstep(-0.1f, 0.1f, sunElevation);

        Vector3f sunColor = new Vector3f(1.0f, 0.96f, 0.88f);
        Vector3f moonColor = new Vector3f(0.4f, 0.45f, 0.65f);

        Vector3f dir = new Vector3f();
        sky.sunDirection.lerp(sky.moonDirection, 1f - blend, dir);
        lights.globalLight.direction.set(dir).normalize();

        Vector3f color = new Vector3f();
        sunColor.lerp(moonColor, 1f - blend, color);
        lights.globalLight.color.set(color);

        float sunFactor = Math.max(0f, sunElevation);
        float moonFactor = Math.max(0f, moonElevation) * 0.15f; // moon much dimmer than the sun
        float dayFactor = Math.min(1f, Math.max(0.05f, sunFactor + moonFactor));

        lights.globalLight.ambient.set(0.3f, 0.3f, 0.35f).mul(dayFactor);
        lights.globalLight.intensity = 0.2f + dayFactor * 1.3f;
    }

    private float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
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