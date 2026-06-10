package org.sharkk2.sengine.core.systems.components;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.core.classes.Component;

import static org.lwjgl.opengl.GL43.*;

public class ModelComponent extends Component {
    public final int vao;
    public final int vboVerts;
    public final int vboNormals;
    public final int vboUVs;
    public final int ebo;
    public final int indexCount;
    public float[] vertices;
    public Material material = new Material();

    public static class Material {
        public Vector3f albedo = new Vector3f(1, 1, 1);
        public float roughness = 0.5f;
        public float metalness = 0.0f;
        public Vector3f emissive = new Vector3f(0, 0, 0);
        public float emissiveStrength = 1f;
        public float opacity = 1.0f;

        public int albedoTex = -1;
        public int normalTex = -1;
        public int roughnessTex = -1;
        public int metalnessTex = -1;
        public int aoTex = -1;
        public int emissiveTex = -1;
        public int heightTex = -1;
        public int opacityTex = -1;

        @Override
        public String toString() {
            return "Material {\n" +
                    "  albedo = " + (albedo != null ? String.format("[%.2f, %.2f, %.2f]", albedo.x, albedo.y, albedo.z) : "null") + ",\n" +
                    "  roughness = " + roughness + ",\n" +
                    "  metalness = " + metalness + ",\n" +
                    "  emissive = " + (emissive != null ? String.format("[%.2f, %.2f, %.2f]", emissive.x, emissive.y, emissive.z) : "null") + ",\n" +
                    "  emissiveStrength = " + emissiveStrength + ",\n" +
                    "  opacity = " + opacity + ",\n" +
                    "  albedoTex = " + albedoTex + ",\n" +
                    "  normalTex = " + normalTex + ",\n" +
                    "  roughnessTex = " + roughnessTex + ",\n" +
                    "  metalnessTex = " + metalnessTex + ",\n" +
                    "  aoTex = " + aoTex + ",\n" +
                    "  emissiveTex = " + emissiveTex + ",\n" +
                    "  heightTex = " + heightTex + ",\n" +
                    "  opacityTex = " + opacityTex + "\n" +
                    "}";
        }
    }

    public ModelComponent(float[] verticies, float[] normals, float[] uvs, int[] indices) {
       this.indexCount = indices.length;
       this.vertices = verticies;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vboVerts = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboVerts);
        glBufferData(GL_ARRAY_BUFFER, verticies, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        vboUVs = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboUVs);
        glBufferData(GL_ARRAY_BUFFER, uvs, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);

        vboNormals = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboNormals);
        glBufferData(GL_ARRAY_BUFFER, normals, GL_STATIC_DRAW);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(2);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        glBindVertexArray(0);

    }

    public void cleanup() {
        glDeleteBuffers(vboVerts);
        glDeleteBuffers(vboNormals);
        glDeleteBuffers(vboUVs);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }

    @Override
    protected void onObjectAttach() {}

    @Override
    protected void onObjectDetach() {}

    @Override
    protected void onUpdate() {

    }
}
