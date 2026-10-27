package org.sharkk2.sengine.core.systems.gui;

import imgui.*;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.implot.ImPlot;
import imgui.extension.implot.flag.ImPlotFlags;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.classes.animation.Animation;
import org.sharkk2.sengine.core.systems.InputService;
import org.sharkk2.sengine.core.systems.RaycastService;
import org.sharkk2.sengine.core.systems.components.*;
import org.sharkk2.sengine.core.systems.components.ModelComponent.Material;
import org.sharkk2.sengine.core.systems.debug.HardwareMonitor;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL43.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F4;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class Imgui {
    private final Engine engine;
    private boolean initialized = false;
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private final Map<String, Float> defaultValueConfig = new HashMap<>();
    private int manipulationMode = Operation.TRANSLATE;
    private GameObject selectedObject;

    private interface EditCommand {
        void undo();
        void redo();
        void remap(GameObject from, GameObject to);
    }

    private static final int MAX_HISTORY = 200;
    private final Deque<EditCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditCommand> redoStack = new ArrayDeque<>();
    private final Map<String, String> fontMap = new HashMap<>();
    private boolean gizmoActive = false;
    private GameObject pendingTarget;
    private Matrix4f pendingBefore;
    private GameObject editTarget = null;
    private GameObject renameTarget = null;
    private String renameBefore = "";
    private final float[] viewData = new float[16];
    private final float[] projData = new float[16];
    private final float[] viewBefore = new float[16];
    private final Matrix4f tmpMat = new Matrix4f();
    private final Quaternionf tmpQuat = new Quaternionf();
    private Component selectedComponent = null;

    private static final int FRAME_HISTORY_SIZE = 90;
    private static final float FT_UPDATE_INTERVAL = 0.1f;
    private final float[] frameTimes = new float[FRAME_HISTORY_SIZE];
    private int frameIndex = 0;
    private boolean ftBufferFilled = false;
    private float ftUpdateCount = 0;
    private final Float[] plotX = new Float[FRAME_HISTORY_SIZE];
    private final Float[] plotY = new Float[FRAME_HISTORY_SIZE];

    private boolean isMouseInViewport = false;
    private final ImString oName = new ImString(512);
    private final float[] viewportPos = new float[2];
    private final float[] viewportSize = new float[2];
    private String pendingType = null;
    private final ImString newName = new ImString(512);
    private final ImInt slices = new ImInt(48);
    private final ImInt stacks = new ImInt(48);
    private final ImFloat mnrRadius = new ImFloat(0.3f);
    private final ImFloat mjrRadius = new ImFloat(0.5f);
    private final ImInt divisions = new ImInt(8);
    private final ImBoolean capped = new ImBoolean(true);
    private final ImString newTagName = new ImString(128);
    private float lastFPS = -1;
    private float lastMS = -1;
    private float[] lastCpuMS = new float[0];
    private float[] lastGpuMS = new float[0];
    private String[] lastPassNames = new String[0];

    private boolean localSpace = false;
    private boolean snapEnabled = false;
    private final float[] snapTranslate = {0.5f, 0.5f, 0.5f};
    private final float[] snapRotate = {15f, 15f, 15f};
    private final float[] snapScale = {0.1f, 0.1f, 0.1f};

    private final ImString treeFilter = new ImString(128);
    private final Set<GameObject> hiddenObjects = new HashSet<>();
    private final Map<ModelComponent, Renderer.RenderMethod> hiddenModels = new IdentityHashMap<>();

    private final ImBoolean showScene = new ImBoolean(true);
    private final ImBoolean showProfiler = new ImBoolean(true);
    private final ImBoolean showInspector = new ImBoolean(true);
    private final ImBoolean showConsole = new ImBoolean(true);

    private final ImBoolean consoleInfo = new ImBoolean(true);
    private final ImBoolean consoleWarn = new ImBoolean(true);
    private final ImBoolean consoleError = new ImBoolean(true);
    private final ImBoolean consoleAutoScroll = new ImBoolean(true);
    private final ImString consoleFilter = new ImString(128);
    private List<Logger.Entry> consoleEntries = new ArrayList<>();
    private long lastConsoleRevision = -1;

    public Imgui(Engine engine) {
        this.engine = engine;
    }
    public boolean isMouseInViewport() {return isMouseInViewport;}
    public void initialize() {
        if (initialized) {
            Logger.warning("ImGUI is already initialized!");
            return;
        }

        ImGui.createContext();
        ImPlot.createContext();
        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        io.setConfigWindowsMoveFromTitleBarOnly(true);
        io.setIniFilename("assets/sengine_layout.ini");

        ImFontAtlas atlas = ImGui.getIO().getFonts();
        ImFontConfig iconsConfig = new ImFontConfig();
        iconsConfig.setPixelSnapH(true);
        iconsConfig.setSizePixels(16);

        atlas.addFontFromFileTTF("assets/fonts/Geomini.ttf", 16, iconsConfig);
        short[] iconRanges = {0x0021, (short) 0xF803, 0};
        iconsConfig.setMergeMode(true);
        atlas.addFontFromFileTTF("assets/fonts/fa-solid-se.ttf", 16, iconsConfig, iconRanges);

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
        applyEditorStyle();

        fontMap.put("CameraComponent", "\uF1C8");
        fontMap.put("AudioComponent", "\uF1C7");
        fontMap.put("ScriptComponent", "\uF1C9");
        fontMap.put("ColliderComponent", "\uF1C6");
        fontMap.put("ModelComponent", "\uF801");
        fontMap.put("LightComponent", "\uF800");
        fontMap.put("AnimationComponent", "\uF802");
        fontMap.put("GameObject", "\uF247");
        // f803 for physics comp later
        ImGui.loadIniSettingsFromDisk("assets/sengine_layout.ini");
        initialized = true;

    }

    public void destroy() {
        ImGui.destroyContext();
    }


    private void applyEditorStyle() {
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(6f);
        style.setChildRounding(6f);
        style.setFrameRounding(4f);
        style.setPopupRounding(6f);
        style.setScrollbarRounding(6f);
        style.setGrabRounding(4f);
        style.setTabRounding(6f);

        // Palette
        float[] accent = hex(0x627BD5); // header / menu bar blue
        float[] text = hex(0xC3C3F1);
        float[] bg = hex(0x21202B);
        float[] widget = hex(0x39426B); // buttons, radios, frames

        float[] accentHover = lighten(accent, 0.12f);
        float[] accentActive = lighten(accent, 0.22f);
        float[] widgetHover = lighten(widget, 0.12f);
        float[] widgetActive = lighten(widget, 0.22f);
        float[] bgLight  = lighten(bg, 0.05f);
        float[] bgDark = darken(bg, 0.03f);

        style.setColor(ImGuiCol.WindowBg, bg[0], bg[1], bg[2], 1.00f);
        style.setColor(ImGuiCol.ChildBg, bg[0], bg[1], bg[2], 1.00f);
        style.setColor(ImGuiCol.PopupBg, bgDark[0], bgDark[1], bgDark[2], 1.00f);
        style.setColor(ImGuiCol.Text, text[0], text[1], text[2], 1.00f);
        style.setColor(ImGuiCol.TextDisabled, text[0] * 0.5f, text[1] * 0.5f, text[2] * 0.5f, 1.00f);
        style.setColor(ImGuiCol.FrameBg, widget[0], widget[1], widget[2], 0.55f);
        style.setColor(ImGuiCol.FrameBgHovered, widgetHover[0], widgetHover[1], widgetHover[2], 0.75f);
        style.setColor(ImGuiCol.FrameBgActive, widgetActive[0], widgetActive[1], widgetActive[2], 0.90f);
        style.setColor(ImGuiCol.Button, widget[0], widget[1], widget[2], 1.00f);
        style.setColor(ImGuiCol.ButtonHovered, widgetHover[0], widgetHover[1], widgetHover[2], 1.00f);
        style.setColor(ImGuiCol.ButtonActive, widgetActive[0], widgetActive[1], widgetActive[2], 1.00f);
        style.setColor(ImGuiCol.Header, widget[0], widget[1], widget[2], 0.80f);
        style.setColor(ImGuiCol.HeaderHovered, widgetHover[0], widgetHover[1], widgetHover[2], 0.90f);
        style.setColor(ImGuiCol.HeaderActive, widgetActive[0], widgetActive[1], widgetActive[2], 1.00f);
        style.setColor(ImGuiCol.MenuBarBg, accent[0], accent[1], accent[2], 1.00f);
        style.setColor(ImGuiCol.TitleBg, bgDark[0], bgDark[1], bgDark[2], 1.00f);
        style.setColor(ImGuiCol.TitleBgActive, accent[0], accent[1], accent[2], 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed, bgDark[0], bgDark[1], bgDark[2], 0.75f);
        style.setColor(ImGuiCol.Tab, bgLight[0], bgLight[1], bgLight[2], 1.00f);
        style.setColor(ImGuiCol.TabHovered, accentHover[0], accentHover[1], accentHover[2], 1.00f);
        style.setColor(ImGuiCol.TabActive, accent[0], accent[1], accent[2], 1.00f);
        style.setColor(ImGuiCol.CheckMark, accent[0], accent[1], accent[2], 1.00f);
        style.setColor(ImGuiCol.SliderGrab, accent[0], accent[1], accent[2], 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive, accentActive[0], accentActive[1], accentActive[2], 1.00f);
        style.setColor(ImGuiCol.ResizeGrip, widget[0], widget[1], widget[2], 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered, widgetHover[0], widgetHover[1], widgetHover[2], 0.75f);
        style.setColor(ImGuiCol.ResizeGripActive, widgetActive[0], widgetActive[1], widgetActive[2], 1.00f);
        style.setColor(ImGuiCol.ScrollbarBg, bgDark[0], bgDark[1], bgDark[2], 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab, widget[0], widget[1], widget[2], 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, widgetHover[0], widgetHover[1], widgetHover[2], 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive, widgetActive[0], widgetActive[1], widgetActive[2], 1.00f);
        style.setColor(ImGuiCol.Border, widget[0], widget[1], widget[2], 0.40f);
        style.setColor(ImGuiCol.Separator, widget[0], widget[1], widget[2], 0.60f);
        style.setColor(ImGuiCol.SeparatorHovered, accent[0], accent[1], accent[2], 0.78f);
        style.setColor(ImGuiCol.SeparatorActive, accent[0], accent[1], accent[2], 1.00f);
    }

    private float[] hex(int rgb) {
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }

    private float[] lighten(float[] c, float amt) {
        return new float[]{
                Math.min(1f, c[0] + amt),
                Math.min(1f, c[1] + amt),
                Math.min(1f, c[2] + amt)
        };
    }

    private float[] darken(float[] c, float amt) {
        return new float[]{
                Math.max(0f, c[0] - amt),
                Math.max(0f, c[1] - amt),
                Math.max(0f, c[2] - amt)
        };
    }

    private void saveScreenshot() {
        BufferedImage image = Helpers.captureTexture(engine.getRenderer().getColorTexture(), (int)engine.getValue("res_width"), (int)engine.getValue("res_height"));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();

            String path = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Save Screenshot",
                    "sengine_screenshot.png",
                    filters,
                    "PNG Image"
            );

            if (path != null) {
                if (!path.toLowerCase().endsWith(".png")) path += ".png";
                ImageIO.write(image, "png", new File(path));
                Logger.info("Screenshot saved to " + path);
            }
        } catch (IOException e) {
            Logger.error("Failed to save screenshot: " + e.getMessage());
        }
    }

    public void renderImGui() {
        if (engine.getSceneManager().getActiveScene() == null || !engine.getSceneManager().getActiveScene().isLoaded()) return;
        tickGUI();
        if (!engine.getIO("imgui.enabled")) return;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, engine.getWindowWidth(), engine.getWindowHeight());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        imGuiGlfw.newFrame();
        ImGui.newFrame();
        ImGuizmo.beginFrame();


        renderMenuBar();
        renderDockspace();
        renderSceneWindow();
        renderFpsProfiler();
        renderViewportWindow();
        renderObjectMenu(selectedObject);
        renderConsole();

        ImDrawList fgDrawList = ImGui.getForegroundDrawList();
        ImGuizmo.setDrawList(fgDrawList);
        ImGuizmo.setOrthographic(false);
        ImGuizmo.setRect(viewportPos[0], viewportPos[1], viewportSize[0], viewportSize[1]);
        fgDrawList.pushClipRect(viewportPos[0], viewportPos[1], viewportPos[0] + viewportSize[0], viewportPos[1] + viewportSize[1], true);

        CameraComponent cam = engine.getSceneManager().getActiveScene().getPrimaryCamera();
        if (cam != null) {
            float[] matrix = new float[16];

            Matrix4f view = cam.getViewMatrix();
            Matrix4f proj = cam.getProjectionMatrix(engine.getWindowAspectRatio());
            view.get(viewData);
            proj.get(projData);
            System.arraycopy(viewData, 0, viewBefore, 0, 16);
            float cubeSize = 100f;
            float[] cubePos = {viewportPos[0] + viewportSize[0] - cubeSize - 5, viewportPos[1] + 11};
            float[] cubeSizeVec = {cubeSize, cubeSize};
            ImGuizmo.viewManipulate(viewData, 8.0f, cubePos, cubeSizeVec, 0x10101010);

            boolean viewChanged = false;
            for (int i = 0; i < 16; i++) {
                if (java.lang.Math.abs(viewBefore[i] - viewData[i]) > 1e-5f) {
                    viewChanged = true;
                    break;
                }
            }

            if (viewChanged) {
                tmpMat.set(viewData).invert();
                Vector3f forward = tmpMat.transformDirection(new Vector3f(0, 0, -1)).normalize();

                float pitch = (float) Math.toDegrees(Math.asin(Math.clamp(forward.y, -1f, 1f)));
                pitch = Math.clamp(pitch, -89.5f, 89.5f);

                float yaw = cam.getYaw();
                if (forward.x * forward.x + forward.z * forward.z > 1e-6f) {
                    yaw = (float) Math.toDegrees(Math.atan2(forward.z, forward.x));
                }

                cam.setDirection(pitch, yaw, cam.getRoll());
            }

            if (selectedObject != null) {
                Matrix4f world = selectedObject.transform.calculateWorldMatrix();
                world.get(matrix);

                engine.getDebugger().visualizeDirection(selectedObject, 3);
                if (selectedObject.hasComponent(ModelComponent.class)) {
                    engine.getDebugger().visualizeBounds(selectedObject.getComponent(ModelComponent.class).bounds);
                }
                int gizmoSpace = localSpace ? Mode.LOCAL : Mode.WORLD;
                boolean snapNow = snapEnabled ^ engine.getInputService().isKeyDown(GLFW_KEY_LEFT_CONTROL);
                if (snapNow) {
                    ImGuizmo.manipulate(viewData, projData, matrix, manipulationMode, gizmoSpace, currentSnap());
                } else {
                    ImGuizmo.manipulate(viewData, projData, matrix, manipulationMode, gizmoSpace);
                }
                boolean isUsingGizmo = ImGuizmo.isUsing();
                if (isUsingGizmo && !gizmoActive) {
                    beginTransformEdit(selectedObject);
                }
                if (isUsingGizmo) {
                    Quaternionf savedRotation = new Quaternionf(selectedObject.transform.getRotationQuat());
                    Matrix4f result = new Matrix4f().set(matrix);
                    selectedObject.transform.applyWorldMatrix(result);
                    if (manipulationMode == Operation.SCALE) {
                        selectedObject.transform.rotate(savedRotation);
                    }
                }
                if (!isUsingGizmo && gizmoActive) {
                    endTransformEdit();
                }
                gizmoActive = isUsingGizmo;
            }
        }
        fgDrawList.popClipRect();
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    private void renderMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New")) { /* ... */ }
                if (ImGui.menuItem("Open", "Ctrl+O")) { /* ... */ }
                if (ImGui.menuItem("Save", "Ctrl+S")) { /* ... */ }
                ImGui.separator();
                if (ImGui.menuItem("Save UI layout")) {ImGui.saveIniSettingsToDisk("assets/sengine_layout.ini");}
                if (ImGui.menuItem("Load UI layout")) {ImGui.loadIniSettingsFromDisk("assets/sengine_layout.ini");}

                ImGui.separator();
                if (ImGui.menuItem("Exit")) { engine.shutdown(); }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Undo", "Ctrl+Z", false, !undoStack.isEmpty())) {undo();}
                if (ImGui.menuItem("Redo", "Ctrl+Y", false, !redoStack.isEmpty())) {redo();}
                ImGui.separator();
                if (ImGui.menuItem("Duplicate", "Ctrl+D", false, selectedObject != null)) {duplicateSelected();}
                if (ImGui.menuItem("Delete", "Del", false, selectedObject != null)) {deleteSelected();}
                if (ImGui.menuItem("Deselect", "Esc", false, selectedObject != null)) {selectedObject = null; selectedComponent = null;}
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("View")) {
                if (ImGui.menuItem("Toggle debug overlay", "Ctrl+`")) {
                    engine.setIO("imgui.debug_overlay", !engine.getIO("imgui.debug_overlay"));
                }
                if (ImGui.menuItem("Screenshot", "Ctrl+F11")) {
                    saveScreenshot();
                }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("Window")) {
                ImGui.menuItem("Scene", "", showScene);
                ImGui.menuItem("FPS Profiler", "", showProfiler);
                ImGui.menuItem("Inspector", "", showInspector);
                ImGui.menuItem("Console", "", showConsole);
                ImGui.endMenu();
            }
            String stats = engine.getFps() + " FPS";
            ImGui.setCursorPosX(ImGui.getWindowWidth() - ImGui.calcTextSize(stats).x - 12);
            ImGui.text(stats);
            ImGui.endMainMenuBar();
        }
    }

    private void tickGUI() {
        InputService input = engine.getInputService();
        Scene scene = engine.getSceneManager().getActiveScene();
        CameraComponent cam = scene.getPrimaryCamera();
        boolean typing = ImGui.getIO().getWantTextInput();
        if (input.isMouseBtnPressed(GLFW_MOUSE_BUTTON_LEFT) && !ImGuizmo.isOver() && !ImGuizmo.isUsing() && isMouseInViewport) {
            float localX = input.getMouseX() - viewportPos[0];
            float localY = input.getMouseY() - viewportPos[1];

            if (localX >= 0 && localX < viewportSize[0] && localY >= 0 && localY < viewportSize[1]) {
                RaycastService.RayHit hit = engine.getRaycastService().castScreenRay(
                        localX,
                        localY,
                        cam.getEyePosition(),
                        cam.getProjectionMatrix(viewportSize[0] / viewportSize[1]),
                        cam.getViewMatrix(),
                        (int) viewportSize[0],
                        (int) viewportSize[1],
                        engine.getComponentRegistry().getComponents(ModelComponent.class),
                        1000,
                        true
                );

                if (hit != null && input.isKeyDown(GLFW_KEY_LEFT_ALT)) {
                    selectedObject = hit.hit().getOwner();
                    selectedComponent = null;
                } else if (hit != null) {
                    selectedObject = hit.hit().getOwner().getRoot();
                    selectedComponent = null;
                } else if (!input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
                    selectedObject = null;
                    selectedComponent = null;
                }
            }
        }

        dayTimeAccumulator += dayTimeSpeedRef.get() * engine.getDeltaTime();
        if (dayTimePlaying) {
            int wholeSeconds = (int) Math.floor(dayTimeAccumulator);
            if (wholeSeconds != 0) {
                scene.environment.sky.dayTime = Math.floorMod(scene.environment.sky.dayTime + wholeSeconds, scene.environment.sky.dayLengthSeconds);
                scene.environment.sky.calculateDirections();
                syncLightWithSky(scene.environment.sky, scene);
                dayTimeAccumulator -= wholeSeconds;
            }
        }

        if (!typing) {
            boolean ctrl = input.isKeyDown(GLFW_KEY_LEFT_CONTROL);
            boolean shift = input.isKeyDown(GLFW_KEY_LEFT_SHIFT);

            if (input.isKeyPressed(GLFW_KEY_Z) && ctrl) {
                if (shift) redo();
                else undo();
            }

            if (input.isKeyPressed(GLFW_KEY_Y) && ctrl) {
                redo();
            }

            if (input.isKeyPressed(GLFW_KEY_DELETE)) deleteSelected();
            if (ctrl && input.isKeyPressed(GLFW_KEY_D)) duplicateSelected();
            if (input.isKeyPressed(GLFW_KEY_ESCAPE)) { selectedObject = null; selectedComponent = null; }
            if (!ctrl && input.isKeyPressed(GLFW_KEY_F)) focusSelected();

            if (input.isKeyPressed(GLFW_KEY_T)){
                manipulationMode = Operation.TRANSLATE;
            } else if (input.isKeyPressed(GLFW_KEY_B)) {
                manipulationMode = Operation.SCALE;
            } else if (input.isKeyPressed(GLFW_KEY_R)) {
                manipulationMode = Operation.ROTATE;
            }
        }

        if (input.isKeyDown(GLFW_KEY_LEFT_CONTROL) && input.isKeyPressed(GLFW_KEY_F11)) {
            saveScreenshot();
        }

        if (input.isKeyDown(GLFW_KEY_LEFT_CONTROL) && input.isKeyPressed(GLFW_KEY_GRAVE_ACCENT)) {
            engine.setIO("imgui.debug_overlay", !engine.getIO("imgui.debug_overlay"));
        }


        if (input.isKeyPressed(GLFW_KEY_F10)) {
            focusSelected();
        }


        Renderer renderer = engine.getRenderer();
        if (input.isKeyPressed(input.getMapping("wireframe").code())) {renderer.enableWireframe(!renderer.wireframeEnabled());}
        if (input.isKeyPressed(input.getMapping("changeRenderingMode").code())) {
            int next = renderer.getRenderingMode().ordinal() + 1;
            if (next >= Renderer.RenderMode.MODE_MAX.ordinal()) next = 0;
            renderer.setRenderingMode(Renderer.RenderMode.values()[next]);
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


    private void pushCommand(EditCommand cmd) {
        undoStack.push(cmd);
        redoStack.clear();
        while (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        EditCommand cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        EditCommand cmd = redoStack.pop();
        cmd.redo();
        undoStack.push(cmd);
    }

    /**Called when an object is rebuilt so older history entries keep pointing at a live object*/
    private void remapAll(GameObject from, GameObject to) {
        for (EditCommand c : undoStack) c.remap(from, to);
        for (EditCommand c : redoStack) c.remap(from, to);
        if (selectedObject == from) selectedObject = to;
    }

    private class TransformCommand implements EditCommand {
        private GameObject object;
        private final Matrix4f before;
        private final Matrix4f after;

        TransformCommand(GameObject object, Matrix4f before, Matrix4f after) {
            this.object = object;
            this.before = before;
            this.after = after;
        }

        public void undo() { object.transform.applyWorldMatrix(before); }
        public void redo() { object.transform.applyWorldMatrix(after); }
        public void remap(GameObject from, GameObject to) { if (object == from) object = to; }
    }

    private class RenameCommand implements EditCommand {
        private GameObject object;
        private final String before;
        private final String after;

        RenameCommand(GameObject object, String before, String after) {
            this.object = object;
            this.before = before;
            this.after = after;
        }

        public void undo() { object.setName(before); }
        public void redo() { object.setName(after); }
        public void remap(GameObject from, GameObject to) { if (object == from) object = to; }
    }

    private class ReparentCommand implements EditCommand {
        private GameObject object;
        private GameObject from;
        private GameObject to;

        ReparentCommand(GameObject object, GameObject from, GameObject to) {
            this.object = object;
            this.from = from;
            this.to = to;
        }

        public void undo() { reparent(object, from); }
        public void redo() { reparent(object, to); }
        public void remap(GameObject f, GameObject t) {
            if (object == f) object = t;
            if (from == f) from = t;
            if (to == f) to = t;
        }
    }

    private class LifecycleCommand implements EditCommand {
        private GameObject live;
        private GameObject parent;
        private Snapshot snapshot;
        private List<GameObject> tree;
        private boolean exists;

        LifecycleCommand(GameObject live, GameObject parent, boolean exists) {
            this.live = live;
            this.parent = parent;
            this.exists = exists;
            this.tree = flatten(live);
        }

        void flip() {
            if (exists) {
                unhideTree(live);
                snapshot = capture(live);
                tree = flatten(live);
                removeFromScene(live);
                exists = false;
            } else {
                GameObject fresh = build(snapshot);
                attachToScene(fresh, parent);
                List<GameObject> freshTree = flatten(fresh);
                for (int i = 0; i < tree.size() && i < freshTree.size(); i++) {
                    remapAll(tree.get(i), freshTree.get(i));
                }
                live = fresh;
                tree = freshTree;
                selectedObject = fresh;
                selectedComponent = null;
                exists = true;
            }
        }

        public void undo() {flip();}
        public void redo() {flip();}
        public void remap(GameObject from, GameObject to) {
            if (live == from) live = to;
            if (parent == from) parent = to;
        }
    }

    private static class Snapshot {
        String name;
        boolean debug;
        Vector3f position;
        Vector3f scale;
        Quaternionf rotation;
        List<Component> components = new ArrayList<>();
        List<Snapshot> children = new ArrayList<>();
    }

    private Snapshot capture(GameObject o) {
        Snapshot s = new Snapshot();
        s.name = o.getName();
        s.debug = o.isDebuggingObject;
        s.position = o.transform.getPosition();
        s.scale = o.transform.getScale();
        s.rotation = new Quaternionf(o.transform.getRotationQuat());
        for (Component c : o.getComponents()) s.components.add(c.copy());
        for (GameObject child : o.children) s.children.add(capture(child));
        return s;
    }

    private GameObject build(Snapshot s) {
        GameObject o = new GameObject(engine);
        o.setName(s.name);
        o.isDebuggingObject = s.debug;
        o.transform.position(s.position);
        o.transform.scale(s.scale);
        o.transform.rotate(new Quaternionf(s.rotation));
        for (Component c : s.components) o.attachComponent(c.copy());
        for (Snapshot child : s.children) o.addChild(build(child));
        return o;
    }

    private List<GameObject> flatten(GameObject root) {
        List<GameObject> out = new ArrayList<>();
        root.cascade(out::add);
        return out;
    }

    /**True if node is the ancestor itself or lives somewhere below it*/
    private boolean isInTree(GameObject ancestor, GameObject node) {
        GameObject current = node;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.parent;
        }
        return false;
    }

    private void attachToScene(GameObject object, GameObject parent) {
        if (parent != null) parent.addChild(object);
        else engine.getSceneManager().getActiveScene().addObject(object);
    }

    private void removeFromScene(GameObject target) {
        Scene scene = engine.getSceneManager().getActiveScene();
        if (selectedObject != null && isInTree(target, selectedObject)) {
            selectedObject = null;
            selectedComponent = null;
        }
        target.cascade(o -> untagAll(scene, o));
        if (target.parent != null) {
            target.parent.children.remove(target);
            target.parent = null;
        } else {
            scene.removeObject(target.id);
        }
        target.destroy();
    }

    private void untagAll(Scene scene, GameObject object) {
        for (String tag : new ArrayList<>(scene.getTags())) {
            if (scene.isTagged(object, tag)) scene.untagObject(object, tag);
        }
    }

    private void deleteSelected() {
        if (selectedObject != null) deleteObject(selectedObject);
    }

    private void deleteObject(GameObject target) {
        if (target == null || target.isDebuggingObject) return;
        CameraComponent primary = engine.getSceneManager().getActiveScene().getPrimaryCamera();
        if (primary != null && isInTree(target, primary.getOwner())) {
            Logger.warning("Can't delete " + target.getName() + ": it owns the primary camera");
            return;
        }
        LifecycleCommand cmd = new LifecycleCommand(target, target.parent, true);
        cmd.flip();
        pushCommand(cmd);
    }

    private void duplicateSelected() {
        if (selectedObject == null) return;
        GameObject source = selectedObject;
        boolean wasHidden = isHidden(source);
        if (wasHidden) setHidden(source, false);
        GameObject copy = source.clone();
        if (wasHidden) setHidden(source, true);
        attachToScene(copy, source.parent);
        pushCommand(new LifecycleCommand(copy, source.parent, true));
        selectedObject = copy;
        selectedComponent = null;
    }

    private void focusSelected() {
        CameraComponent cam = engine.getSceneManager().getActiveScene().getPrimaryCamera();
        if (cam != null && selectedObject != null) cam.lookAt(selectedObject.transform.getPosition());
    }

    private void reparent(GameObject object, GameObject newParent) {
        Scene scene = engine.getSceneManager().getActiveScene();
        Matrix4f world = object.transform.calculateWorldMatrix();
        if (object.parent != null) {
            object.parent.children.remove(object);
            object.parent = null;
        } else {
            scene.removeObject(object.id);
        }
        if (newParent != null) newParent.addChild(object);
        else scene.addObject(object);
        object.transform.applyWorldMatrix(world);
    }

    private boolean canReparent(GameObject dragged, GameObject target) {
        if (dragged == null || dragged == target) return false;
        if (dragged.parent == target) return false;
        return target == null || !isInTree(dragged, target);
    }

    private void doReparent(GameObject dragged, GameObject target) {
        if (!canReparent(dragged, target)) return;
        GameObject oldParent = dragged.parent;
        reparent(dragged, target);
        pushCommand(new ReparentCommand(dragged, oldParent, target));
    }


    private void beginTransformEdit(GameObject object) {
        if (pendingTarget != null) return;
        pendingTarget = object;
        pendingBefore = new Matrix4f(object.transform.calculateWorldMatrix());
    }

    private void endTransformEdit() {
        if (pendingTarget == null) return;
        Matrix4f after = new Matrix4f(pendingTarget.transform.calculateWorldMatrix());
        if (!after.equals(pendingBefore, 1e-6f)) {
            pushCommand(new TransformCommand(pendingTarget, pendingBefore, after));
        }
        pendingTarget = null;
        pendingBefore = null;
    }

    /**Call right after a widget that edits editTarget's transform*/
    private void trackEdit() {
        if (editTarget == null) return;
        if (ImGui.isItemActivated()) beginTransformEdit(editTarget);
        if (ImGui.isItemDeactivated()) endTransformEdit();
    }


    private boolean isHidden(GameObject o) {return hiddenObjects.contains(o);}

    private void setHidden(GameObject root, boolean hide) {
        root.cascade(go -> {
            if (hide) hiddenObjects.add(go);
            else hiddenObjects.remove(go);

            ModelComponent mc = go.getComponent(ModelComponent.class);
            if (mc == null) return;
            if (hide) {
                if (!hiddenModels.containsKey(mc)) {
                    hiddenModels.put(mc, mc.getRenderMethod());
                    mc.setRenderMethod(Renderer.RenderMethod.RENDER_SKIP);
                }
            } else {
                Renderer.RenderMethod previous = hiddenModels.remove(mc);
                if (previous != null) mc.setRenderMethod(previous);
            }
        });
    }

    private void unhideTree(GameObject root) {setHidden(root, false);}

    private void renderConsole() {
        if (!showConsole.get()) return;
        if (!ImGui.begin("Console", showConsole)) {
            ImGui.end();
            return;
        }

        long revision = Logger.getRevision();
        boolean fresh = revision != lastConsoleRevision;
        if (fresh) {
            consoleEntries = Logger.getHistory();
            lastConsoleRevision = revision;
        }

        if (ImGui.button("Clear")) {Logger.clearHistory();}
        ImGui.sameLine();
        ImGui.checkbox("Info", consoleInfo);
        ImGui.sameLine();
        ImGui.checkbox("Warnings", consoleWarn);
        ImGui.sameLine();
        ImGui.checkbox("Errors", consoleError);
        ImGui.sameLine();
        ImGui.checkbox("Auto-scroll", consoleAutoScroll);
        ImGui.sameLine();
        ImGui.setNextItemWidth(200);
        ImGui.inputTextWithHint("##consoleFilter", "Filter...", consoleFilter);
        ImGui.sameLine();
        boolean copyAll = ImGui.button("Copy");
        ImGui.separator();

        String filter = consoleFilter.get().trim().toLowerCase();
        StringBuilder copyBuffer = copyAll ? new StringBuilder() : null;

        ImGui.beginChild("ConsoleScroll", 0, 0, false, ImGuiWindowFlags.HorizontalScrollbar);
        for (Logger.Entry e : consoleEntries) {
            boolean levelOk = switch (e.level()) {
                case INFO -> consoleInfo.get();
                case WARNING -> consoleWarn.get();
                case ERROR -> consoleError.get();
            };
            if (!levelOk) continue;
            String line = "[" + e.time() + "] [" + e.caller() + "] " + e.message();
            if (!filter.isEmpty() && !line.toLowerCase().contains(filter)) continue;

            switch (e.level()) {
                case INFO -> ImGui.textColored(0.75f, 0.75f, 0.9f, 1f, line);
                case WARNING -> ImGui.textColored(1f, 0.85f, 0.35f, 1f, line);
                case ERROR -> ImGui.textColored(1f, 0.4f, 0.4f, 1f, line);
            }
            if (copyBuffer != null) copyBuffer.append(line).append('\n');
        }
        if (fresh && consoleAutoScroll.get()) ImGui.setScrollHereY(1f);
        ImGui.endChild();

        if (copyBuffer != null) ImGui.setClipboardText(copyBuffer.toString());
        ImGui.end();
    }

    private void renderFpsProfiler() {
        if (!showProfiler.get()) return;
        ftUpdateCount += engine.getDeltaTime();
        if (ftUpdateCount >= FT_UPDATE_INTERVAL) {
            ftUpdateCount -= FT_UPDATE_INTERVAL;
            frameTimes[frameIndex] = engine.getDeltaTime() * 1000;
            frameIndex = (frameIndex + 1) % FRAME_HISTORY_SIZE;
            if (frameIndex == 0) ftBufferFilled = true;
            lastFPS = engine.getFps();
            lastMS = engine.getDeltaTime() * 1000;
            int passCount = engine.getRenderer().getPassCount();
            if (passCount != lastPassNames.length) {
                lastCpuMS = new float[passCount];
                lastGpuMS = new float[passCount];
                lastPassNames = new String[passCount];
            }

            for (int i = 0; i < passCount; i++) {
                RenderPass rp = engine.getRenderer().getRenderPass(i);
                lastPassNames[i] = rp.getName();
                lastGpuMS[i] = rp.queryGpuTime();
                lastCpuMS[i] = rp.queryCpuTime();
            }
        }


        if (!ImGui.begin("FPS Profiler", showProfiler)) {
            ImGui.end();
            return;
        }

        ImGui.text("FPS Profiler");
        ImGui.text(String.format("%d FPS (%.3fms)", engine.getFps(), engine.getDeltaTime()*1000));
        ImGui.separator();
        for (int i = 0; i < lastPassNames.length; i++) {
            ImGui.text(String.format(
                    "%s: %.2fms total (cpu: %.3fms)",
                    lastPassNames[i],
                    lastGpuMS[i] + lastCpuMS[i],
                    lastCpuMS[i]
            ));
        }


        if (ftBufferFilled) {
            if (ImPlot.beginPlot("Frame Time (ms)", "", "", new ImVec2(400, 200), 0, ImPlotFlags.CanvasOnly, ImPlotFlags.CanvasOnly)) {
                int count = FRAME_HISTORY_SIZE;
                for (int i = 0; i < count; i++) {
                    int idx = ftBufferFilled ? (frameIndex + i) % count : i;
                    float v = frameTimes[idx];
                    if (v > 50f) v = 50f;
                    plotX[i] = (float) i;
                    plotY[i] = v;
                }
                ImPlot.plotLine("Frame Time", plotX, plotY);
                Float[] targetX = {0f, (float) count};
                Float[] targetY = {16.7f, 16.7f};
                ImPlot.plotLine("60 FPS (16.6ms)", targetX, targetY);
                ImPlot.endPlot();
            }
        }

        ImGui.end();

    }

    private void renderDockspace() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
        ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
        ImGui.setNextWindowViewport(viewport.getID());

        int windowFlags = ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.NoBackground;

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
        ImGui.begin("DockSpaceHost", windowFlags);
        ImGui.popStyleVar(3);

        int dockspaceId = ImGui.getID("MainDockSpace");
        ImGui.dockSpace(dockspaceId, 0f, 0f, ImGuiDockNodeFlags.PassthruCentralNode);

        ImGui.end();
    }

    private void renderViewportWindow() {
        if (ImGui.begin("Viewport")) {
            if (engine.getIO("imgui.debug_overlay")) {renderDebugOverlay();}
            isMouseInViewport = ImGui.isWindowHovered();
            renderViewportToolbar();

            ImVec2 avail = ImGui.getContentRegionAvail();
            ImVec2 cursorScreen = ImGui.getCursorScreenPos();
            viewportPos[0] = cursorScreen.x;
            viewportPos[1] = cursorScreen.y;
            viewportSize[0] = avail.x;
            viewportSize[1] = avail.y;
            if (avail.x > 0 && avail.y > 0) {
                int tex = engine.getRenderer().getColorTexture();
                ImGui.image(tex, avail.x, avail.y, 0, 1, 1, 0);
            }
        }
        ImGui.end();
    }

    private boolean toolButton(String label, boolean active) {
        if (active) ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(ImGuiCol.TabActive));
        boolean clicked = ImGui.button(label);
        if (active) ImGui.popStyleColor();
        return clicked;
    }

    private float[] currentSnap() {
        if (manipulationMode == Operation.ROTATE) return snapRotate;
        if (manipulationMode == Operation.SCALE) return snapScale;
        return snapTranslate;
    }

    private void renderViewportToolbar() {
        if (toolButton("Move", manipulationMode == Operation.TRANSLATE)) manipulationMode = Operation.TRANSLATE;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Translate (T)");
        ImGui.sameLine();
        if (toolButton("Rotate", manipulationMode == Operation.ROTATE)) manipulationMode = Operation.ROTATE;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Rotate (R)");
        ImGui.sameLine();
        if (toolButton("Scale", manipulationMode == Operation.SCALE)) manipulationMode = Operation.SCALE;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Scale (B)");
        ImGui.sameLine();
        if (toolButton(localSpace ? "Local" : "World", false)) localSpace = !localSpace;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Gizmo space");
        ImGui.sameLine();
        if (toolButton("Snap", snapEnabled)) snapEnabled = !snapEnabled;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Snapping (hold Ctrl to toggle temporarily)");
        ImGui.sameLine();
        float[] snap = currentSnap();
        float[] v = {snap[0]};
        ImGui.setNextItemWidth(70);
        if (ImGui.dragFloat("##snapValue", v, 0.05f, 0.01f, 360f)) Arrays.fill(snap, v[0]);
        ImGui.sameLine();
        if (ImGui.button("Focus")) focusSelected();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Look at selected object (F)");

        float btnWidth = ImGui.getFrameHeight(); // square button, height == width
        ImGui.sameLine(ImGui.getWindowWidth() - btnWidth - ImGui.getStyle().getWindowPaddingX());
        if (ImGui.button("\uF030", btnWidth, btnWidth)) {saveScreenshot();}
        if (ImGui.isItemHovered()) {ImGui.setTooltip("Save screenshot");}
    }

    private void renderSceneWindow() {
        if (!showScene.get()) return;
        if (!ImGui.begin("Scene", showScene)) {
            ImGui.end();
            return;
        }

        if (ImGui.beginTabBar("SceneTabs")) {
            if (ImGui.beginTabItem("Scene tree")) {
                renderSceneTree();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Properties")) {
                renderSceneSettings();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }

        ImGui.end();
    }

    private void placeObject(GameObject object, CameraComponent cam) {
        object.transform.position(cam.getOwner().transform.getPosition());
        object.transform.transformPos(0, 1, 0);
        engine.getSceneManager().getActiveScene().addObject(object);
    }

    private void renderSceneTree() {
        Scene scene = engine.getSceneManager().getActiveScene();
        if (ImGui.button("\uF0FE Add object")) {
            ImGui.openPopup("addObject");
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##treeFilter", "Search...", treeFilter);

        if (ImGui.beginPopup("addObject")) {
            for (String type : new String[]{"Cube", "Capsule", "Torus", "Sphere", "Wireframe", "Cylinder", "Cone"}) {
                if (ImGui.menuItem(type)) {
                    openDialog(type);
                }
            }
            ImGui.separator();
            for (String type : new String[]{"Grid", "Line", "Plane", "Triangle"}) {
                if (ImGui.menuItem(type)) {
                    openDialog(type);
                }
            }
            ImGui.endPopup();
        }

        renderAddDialog();
        ImGui.separator();

        for (GameObject obj : scene.getObjects()) {
            renderGameObjectNode(obj);
        }

        ImVec2 remaining = ImGui.getContentRegionAvail();
        if (remaining.y > 8) {
            ImGui.invisibleButton("##treeEmpty", remaining.x, remaining.y);
            if (ImGui.isItemClicked()) {selectedObject = null; selectedComponent = null;}
            if (ImGui.beginDragDropTarget()) {
                Object payload = ImGui.acceptDragDropPayload("GAMEOBJECT");
                if (payload instanceof GameObject dragged) doReparent(dragged, null);
                ImGui.endDragDropTarget();
            }
        }
    }

    private boolean shouldOpenDialog = false;

    private void openDialog(String type) {
        pendingType = type;
        newName.set(type);
        shouldOpenDialog = true;
    }

    private void renderAddDialog() {
        // openPopup must be called outside the menu popup's scope
        if (shouldOpenDialog) {
            ImGui.openPopup("Add object");
            shouldOpenDialog = false;
        }

        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        Scene scene = engine.getSceneManager().getActiveScene();
        if (ImGui.beginPopupModal("Add object", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.inputText("Name", newName);

            switch (pendingType) {
                case "Sphere" -> {
                    ImGui.inputInt("Slices", slices);
                    ImGui.inputInt("Stacks", stacks);
                }
                case "Cylinder", "Cone" -> {
                    ImGui.inputInt("Slices", slices);
                    ImGui.checkbox("Capped", capped);
                }
                case "Capsule" -> {
                    ImGui.inputInt("Slices", slices);
                    ImGui.inputInt("Stacks", stacks);
                    ImGui.inputFloat("Radius", mnrRadius);
                }
                case "Torus" -> {
                    ImGui.inputInt("Slices", slices);
                    ImGui.inputInt("Stacks", stacks);
                    ImGui.inputFloat("Minor radius", mnrRadius);
                    ImGui.inputFloat("Major radius", mjrRadius);

                }
                case "Grid" -> {
                    ImGui.inputInt("Divisions", divisions);
                }
            }

            if (ImGui.button("Add")) {
                GameObject obj = new GameObject(engine);
                obj.setName(newName.get());
                obj.attachComponent(buildModel(pendingType));
                placeObject(obj, scene.getPrimaryCamera());
                pushCommand(new LifecycleCommand(obj, null, true));
                selectedObject = obj;
                selectedComponent = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private ModelComponent buildModel(String type) {
        var p = engine.getAssetLoader().primitives;
        return switch (type) {
            case "Cube" -> p.cube();
            case "Capsule" -> p.capsule(mnrRadius.get(), 1f, slices.get(), stacks.get());
            case "Torus" -> p.torus(mjrRadius.get(), mnrRadius.get(), slices.get(), stacks.get());
            case "Sphere" -> p.sphere(slices.get(), stacks.get());
            case "Wireframe" -> p.wireframeBox();
            case "Cylinder" -> p.cylinder(slices.get(), capped.get());
            case "Cone" -> p.cone(slices.get(), capped.get());
            case "Grid" -> p.grid(divisions.get());
            case "Line" -> p.line();
            case "Plane" -> p.plane();
            default -> p.triangle();
        };
    }

    private boolean matchesFilter(GameObject o) {
        String f = treeFilter.get().trim().toLowerCase();
        if (f.isEmpty() || o.getName().toLowerCase().contains(f)) return true;
        for (GameObject c : o.children) if (matchesFilter(c)) return true;
        return false;
    }

    private void renderGameObjectNode(GameObject obj) {
        if (obj.getName().startsWith("###") && obj.getName().endsWith("###")) return;
        if (!matchesFilter(obj)) return;
        ImGui.pushID(obj.id.toString());

        int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (obj == selectedObject) flags |= ImGuiTreeNodeFlags.Selected;

        float nodeScreenX = ImGui.getCursorScreenPosX();
        float nodeScreenY = ImGui.getCursorScreenPosY();

        boolean filtering = !treeFilter.get().isEmpty();
        boolean hidden = isHidden(obj);
        if (filtering) ImGui.setNextItemOpen(true);
        if (hidden) ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f);
        boolean open = ImGui.treeNodeEx(obj.getName(), flags);
        ImGui.setItemAllowOverlap();

        drawCustomArrow(nodeScreenX, nodeScreenY, open);
        if (hidden) ImGui.popStyleColor();

        if (ImGui.isItemClicked()) { selectedObject = obj; selectedComponent = null; }

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("GAMEOBJECT", obj);
            ImGui.text(obj.getName());
            ImGui.endDragDropSource();
        }
        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload("GAMEOBJECT");
            if (payload instanceof GameObject dragged) doReparent(dragged, obj);
            ImGui.endDragDropTarget();
        }

        if (ImGui.beginPopupContextItem("nodeCtx")) {
            selectedObject = obj;
            selectedComponent = null;
            if (ImGui.menuItem("Focus", "F")) focusSelected();
            if (ImGui.menuItem("Duplicate", "Ctrl+D")) duplicateSelected();
            if (ImGui.menuItem(hidden ? "Show" : "Hide")) setHidden(obj, !hidden);
            if (ImGui.menuItem("Unparent", "", false, obj.parent != null)) doReparent(obj, null);
            if (ImGui.menuItem("Copy ID")) ImGui.setClipboardText(obj.id.toString());
            ImGui.separator();
            if (ImGui.menuItem("Delete", "Del")) deleteObject(obj);
            ImGui.endPopup();
        }

        if (open) {
            if (!obj.children.isEmpty()) {
                float childrenScreenX = ImGui.getCursorScreenPosX();
                float childrenScreenY = ImGui.getCursorScreenPosY();
                if (filtering) ImGui.setNextItemOpen(true);
                boolean childrenOpen = ImGui.treeNode("Children (" + obj.children.size() + ")");
                drawCustomArrow(childrenScreenX, childrenScreenY, childrenOpen);
                if (childrenOpen) {
                    for (GameObject child : new ArrayList<>(obj.children)) { renderGameObjectNode(child); }
                    ImGui.treePop();
                }
            }

            for (Component c : obj.getComponents()) {
                String icon = fontMap.getOrDefault(c.getClass().getSimpleName(), "\uF016");
                String label = icon + " " + c.getClass().getSimpleName();
                ImGui.pushID(c.getID().toString());
                if (ImGui.selectable(label, false, 0, ImGui.calcTextSize(label).x, 0)) {
                    selectedObject = obj;
                    selectedComponent = c;
                }
                ImGui.popID();
                if (!c.name.equals("object_component")) {
                    ImGui.sameLine();
                    textDim("(" + c.name + ")");
                }
            }
            ImGui.treePop();
        }

        ImGui.popID();
    }


    private void drawCustomArrow(float x, float y, boolean open) {
        float lineHeight = ImGui.getTextLineHeight();
        int bgColor = ImGui.getColorU32(ImGuiCol.WindowBg);
        ImGui.getWindowDrawList().addRectFilled(x, y, x + lineHeight, y + lineHeight, bgColor);
        String icon = open ? "\uF146" : "\uF0FE";
        ImGui.getWindowDrawList().addText(x, y, ImGui.getColorU32(ImGuiCol.Text), icon);
    }

    private final ImBoolean directionalLEnabled = new ImBoolean();
    private final ImBoolean directionalLShadows = new ImBoolean();
    private final ImBoolean fogEnabled = new ImBoolean();
    private final ImBoolean fogBlendSky = new ImBoolean();
    private final float[] fogColor = new float[3];
    private final float[] fogDensity = new float[1];
    private final float[] fogStart = new float[1];
    private final float[] fogEnd = new float[1];
    private final ImBoolean skyEnabled = new ImBoolean();
    private final ImBoolean showMoon = new ImBoolean();
    private final ImBoolean showSun = new ImBoolean();
    private final ImBoolean showClouds = new ImBoolean();
    private final ImBoolean showStars = new ImBoolean();
    private final ImBoolean dayTimeEffect = new ImBoolean();
    private final ImFloat dayTimeSpeedRef = new ImFloat(30);
    private boolean dayTimePlaying = false;
    private float dayTimeAccumulator = 0f;

    private void renderSceneSettings() {
        Scene scene = engine.getSceneManager().getActiveScene();

        ImGui.text("Scene name:");
        ImGui.sameLine();
        ImGui.textColored(1f, 1f, 1f, 1f, scene.getName());

        ImGui.text("Root object count:");
        ImGui.sameLine();
        ImGui.text(String.valueOf(scene.objectCount()));

        ImGui.text("Scene ID:");
        ImGui.sameLine();
        textDim(scene.id.toString());
        Scene.Environment environ = scene.environment;
        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Light count:");
        ImGui.sameLine();
        ImGui.text(engine.getComponentRegistry().getCount(LightComponent.class) + " (+1)");

        directionalLEnabled.set(environ.globalLight.enabled);
        if (ImGui.checkbox("Directional light enabled", directionalLEnabled)) {
            environ.globalLight.enabled = directionalLEnabled.get();
        }

        directionalLShadows.set(environ.globalLight.castShadow);
        if (ImGui.checkbox("Directional light shadows", directionalLShadows)) {
            environ.globalLight.castShadow = directionalLShadows.get();
        }

        float[] color = new float[]{environ.globalLight.color.x, environ.globalLight.color.y, environ.globalLight.color.z};
        if (ImGui.colorEdit3("Directional light color", color)) {
            environ.globalLight.color.set(color[0], color[1], color[2]);
        }

        float[] ambient = new float[]{environ.globalLight.ambient.x, environ.globalLight.ambient.y, environ.globalLight.ambient.z};
        if (ImGui.colorEdit3("Directional light ambient", ambient)) {
            environ.globalLight.ambient.set(ambient[0], ambient[1], ambient[2]);
        }

        ImGui.text("Directional light direction");
        renderPositionField("lightDir", environ.globalLight.direction);

        float[] value = new float[]{environ.globalLight.intensity};
        if (ImGui.dragFloat("Directional light intensity", value, 0.1f, 0f, 10f)) {
            environ.globalLight.intensity = value[0];
        }

        float[] bvalue = new float[]{environ.globalLight.shadowNormalBias};
        if (ImGui.dragFloat("Shadow normal Bias", bvalue, 0.001f, 0f, 1f)) {
            environ.globalLight.shadowNormalBias = bvalue[0];
        }

        ImGui.separator();
        Scene.Fog fog = scene.environment.fog;

        fogEnabled.set(fog.enabled);
        if (ImGui.checkbox("Fog enabled", fogEnabled)) {
            fog.enabled = fogEnabled.get();
        }

        fogBlendSky.set(fog.blendSkyColor);
        if (ImGui.checkbox("Fog blends with sky color", fogBlendSky)) {
            fog.blendSkyColor = fogBlendSky.get();
        }

        ImGui.text("Fog mode:");
        ImGui.sameLine();
        if (ImGui.radioButton("Linear", fog.mode == Scene.Fog.FogMode.LINEAR)) {
            fog.mode = Scene.Fog.FogMode.LINEAR;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Quadratic", fog.mode == Scene.Fog.FogMode.QUADRATIC)) {
            fog.mode = Scene.Fog.FogMode.QUADRATIC;
        }

        boolean isQuadratic = fog.mode == Scene.Fog.FogMode.QUADRATIC;

        fogColor[0] = fog.color.x;
        fogColor[1] = fog.color.y;
        fogColor[2] = fog.color.z;
        if (ImGui.colorEdit3("Fog color", fogColor)) {
            fog.color.set(fogColor[0], fogColor[1], fogColor[2]);
        }

        ImGui.beginDisabled(!isQuadratic);
        fogDensity[0] = fog.density;
        if (ImGui.dragFloat("Fog density", fogDensity, 0.001f, 0f, 1f)) {
            fog.density = fogDensity[0];
        }
        ImGui.endDisabled();

        ImGui.beginDisabled(isQuadratic);
        fogStart[0] = fog.start;
        if (ImGui.dragFloat("Fog start", fogStart, 0.1f, 0f, fog.end)) {
            fog.start = fogStart[0];
        }

        fogEnd[0] = fog.end;
        if (ImGui.dragFloat("Fog end", fogEnd, 0.1f, fog.start, 1000f)) {
            fog.end = fogEnd[0];
        }
        ImGui.endDisabled();

        ImGui.separator();

        Scene.Sky sky = scene.environment.sky;
        ImGui.text("Skybox mode: " + sky.getMode().name());
        boolean procedural = sky.getMode() == Scene.Sky.SkyMode.PROCEDURAL;

        skyEnabled.set(sky.enabled);
        if (ImGui.checkbox("Sky enabled", skyEnabled)) {
            sky.enabled = skyEnabled.get();
        }
        ImGui.beginDisabled(!procedural);

        showSun.set(sky.showSun);
        if (ImGui.checkbox("Show sun", showSun)) {
            sky.showSun = showSun.get();
        }

        showMoon.set(sky.showMoon);
        if (ImGui.checkbox("Show moon", showMoon)) {
            sky.showMoon = showMoon.get();
        }

        showClouds.set(sky.clouds);
        if (ImGui.checkbox("Clouds", showClouds)) {
            sky.clouds = showClouds.get();
        }

        showStars.set(sky.stars);
        if (ImGui.checkbox("Stars", showStars)) {
            sky.stars = showStars.get();
        }

        dayTimeEffect.set(sky.dayTimeEffect);
        if (ImGui.checkbox("Day time affects sky", dayTimeEffect)) {
            sky.dayTimeEffect = dayTimeEffect.get();
        }

        ImGui.text("Weather:");
        ImGui.sameLine();
        if (ImGui.radioButton("Clear", sky.weather == Scene.Sky.SkyWeather.CLEAR)) {
            sky.weather = Scene.Sky.SkyWeather.CLEAR;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Partly cloudy", sky.weather == Scene.Sky.SkyWeather.PARTLY_CLOUDY)) {
            sky.weather = Scene.Sky.SkyWeather.PARTLY_CLOUDY;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Cloudy", sky.weather == Scene.Sky.SkyWeather.CLOUDY)) {
            sky.weather = Scene.Sky.SkyWeather.CLOUDY;
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Overcast", sky.weather == Scene.Sky.SkyWeather.OVERCAST)) {
            sky.weather = Scene.Sky.SkyWeather.OVERCAST;
        }

        ImGui.text("Sun direction");
        renderPositionField("sunDir", sky.sunDirection);

        ImGui.text("Moon direction");
        renderPositionField("moonDir", sky.moonDirection);

        int[] time = {sky.dayTime};
        if (ImGui.sliderInt("Time", time, 0, sky.dayLengthSeconds)) {
            sky.dayTime = time[0];
            sky.calculateDirections();
            syncLightWithSky(sky, scene);
        }

        ImGui.sameLine();
        if (ImGui.button(dayTimePlaying ? "Stop" : "Play")) {
            dayTimePlaying = !dayTimePlaying;
            if (dayTimePlaying) {
                dayTimeAccumulator = 0f;
            }
        }

        ImGui.setNextItemWidth(120);
        ImGui.inputFloat("Day Time Speed", dayTimeSpeedRef);

        int[] dayLength = {sky.dayLengthSeconds};
        if (ImGui.sliderInt("Day length (seconds)", dayLength, 10, 86400)) {
            sky.dayLengthSeconds = dayLength[0];
        }

        ImGui.endDisabled();
    }

    private void syncLightWithSky(Scene.Sky sky, Scene scene) {
        float sunElevation = -sky.sunDirection.y;
        float moonElevation = -sky.moonDirection.y;

        // 1 = fully sunlit, 0 = fully moonlit
        float blend = smoothstep(-0.1f, 0.1f, sunElevation);

        Vector3f sunColor = new Vector3f(1.0f, 0.96f, 0.88f);
        Vector3f moonColor = new Vector3f(0.4f, 0.45f, 0.65f);

        Vector3f dir = new Vector3f();
        sky.sunDirection.lerp(sky.moonDirection, 1f - blend, dir);
        scene.environment.globalLight.direction.set(dir).normalize();

        Vector3f color = new Vector3f();
        sunColor.lerp(moonColor, 1f - blend, color);
        scene.environment.globalLight.color.set(color);

        float sunFactor = Math.max(0f, sunElevation);
        float moonFactor = Math.max(0f, moonElevation) * 0.1f; // moon much dimmer than the sun
        float dayFactor = Math.clamp(sunFactor + moonFactor, 0.025f, 1f);

        scene.environment.globalLight.ambient.set(0.1f, 0.1f, 0.15f).mul(dayFactor);
        scene.environment.globalLight.intensity = 0.6f + dayFactor * 1.3f;
    }

    private float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private void axisLabel(String name, int axis) {
        float[][] colors = {{0.9f, 0.35f, 0.35f}, {0.4f, 0.8f, 0.4f}, {0.4f, 0.55f, 0.95f}, {0.8f, 0.8f, 0.8f}};
        float[] c = colors[axis];
        ImGui.textColored(c[0], c[1], c[2], 1f, name);
    }

    private boolean renderPositionField(String id, Vector3f pos, String xName, String yName, String zName, float min, float max) {
        float[] x = {pos.x};
        float[] y = {pos.y};
        float[] z = {pos.z};

        float width = (ImGui.calcItemWidth() - ImGui.getStyle().getItemSpacingX() * 4) / 3f;
        ImGui.pushID(id);
        ImGui.pushItemWidth(width);

        axisLabel(xName, 0);
        ImGui.sameLine();
        boolean changed = ImGui.dragFloat("##X", x, 0.1f, min, max);
        trackEdit();
        ImGui.sameLine();

        axisLabel(yName, 1);
        ImGui.sameLine();
        changed |= ImGui.dragFloat("##Y", y, 0.1f, min, max);
        trackEdit();
        ImGui.sameLine();

        axisLabel(zName, 2);
        ImGui.sameLine();
        changed |= ImGui.dragFloat("##Z", z, 0.1f, min, max);
        trackEdit();
        ImGui.popItemWidth();
        ImGui.popID();

        if (changed) pos.set(x[0], y[0], z[0]);
        return changed;
    }

    private boolean renderPositionField(String id, Vector3f pos) {return renderPositionField(id, pos, "X", "Y", "Z", -99999, 99999);}
    private boolean renderPositionField4D(String id, Vector4f value, String xName, String yName, String zName, String wName, float min, float max) {
        float[] x = {value.x};
        float[] y = {value.y};
        float[] z = {value.z};
        float[] w = {value.w};

        float width = (ImGui.calcItemWidth() - ImGui.getStyle().getItemSpacingX() * 6) / 4f;
        ImGui.pushID(id);

        ImGui.pushItemWidth(width);

        axisLabel(xName, 0);
        ImGui.sameLine();
        boolean changed = ImGui.dragFloat("##X", x, 0.1f, min, max);
        trackEdit();
        ImGui.sameLine();

        axisLabel(yName, 1);
        ImGui.sameLine();
        changed |= ImGui.dragFloat("##Y", y, 0.1f, min, max);
        trackEdit();
        ImGui.sameLine();

        axisLabel(zName, 2);
        ImGui.sameLine();
        changed |= ImGui.dragFloat("##Z", z, 0.1f, min, max);
        trackEdit();
        ImGui.sameLine();

        axisLabel(wName, 3);
        ImGui.sameLine();
        changed |= ImGui.dragFloat("##W", w, 0.1f, min, max);
        trackEdit();

        ImGui.popItemWidth();
        ImGui.popID();

        if (changed) {value.set(x[0], y[0], z[0], w[0]);}
        return changed;
    }

    private boolean renderPositionField4D(String id, Vector4f value) {
        return renderPositionField4D(id, value, "X", "Y", "Z", "W", -999999, 9999999);
    }

    private void textDim(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.55f, 0.55f, 1f);
        ImGui.text(text);
        ImGui.popStyleColor();
    }

    private boolean quaternionMode = false;
    private void renderObjectMenu(GameObject object) {
        if (!showInspector.get()) return;
        if (!ImGui.begin("GameObject Editor", showInspector)) {
            ImGui.end();
            return;
        }

        if (object == null) {
            textDim("Select an object in the Scene tree or click one in the viewport.");
            ImGui.end();
            return;
        }

        ImGui.text("ID: ");
        ImGui.sameLine();
        textDim(object.id.toString());
        oName.set(object.getName());
        String nameBefore = object.getName();
        if (ImGui.inputText("Name", oName)) {
            object.setName(oName.get());
        }
        if (ImGui.isItemActivated()) {
            renameTarget = object;
            renameBefore = nameBefore;
        }
        if (ImGui.isItemDeactivated() && renameTarget != null) {
            if (!renameTarget.getName().equals(renameBefore)) {
                pushCommand(new RenameCommand(renameTarget, renameBefore, renameTarget.getName()));
            }
            renameTarget = null;
        }

        ImBoolean visible = new ImBoolean(!isHidden(object));
        if (ImGui.checkbox("Visible", visible)) setHidden(object, !visible.get());

        ImGui.text("Children count (direct): " + object.children.size());
        GameObject root = object.getRoot();
        if (root != null) {
            ImGui.text("Root parent: " + root.getName());
            ImGui.sameLine();
            textDim(root.id.toString());
        }
        if (object.parent != null) {
            ImGui.text("Direct parent: " + object.parent.getName());
            ImGui.sameLine();
            textDim(object.parent.id.toString());
        }

        ImGui.text("Debug object: " + object.isDebuggingObject);
        ImGui.separator();

        if (ImGui.collapsingHeader("Transform", ImGuiTreeNodeFlags.DefaultOpen)) {
            editTarget = object;

            ImGui.text("World position");
            ImGui.sameLine();
            if (ImGui.smallButton("Reset##pos")) {
                beginTransformEdit(object);
                object.transform.position(new Vector3f());
                endTransformEdit();
            }
            Vector3f pos = object.transform.getPosition();
            if (renderPositionField("wpos", pos)) {
                object.transform.position(pos);
            }

            ImGui.text("World scale");
            ImGui.sameLine();
            if (ImGui.smallButton("Reset##scale")) {
                beginTransformEdit(object);
                object.transform.scale(new Vector3f(1f, 1f, 1f));
                endTransformEdit();
            }
            Vector3f scale = object.transform.getScale();
            if (renderPositionField("wscale", scale, "Width", "Height", "Depth", 0, 99999)) {
                object.transform.scale(scale);
            }

            ImGui.text("Orientation mode");

            if (ImGui.radioButton("Euler", !quaternionMode)) {quaternionMode = false;}
            ImGui.sameLine();

            if (ImGui.radioButton("Quaternion", quaternionMode)) {quaternionMode = true;}
            ImGui.text("World orientation");
            if (!quaternionMode) {
                Vector3f rot = object.transform.getRotationEuler();
                if (renderPositionField("wrot", rot)) {
                    object.transform.rotate(rot);
                }

                ImGui.beginDisabled();
                Vector4f quat = object.transform.getRotation();
                renderPositionField4D("wquat", quat);
                ImGui.endDisabled();

            } else {
                ImGui.beginDisabled();
                Vector3f rot = object.transform.getRotationEuler();
                renderPositionField("wrot", rot);
                ImGui.endDisabled();

                Vector4f quat = object.transform.getRotation();

                if (renderPositionField4D("wquat", quat)) {
                    object.transform.rotate(new Quaternionf(quat.x, quat.y, quat.z, quat.w));
                }
            }

            if (ImGui.button("Reset orientation")) {
                beginTransformEdit(object);
                object.transform.rotate(0,0,0);
                endTransformEdit();
            }
            editTarget = null;
        }

        ImGui.separator();
        ImGui.text("Components (" + object.getComponentCount() + ")");
        renderAddComponentButton(object);
        renderComponentsInline(object);
        ImGui.separator();
        ImGui.text("Tags");
        renderTagButtonRow(object);
        ImGui.end();

    }

    private void renderTagButtonRow(GameObject object) {
        Scene scene = engine.getSceneManager().getActiveScene();
        if (scene == null) return;
        ImGui.pushID("TagsRow");

        List<String> objectTags = new ArrayList<>();
        for (String tag : scene.getTags()) {
            if (scene.isTagged(object, tag)) objectTags.add(tag);
        }

        float spacing = ImGui.getStyle().getItemSpacingX();
        float framePaddingX = ImGui.getStyle().getFramePaddingX();
        float windowVisibleX = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX();

        String plusLabel = "\uF0FE";
        float plusWidth = ImGui.calcTextSize(plusLabel).x + framePaddingX * 2f;
        for (int i = 0; i < objectTags.size(); i++) {
            String tag = objectTags.get(i);

            ImGui.pushID(i);
            if (ImGui.button(tag)) {
                ImGui.openPopup("TagContextMenu");
            }
            if (ImGui.beginPopup("TagContextMenu")) {
                if (ImGui.menuItem("Remove")) {scene.untagObject(object, tag);}
                ImGui.endPopup();
            }
            ImGui.popID();

            float lastButtonX2 = ImGui.getItemRectMaxX();
            boolean hasNext = i + 1 < objectTags.size();
            float nextWidth = hasNext ? ImGui.calcTextSize(objectTags.get(i + 1)).x + framePaddingX * 2f : plusWidth;

            if (lastButtonX2 + spacing + nextWidth < windowVisibleX) {
                ImGui.sameLine();
            }
        }


        if (ImGui.button(plusLabel)) {
            newTagName.set("");
            ImGui.openPopup("AddTagPopup");
        }

        if (ImGui.beginPopup("AddTagPopup")) {
            ImGui.text("Tag name:");
            ImGui.inputText("##NewTagName", newTagName);
            if (ImGui.button("Add")) {
                String tagName = newTagName.get().trim();
                if (!tagName.isEmpty()) {
                    scene.tagObject(object, tagName);
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
        ImGui.popID();

    }



    private void renderComponentsInline(GameObject object) {
        Component toDetach = null;
        for (Component c : new ArrayList<>(object.getComponents())) {
            String name = c.getClass().getSimpleName();
            String icon = fontMap.getOrDefault(name, "\uF016");
            ImGui.pushID(c.getID().toString());

            boolean focusThis = c == selectedComponent;
            if (focusThis) ImGui.setNextItemOpen(true);
            boolean open = ImGui.collapsingHeader(icon + " " + name, ImGuiTreeNodeFlags.DefaultOpen);
            if (focusThis) {
                ImGui.setScrollHereY(0f);
                selectedComponent = null;
            }

            if (ImGui.beginPopupContextItem("compCtx")) {
                if (ImGui.menuItem("Detach")) toDetach = c;
                if (ImGui.menuItem("Copy ID")) ImGui.setClipboardText(c.getID().toString());
                ImGui.endPopup();
            }
            if (open) {
                textDim(c.name + "  " + c.getID());
                ImGui.indent();
                renderComponentEditor(c);
                ImGui.unindent();
                ImGui.spacing();
            }
            ImGui.popID();
        }
        if (toDetach != null) object.detachComponent(toDetach);
    }

    private void renderAddComponentButton(GameObject object) {
        if (ImGui.button("\uF0FE Add component")) {ImGui.openPopup("AddComponent");}

        if (ImGui.beginPopup("AddComponent")) {
            boolean colCompEnabled = object.hasComponent(ModelComponent.class) && !object.hasComponent(ColliderComponent.class);
            boolean camCompEnabled = !object.hasComponent(CameraComponent.class);
            boolean lightCompEnabled = !object.hasComponent(LightComponent.class);

            if (ImGui.menuItem("Collider", "", false, colCompEnabled)) {
                object.attachComponent(new ColliderComponent(object.getComponent(ModelComponent.class).bounds));
            }
            if (ImGui.menuItem("Camera", "", false, camCompEnabled)) {
                object.attachComponent(new CameraComponent());
            }

            ImGui.separator();
            if (ImGui.beginMenu("Light", lightCompEnabled)) {
                if (ImGui.menuItem("Spot light")) {
                    LightComponent l = new LightComponent(LightComponent.LightType.SPOT_LIGHT);
                    if (object.hasComponent(ModelComponent.class)) {l.addShadowExclusion(object.getComponent(ModelComponent.class));}
                    object.attachComponent(l);
                }
                if (ImGui.menuItem("Point light")) {
                    LightComponent l = new LightComponent(LightComponent.LightType.POINT_LIGHT);
                    if (object.hasComponent(ModelComponent.class)) {l.addShadowExclusion(object.getComponent(ModelComponent.class));}
                    object.attachComponent(l);
                }
                ImGui.endMenu();
            }
            ImGui.endPopup();
        }
    }

    private void renderComponentEditor(Component c) {
        switch (c) {
            case CameraComponent camComp -> {
                ImGui.text("Position offset");
                Vector3f offset = camComp.getPositionOffset();
                if (renderPositionField("camOffset", offset)) {
                    camComp.setPositionOffset(offset);
                }
                float[] fov = {camComp.getFov()};
                if (ImGui.dragFloat("FOV", fov, 0.5f, 1f, 179f)) {
                    camComp.setFov(fov[0]);
                }
                float[] near = {camComp.getNear()};
                if (ImGui.dragFloat("Near", near, 0.01f, 0.001f, camComp.getFar())) {
                    camComp.setNear(near[0]);
                }
                float[] far = {camComp.getFar()};
                if (ImGui.dragFloat("Far", far, 1f, camComp.getNear(), 100000f)) {
                    camComp.setFar(far[0]);
                }
                ImGui.spacing();
                ImGui.text("Orientation");
                float[] pitch = {camComp.getPitch()};
                float[] yaw = {camComp.getYaw()};
                float[] roll = {camComp.getRoll()};
                boolean orientationChanged = ImGui.dragFloat("Pitch", pitch, 0.5f, -89f, 89f);
                orientationChanged |= ImGui.dragFloat("Yaw", yaw, 0.5f, -360f, 360f);
                orientationChanged |= ImGui.dragFloat("Roll", roll, 0.5f, -180f, 180f);
                if (orientationChanged) {
                    camComp.setDirection(pitch[0], yaw[0], roll[0]);
                }
                ImGui.beginDisabled();
                Vector3f direction = camComp.getDirection();
                renderPositionField("camDir", direction);
                ImGui.endDisabled();
                ImGui.separator();
                ImBoolean DoF = new ImBoolean(camComp.depthOfField);
                if (ImGui.checkbox("Depth of field", DoF)) {camComp.depthOfField = DoF.get();}
                ImGui.beginDisabled(!camComp.depthOfField);
                float[] nfo = {camComp.getFocusNearFalloff()};
                float[] ffo = {camComp.getFocusFarFalloff()};
                float[] fr = {camComp.getFocusRange()};
                float[] focusDist = {camComp.getFocusDistance()};
                if (ImGui.dragFloat("Focus range", fr, 0.01f, 0.01f, 9999)) {
                    camComp.setFocusRange(fr[0]);
                }

                if (ImGui.dragFloat("Focus Distance", focusDist, 0.01f, 0.01f, 1000)) {
                    camComp.setFocusDistance(focusDist[0]);
                }

                if (ImGui.dragFloat("Focus near falloff", nfo, 0.01f, 0.01f, 1000)) {
                    camComp.setFocusNearFalloff(nfo[0]);
                }

                if (ImGui.dragFloat("Focus far falloff", ffo, 0.01f, 0.01f, 1000)) {
                    camComp.setFocusFarFalloff(ffo[0]);
                }

                ImGui.sameLine();
                if (ImGui.button("Focus")) {
                    Logger.info("Focusing");
                    RaycastService.RayHit hit = engine.getRaycastService().castRay(
                            camComp.getEyePosition(),
                            camComp.getDirection(),
                            engine.getComponentRegistry().getComponents(ModelComponent.class),
                            camComp.getFar(),
                            true
                    );
                    if (hit != null) {
                        Logger.info("Rayhit on: " + hit.distance() + " penetrating at " + hit.penetration());

                        camComp.setFocusDistance(hit.distance());
                        camComp.setFocusRange(hit.penetration());
                    }
                }
                ImGui.endDisabled();
                ImGui.separator();
                float[] vignette = {camComp.getVignette()};
                if (ImGui.dragFloat("Vignette", vignette, 0.01f, 0f, 1)) {
                    camComp.setVignette(vignette[0]);
                }
                ImGui.separator();
                if (ImGui.button("Set as primary")) {
                    engine.getSceneManager().getActiveScene().setPrimaryCamera(camComp);
                }
            }
            case ModelComponent modelComp -> {
                ImGui.text("Index count: " + modelComp.indexCount);
                textDim("Rigged: " + modelComp.rigged);
                ImGui.spacing();
                ImGui.separator();
                ImBoolean castShadow = new ImBoolean(modelComp.castsShadow());
                if (ImGui.checkbox("Cast shadow", castShadow)) {
                    modelComp.setCastShadow(castShadow.get());
                }
                ImGui.text("Draw mode:");
                ImGui.sameLine();
                if (ImGui.radioButton("Triangles", modelComp.getDrawMode() == Renderer.DrawMode.TRIANGLES)) {
                    modelComp.setDrawMode(Renderer.DrawMode.TRIANGLES);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Lines", modelComp.getDrawMode() == Renderer.DrawMode.LINES)) {
                    modelComp.setDrawMode(Renderer.DrawMode.LINES);
                }
                ImGui.text("Render method:");
                ImGui.sameLine();
                if (ImGui.radioButton("Deferred", modelComp.getRenderMethod() == Renderer.RenderMethod.RENDER_DEFERRED)) {
                    modelComp.setRenderMethod(Renderer.RenderMethod.RENDER_DEFERRED);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Forward", modelComp.getRenderMethod() == Renderer.RenderMethod.RENDER_FORWARD)) {
                    modelComp.setRenderMethod(Renderer.RenderMethod.RENDER_FORWARD);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("No Depth", modelComp.getRenderMethod() == Renderer.RenderMethod.RENDER_NO_DEPTH)) {
                    modelComp.setRenderMethod(Renderer.RenderMethod.RENDER_NO_DEPTH);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Skip", modelComp.getRenderMethod() == Renderer.RenderMethod.RENDER_SKIP)) {
                    modelComp.setRenderMethod(Renderer.RenderMethod.RENDER_SKIP);
                }

                ImGui.separator();
                ImGui.text("Material");
                ModelComponent.Material mat = modelComp.material;
                ImBoolean matEnabled = new ImBoolean(mat.enabled);
                ImGui.beginDisabled(modelComp.getRenderMethod() != Renderer.RenderMethod.RENDER_FORWARD);
                if (ImGui.checkbox("Material enabled", matEnabled)) {
                    mat.enabled = matEnabled.get();
                }
                ImGui.endDisabled();
                float[] albedo = {mat.albedo.x, mat.albedo.y, mat.albedo.z};
                if (ImGui.colorEdit3("Albedo", albedo)) {
                    mat.albedo.set(albedo[0], albedo[1], albedo[2]);
                }
                float[] roughness = {mat.roughness};
                if (ImGui.dragFloat("Roughness", roughness, 0.01f, 0f, 1f)) {
                    mat.roughness = roughness[0];
                }
                float[] metalness = {mat.metalness};
                if (ImGui.dragFloat("Metalness", metalness, 0.01f, 0f, 1f)) {
                    mat.metalness = metalness[0];
                }
                float[] emissive = {mat.emissive.x, mat.emissive.y, mat.emissive.z};
                if (ImGui.colorEdit3("Emissive", emissive)) {
                    mat.emissive.set(emissive[0], emissive[1], emissive[2]);
                }
                float[] emissiveStrength = {mat.emissiveStrength};
                if (ImGui.dragFloat("Emissive strength", emissiveStrength, 0.05f, 0f, 100f)) {
                    mat.emissiveStrength = emissiveStrength[0];
                }
                float[] opacity = {mat.opacity};
                if (ImGui.dragFloat("Opacity", opacity, 0.01f, 0f, 1f)) {
                    mat.opacity = opacity[0];
                }
                ImBoolean alphaCutout = new ImBoolean(mat.alphaCutout);
                if (ImGui.checkbox("Alpha cutout", alphaCutout)) {
                    mat.alphaCutout = alphaCutout.get();
                }
                ImGui.beginDisabled(!mat.alphaCutout);
                float[] alphaThreshold = {mat.alphaMaskThreshold};
                if (ImGui.dragFloat("Alpha mask threshold", alphaThreshold, 0.01f, 0f, 1f)) {
                    mat.alphaMaskThreshold = alphaThreshold[0];
                }

                ImGui.endDisabled();
                ImGui.separator();
                ImBoolean batched = new ImBoolean(!modelComp.batchID.isEmpty());
                if (ImGui.checkbox("Batch model", batched)) {
                    boolean state = batched.get();
                    if (state) modelComp.batch();
                    else modelComp.unbatch();
                }
                ImGui.spacing();
                ImGui.text("Textures");
                textDim("Albedo: " + mat.getAlbedoTexture());
                textDim("Normal: " + mat.getNormalTexture());
                textDim("Roughness: " + mat.getRoughnessTexture());
                textDim("Metalness: " + mat.getMetalnessTexture());
                textDim("AO: " + mat.getAoTexture());
                textDim("Emissive: " + mat.getEmissiveTexture());
                textDim("Height: " + mat.getHeightTexture());
                textDim("Opacity: " + mat.getOpacityTexture());
                textDim("Alpha mask: " + mat.getAlphaMaskTexture());

            }
            case LightComponent lightComp -> {
                ImGui.text("Light type");
                if (ImGui.radioButton("Point light", lightComp.getType() == LightComponent.LightType.POINT_LIGHT)) {
                    lightComp.setType(LightComponent.LightType.POINT_LIGHT);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Spot light", lightComp.getType() == LightComponent.LightType.SPOT_LIGHT)) {
                    lightComp.setType(LightComponent.LightType.SPOT_LIGHT);
                }

                float[] color = {lightComp.color.x, lightComp.color.y, lightComp.color.z};
                if (ImGui.colorEdit3("Color", color)) {
                    lightComp.color.set(color[0], color[1], color[2]);
                }

                float[] intensity = {lightComp.intensity};
                if (ImGui.dragFloat("Intensity", intensity, 0.01f, 0f, 999f)) {
                    lightComp.intensity = intensity[0];
                }

                float[] range = {lightComp.range};
                if (ImGui.dragFloat("Range", range, 0.01f, 0f, 9999f)) {
                    lightComp.range = range[0];
                }

                ImGui.separator();
                ImGui.text("Attenuation");
                float[] linear = {lightComp.linear};
                float[] constant = {lightComp.constant};
                float[] quadratic = {lightComp.quadratic};
                if (ImGui.dragFloat("Linear", linear, 0.01f, 0, 5)) {
                    lightComp.linear = linear[0];
                }

                if (ImGui.dragFloat("Constant", constant, 0.01f, 0.001f, 1)) {
                    lightComp.constant = constant[0];
                }

                if (ImGui.dragFloat("Quadratic", quadratic, 0.01f, 0, 5)) {
                    lightComp.quadratic = quadratic[0];
                }
                ImGui.separator();
                ImBoolean castsShadow = new ImBoolean(lightComp.castShadow);
                ImBoolean bakedShadow = new ImBoolean(lightComp.bakeShadows);
                if (ImGui.checkbox("Cast shadow", castsShadow)) {
                    lightComp.castShadow = castsShadow.get();
                }
                ImGui.sameLine();
                if (ImGui.checkbox("Baked shadows", bakedShadow)) {
                    lightComp.bakeShadows = bakedShadow.get();
                }

                float[] bvalue = new float[]{lightComp.shadowNormalBias};
                if (ImGui.dragFloat("Shadow normal Bias", bvalue, 0.001f, 0f, 1f)) {
                    lightComp.shadowNormalBias = bvalue[0];
                }
                ImGui.separator();
                ImGui.text("Position offset");
                Vector3f offset = lightComp.getOffset();
                if (renderPositionField("lightOffset", offset)) {
                    lightComp.offset.set(offset);
                }

                ImGui.beginDisabled(lightComp.getType() == LightComponent.LightType.POINT_LIGHT);
                ImGui.text("Spotlight direction");
                Vector3f splDir = lightComp.getSpotLightDirection();
                if (renderPositionField("splDir", splDir)) {
                    lightComp.spotLightDirection.set(splDir);
                }

                float[] iC = {lightComp.spotLightInnerCutoff};
                float[] oC = {lightComp.spotLightOuterCutoff};
                if (ImGui.dragFloat("Inner cutoff", iC, 0.01f, 0.001f, 1)) {
                    lightComp.spotLightInnerCutoff = iC[0];
                }

                if (ImGui.dragFloat("Outer cutoff", oC, 0.01f, 0.001f, 1)) {
                    lightComp.spotLightOuterCutoff = oC[0];
                }

                ImGui.endDisabled();
            }
            case ScriptComponent scriptComp -> {
                ImGui.text("Lua script: " + scriptComp.isLua());
                int[] runInterval = {scriptComp.runFrameInterval};
                if (ImGui.dragInt("Frame run interval", runInterval, 1f, 0, 999999999999f)) {
                    scriptComp.runFrameInterval = runInterval[0];
                }
                textDim("Script editor is in development*");
            } case AnimationComponent animComp -> {
                Animation currentAnimation = animComp.getCurrentAnimation();
                String animName = (currentAnimation != null) ? currentAnimation.name : null;

                if (currentAnimation != null) {
                    ImGui.text("Current animation: " + currentAnimation.name);
                    ImGui.text(String.format("Duration: %.2f", currentAnimation.duration));
                    ImGui.sameLine();
                    textDim("Keyframes: " + currentAnimation.keyframes.size());
                } else {
                    ImGui.text("No animation selected");
                }

                if (ImGui.beginCombo("Select Animation", animName != null ? animName : "")) {
                    for (String option : animComp.getAnimationNames()) {
                        boolean isSelected = option.equals(animName);
                        if (ImGui.selectable(option, isSelected)) {
                            animName = option;
                            animComp.play(animName, animComp.looping);
                        }
                        if (isSelected) {
                            ImGui.setItemDefaultFocus();
                        }
                    }
                    ImGui.endCombo();
                }

                if (currentAnimation != null) {
                    float[] ctime = {animComp.getTime()};
                    textDim(String.format("Time: %.2f / %.2f", ctime[0], currentAnimation.duration));
                    if (ImGui.sliderFloat("Seek", ctime, 0, currentAnimation.duration)) {
                        animComp.seek(ctime[0]);
                    }

                    boolean needsPop = false;
                    if (animComp.isPlaying()) {
                        needsPop = true;
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.709f, 0.356f, 0.282f, 1);
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.505f, 0.256f, 0.182f, 1);
                        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.505f, 0.256f, 0.182f, 1);
                    }

                    float btnWidth = ImGui.getFrameHeight();
                    if (ImGui.button("\uF28B", btnWidth, btnWidth)) {
                        if (animComp.isPlaying()) animComp.stop();
                        else animComp.resume();
                    }
                    if (needsPop) ImGui.popStyleColor(3);
                    ImGui.sameLine();
                    if (ImGui.checkbox("Looping", animComp.looping)) {
                        animComp.looping = !animComp.looping;
                    }

                    float[] animationSpeed = {animComp.animationSpeed};
                    if (ImGui.dragFloat("Speed", animationSpeed, 0.1f, 0, 9999)) {
                        animComp.animationSpeed = animationSpeed[0];
                    }
                }
            } case AudioComponent audioComp -> {
                ImGui.text("Audio type");
                if (ImGui.radioButton("Point audio", audioComp.type == AudioComponent.AudioType.POINT_AUDIO)) {
                    audioComp.type = AudioComponent.AudioType.POINT_AUDIO;
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Directed audio", audioComp.type == AudioComponent.AudioType.DIRECTED_AUDIO)) {
                    audioComp.type = AudioComponent.AudioType.DIRECTED_AUDIO;
                }

                ImGui.separator();
                boolean needsPop = false;
                if (audioComp.playing && !audioComp.paused) {
                    needsPop = true;
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.709f, 0.356f, 0.282f, 1);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.505f, 0.256f, 0.182f, 1);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.505f, 0.256f, 0.182f, 1);
                }

                float btnWidth = ImGui.getFrameHeight();
                if (ImGui.button("\uF28B", btnWidth, btnWidth)) {
                    audioComp.paused = !audioComp.paused;
                    if (!audioComp.playing && !audioComp.looping) {
                        audioComp.playing = true;
                        audioComp.paused = false;
                    }
                }
                if (needsPop) ImGui.popStyleColor(3);
                ImGui.sameLine();
                if (ImGui.checkbox("Looping", audioComp.looping)) {audioComp.looping = !audioComp.looping;}

                ImGui.sameLine();
                textDim("Playing: " + audioComp.playing);
                ImGui.sameLine();
                textDim("Paused: " + audioComp.paused);

                float[] volume = {audioComp.volume};
                if (ImGui.dragFloat("Volume", volume, 0.01f, 0f, 10f)) {
                    audioComp.volume = volume[0];
                }

                float[] pitch = {audioComp.pitch};
                if (ImGui.dragFloat("Pitch", pitch, 0.01f, 0.1f, 10f)) {
                    audioComp.pitch = pitch[0];
                }

                ImGui.separator();
                ImGui.text("Attenuation");
                float[] minDistance = {audioComp.minDistance};
                if (ImGui.dragFloat("Min distance", minDistance, 0.1f, 0f, audioComp.maxDistance)) {
                    audioComp.minDistance = minDistance[0];
                }

                float[] maxDistance = {audioComp.maxDistance};
                if (ImGui.dragFloat("Max distance", maxDistance, 0.5f, audioComp.minDistance, 99999f)) {
                    audioComp.maxDistance = maxDistance[0];
                }

                float[] fadeRate = {audioComp.fadeRate};
                if (ImGui.dragFloat("Fade rate", fadeRate, 0.01f, 0f, 100f)) {
                    audioComp.fadeRate = fadeRate[0];
                }

                ImGui.beginDisabled(audioComp.type == AudioComponent.AudioType.POINT_AUDIO);
                ImGui.separator();
                ImGui.text("Cone");
                float[] innerCone = {audioComp.innerConeAngle};
                if (ImGui.dragFloat("Inner cone angle", innerCone, 0.5f, 0f, audioComp.outerConeAngle)) {
                    audioComp.innerConeAngle = innerCone[0];
                }

                float[] outerCone = {audioComp.outerConeAngle};
                if (ImGui.dragFloat("Outer cone angle", outerCone, 0.5f, audioComp.innerConeAngle, 360f)) {
                    audioComp.outerConeAngle = outerCone[0];
                }

                float[] outerConeVolume = {audioComp.outerConeVolume};
                if (ImGui.dragFloat("Outer cone volume", outerConeVolume, 0.01f, 0f, 1f)) {
                    audioComp.outerConeVolume = outerConeVolume[0];
                }

                if (ImGui.checkbox("Prefer object direction", audioComp.preferObjectDirection)) {
                    audioComp.preferObjectDirection = !audioComp.preferObjectDirection;
                }

                ImGui.beginDisabled(audioComp.preferObjectDirection);
                ImGui.text("Direction");
                Vector3f dir = audioComp.direction;
                if (renderPositionField("audioDir", dir)) {audioComp.direction.set(dir);}
                ImGui.endDisabled();
                ImGui.endDisabled();
            }
            default -> ImGui.text("No editor for this component type yet");
        }
    }



    private void renderDebugOverlay() {
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoFocusOnAppearing | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoMove;

        ImGui.setNextWindowBgAlpha(0.55f);
        ImGui.setNextWindowPos(17, 35);

        if (ImGui.begin("##debug_overlay", flags)) {

            Scene activeScene = engine.getSceneManager().getActiveScene();
            ImGui.text("SEngine (" + engine.version + ") - a sharkk2 project");
            ImGui.separator();
            ImGui.text(String.format("Performance: " + engine.getFps() + " FPS (%.3fms)", engine.getDeltaTime()*1000));
            ImGui.text(String.format("CPU: %d%% - GPU: %d%% (%dc)", HardwareMonitor.getCPULoad(), HardwareMonitor.getGPULoad(), HardwareMonitor.getGpuTemperature()));
            ImGui.separator();
            ImGui.text("Scene: " + activeScene.getName());
            ImGui.text("Rendered objects: " + engine.getRenderer().getRenderCount());
            ImGui.text("Lights: " + engine.getComponentRegistry().getCount(LightComponent.class));
            ImGui.text("Rendering mode: " + engine.getRenderer().getRenderingMode().name());
            CameraComponent mainCamera = activeScene.getPrimaryCamera();
            if (mainCamera != null) {
                GameObject camObject = mainCamera.getOwner();
                ImGui.separator();
                ImGui.text("Primary camera: " + mainCamera.name + " (owned by " + camObject.getName() + "): ");
                ImGui.text(String.format("XYZ: %.1f, %.1f, %.1f", camObject.transform.getX(), camObject.transform.getY(), camObject.transform.getZ()));
                ImGui.text(String.format("Orientation (pitch,yaw,roll): %.1f, %.1f, %.1f", mainCamera.getPitch(), mainCamera.getYaw(), mainCamera.getRoll()));
                ImGui.text(String.format("FOV (degrees): %.2f", mainCamera.getFov()));
                ImGui.separator();
                RaycastService.RayHit hit = engine.getRaycastService().castRay(mainCamera.getEyePosition(), mainCamera.getDirection(), engine.getComponentRegistry().getComponents(ModelComponent.class) ,mainCamera.getFar(), true);
                if (hit!=null) {
                    ImGui.text("Looking at: " + hit.hit().getOwner().getRoot().getName());
                } else {
                    ImGui.text("Looking at: nothing");
                }
                if (selectedObject!=null) {
                    ImGui.text("Selected object: " + selectedObject.getName());
                } else {
                    ImGui.text("Selected object: nothing");
                }
            }


        }
        ImGui.end();
    }
}