package org.sharkk2.sengine;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.sharkk2.sengine.core.classes.ShadowMap;
import org.sharkk2.sengine.core.classes.exceptions.EngineInitException;
import org.sharkk2.sengine.core.systems.*;
import org.sharkk2.sengine.core.systems.debug.Debugger;
import org.sharkk2.sengine.core.systems.debug.HardwareMonitor;
import org.sharkk2.sengine.core.systems.gui.Imgui;
import org.sharkk2.sengine.core.systems.renderer.Renderer;
import org.sharkk2.sengine.core.systems.renderer.ShaderService;

import java.nio.IntBuffer;
import java.util.HashMap;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_DONT_CARE;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Engine {
    protected void onInit() {}
    protected void onDestroy() {}

    public boolean initialized = false;
    public final boolean devMode = true;

    public final String version = "3.0.5";
    private long windowHandle;
    private int windowWidth, windowHeight;
    private long monitor;
    private float deltaTime;
    private long lastFrame = System.nanoTime();
    private int fps;
    private int totalFrameCount = 0;


    public final HashMap<String, Boolean> ioConfig = new HashMap<>();
    public final HashMap<String, Float> valueConfig = new HashMap<>();

    private InputService inputService;
    private Debugger debugger;
    private ShaderService shaderService;
    private RaycastService raycastService;
    private CameraService cameraService;
    private AssetLoader assetLoader;
    private Renderer renderer;
    private SceneManager sceneManager;
    private ScriptService scriptService;
    private Imgui sengineImGui;
    private AudioService audioService;
    private CollisionService collisionService;

    public float getWindowAspectRatio() {return (float) windowWidth / windowHeight;}
    public int getWindowWidth() {return windowWidth;}
    public int getWindowHeight() {return  windowHeight;}
    public long getWindowHandle() {return windowHandle;}
    public long getMonitor() {return monitor;}
    public boolean getIO(String key) { return ioConfig.get(key); }
    public float getValue(String key) {return valueConfig.get(key);}
    public void setValue(String key, float value) { valueConfig.put(key, value); }
    public void setIO(String key, boolean value) {
        ioConfig.put(key, value);
        if (key.equals("vsync")) {glfwSwapInterval(value?1:0);}
    }
    public float getDeltaTime() {return deltaTime;}
    public int getFps() {return fps;}
    public void setWindowTitle(String title) {glfwSetWindowTitle(windowHandle, title);}
    public String getWindowTitle() {return glfwGetWindowTitle(windowHandle);}
    public int getTotalFrameCount() {return totalFrameCount;}

    public InputService getInputService() {return inputService;}
    public ShaderService getShaderService() {return shaderService;}
    public AssetLoader getAssetLoader() {return assetLoader;}
    public Renderer getRenderer() {return renderer;}
    public CameraService getCameraService() {return cameraService;}
    public SceneManager getSceneManager() {return sceneManager;}
    public ScriptService getScriptService() {return scriptService;}
    public Debugger getDebugger() {return debugger;}
    public RaycastService getRaycastService() {return raycastService;};
    public AudioService getAudioService() {return audioService;}
    public CollisionService getCollisionService() {return collisionService;}

    public Engine() {
        ioConfig.put("debug", true);
        ioConfig.put("vsync", false);
        ioConfig.put("ssao", true);
        ioConfig.put("bloom", true);
        ioConfig.put("frustum_culling", false);
        ioConfig.put("mouse_visible", false);

        ioConfig.put("imgui.debug_overlay", true);
        ioConfig.put("imgui.full_settings", true);
        ioConfig.put("imgui.enabled", true);
        ioConfig.put("gizmo.snap_enabled", false);

        ioConfig.put("hdr", true);
        ioConfig.put("apply_aces", false);
        ioConfig.put("color_grading", false);
        ioConfig.put("gamma_correct", true);

        ioConfig.put("light_shafts", true);

        valueConfig.put("controls.gravity", 0.098f);
        valueConfig.put("controls.jump_force", 7f);
        valueConfig.put("controls.bob_frequency", 1.75f);
        valueConfig.put("controls.bob_amplitude", 0.1f);
        valueConfig.put("controls.mouse_sensitivity", 0.1f);
        valueConfig.put("controls.acceleration", 3.5f);
        valueConfig.put("controls.friction", 0.85f);
        valueConfig.put("controls.max_speed", 7f);

        valueConfig.put("exposure", 1.3f);
        valueConfig.put("saturation", 1.6f);
        valueConfig.put("gamma", 2.2f);
        valueConfig.put("shadows.quality", (float)ShadowMap.ShadowQuality.MEDIUM.ordinal()); // 0: HD, 1: 2K, 2: 4K
        valueConfig.put("shadows.distance", 26f);
        valueConfig.put("ssao_samples", 20f);
        valueConfig.put("bloom_strength", 0.04f);
        valueConfig.put("bloom_spread", 0.009f);
        valueConfig.put("bloom_threshold", 1.0f);
        valueConfig.put("audio.doppler_intensity", 0.5f);
        valueConfig.put("godray_exposure", 0.07f);
        valueConfig.put("godray_decay", 0.85f);
        valueConfig.put("godray_density", 0.75f);
        valueConfig.put("godray_weight", 0.25f);
        valueConfig.put("godray_max_brightness", 0.2f);
        valueConfig.put("gizmo.snap_amount", 0.5f);
    }

    public void initialize(int windowWidth, int windowHeight, Long monitor) throws EngineInitException {
        if (initialized) {
            Logger.warning("Engine is already initialized!");
            return;
        }
        long ctime = System.currentTimeMillis();
        this.windowHeight = windowHeight;
        this.windowWidth = windowWidth;
        if (monitor == null) {
            monitor = glfwGetPrimaryMonitor();
            this.monitor = glfwGetPrimaryMonitor();
        }
        Logger.info("Initializing SharkEngine " + version);
        HardwareMonitor.start();
        if (!glfwInit()) {
            Logger.error("Failed to initialize engine");
            throw new EngineInitException("Unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
        windowHandle = glfwCreateWindow(windowWidth, windowHeight, "SharkEngine " + version, monitor, NULL);
        glfwMakeContextCurrent(windowHandle);
        GL.createCapabilities();
        if (getIO("debug")) {
            Logger.warning("Debug mode is on!");
            glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_NOTIFICATION, (IntBuffer) null, false);
            glDebugMessageCallback(new GLDebugMessageCallback() {
                @Override
                public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
                    String msg = GLDebugMessageCallback.getMessage(length, message);
                    Logger.info("[OpenGL DEBUG]: " + msg);
                    if (severity == GL_DEBUG_SEVERITY_HIGH) {
                        Logger.error("[OpenGL DEBUG ERR]: " + msg);
                    }
                }
            }, 0);
        }

        Logger.info("Loaded OpenGL "+ glGetString(GL_VERSION) + " (" + glGetString(GL_RENDERER) + ")");
        glViewport(0, 0, windowWidth, windowHeight);
        glfwSetFramebufferSizeCallback(windowHandle, (win, width, height) -> {
            this.windowWidth = width;
            this.windowHeight = height;
            glViewport(0, 0, width, height);
            if (renderer != null) {
                renderer.onResize(width, height);
            }
        });

        glfwSwapInterval(getIO("vsync") ? 1:0);
        glEnable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);




        debugger = new Debugger(this); //! init order must NOT be messed up
        inputService = new InputService(this);
        shaderService = new ShaderService(this);
        raycastService = new RaycastService(this);
        cameraService = new CameraService(this);
        assetLoader = new AssetLoader(this);
        renderer = new Renderer(this);
        collisionService = new CollisionService(this);
        audioService = new AudioService(this);
        sceneManager = new SceneManager(this);
        scriptService = new ScriptService(this);
        if (devMode) {
            sengineImGui = new Imgui(this);
            sengineImGui.initialize();
        }

        Logger.info("SharkEngine initialized! (" + ((System.currentTimeMillis() - ctime) / 1000) + "s)");
        initialized = true;
        onInit();
    }

    public final void start() {
        if (!initialized) throw new EngineInitException("Engine is not initialized");
        gameLoop();
        Logger.info("Cleaning up");
        shaderService.destroyAll();
        renderer.getPostProcessor().destroy();
        renderer.getGbuffer().destroy();
        renderer.cleanup();
        sceneManager.destroy();
        onDestroy();
        Logger.info("Destroying window");
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
    }


    private void gameLoop() {
        long lastSec = System.currentTimeMillis();
        int framecount = 0;

        while (!glfwWindowShouldClose(windowHandle)) {
            long frameStart = System.nanoTime();
            deltaTime = (frameStart - lastFrame) / 1_000_000_000f;
            lastFrame = frameStart;
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            glfwPollEvents();
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            audioService.update();
            if (sceneManager.isSceneRunning()) {
                sceneManager.getActiveScene().tick();
                renderer.renderScene(sceneManager.getActiveScene());
            }
            inputService.update();
            if (devMode && sengineImGui != null) {
                sengineImGui.renderImGui();
            }
            glfwSwapBuffers(windowHandle);
            framecount++;
            totalFrameCount++;
            if ((System.currentTimeMillis() - lastSec) >= 1000) {
                lastSec = System.currentTimeMillis();
                fps = framecount;
                framecount = 0;
            }
        }
    }


}