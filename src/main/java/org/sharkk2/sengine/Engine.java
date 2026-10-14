package org.sharkk2.sengine;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.sharkk2.sengine.core.classes.exceptions.EngineInitException;
import org.sharkk2.sengine.core.systems.*;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.debug.Debugger;
import org.sharkk2.sengine.core.systems.debug.HardwareMonitor;
import org.sharkk2.sengine.core.systems.gui.Imgui;
import org.sharkk2.sengine.core.systems.renderer.Renderer;
import org.sharkk2.sengine.core.systems.ShaderService;

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
    public boolean running = false;
    private boolean devMode;

    public final String version = "3.2.0";
    private long windowHandle;
    private int windowWidth, windowHeight;
    private long monitor;
    private float deltaTime;
    private long lastFrame = System.nanoTime();
    private int fps;
    private int totalFrameCount = 0;
    private boolean showEngineSplash = true;
    private int engineSplash;

    public final HashMap<String, Boolean> ioConfig = new HashMap<>();
    public final HashMap<String, Float> valueConfig = new HashMap<>();

    private InputService inputService;
    private Debugger debugger;
    private ShaderService shaderService;
    private RaycastService raycastService;
    private AssetLoader assetLoader;
    private Renderer renderer;
    private SceneManager sceneManager;
    private ScriptService scriptService;
    private Imgui sengineImGui;
    private AudioService audioService;
    private CollisionService collisionService;
    private ThreadService threadService;

    private ComponentRegistry componentRegistry;

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
    /**Returns frame time in seconds*/
    public float getDeltaTime() {return deltaTime;}
    public int getFps() {return fps;}
    public void setWindowTitle(String title) {glfwSetWindowTitle(windowHandle, title);}
    public String getWindowTitle() {return glfwGetWindowTitle(windowHandle);}
    public int getTotalFrameCount() {return totalFrameCount;}
    public Imgui getEngineImGui() {return devMode ? sengineImGui:null;}
    /**returns current time in milliseconds*/
    public long getCurrentTime() {return System.currentTimeMillis();}
    public InputService getInputService() {return inputService;}
    public ShaderService getShaderService() {return shaderService;}
    public AssetLoader getAssetLoader() {return assetLoader;}
    public Renderer getRenderer() {return renderer;}
    public SceneManager getSceneManager() {return sceneManager;}
    public ScriptService getScriptService() {return scriptService;}
    public Debugger getDebugger() {return debugger;}
    public RaycastService getRaycastService() {return raycastService;};
    public AudioService getAudioService() {return audioService;}
    public CollisionService getCollisionService() {return collisionService;}
    public ThreadService getThreadService() {return threadService;}
    public ComponentRegistry getComponentRegistry() {return componentRegistry;}

    public void setResolution(int width, int height) {
        valueConfig.put("res_width", (float)Math.max(width, 100));
        valueConfig.put("res_height", (float)Math.max(height, 100));
        renderer.resize();
    }

    public boolean isDevMode() {return devMode;}

    public Engine() {
        ioConfig.put("debug", true);
        ioConfig.put("vsync", false);
        ioConfig.put("ssao", true);
        ioConfig.put("bloom", true);
        ioConfig.put("shadows", true);
        ioConfig.put("instancing", true);
        ioConfig.put("frustum_culling", true);
        ioConfig.put("mouse_visible", false);

        ioConfig.put("imgui.debug_overlay", true);
        ioConfig.put("imgui.full_settings", true);
        ioConfig.put("imgui.enabled", true);
        ioConfig.put("gizmo.snap_enabled", false);

        ioConfig.put("hdr", true);
        ioConfig.put("apply_aces", false);
        ioConfig.put("color_grading", false);
        ioConfig.put("gamma_correct", true);
        ioConfig.put("lens_dirt", true);

        ioConfig.put("light_shafts", true);


        valueConfig.put("exposure", 1.3f);

        valueConfig.put("anisotropic_filtering", 16f);
        valueConfig.put("saturation", 1.6f);
        valueConfig.put("gamma", 2.2f);
        valueConfig.put("shadows.quality", (float) LightComponent.ShadowQuality.MEDIUM.ordinal()); // 0: HD, 1: 2K, 2: 4K
        valueConfig.put("shadows.distance", 36f);
        valueConfig.put("ssao_samples", 20f);
        valueConfig.put("bloom_strength", 0.04f);
        valueConfig.put("bloom_spread", 0.009f);
        valueConfig.put("bloom_threshold", 1.0f);
        valueConfig.put("audio.doppler_intensity", 0.15f);
        valueConfig.put("godray_exposure", 0.01f);
        valueConfig.put("godray_decay", 0.85f);
        valueConfig.put("godray_density", 0.75f);
        valueConfig.put("godray_weight", 0.3f);
        valueConfig.put("godray_max_brightness", 0.2f);
        valueConfig.put("gizmo.snap_amount", 0.5f);
        valueConfig.put("res_width", 1920f);
        valueConfig.put("res_height", 1080f);
        valueConfig.put("lens_dirt.intensity", 10f);
    }

    public void initialize(int windowWidth, int windowHeight, Long monitor, boolean devMode) throws EngineInitException {
        this.devMode = devMode;
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
                    if (id == 131218) {return;} // annoying ass shader recompile warning

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
        });

        glfwSwapInterval(getIO("vsync") ? 1:0);
        glEnable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);


        //! init order must NOT be messed up
        componentRegistry = new ComponentRegistry(this);
        debugger = new Debugger(this);
        threadService = new ThreadService();
        shaderService = new ShaderService(this);
        assetLoader = new AssetLoader(this);
        inputService = new InputService(this);
        raycastService = new RaycastService(this);
        renderer = new Renderer(this);
        collisionService = new CollisionService(this);
        audioService = new AudioService(this);
        sceneManager = new SceneManager(this);
        scriptService = new ScriptService(this);
        engineSplash = assetLoader.loadTexture("assets/textures/sengine.png", AssetLoader.TEXTURE_BLENDED);

        componentRegistry.scanComponents();

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
        if (running) Logger.error("Engine is already running!");
        running = true;
        gameLoop();
        shutdown();
    }

    public void shutdown() {
        running = false;
        Logger.info("Cleaning up");
        shaderService.destroyAll();
        renderer.destroy();
        sceneManager.destroy();
        onDestroy();
        audioService.destroy();
        threadService.shutdown();
        Logger.info("Destroying window");
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
        System.exit(0);
    }


    private void gameLoop() {
        long lastSec = System.currentTimeMillis();
        int framecount = 0;

        //todo refactor the splash thing
        while (!glfwWindowShouldClose(windowHandle) && running) {
            long frameStart = System.nanoTime();
            deltaTime = (frameStart - lastFrame) / 1_000_000_000f;
            lastFrame = frameStart;
            glfwPollEvents();

            audioService.update();
            scriptService.executeComponents();
            if (sceneManager.isSceneRunning()) {
                showEngineSplash = false;
                sceneManager.getActiveScene().tick();
                renderer.renderScene(sceneManager.getActiveScene());
            } else if (showEngineSplash) {
                debugger.render2DTextureOverlay(engineSplash);
            }

            inputService.update();
            if (devMode && sengineImGui != null) {
                sengineImGui.renderImGui();
            }

            threadService.drainTasks();
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