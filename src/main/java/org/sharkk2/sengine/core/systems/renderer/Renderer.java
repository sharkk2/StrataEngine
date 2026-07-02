package org.sharkk2.sengine.core.systems.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.components.SkyboxComponent;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static org.lwjgl.opengl.GL43.*;

public class Renderer {
    // fixme: this is probably the second most unoptimized shit i wrote
    // fixme: its not obvious because i have less than 300 objects in my scene but under real load this is fucked
    // for example that fucking uploadLight methods is scary asf
    private final Engine engine;
    private final PostProcessor postProcessor;
    private final ShaderService.Shader objectShader;
    private final ShaderService.Shader skyboxShader;
    private final ShaderService.Shader depthShader;
    private final ShaderService.Shader gBufferShader;
    private final ShaderService.Shader lightingShader;
    private final ShaderService.Shader ssaoShader;
    private final ShaderService.Shader ssaoBlurShader;
    private final ShaderService.UBO cameraUBO;
    private final ShaderService.UBO fogUBO;
    private final GBuffer gbuffer;
    private final SSAOBuffer ssaoBuffer;

    private CameraComponent camera;
    private Scene activeScene;
    private boolean wireframe = false;
    private int counter = 0;

    private final int lightQuadVAO;
    private final int lightQuadVBO;
    private static final String[] LIGHT_PROPS = {
            "type", "position", "color", "direction", "range", "intensity",
            "constant", "linear", "quadratic", "innerCutOff", "outerCutOff", "hasCookie"
    };
    private static final String[][] LIGHT_UNIFORMS = new String[6][LIGHT_PROPS.length];
    private static final String[] COOKIE_TEX_UNIFORMS = new String[6];
    static {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < LIGHT_PROPS.length; j++) {
                LIGHT_UNIFORMS[i][j] = "lights[" + i + "]." + LIGHT_PROPS[j];
            }
            COOKIE_TEX_UNIFORMS[i] = "cookieTextures[" + i + "]";
        }
    }

    private static final int TEX_ALBEDO = 0;
    private static final int TEX_ROUGHNESS = 1;
    private static final int TEX_METALNESS = 2;
    private static final int TEX_NORMAL = 3;
    private static final int TEX_EMISSIVE = 4;
    private static final int TEX_AO = 5;
    private static final int TEX_ALPHA_MASK = 6;
    private static final int TEX_OPACITY = 7;
    private static final String[] TEX_USE_UNIFORMS = {
            "useAlbedoTex", "useRoughnessTex", "useMetalnessTex", "useNormalTex",
            "useEmissiveTex", "useAoTex", "useAlphaMaskTex", "useOpacityTex"
    };
    private static final String[] TEX_UNIFORMS = {
            "albedoTex", "roughnessTex", "metalnessTex", "normalTex",
            "emissiveTex", "aoTex", "alphaMaskTex", "opacityTex"
    };

    public enum RenderMode {
        MODE_ALL, MODE_BASECOLOR, MODE_METALNESS, MODE_ROUGHNESS, MODE_NORMALS, MODE_EMISSIVE, MODE_AO, MODE_OPACITY, MODE_DEFERRED_ONLY, MODE_MAX
    }

    public enum RenderMethod {
        RENDER_DEFERRED, RENDER_FORWARD, RENDER_SKIP
    }

    public enum DrawMode {
        TRIANGLES, LINES
    }

    private RenderMode renderingMode = RenderMode.MODE_ALL;
    private final List<LightComponent> thelights = new ArrayList<>();
    private final Vector3f skycolor = new Vector3f();
    private final List<GameObject> forwardRenders = new ArrayList<>();
    private final List<GameObject> transparentObjects = new ArrayList<>();
    private final List<GameObject> sceneObjects = new ArrayList<>();
    private final ArrayDeque<GameObject> toVisit = new ArrayDeque<>();

    public Renderer(Engine engine) {
        this.engine = engine;
        this.postProcessor = new PostProcessor(engine);
        this.gbuffer = new GBuffer(engine.getWindowWidth(), engine.getWindowHeight());
        this.ssaoBuffer = new SSAOBuffer(engine.getWindowWidth() / 2, engine.getWindowHeight() / 2);
        ssaoShader = engine.getShaderService().get("shaders/ssao/ssaoVert.glsl", "shaders/ssao/ssaoFrag.glsl");
        ssaoBlurShader = engine.getShaderService().get("shaders/ssao/ssaoVert.glsl", "shaders/ssao/ssaoBlurFrag.glsl");
        depthShader = engine.getShaderService().get("shaders/depthVert.glsl", "shaders/depthFrag.glsl");
        objectShader = engine.getShaderService().get("shaders/objectVert.glsl", "shaders/ObjectFrag.glsl");
        skyboxShader = engine.getShaderService().get("shaders/skyboxVert.glsl", "shaders/skyboxFrag.glsl");
        gBufferShader = engine.getShaderService().get("shaders/dpass/gBufferVert.glsl", "shaders/dpass/gBufferFrag.glsl");
        lightingShader = engine.getShaderService().get("shaders/dpass/lightingVert.glsl", "shaders/dpass/lightingFrag.glsl");
        cameraUBO = engine.getShaderService().createUBO("camera", 0, 192);
        fogUBO = engine.getShaderService().createUBO("fog", 1, 32);

        float[] quadVertices = {
                -1f, 1f, 0f, 1f,
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f
        };

        lightQuadVAO = glGenVertexArrays();
        lightQuadVBO = glGenBuffers();
        glBindVertexArray(lightQuadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, lightQuadVBO);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glBindVertexArray(0);

        ssaoShader.use();
        Vector3f[] ssaoKernel = ssaoBuffer.generateSSAOKernel((int) engine.getValue("ssao_samples"));
        for (int i = 0; i < ssaoKernel.length; i++) {
            ssaoShader.setVec3("samples[" + i + "]", ssaoKernel[i]);
        }


    }

    public void enableWireframe(boolean v) { wireframe = v; }
    public boolean wireframeEnabled() { return wireframe; }
    public PostProcessor getPostProcessor() { return postProcessor; }
    public GBuffer getGbuffer() {return gbuffer;}
    public void setRenderingMode(RenderMode mode) { renderingMode = mode; }
    public RenderMode getRenderingMode() { return renderingMode; }

    public void renderScene(Scene scene) {
        counter = 0; // for counting how many objects we render
        activeScene = scene;
        camera = engine.getCameraService().getPrimaryCamera();
        forwardRenders.clear();
        transparentObjects.clear();
        scene.getObjects(sceneObjects);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(48);
            camera.getProjectionMatrix(engine.getWindowAspectRatio()).get(0, buf);
            camera.getViewMatrix().get(16, buf);
            camera.getViewMatrix().invert().get(32, buf);
            cameraUBO.upload(buf);
        } // uploading cam ubo "each matrix is 16 floats, times 4 is 64 bytes"

        Scene.Fog fog = scene.environment.fog;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(32);
            buf.putFloat(0, fog.density);
            buf.putFloat(4, fog.start);
            buf.putFloat(8, fog.end);
            buf.putInt(12, fog.enabled ? 1 : 0);
            buf.putInt(16, fog.mode.ordinal());
            fogUBO.upload(buf);
        } // uploading fog ubo "floats and ints are both 4 bytes"

        glPolygonMode(GL_FRONT_AND_BACK, wireframe ? GL_LINE : GL_FILL);

        if (wireframe) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            setupObjectShader(scene);

            for (GameObject object : sceneObjects) renderObject(object);
            Vector3f cp = camera.getOwner().transform.getPosition();
            engine.setWindowTitle("SharkEngine " + engine.version + " [WIREFRAME] - " + counter + " model(s) (" + engine.getFps() + "fps) " + Math.round(cp.x) + ":" + Math.round(cp.y) + ":" + Math.round(cp.z));
            return;
        } // if wireframe on, if we used deferred or post processing, the 2D fullscreen quads will cover the screen, plus its useless cuz its just lines

        // collecting objects
        toVisit.clear();
        toVisit.addAll(sceneObjects);
        while (!toVisit.isEmpty()) {
            GameObject obj = toVisit.pop();
            toVisit.addAll(obj.children);
            if (obj.renderMethod == RenderMethod.RENDER_SKIP || obj.renderMethod == RenderMethod.RENDER_FORWARD) continue;
            if (obj.hasComponent(ModelComponent.class) && obj.getComponent(ModelComponent.class).material.isTransparent()) {
                transparentObjects.add(obj);
            }
        }

        gbuffer.bindGPass(); // Bind the GBUFFER
        glDisable(GL_BLEND);   // <-- add this
        // "this was a bug" u do NOT want blending enabled as it blends or averages gbuffer data

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glStencilMask(0xFF); // allow writing
        glClear(GL_STENCIL_BUFFER_BIT); // clear the stencil buffer data
        glStencilFunc(GL_ALWAYS, 1, 0xFF); // make it always pass
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE); // if pass write one
        gBufferShader.use();
        for (GameObject object : sceneObjects) gBufferPass(object);

        glStencilMask(0x00); // disable writing
        // that makes every pixel stamped with 1, which we will use down later to determine where to draw the skybox and do lighting


        postProcessor.bindFBO();
        runShadowPass();

        if (engine.getIO("ssao")) {runSSAOPass();}

        glBindFramebuffer(GL_READ_FRAMEBUFFER, gbuffer.getFBO());
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, postProcessor.getFBO());
        glBlitFramebuffer(0, 0, gbuffer.getWidth(), gbuffer.getHeight(), 0, 0, gbuffer.getWidth(), gbuffer.getHeight(), GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, postProcessor.getFBO());
        //  copy the depth and stencil buffer data to the pp, so forward objects have a correct depth buffer to read/write

        // using the stencil buffer again, we make it that every pixel thats not stamped with 1 the skybox fills it
        // the forward rendered stuff is drawn later on top so we dont have to care about them
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_EQUAL, 0, 0xFF);
        glStencilMask(0x00);
        renderSkybox();

        glStencilFunc(GL_EQUAL, 1, 0xFF); // now if we DID stamp em, draw the light quad and do the fancy lighting
        glDisable(GL_DEPTH_TEST);
        Scene.GlobalSceneLight gsl = scene.lights.globalLight;
        lightingShader.use();
        lightingShader.setInt("renderingMode", renderingMode.ordinal());
        lightingShader.setVec3("cameraPos", camera.getOwner().transform.getPosition());
        lightingShader.setVec3("direction", gsl.direction);
        lightingShader.setVec3("color", gsl.color);
        lightingShader.setVec3("ambient", gsl.ambient);
        lightingShader.setInt("enabled", gsl.enabled ? 1 : 0);
        lightingShader.setInt("globalShadowEnabled", gsl.castShadow ? 1 : 0);
        lightingShader.setMat4("globalLightSpaceMatrix", gsl.calcLightSpace(engine));
        lightingShader.setVec3("skyColor", skycolor);
        glActiveTexture(GL_TEXTURE30);
        glBindTexture(GL_TEXTURE_2D, gsl.shadowMap.depthTexture);
        lightingShader.setInt("globalShadowTex", 30);
        uploadLights(lightingShader, camera.getOwner().transform.getPosition());
        gbuffer.bindTextures(0);
        lightingShader.setInt("gPosition", 0);
        lightingShader.setInt("gNormal", 1);
        lightingShader.setInt("gAlbedo", 2);
        lightingShader.setInt("gMaterial", 3);
        lightingShader.setInt("gEmissive", 4);
        lightingShader.setInt("ssaoEnabled", engine.getIO("ssao") ? 1 : 0);
        glActiveTexture(GL_TEXTURE29);
        glBindTexture(GL_TEXTURE_2D, ssaoBuffer.getBlurTexture());
        lightingShader.setInt("ssaoTex", 29);

        glBindVertexArray(lightQuadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST); // because we didn't care about forward stuff earlier, the stencil would just reject them, plus we dont even need it
        setupObjectShader(scene);
        uploadLights(objectShader, camera.getOwner().transform.getPosition());

        // because we r gonna blend again, we HAVE to blend from far things to close things, if we do the opposite, the closest thing would just blend with the skybox or something
        Vector3f camPos = camera.getOwner().transform.getPosition();
        transparentObjects.sort((a, b) -> Float.compare(
                b.transform.getPosition().distanceSquared(camPos),
                a.transform.getPosition().distanceSquared(camPos)
        ));

        if (!renderingMode.equals(RenderMode.MODE_DEFERRED_ONLY)) {
            glEnable(GL_BLEND);
            glDepthMask(true);
            for (GameObject object : forwardRenders) renderObject(object);
            for (GameObject object : transparentObjects) {
                glEnable(GL_CULL_FACE); // back faces render first, front last
                glCullFace(GL_FRONT);
                renderModel(object, object.getComponent(ModelComponent.class));
                glCullFace(GL_BACK);
                renderModel(object, object.getComponent(ModelComponent.class));
                glDisable(GL_CULL_FACE);
            }
        }


        postProcessor.render();
        engine.setWindowTitle("SharkEngine " + engine.version + " - Rendering " + counter + " model(s) (" + engine.getFps() + "fps: " + String.format("%.2f", engine.getDeltaTime() * 1000) + "ms) " + Math.round(camPos.x) + ":" + Math.round(camPos.y) + ":" + Math.round(camPos.z));
    }

    private void gBufferPass(GameObject object) {
        for (GameObject child : object.children) gBufferPass(child);
        if (!object.hasComponent(ModelComponent.class)) return;
        if (object.renderMethod == RenderMethod.RENDER_SKIP) return;
        if (object.renderMethod == RenderMethod.RENDER_FORWARD) {
            forwardRenders.add(object);
            return;
        }
        ModelComponent model = object.getComponent(ModelComponent.class);
        if (model.material.isTransparent()) return;
        if (engine.getIO("frustumCulling") && !camera.inFrustum(object, engine.getWindowAspectRatio(), model.boundingRadius)) return;
        renderGBufferModel(object, model);
        counter++;
    }

    private void renderGBufferModel(GameObject object, ModelComponent model) {
        glBindVertexArray(model.vao);
        gBufferShader.setMat4("uModel", object.transform.calculateWorldMatrix());
        gBufferShader.setVec3("albedo", model.material.albedo);
        gBufferShader.setFloat("metalness", model.material.metalness);
        gBufferShader.setFloat("roughness", model.material.roughness);
        gBufferShader.setVec3("emissive", model.material.emissive);
        gBufferShader.setFloat("alphaMaskThreshold", model.material.alphaMaskThreshold);
        gBufferShader.setInt("alphaCutout", model.material.alphaCutout ? 1 : 0);
        gBufferShader.setFloat("emissiveStrength", model.material.emissiveStrength);
        gBufferShader.setFloat("opacity", model.material.opacity);
        boolean isPacked = (model.material.roughnessTex == model.material.metalnessTex) && (model.material.roughnessTex != -1);
        gBufferShader.setInt("isPackedORM", isPacked ? 1 : 0);
        int unit = 0;
        unit = bindTexture2D(gBufferShader, TEX_ALBEDO, model.material.albedoTex, unit);
        if (isPacked) {
            unit = bindTexture2D(gBufferShader, TEX_ROUGHNESS, model.material.metalnessTex, unit);
        } else {
            unit = bindTexture2D(gBufferShader, TEX_ROUGHNESS, model.material.roughnessTex, unit);
            unit = bindTexture2D(gBufferShader, TEX_METALNESS, model.material.metalnessTex, unit);
        }
        unit = bindTexture2D(gBufferShader, TEX_NORMAL, model.material.normalTex, unit);
        unit = bindTexture2D(gBufferShader, TEX_EMISSIVE, model.material.emissiveTex, unit);
        unit = bindTexture2D(gBufferShader, TEX_AO, model.material.aoTex, unit);
        unit = bindTexture2D(gBufferShader, TEX_ALPHA_MASK, model.material.alphaMaskTex, unit);
        bindTexture2D(gBufferShader, TEX_OPACITY, model.material.opacityTex, unit);
        glDrawElements(model.drawMode == DrawMode.TRIANGLES ? GL_TRIANGLES:GL_LINES, model.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private void setupObjectShader(Scene scene) {
        Scene.GlobalSceneLight gsl = scene.lights.globalLight;
        objectShader.use();
        objectShader.setInt("renderingMode", renderingMode.ordinal());
        objectShader.setVec3("cameraPos", camera.getOwner().transform.getPosition());
        objectShader.setVec3("direction", gsl.direction);
        objectShader.setVec3("color", gsl.color);
        objectShader.setVec3("ambient", gsl.ambient);
        objectShader.setInt("enabled", gsl.enabled ? 1 : 0);
        objectShader.setInt("globalShadowEnabled", gsl.castShadow ? 1 : 0);
        objectShader.setMat4("globalLightSpaceMatrix", gsl.calcLightSpace(engine));
        objectShader.setVec3("skyColor", skycolor);
        glActiveTexture(GL_TEXTURE30);
        glBindTexture(GL_TEXTURE_2D, gsl.shadowMap.depthTexture);
        objectShader.setInt("globalShadowTex", 30);
    }

    private void renderSkybox() {
        if (activeScene.environment.activeSkybox == null || !activeScene.environment.skyboxEnabled) return;
        if (!activeScene.environment.activeSkybox.hasComponent(SkyboxComponent.class)) {
            Logger.warning(activeScene.getName() + "'s active Skybox is invalid! disabling skybox");
            activeScene.environment.skyboxEnabled = false;
            return;
        }
        SkyboxComponent skybox = activeScene.environment.activeSkybox.getComponent(SkyboxComponent.class);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_DEPTH_TEST);
        skyboxShader.use();
        skyboxShader.setVec3("sunDir", activeScene.lights.globalLight.direction);
        glBindVertexArray(skybox.vao);
        if (skybox.getTextureID() != -1) {
            skyboxShader.setInt("useTexture", 1);
            glActiveTexture(GL_TEXTURE31);
            glBindTexture(GL_TEXTURE_CUBE_MAP, skybox.getTextureID());
            skyboxShader.setInt("skybox", 31);
        } else {
            skyboxShader.setInt("useTexture", 0);
        }
        skybox.computeSkyColor(activeScene.lights.globalLight.direction, skycolor);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);
        glDepthMask(true);
        glDepthFunc(GL_LESS);
    }

    private void renderObject(GameObject object) {
        for (GameObject child : object.children) renderObject(child);
        if (object.hasComponent(ModelComponent.class)) {
            ModelComponent model = object.getComponent(ModelComponent.class);
            if (!camera.inFrustum(object, engine.getWindowAspectRatio(), model.boundingRadius) && engine.getIO("frustumCulling")) return;
            if ((model.material.isMasked() || (model.material.opacity >= 1.0f && model.material.opacityTex == -1))) {
                renderModel(object, model);
                counter++;
            }
        }
    }

    private void renderModel(GameObject object, ModelComponent model) {
        glBindVertexArray(model.vao);
        objectShader.setInt("lightEnabled", model.material.enabled ? 1 : 0);
        objectShader.setMat4("uModel", object.transform.calculateWorldMatrix());
        objectShader.setVec3("albedo", model.material.albedo);
        objectShader.setFloat("metalness", model.material.metalness);
        objectShader.setFloat("roughness", model.material.roughness);
        objectShader.setVec3("emissive", model.material.emissive);
        objectShader.setFloat("alphaMaskThreshold", model.material.alphaMaskThreshold);
        objectShader.setInt("alphaCutout", model.material.alphaCutout ? 1 : 0);
        objectShader.setFloat("emissiveStrength", model.material.emissiveStrength);
        objectShader.setFloat("opacity", model.material.opacity);
        boolean isPacked = (model.material.roughnessTex == model.material.metalnessTex) && (model.material.roughnessTex != -1);
        objectShader.setInt("isPackedORM", isPacked ? 1 : 0);
        int unit = 0;
        unit = bindTexture2D(objectShader, TEX_ALBEDO, model.material.albedoTex, unit);
        if (isPacked) {
            unit = bindTexture2D(objectShader, TEX_ROUGHNESS, model.material.metalnessTex, unit);
        } else {
            unit = bindTexture2D(objectShader, TEX_ROUGHNESS, model.material.roughnessTex, unit);
            unit = bindTexture2D(objectShader, TEX_METALNESS, model.material.metalnessTex, unit);
        }
        unit = bindTexture2D(objectShader, TEX_NORMAL, model.material.normalTex, unit);
        unit = bindTexture2D(objectShader, TEX_EMISSIVE, model.material.emissiveTex, unit);
        unit = bindTexture2D(objectShader, TEX_AO, model.material.aoTex, unit);
        unit = bindTexture2D(objectShader, TEX_ALPHA_MASK, model.material.alphaMaskTex, unit);
        bindTexture2D(objectShader, TEX_OPACITY, model.material.opacityTex, unit);
        glDrawElements(model.drawMode == DrawMode.TRIANGLES ? GL_TRIANGLES:GL_LINES, model.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private void uploadLights(ShaderService.Shader shader, Vector3f sortPos) {
        activeScene.lights.getLights(thelights);
        thelights.sort((a, b) -> {
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
        int count = 0;
        for (int i = 0; i < thelights.size() && count < 6; i++) {
            LightComponent light = thelights.get(i);
            if (light.getOwner() == null) break; // nulls are at end, nothing left to process
            String[] u = LIGHT_UNIFORMS[count]; // pre-built, no allocation
            shader.setInt(u[0], light.type == LightComponent.LightType.SPOT_LIGHT ? 1 : 0);
            Vector3f pos = light.getOwner().transform.getPosition();
            Vector3f off = light.offset;
            shader.setFloat3(u[1], pos.x + off.x, pos.y + off.y, pos.z + off.z);
            shader.setVec3(u[2], light.color);
            shader.setVec3(u[3], light.spotLightDirection);
            shader.setFloat(u[4], light.range);
            shader.setFloat(u[5], light.intensity);
            shader.setFloat(u[6], light.constant);
            shader.setFloat(u[7], light.linear);
            shader.setFloat(u[8], light.quadratic);
            shader.setFloat(u[9], light.spotLightInnerCutoff);
            shader.setFloat(u[10], light.spotLightOuterCutoff);
            boolean hasCookie = light.type == LightComponent.LightType.SPOT_LIGHT && light.lightCookieTex != -1;
            shader.setInt(u[11], hasCookie ? 1 : 0);
            if (hasCookie) {
                int unit = 8 + count;
                glActiveTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_2D, light.lightCookieTex);
                shader.setInt(COOKIE_TEX_UNIFORMS[count], unit);
            }
            count++;
        }
        shader.setInt("lightCount", count);
    }

    private int bindTexture2D(ShaderService.Shader shader, int texIndex, int textureId, int unit) {
        boolean hasTexture = textureId != -1;
        shader.setInt(TEX_USE_UNIFORMS[texIndex], hasTexture ? 1 : 0);
        if (hasTexture) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, textureId);
            shader.setInt(TEX_UNIFORMS[texIndex], unit);
            return unit + 1;
        }
        return unit;
    }

    private void renderDepth(GameObject obj) {
        for (GameObject child : obj.children) renderDepth(child);
        if (obj.hasComponent(ModelComponent.class)) {
            ModelComponent model = obj.getComponent(ModelComponent.class);
            if (model.drawMode == DrawMode.LINES) return;
            glBindVertexArray(model.vao);
            if (model.material.albedoTex != -1) {
                glActiveTexture(GL_TEXTURE4);
                glBindTexture(GL_TEXTURE_2D, model.material.albedoTex);
                depthShader.setInt("albedo", 4);
                depthShader.setInt("hasAlbedo", 1);
            } else {
                depthShader.setInt("hasAlbedo", 0);
            }

            depthShader.setMat4("model", obj.transform.calculateWorldMatrix());
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            glDrawElements(GL_TRIANGLES, model.indexCount, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);
            glDisable(GL_CULL_FACE);
        }
    }

    private void renderSceneDepth(Scene scene, Matrix4f space) {

    }

    public void runShadowPass() {
        if (activeScene.lights.globalLight.castShadow) {
            Scene.GlobalSceneLight gsl = activeScene.lights.globalLight;
            glViewport(0, 0, gsl.shadowMap.width, gsl.shadowMap.height);
            glBindFramebuffer(GL_FRAMEBUFFER, gsl.shadowMap.fbo);
            glClear(GL_DEPTH_BUFFER_BIT);
            depthShader.use();
            depthShader.setMat4("spaceMatrix", gsl.calcLightSpace(engine));
            for (GameObject obj : sceneObjects) renderDepth(obj);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, postProcessor.getFBO());
        glViewport(0, 0, engine.getWindowWidth(), engine.getWindowHeight());
    }

    private void runSSAOPass() {
        glViewport(0, 0, engine.getWindowWidth() / 2, engine.getWindowHeight() / 2);

        glBindFramebuffer(GL_FRAMEBUFFER, ssaoBuffer.getFBO());
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        ssaoShader.use();
        ssaoShader.setFloat("radius", 0.4f);
        ssaoShader.setFloat("bias", 0.025f);

        ssaoShader.setFloat("noiseScaleX", (engine.getWindowWidth() / 2.0f) / 4.0f);
        ssaoShader.setFloat("noiseScaleY", (engine.getWindowHeight() / 2.0f) / 4.0f);
        gbuffer.bindTextures(0);
        ssaoShader.setInt("gPosition", 0);
        ssaoShader.setInt("gNormal", 1);
        ssaoShader.setInt("kernelSize", (int) engine.getValue("ssao_samples"));

        glActiveTexture(GL_TEXTURE5);
        glBindTexture(GL_TEXTURE_2D, ssaoBuffer.getNoiseTexture());
        ssaoShader.setInt("texNoise", 5);

        glBindVertexArray(lightQuadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glBindFramebuffer(GL_FRAMEBUFFER, ssaoBuffer.getBlurFBO());
        glClear(GL_COLOR_BUFFER_BIT);

        ssaoBlurShader.use();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, ssaoBuffer.getTexture());
        ssaoBlurShader.setInt("ssaoInput", 0);

        glBindVertexArray(lightQuadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glViewport(0, 0, engine.getWindowWidth(), engine.getWindowHeight());
    }

    public void onResize(int width, int height) {
        postProcessor.resize(width, height);
        gbuffer.resize(width, height);
        ssaoBuffer.resize(width / 2, height / 2);
    }

}