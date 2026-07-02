package org.sharkk2.sengine.core.systems.renderer;

import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.exceptions.FrameBufferException;

import static org.lwjgl.opengl.GL43.*;

/**
 * Layout:
 *   Attachment 0: gPosition - RGB32F view-space XYZ position
 *   Attachment 1: gNormal - RGB16F world-space XYZ normal
 *   Attachment 2: gAlbedo - RGBA8 RGB albedo, A opacity
 *   Attachment 3: gMaterial - RGBA8 R metalness, G roughness, B AO, A emissive strength
 *   Attachment 4: gEmissive - RGB16F emissive color (multiplied by strength in geometry pass)
 *   Depth: gDepth - DEPTH24_STENCIL8 for depth testing and forward pass blending
 */
public class GBuffer {

    private int fbo;
    private int gPosition;
    private int gNormal;
    private int gAlbedo;
    private int gMaterial;
    private int gEmissive;
    private int gDepth;
    private int width;
    private int height;

    public GBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        create();
    }

    private void create() {
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

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

    public void bindGPass() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void bindTextures(int baseUnit) {
        glActiveTexture(GL_TEXTURE0 + baseUnit);
        glBindTexture(GL_TEXTURE_2D, gPosition);
        glActiveTexture(GL_TEXTURE0 + baseUnit + 1);
        glBindTexture(GL_TEXTURE_2D, gNormal);
        glActiveTexture(GL_TEXTURE0 + baseUnit + 2);
        glBindTexture(GL_TEXTURE_2D, gAlbedo);
        glActiveTexture(GL_TEXTURE0 + baseUnit + 3);
        glBindTexture(GL_TEXTURE_2D, gMaterial);
        glActiveTexture(GL_TEXTURE0 + baseUnit + 4);
        glBindTexture(GL_TEXTURE_2D, gEmissive);
    }


   /* public void blitDepthTo(int targetFBO) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFBO);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, targetFBO);
    }*/

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        destroy();
        create();
    }

    public void destroy() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(gPosition);
        glDeleteTextures(gNormal);
        glDeleteTextures(gAlbedo);
        glDeleteTextures(gMaterial);
        glDeleteTextures(gEmissive);
        glDeleteTextures(gDepth);
    }

    public int getFBO() { return fbo; }
    public int getPositionTex() { return gPosition; }
    public int getNormalTex() { return gNormal; }
    public int getAlbedoTex() { return gAlbedo; }
    public int getMaterialTex() { return gMaterial; }
    public int getEmissiveTex() { return gEmissive; }
    public int getDepthTex() { return gDepth; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}