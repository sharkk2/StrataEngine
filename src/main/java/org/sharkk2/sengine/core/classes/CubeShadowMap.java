package org.sharkk2.sengine.core.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.core.classes.enums.ShadowQuality;
import org.sharkk2.sengine.core.systems.components.LightComponent;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL43.*;

public class CubeShadowMap implements IShadowMap {
    public final ShadowQuality quality;
    public final int size;
    public int fbo;
    public int depthCubemap;
    private static final Vector3f[] DIRS = {
            new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
            new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
            new Vector3f(0, 0, 1), new Vector3f(0, 0, -1)
    };
    private static final Vector3f[] UPS = {
            new Vector3f(0, -1, 0), new Vector3f(0, -1, 0),
            new Vector3f(0, 0, 1),  new Vector3f(0, 0, -1),
            new Vector3f(0, -1, 0), new Vector3f(0, -1, 0)
    };
    private final Matrix4f view = new Matrix4f();
    private final Vector3f target = new Vector3f();
    private final Matrix4f[] faceMatrices = {
            new Matrix4f(), new Matrix4f(), new Matrix4f(),
            new Matrix4f(), new Matrix4f(), new Matrix4f()
    };

    public void init() {
        depthCubemap = glGenTextures();
        glActiveTexture(GL_TEXTURE31);
        glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubemap);
        for (int face = 0; face < 6; face++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, 0, GL_DEPTH_COMPONENT, size, size, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

    }

    public CubeShadowMap(ShadowQuality quality) {
        this.quality = quality;
        this.size = (int)(1024 * Math.pow(2, quality.ordinal()));
        init();
    }

    public void bindFace(int face) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, depthCubemap, 0);
        glViewport(0, 0, size, size);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthCubemap, 0);
        glViewport(0, 0, size, size);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    public void cleanup() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthCubemap);
    }

    /** +X, -X, +Y, -Y, +Z, -Z*/
    public Matrix4f[] update(Vector3f pos, float range) {
        for (int face = 0; face < 6; face++) {
            target.set(pos).add(DIRS[face]);
            view.identity().lookAt(pos, target, UPS[face]);
            faceMatrices[face].identity().perspective((float) Math.toRadians(90.0), 1f, 0.1f, range).mul(view);
        }
        return faceMatrices;
    }
}