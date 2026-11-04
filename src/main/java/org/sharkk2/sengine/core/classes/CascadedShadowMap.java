package org.sharkk2.sengine.core.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.core.classes.enums.ShadowQuality;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL43.*;

public class CascadedShadowMap {
    public final int size;
    public final int layers;
    public int fbo;
    public int depthTexture;

    public final Matrix4f[] matrices;
    public final float[] splits; // viewspace far distance per cascade
    public final float[] texelSizes; // world units per shadow texel
    public float lambda = 0.75f;  // 0 = even splits, 1 = logarithmic
    public float casterPad = 200f; // how far toward the sun casters are still included

    private final Matrix4f camProj = new Matrix4f();
    private final Matrix4f invCamViewProj = new Matrix4f();
    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightProj = new Matrix4f();
    private final Vector3f[] corners = new Vector3f[8];
    private final Vector3f center = new Vector3f();
    private final Vector3f lightCenter = new Vector3f();
    private static final Vector3f up = new Vector3f(0, 1, 0);
    private static final Vector3f anotherUP = new Vector3f(1, 0, 0);

    public CascadedShadowMap(ShadowQuality quality, int layers) {
        this.size = (int)(1024 * Math.pow(2, quality.ordinal()));
        this.layers = layers;
        matrices = new Matrix4f[layers];
        splits = new float[layers];
        texelSizes = new float[layers];
        for (int i = 0; i < layers; i++) matrices[i] = new Matrix4f();
        for (int i = 0; i < 8; i++) corners[i] = new Vector3f();
        init();
    }

    private void init() {
        depthTexture = glGenTextures();
        glActiveTexture(GL_TEXTURE31);
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTexture);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_DEPTH_COMPONENT24, size, size, layers, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_FUNC, GL_LESS);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameterfv(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindLayer(int layer) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0, layer);
    }

    public void bindAllLayers() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTexture, 0); // layered attachment
    }

    public void update(CameraComponent cam, float aspect, float maxDistance, Vector3f lightDir) {
        float near = cam.near;
        float far = Math.min(cam.far, maxDistance);
        Matrix4f view = cam.getViewMatrix();

        float prev = near;
        for (int i = 0; i < layers; i++) {
            float p = (i + 1f) / layers;
            float log = near * (float) Math.pow(far / near, p);
            float uni = near + (far - near) * p;
            float split = uni + (log - uni) * lambda;

            fitCascade(i, cam, aspect, prev, split, view, lightDir);
            splits[i] = split;
            prev = split;
        }
    }

    private void fitCascade(int i, CameraComponent cam, float aspect, float splitNear, float splitFar, Matrix4f view, Vector3f lightDir) {
        // sub-frustum corners in world space
        camProj.identity().perspective((float) Math.toRadians(cam.fov), aspect, splitNear, splitFar);
        invCamViewProj.set(camProj).mul(view).invert();
        center.zero();
        for (int c = 0; c < 8; c++) {
            invCamViewProj.transformProject((c & 1) * 2f - 1f, ((c >> 1) & 1) * 2f - 1f, ((c >> 2) & 1) * 2f - 1f, 1f, corners[c]);
            center.add(corners[c]);
        }
        center.div(8f);

        // bounding sphere: rotation-invariant size
        float radius = 0f;
        for (Vector3f c : corners) radius = Math.max(radius, c.distance(center));
        radius = (float) Math.ceil(radius * 16f) / 16f;

        // light view from origin, snap center to the texel grid
        lightView.identity().lookAlong(lightDir, Math.abs(lightDir.y) < 0.99f ? up : anotherUP);
        float texel = radius * 2f / size;
        lightView.transformPosition(center, lightCenter);
        float snapX = Math.round(lightCenter.x / texel) * texel;
        float snapY = Math.round(lightCenter.y / texel) * texel;
        lightView.translateLocal(-snapX, -snapY, 0f);

        lightProj.setOrtho(-radius, radius, -radius, radius, -lightCenter.z - radius - casterPad, -lightCenter.z + radius);
        lightProj.mul(lightView, matrices[i]);
        texelSizes[i] = texel;
    }

    public void cleanup() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthTexture);
    }
}