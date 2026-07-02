package org.sharkk2.sengine.core.systems.components;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.Component;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

public class SkyboxComponent extends Component {
    private final Engine engine;
    private int cubemapTex;
    public final int vao;
    private final int vbo;

    private static final float[] VERTICES = {
            -1f,  1f, -1f,  -1f, -1f, -1f,   1f, -1f, -1f,
            1f, -1f, -1f,   1f,  1f, -1f,  -1f,  1f, -1f,

            -1f, -1f,  1f,  -1f, -1f, -1f,  -1f,  1f, -1f,
            -1f,  1f, -1f,  -1f,  1f,  1f,  -1f, -1f,  1f,

            1f, -1f, -1f,   1f, -1f,  1f,   1f,  1f,  1f,
            1f,  1f,  1f,   1f,  1f, -1f,   1f, -1f, -1f,

            -1f, -1f,  1f,  -1f,  1f,  1f,   1f,  1f,  1f,
            1f,  1f,  1f,   1f, -1f,  1f,  -1f, -1f,  1f,

            -1f,  1f, -1f,   1f,  1f, -1f,   1f,  1f,  1f,
            1f,  1f,  1f,  -1f,  1f,  1f,  -1f,  1f, -1f,

            -1f, -1f, -1f,  -1f, -1f,  1f,   1f, -1f, -1f,
            1f, -1f, -1f,  -1f, -1f,  1f,   1f, -1f,  1f
    };

    private static final Vector3f DAY_HORIZON = new Vector3f(0.72f, 0.84f, 0.98f);
    private static final Vector3f DAY_HAZE = new Vector3f(0.9f, 0.75f, 0.6f);
    private static final Vector3f SUNSET_HORIZON = new Vector3f(1.0f, 0.45f, 0.1f);
    private static final Vector3f SUNSET_HAZE = new Vector3f(1.0f, 0.4f, 0.15f);
    private static final Vector3f NIGHT_HORIZON = new Vector3f(0.02f, 0.02f, 0.07f);
    private static final Vector3f NIGHT_HAZE = new Vector3f(0.01f, 0.01f, 0.04f);
    private final Vector3f scratchSDir = new Vector3f();
    private final Vector3f scratchHorizon = new Vector3f();
    private final Vector3f scratchHaze = new Vector3f();

    public SkyboxComponent(Engine engine, int cubemapTex) {
       this.engine = engine;
       this.cubemapTex = cubemapTex;
       vao = glGenVertexArrays();
       vbo = glGenBuffers();
       glBindVertexArray(vao);
       glBindBuffer(GL_ARRAY_BUFFER, vbo);
       glBufferData(GL_ARRAY_BUFFER, VERTICES, GL_STATIC_DRAW);
       glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
       glEnableVertexAttribArray(0);
       glBindVertexArray(0);
    }

    public SkyboxComponent(Engine engine) {
        this.engine = engine;
        this.cubemapTex = -1;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, VERTICES, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }


    @Override
    protected void onObjectAttach() {

    }

    @Override
    protected void onObjectDetach() {
        glDeleteTextures(cubemapTex);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }

    @Override
    protected void onUpdate() {

    }

    public int getTextureID() {return cubemapTex;}
    public void setSkybox(int cubemapTex) {
        this.cubemapTex = cubemapTex;
    }

    public void computeSkyColor(Vector3f sunDir, Vector3f out) {
        sunDir.negate(scratchSDir).normalize();
        float sunHeight = scratchSDir.y;

        float dayBlend = Math.max(0.0f, Math.min(1.0f, sunHeight * 2.0f));
        float sunsetBlend = Math.max(0.0f, Math.min(1.0f, 1.0f - Math.abs(sunHeight) * 6.0f));

        NIGHT_HORIZON.lerp(SUNSET_HORIZON, dayBlend, scratchHorizon);
        scratchHorizon.lerp(DAY_HORIZON, dayBlend, scratchHorizon);
        scratchHorizon.lerp(SUNSET_HORIZON, sunsetBlend * 0.55f, scratchHorizon);

        NIGHT_HAZE.lerp(SUNSET_HAZE, dayBlend, scratchHaze);
        scratchHaze.lerp(DAY_HAZE, dayBlend, scratchHaze);
        scratchHaze.lerp(SUNSET_HAZE, sunsetBlend * 0.65f, scratchHaze);

        scratchHorizon.lerp(scratchHaze, 0.5f, out);
    }


}
