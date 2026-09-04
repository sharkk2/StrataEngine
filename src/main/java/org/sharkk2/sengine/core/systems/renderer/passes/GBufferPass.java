package org.sharkk2.sengine.core.systems.renderer.passes;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.exceptions.FrameBufferException;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import static org.lwjgl.opengl.GL43.*;

public class GBufferPass extends RenderPass {
    private int fbo;
    private int gPosition;
    private int gNormal;
    private int gAlbedo;
    private int gMaterial;
    private int gEmissive;
    private int gDepth;
    private int width, height;
    private final ShaderService.Shader gBufferShader;

    public GBufferPass(Engine engine) {
        super(engine, "gbuffer_Pass");
        gBufferShader = engine.getShaderService().get("shaders/dpass/gBufferVert.glsl", "shaders/dpass/gBufferFrag.glsl");
        create();
    }

    private void create() {
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        width = (int) engine.getValue("res_width");
        height = (int) engine.getValue("res_height");

        // View space position (RGB32F high precision needed)
        gPosition = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gPosition);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, width, height, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gPosition, 0);

        // World space normal (RGB16F)
        gNormal = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gNormal);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, gNormal, 0);

        // Albedo (RGBA8: A channel holds opacity for the lighting pass)
        gAlbedo = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gAlbedo);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, gAlbedo, 0);

        // RGBA8: R metalness, G roughness, B AO, A emissive strength
        gMaterial = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gMaterial);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT3, GL_TEXTURE_2D, gMaterial, 0);

        // RGB16F: HDR range needed for bloom
        gEmissive = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gEmissive);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT4, GL_TEXTURE_2D, gEmissive, 0);

        glDrawBuffers(new int[]{
                GL_COLOR_ATTACHMENT0,
                GL_COLOR_ATTACHMENT1,
                GL_COLOR_ATTACHMENT2,
                GL_COLOR_ATTACHMENT3,
                GL_COLOR_ATTACHMENT4
        });

        gDepth = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gDepth);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH24_STENCIL8, width, height, 0, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, gDepth, 0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("GBuffer FBO was NOT completed (0x" + Integer.toHexString(status) + ")");
            throw new FrameBufferException("GBuffer FBO incomplete: 0x" + Integer.toHexString(status));
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }



    private void gBufferPass(GameObject object, FrameContext fc) {
        if (!object.hasComponent(ModelComponent.class)) return;

        ModelComponent model = object.getComponent(ModelComponent.class);
        if (model.getRenderMethod() != Renderer.RenderMethod.RENDER_DEFERRED || model.material.isTransparent()) return;
        if (engine.getIO("frustum_culling") && !fc.mainCamera.inFrustum(object, engine.getWindowAspectRatio(), model.bounds.boundingRadius) && !object.isDebuggingObject) return;
        drawGModel(model);
        fc.renderCounter++;
    }

    private void drawGModel(ModelComponent model) {
        if (!model.isVisible()) return;
        glBindVertexArray(model.vao);
        gBufferShader.setMat4("uModel",model.getOwner().transform.calculateWorldMatrix());
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
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.ALBEDO, model.material.albedoTex, unit);
        if (isPacked) {
            unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.ROUGHNESS, model.material.metalnessTex, unit);
        } else {
            unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.ROUGHNESS, model.material.roughnessTex, unit);
            unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.METALNESS, model.material.metalnessTex, unit);
        }
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.NORMAL, model.material.normalTex, unit);
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.EMISSIVE, model.material.emissiveTex, unit);
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.AO, model.material.aoTex, unit);
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.ALPHA_MASK, model.material.alphaMaskTex, unit);
        engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.OPACITY, model.material.opacityTex, unit);
        engine.getRenderer().uploadBoneMatrices(gBufferShader, model);
        glDrawElements(model.getDrawMode() == Renderer.DrawMode.TRIANGLES ? GL_TRIANGLES:GL_LINES, model.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    @Override
    protected void onPass(FrameContext frameContext) {
        frameContext.set("gbuffer.fbo", fbo);
        frameContext.set("gbuffer.position", gPosition);
        frameContext.set("gbuffer.normal", gNormal);
        frameContext.set("gbuffer.albedo", gAlbedo);
        frameContext.set("gbuffer.material", gMaterial);
        frameContext.set("gbuffer.emissive", gEmissive);
        frameContext.set("gbuffer.depth", gDepth);

        glViewport(0,0,width, height);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glDisable(GL_BLEND);   // <-- add this
        // "this was a bug" u do NOT want blending enabled as it blends or averages gbuffer data

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glStencilMask(0xFF); // allow writing
        glClear(GL_STENCIL_BUFFER_BIT); // clear the stencil buffer data
        glStencilFunc(GL_ALWAYS, 1, 0xFF); // make it always pass
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE); // if pass write one
        gBufferShader.use();
        for (GameObject object : frameContext.deferredObjects) gBufferPass(object, frameContext);

        glStencilMask(0x00);
        //  copy the depth and stencil buffer data to the pp, so forward objects have a correct depth buffer to read/write

        // using the stencil buffer again, we make it that every pixel thats not stamped with 1 the skybox fills it
        // the forward rendered stuff is drawn later on top so we dont have to care about them
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameContext.defaultFrameBuffer);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, frameContext.defaultFrameBuffer);
    }

    @Override
    protected void onDestroy() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(gPosition);
        glDeleteTextures(gNormal);
        glDeleteTextures(gAlbedo);
        glDeleteTextures(gMaterial);
        glDeleteTextures(gEmissive);
        glDeleteTextures(gDepth);
    }

    @Override
    protected void onReset() {
        onDestroy();
        create();
    }

    @Override
    protected String[] dependencies() {
        return new String[0];
    }
}