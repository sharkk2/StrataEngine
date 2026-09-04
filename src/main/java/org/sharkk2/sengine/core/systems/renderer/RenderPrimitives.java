package org.sharkk2.sengine.core.systems.renderer;

import static org.lwjgl.opengl.GL43.*;

public class RenderPrimitives {

    public static class RenderPrimitive {
        public final int vao;
        public final int vbo;
        public final int vertexCount;

        public RenderPrimitive(int vao, int vbo, int vertexCount) {
            this.vao = vao;
            this.vbo = vbo;
            this.vertexCount = vertexCount;
        }

        public void bind() {glBindVertexArray(vao);}

        public void draw() {glDrawArrays(GL_TRIANGLES, 0, vertexCount);glBindVertexArray(0);}
        public void draw(boolean unbind) {
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            if (unbind) glBindVertexArray(0);
        }

        public void unbind() {glBindVertexArray(0);}
        public void destroy() {
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
        }

    }

    public static RenderPrimitive cube() {
        float[] vertices = {
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

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);

        return new RenderPrimitive(vao, vbo, vertices.length / 3);
    }

    public static RenderPrimitive quad() {
        float[] vertices = {
                // pos        // uv
                -1f,  1f,     0f, 1f,
                -1f, -1f,     0f, 0f,
                1f, -1f,     1f, 0f,

                -1f,  1f,     0f, 1f,
                1f, -1f,     1f, 0f,
                1f,  1f,     1f, 1f,
        };

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        int stride = 4 * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0); // aPos
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES); // aUV
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        return new RenderPrimitive(vao, vbo, vertices.length / 4);
    }
}