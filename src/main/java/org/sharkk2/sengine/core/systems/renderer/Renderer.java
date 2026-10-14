package org.sharkk2.sengine.core.systems.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.classes.exceptions.FrameBufferException;
import org.sharkk2.sengine.core.classes.exceptions.RendererException;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.AnimationComponent;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.passes.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL43.*;
// ! Important bugs to fix:
// fixme: Fix the fillrate problem on close geometry, if the camera is very close to geometry the fill rate grows exponentially, especially with forwarded objects
// fixme: there are multiple points in the frame where many objects are being allocated and choking the GC
// fixme: Implement batching or instancing to reduce draw calls, and LoD for shaders "can help with bug 1" and very early depth pass to reduce the impact on forwarded objects

public class Renderer {
    public enum RenderMode {MODE_ALL, MODE_BASECOLOR, MODE_METALNESS, MODE_ROUGHNESS, MODE_NORMALS, MODE_EMISSIVE, MODE_AO, MODE_OPACITY, MODE_DEFERRED_ONLY, MODE_MAX}
    public enum RenderMethod {RENDER_DEFERRED, RENDER_FORWARD, RENDER_SKIP}
    public enum AntiAliasingMode {NONE, FXAA, SMAA}
    public enum DrawMode {TRIANGLES, LINES}

    public enum TextureSlot {
        ALBEDO("useAlbedoTex", "albedoTex"),
        ROUGHNESS("useRoughnessTex", "roughnessTex"),
        METALNESS("useMetalnessTex", "metalnessTex"),
        NORMAL("useNormalTex", "normalTex"),
        EMISSIVE("useEmissiveTex", "emissiveTex"),
        AO("useAoTex", "aoTex"),
        ALPHA_MASK("useAlphaMaskTex", "alphaMaskTex"),
        OPACITY("useOpacityTex", "opacityTex");

        public final String useUniform;
        public final String texUniform;

        TextureSlot(String useUniform, String texUniform) {
            this.useUniform = useUniform;
            this.texUniform = texUniform;
        }
    }


    private static final int BONE_SSBO_BINDING = 3;
    private static final int INITIAL_BONE_CAPACITY = 128; // joints
    private static final int LIGHT_STRUCT_BYTES = 160;
    private static final int LIGHT_SSBO_BINDING = 2; // camera UBO=0, fog UBO=1
    private static final int INITIAL_LIGHT_CAPACITY = 128;
    private static final int MAX_COOKIE_LIGHTS = 64;
    private static final int MAX_SHADOW_MAPS = 64;

    private static final int COOKIE_TEX_UNIT_BASE = 9;
    private static final int FALLBACK_COOKIE_CAP = 6;
    private static final int SHADOW_TEX_UNIT_BASE = COOKIE_TEX_UNIT_BASE + FALLBACK_COOKIE_CAP;
    private static final int FALLBACK_SHADOW_CAP = 6;
    private static final int SHADOW_CUBE_TEX_UNIT_BASE = SHADOW_TEX_UNIT_BASE + FALLBACK_SHADOW_CAP;
    private static final String[] COOKIE_TEX_UNIFORMS = new String[MAX_COOKIE_LIGHTS];
    private static final String[] SHADOW_TEX_UNIFORMS = new String[MAX_SHADOW_MAPS];
    private static final String[] SHADOW_CUBE_TEX_UNIFORMS = new String[MAX_SHADOW_MAPS];

    static {
        for (int i = 0; i < MAX_COOKIE_LIGHTS; i++) {
            COOKIE_TEX_UNIFORMS[i] = "cookieTextures[" + i + "]";
        }

        for (int i = 0; i < MAX_SHADOW_MAPS; i++) {
            SHADOW_TEX_UNIFORMS[i] = "shadowTextures[" + i + "]";
            SHADOW_CUBE_TEX_UNIFORMS[i] = "shadowCubeTextures[" + i + "]";
        }
    }



    public AntiAliasingMode AAMode = AntiAliasingMode.FXAA;
    private RenderMode renderingMode = RenderMode.MODE_ALL;
    private final Engine engine;
    private final ShaderService.UBO cameraUBO;
    private final ShaderService.UBO fogUBO;
    private final ShaderService.SSBO boneMatrixSSBO;
    private final ShaderService.Shader objectShader;
    private final ShaderService.SSBO lightSSBO;
    private final int dummyDepthTex;
    private final int dummyDepthCubeTex;

