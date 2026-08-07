package org.sharkk2.sengine.core.systems.renderer;

import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL43.*;

public class SSAOBuffer {
    private int ssaoFBO;
    private int blurFBO;
    private int ssaoTexture;
    private int blurTexture;
    private int noiseTexture;
    private int width, height;

    public SSAOBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        create();
    }

    private void create() {
        ssaoFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO);

        ssaoTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, ssaoTexture);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16F, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ssaoTexture, 0);

        blurFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, blurFBO);

        blurTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, blurTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16F, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, blurTexture, 0);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        generateNoiseTex();

    }

    private void generateNoiseTex() {
        float[] noise = new float[16 * 3];  // 4×4 = 16 texels, RGB each
        Random rng = new Random();

        for (int i = 0; i < 16; i++) {
            float x = rng.nextFloat() * 2.0f - 1.0f;
            float y = rng.nextFloat() * 2.0f - 1.0f;
            float invLen = 1.0f / (float) Math.sqrt(x*x + y*y);
            noise[i*3] = x * invLen;
            noise[i*3+1] = y * invLen;
            noise[i*3+2] = 0.0f;
        }

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, 4, 4, 0, GL_RGB, GL_FLOAT, noise);
        // GL_REPEAT is crucial cuz the 4×4 tile must tile across the full screen
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        this.noiseTexture = tex;
    }

    public Vector3f[] generateSSAOKernel(int size) {
        Vector3f[] kernel = new Vector3f[size];
        Random rng = new Random();
        for (int i = 0; i < size; i++) {
            float x = rng.nextFloat() * 2.0f - 1.0f; // [-1, 1]
            float y = rng.nextFloat() * 2.0f - 1.0f; // [-1, 1]
            float z = rng.nextFloat(); // [ 0, 1] hemisphere, not full sphere
            float len = (float) Math.sqrt(x*x + y*y + z*z);
            x /= len; y /= len; z /= len;

            // Scale quadratic falloff biases more samples close to origin to make the AO radius feel less uniform and artificial
            float scale = (float) i / size;
            scale = 0.1f + scale * scale * 0.9f;
            x *= scale; y *= scale; z *= scale;
            kernel[i] = new Vector3f(x, y, z);
        }
        return kernel;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        glDeleteTextures(ssaoTexture);
        glDeleteTextures(blurTexture);
        glDeleteFramebuffers(ssaoFBO);
        glDeleteFramebuffers(blurFBO);
        create();
    }

    public int getFBO() {return ssaoFBO;}
    public int getBlurFBO() {return blurFBO;}
    public int getTexture() {return ssaoTexture;}
    public int getBlurTexture() {return blurTexture;}
    public int getNoiseTexture() {return noiseTexture;}
    public int getWidth() {return width;}
    public int getHeight() {return height;}


}