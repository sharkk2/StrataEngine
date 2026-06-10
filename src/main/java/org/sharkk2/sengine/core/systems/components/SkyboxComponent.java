package org.sharkk2.sengine.core.systems.components;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.systems.ShaderService;

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



}