    private boolean wireframe = false;
    private int lastRenderCount = 0;
    private int framebuffer;
    private int fbColorTexture;
    private int fbDepthTexture;
    private int fbStencilTexture;

    private final FrameContext frameContext = new FrameContext();
    private final List<ModelComponent> renderQueue = new ArrayList<>();
    private final List<RenderPass> renderPasses = new ArrayList<>();

    private final List<LightComponent> lights = new ArrayList<>();
    private final List<ModelComponent> models = new ArrayList<>();
    private final List<ModelComponent> eligibleAnimModels = new ArrayList<>();
    private final Map<String, Batch> batches = new HashMap<>();

    private Matrix4f[][] boneSets = new Matrix4f[32][];



    public Renderer(Engine engine) {
        this.engine = engine;
        cameraUBO = engine.getShaderService().createUBO("camera", 0, 240);
        fogUBO = engine.getShaderService().createUBO("fog", 1, 48);
        objectShader = engine.getShaderService().get("shaders/objectVert.glsl", "shaders/objectFrag.glsl");
        boneMatrixSSBO = engine.getShaderService().createSSBO("boneMatrices", BONE_SSBO_BINDING, INITIAL_BONE_CAPACITY * 64);
        lightSSBO = engine.getShaderService().createSSBO("lights", LIGHT_SSBO_BINDING, INITIAL_LIGHT_CAPACITY * LIGHT_STRUCT_BYTES);
        dummyDepthTex = engine.getAssetLoader().loadEmptyDepthTexture(1, 1);
        dummyDepthCubeTex = engine.getAssetLoader().loadEmptyDepthCubeTexture(1,1);
        createFrameBuffer();

        registerPass(new GBufferPass(engine), false);
        registerPass(new ShadowPass(engine), false);
        registerPass(new SSAOPass(engine), false);
        registerPass(new LightingPass(engine), false);
        registerPass(new SkyPass(engine), false);
        registerPass(new ForwardPass(engine), false);
        registerPass(new BloomPass(engine), false);
        registerPass(new PostProcessingPass(engine));


    }

    public static class Batch {
        public final List<ModelComponent> models = new ArrayList<>();
        public final String id;
        public Batch(String id) {this.id = id;}
    }
    // so how this works is quite simple,
    // when a model component's material textures or vao change, it creates a batch key of those textures + a key of the vao contents, finds or creates a batch with it and joins it.
    // during rendering, these batches will be passed along in batch lists instead of model component list inside the frame context and instanced, or used directly as containers.

    public Batch createRenderBatch(String id) {
        Batch batch = new Batch(id);
        batches.put(id, batch);
        return batch;
    }

    public Batch getRenderBatch(String id) {return batches.get(id);}
    public Batch removeRenderBatch(String id) {return batches.remove(id);}

    public void enableWireframe(boolean v) {
        wireframe = v;
        if (v) engine.setWindowTitle("SharkEngine [WIREFRAME]" + engine.version);
        else engine.setWindowTitle("SharkEngine " + engine.version);
    }
    public boolean wireframeEnabled() { return wireframe; }
    public void setRenderingMode(RenderMode mode) { renderingMode = mode; }
    public RenderMode getRenderingMode() { return renderingMode; }
    public int getRenderCount() {return lastRenderCount;}
    public ShaderService.SSBO getBoneMatrixSSBO() {return boneMatrixSSBO;}
    public ShaderService.SSBO getLightSSBO() {return lightSSBO;}

