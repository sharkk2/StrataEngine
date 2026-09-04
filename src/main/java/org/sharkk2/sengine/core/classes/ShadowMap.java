package org.sharkk2.sengine.core.classes;

import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.systems.components.LightComponent;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL43.*;

public class ShadowMap {
    public final LightComponent.ShadowQuality quality;
    public final int size;
    public int fbo;
    public int depthTexture;

    private void init() {
        depthTexture = glGenTextures();
        glActiveTexture(GL_TEXTURE31);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, size, size, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LESS);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        float[] border = {1.0f, 1.0f, 1.0f, 1.0f};
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, border);

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }


    public ShadowMap(LightComponent.ShadowQuality quality) {
        this.quality = quality;
        this.size = (int)(1024 * Math.pow(2, quality.ordinal()));
        init();
    }

    public void cleanup() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthTexture);
    }
}