    /**Topological sort*/
    private void sort(List<RenderPass> passes) {
        Map<String, RenderPass> byName = new HashMap<>();
        for (RenderPass p : passes) {
            if (byName.containsKey(p.name)) {throw new RendererException("Duplicate pass name: " + p.name);}
            byName.put(p.name, p);
        }

        Map<String, Integer> inDegree = new HashMap<>(); // total dependencies each pass has
        Map<String, List<String>> dependents = new HashMap<>();

        for (RenderPass p : passes) {
            inDegree.putIfAbsent(p.name, 0);
            dependents.putIfAbsent(p.name, new ArrayList<>());
        }

        for (RenderPass p : passes) {
            for (String depName : p.dependencies()) {
                if (!byName.containsKey(depName)) {throw new RendererException("Pass '" + p.name + "' depends on unknown pass '" + depName + "'");}
                dependents.get(depName).add(p.name);
                inDegree.merge(p.name, 1, Integer::sum);
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {if (e.getValue() == 0) queue.add(e.getKey());}
        List<RenderPass> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(byName.get(current));
            for (String dependent : dependents.get(current)) {
                inDegree.merge(dependent, -1, Integer::sum);
                if (inDegree.get(dependent) == 0) {queue.add(dependent);}
            }
        }

        if (sorted.size() != passes.size()) {
            List<String> remaining = new ArrayList<>();
            for (Map.Entry<String, Integer> e : inDegree.entrySet()) {if (e.getValue() > 0) remaining.add(e.getKey());}
            throw new RendererException("Cycle detected among passes: " + remaining);
        }

        passes.clear();
        passes.addAll(sorted);
        String passNames = sorted.stream()
                .map(p -> p.name)
                .collect(Collectors.joining(", "));

        Logger.info("Detected and sorted (" + passes.size() + ") render passes: " + passNames);    }

    public void registerPass(RenderPass pass) {
        registerPass(pass, true);
    }
    public void registerPass(RenderPass pass, boolean sort) {
        renderPasses.add(pass);
        if (sort) sort(renderPasses);
    }

    public void removePass(RenderPass pass) {
        renderPasses.remove(pass);
        sort(renderPasses);
    }

    private void createFrameBuffer() {
        int width = (int) engine.getValue("res_width");
        int height = (int) engine.getValue("res_height");
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        fbColorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fbColorTexture);

        if (engine.getIO("hdr")) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        } else glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fbColorTexture, 0);
        fbDepthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fbDepthTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_DEPTH24_STENCIL8, width, height);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, fbDepthTexture, 0);

        fbStencilTexture = glGenTextures();
        glTextureView(fbStencilTexture, GL_TEXTURE_2D, fbDepthTexture, GL_DEPTH24_STENCIL8, 0, 1, 0, 1);
        glBindTexture(GL_TEXTURE_2D, fbStencilTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("Failed to init main framebuffer");
            throw new FrameBufferException("Main FBO not complete D:");

        }
    }


    private void drawBareModel(ModelComponent model) {
        objectShader.use();
        Scene scene = engine.getSceneManager().getActiveScene();
        if (scene == null) return;
        CameraComponent camera = scene.getPrimaryCamera();
        if (camera == null) return;
        if (!camera.inFrustum(model.getOwner(), engine.getWindowAspectRatio(), model.bounds.boundingRadius) && engine.getIO("frustum_culling") && !model.getOwner().isDebuggingObject) return;
        if (model.getRenderMethod() == RenderMethod.RENDER_SKIP) return;
        glBindVertexArray(model.vao);
        objectShader.setMat4("uModel", model.getOwner().transform.calculateWorldMatrix());
        glDrawElements(model.getDrawMode() == DrawMode.TRIANGLES ? GL_TRIANGLES : GL_LINES, model.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void renderScene(Scene scene) {
        engine.getDebugger().beginTimer();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        lastRenderCount = frameContext.renderCounter;
        CameraComponent camera = scene.getPrimaryCamera();
        if (camera == null) {
            Logger.warning("No active camera found");
            return;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        frameContext.clear();
        frameContext.defaultFrameBuffer = framebuffer;
        frameContext.defaultColorTexture = fbColorTexture;
        frameContext.defaultDepthTexture = fbDepthTexture;
        frameContext.defaultStencilTexture = fbStencilTexture;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(60);
            camera.getProjectionMatrix(engine.getWindowAspectRatio()).get(0, buf);
            camera.getViewMatrix().get(16, buf);
            camera.getViewMatrix().invert().get(32, buf);
            camera.getOwner().transform.getPosition().get(48, buf);
            cameraUBO.upload(buf);
        } // uploading cam ubo "each matrix is 16 floats (48 floats total), times 4 is 192 bytes" then a 3D vec for 3 floats "12 bytes" (totaling 208 bytes)

        Scene.Fog fog = scene.environment.fog;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(48);
            buf.putFloat(0, fog.color.x);
            buf.putFloat(4, fog.color.y);
            buf.putFloat(8, fog.color.z);
            buf.putFloat(12, 0f); // fog color's alpha is js padding
            buf.putFloat(16, fog.density);
            buf.putFloat(20, fog.start);
            buf.putFloat(24, fog.end);
            buf.putInt(28, fog.enabled ? 1 : 0);
            buf.putInt(32, fog.mode.ordinal());
            buf.putInt(36, fog.blendSkyColor ? 1 : 0);
            fogUBO.upload(buf);
        } // uploading fog ubo "floats and ints are both 4 bytes"

        frameContext.scene = scene;
        frameContext.mainCamera = camera;
        engine.getComponentRegistry().getComponents(ModelComponent.class, models);
        if (wireframe) {
            // fixme: wireframe mode is broken, i knew the reason 3 days ago and planned to fix it the next day but next day never came and now i forgot the reason
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            for (ModelComponent model : models) {drawBareModel(model);}
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            return;
        } // if wireframe on, if we used deferred or post processing, the 2D fullscreen quads will cover the screen, plus its useless cuz its just lines

        // collecting objects
        // using the registry i found that it's 1.92x faster than finding models manually, but it's not even noticeable cuz both approaches were under a tenth of a millisecond
        // i should do this comparison again with a higher number of models
        engine.getComponentRegistry().getComponents(LightComponent.class, lights);
        for (LightComponent light : lights) {
            frameContext.lights.add(light);
            if (light.castShadow) frameContext.shadowLights.add(light);
        }

        // todo: have the frameContext carry batch lists with their models checked so passes do not need to manually do this
        eligibleAnimModels.clear();
        for (ModelComponent model : models) { // you have ALL the model components in the entire game here
            if (!scene.containsObject(model.getOwner().getRoot().id) && !renderQueue.contains(model)) {
                continue;
            }
            if (model.getRenderMethod() == RenderMethod.RENDER_SKIP) continue;
            if (camera.invisibleModels.contains(model)) continue;
            eligibleAnimModels.add(model);
            if (model.castsShadow() && model.getDrawMode() != Renderer.DrawMode.LINES) frameContext.shadowingModels.add(model);
            if (engine.getIO("frustum_culling") && !camera.inFrustum(model.getOwner(), engine.getWindowAspectRatio(), model.bounds.boundingRadius) && !model.getOwner().isDebuggingObject) continue;
            if (model.material.isTransparent()) {
                frameContext.transparentForwardModels.add(model);
            } else {
                if (model.getRenderMethod() == RenderMethod.RENDER_DEFERRED) frameContext.deferredModels.add(model);
                else frameContext.opaqueForwardModels.add(model);
            }
        }
        uploadBoneMatrices(eligibleAnimModels, frameContext.animationBoneOffsets);
        renderQueue.clear();
        frameContext.preparationTimeMs = engine.getDebugger().endTimer();
        for (RenderPass pass : renderPasses) {pass.pass(frameContext);}
    }

    public int getColorTexture() { return fbColorTexture; }
    public int getPassCount() {return renderPasses.size();}
    public RenderPass getRenderPass(String passname) {
        for (RenderPass p : renderPasses) {
            if (p.name.equals(passname)) return p;
        }
        return null;
    }


    public RenderPass getRenderPass(int index) {
        try {
            return renderPasses.get(index);
        } catch (Exception e) {
            Logger.error("Invalid index");
            return null;
        }
    }

    public int bindTexture2D(ShaderService.Shader shader, TextureSlot slot, int textureId, int unit) {
        boolean hasTexture = textureId != -1;
        shader.setInt(slot.useUniform, hasTexture ? 1 : 0);
        if (hasTexture) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, textureId);
            shader.setInt(slot.texUniform, unit);
            return unit + 1;
        }
        return unit;
    }

    private AnimationComponent findAnimComp(GameObject go) {
        while (go != null) {
            if (go.hasComponent(AnimationComponent.class)) return go.getComponent(AnimationComponent.class);
            go = go.parent;
        }
        return null;
    }



    private void uploadBoneMatrices(List<ModelComponent> models, Map<ModelComponent, Integer> target) {
        target.clear();
        int count = models.size();
        if (count == 0) return;

        if (boneSets.length < count) {
            int newSize = Math.max(count, boneSets.length * 2);
            boneSets = new Matrix4f[newSize][];
        }

        int totalMatrices = 0;
        for (int i = 0; i < count; i++) {
            ModelComponent model = models.get(i);
            AnimationComponent anim = findAnimComp(model.getOwner());
            Matrix4f[] matrices = anim != null ? anim.getBoneMatrices() : null;
            boneSets[i] = matrices;

            if (matrices != null) {
                target.put(model, totalMatrices);
                totalMatrices += matrices.length;
            } else {
                target.put(model, -1);
            }
        }

        ByteBuffer buf = boneMatrixSSBO.beginUpload(totalMatrices * 64);
        int byteOffset = 0;

        for (int i = 0; i < count; i++) {
            Matrix4f[] matrices = boneSets[i];
            if (matrices == null) continue;
            for (Matrix4f m : matrices) {
                m.get(byteOffset, buf);
                byteOffset += 64;
            }
        }

        buf.position(byteOffset);
        boneMatrixSSBO.endUpload();
        Arrays.fill(boneSets, 0, count, null);
    }

    public void uploadLights(ShaderService.Shader shader, Vector3f sortPos, List<LightComponent> lights) {
        // sorting to prioritize texture slots for closer lights
        lights.sort((a, b) -> {
            boolean aNull = a.getOwner() == null;
            boolean bNull = b.getOwner() == null;
            if (aNull && bNull) return 0;
            if (aNull) return 1;
            if (bNull) return -1;
            return Float.compare(
                    a.getOwner().transform.getPosition().distanceSquared(sortPos),
                    b.getOwner().transform.getPosition().distanceSquared(sortPos)
            );
        });

        int liveCount = lights.size();
        boolean bindless = engine.getShaderService().bindlessTexturesSupported;

        int cookieCap = bindless ? MAX_COOKIE_LIGHTS : FALLBACK_COOKIE_CAP;
        int shadowCap = bindless ? MAX_SHADOW_MAPS : FALLBACK_SHADOW_CAP;

        long dummy2DHandle = bindless ? engine.getShaderService().makeBindless(dummyDepthTex).getHandle() : 0;
        long dummyCubeHandle = bindless ? engine.getShaderService().makeBindless(dummyDepthCubeTex).getHandle() : 0;

        ByteBuffer buf = lightSSBO.beginUpload(liveCount * LIGHT_STRUCT_BYTES);
        int cookieSlot = 0;
        int shadowSlot = 0;
        boolean shadowsEnabled = engine.getIO("shadows");
        for (int i = 0; i < liveCount; i++) {
            LightComponent light = lights.get(i);
            Vector3f pos = light.getOwner().transform.getPosition();
            Vector3f off = light.offset;

            boolean isSpot = light.getType() == LightComponent.LightType.SPOT_LIGHT;

            boolean wantsCookie = isSpot && light.lightCookieTex != -1;
            boolean boundCookie = wantsCookie && cookieSlot < cookieCap;
            boolean boundShadow = light.castShadow && shadowsEnabled && shadowSlot < shadowCap;

            buf.putFloat(pos.x + off.x).putFloat(pos.y + off.y).putFloat(pos.z + off.z).putFloat(light.range);
            buf.putFloat(light.color.x).putFloat(light.color.y).putFloat(light.color.z).putFloat(light.intensity);
            buf.putFloat(light.spotLightDirection.x).putFloat(light.spotLightDirection.y).putFloat(light.spotLightDirection.z).putFloat(light.constant);
            buf.putFloat(light.linear);
            buf.putFloat(light.quadratic);
            buf.putFloat(light.spotLightInnerCutoff);
            buf.putFloat(light.spotLightOuterCutoff);
            buf.putFloat(light.shadowNormalBias);

            buf.putInt(isSpot ? 1 : 0);
            buf.putInt(boundCookie ? 1 : 0);
            buf.putInt(boundCookie ? cookieSlot : -1);
            buf.putInt(boundShadow ? shadowSlot : -1);
            buf.putInt(0).putInt(0).putInt(0);

            // Only spot lights need a light-space matrix, pooint lights compute their shadow depth in the shader from position + range instead
            Matrix4f lsMat = (boundShadow && isSpot) ? light.calcLightSpace() : new Matrix4f();
            buf.putFloat(lsMat.m00()).putFloat(lsMat.m01()).putFloat(lsMat.m02()).putFloat(lsMat.m03());
            buf.putFloat(lsMat.m10()).putFloat(lsMat.m11()).putFloat(lsMat.m12()).putFloat(lsMat.m13());
            buf.putFloat(lsMat.m20()).putFloat(lsMat.m21()).putFloat(lsMat.m22()).putFloat(lsMat.m23());
            buf.putFloat(lsMat.m30()).putFloat(lsMat.m31()).putFloat(lsMat.m32()).putFloat(lsMat.m33());

            if (boundCookie) {
                bindTexture(shader, light.lightCookieTex, cookieSlot, bindless, true);
                cookieSlot++;
            }
            if (boundShadow) {
                // we have 1 slot pointing to 2 arrays, so if we fill a slot in one array we have to also fill a dummy in the other
                if (isSpot) {
                    bindTexture(shader, light.spotLightShadowMap.depthTexture, shadowSlot, bindless, false);
                    bindDummyCube(shader, shadowSlot, bindless, dummyCubeHandle);
                } else {
                    bindCubeTexture(shader, light.pointLightShadowMap.depthCubemap, shadowSlot, bindless);
                    bindDummy2D(shader, shadowSlot, bindless, dummy2DHandle);
                }
                shadowSlot++;
            }
        }

        for (int i = shadowSlot; i < shadowCap; i++) {
            bindDummy2D(shader, i, bindless, dummy2DHandle);
            bindDummyCube(shader, i, bindless, dummyCubeHandle);
        }

        lightSSBO.endUpload();
        shader.setInt("lightCount", liveCount);
    }

    private void bindDummy2D(ShaderService.Shader shader, int slot, boolean bindless, long dummyHandle) {
        if (bindless) {
            shader.setBindlessTexture("shadowTextures[" + slot + "]", dummyHandle);
        } else {
            int unit = SHADOW_TEX_UNIT_BASE + slot;
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, dummyDepthTex);
            shader.setInt(SHADOW_TEX_UNIFORMS[slot], unit);
        }
    }

    private void bindDummyCube(ShaderService.Shader shader, int slot, boolean bindless, long dummyHandle) {
        if (bindless) {
            shader.setBindlessTexture("shadowCubeTextures[" + slot + "]", dummyHandle);
        } else {
            int unit = SHADOW_CUBE_TEX_UNIT_BASE + slot;
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_CUBE_MAP, dummyDepthCubeTex);
            shader.setInt(SHADOW_CUBE_TEX_UNIFORMS[slot], unit);
        }
    }

    private void bindCubeTexture(ShaderService.Shader shader, int textureId, int slot, boolean bindless) {
        if (bindless) {
            long handle = engine.getShaderService().makeBindless(textureId).getHandle();
            shader.setBindlessTexture("shadowCubeTextures[" + slot + "]", handle);
        } else {
            int unit = SHADOW_CUBE_TEX_UNIT_BASE + slot;
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_CUBE_MAP, textureId);
            shader.setInt(SHADOW_CUBE_TEX_UNIFORMS[slot], unit);
        }
    }

    private void bindTexture(ShaderService.Shader shader, int textureId, int slot, boolean bindless, boolean isCookie) {
        if (textureId == -1) return;
        if (bindless) {
            long handle = engine.getShaderService().makeBindless(textureId).getHandle();
            String name = isCookie ? "cookieTextures[" + slot + "]" : "shadowTextures[" + slot + "]";
            shader.setBindlessTexture(name, handle);
        } else {
            int unit = (isCookie ? COOKIE_TEX_UNIT_BASE : SHADOW_TEX_UNIT_BASE) + slot;
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, textureId);
            shader.setInt(isCookie ? COOKIE_TEX_UNIFORMS[slot] : SHADOW_TEX_UNIFORMS[slot], unit);
        }
    }

    public void renderObject(ModelComponent model) {
        if (wireframeEnabled()) return;
        if (renderQueue.contains(model) && engine.isDevMode()) Logger.warning("Drawing model [" + model.getID().toString() + "] (attached to " + model.getOwner().getName() + ") twice!");
        this.renderQueue.add(model);
    }

    public void renderObject(GameObject object) {
        if (!object.hasComponent(ModelComponent.class)) return;
        renderObject(object.getComponent(ModelComponent.class));
    }

    public void destroy() {
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(fbColorTexture);
        glDeleteTextures(fbDepthTexture);
        glDeleteTextures(fbStencilTexture);
        for (RenderPass p : renderPasses) p.destroy();
    }

    public void resize() {
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(fbColorTexture);
        glDeleteTextures(fbDepthTexture);
        glDeleteTextures(fbStencilTexture);
        createFrameBuffer();
        for (RenderPass p : renderPasses) p.reset();
    }
}
