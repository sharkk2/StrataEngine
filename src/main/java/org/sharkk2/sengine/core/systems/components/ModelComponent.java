package org.sharkk2.sengine.core.systems.components;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL43.*;

public class ModelComponent extends Component {
    public int vao;
    public int vboVerts;
    public int vboNormals;
    public int vboUVs;
    public int ebo;
    public final int indexCount;
    public float[] vertices;
    public float[] normals;
    public float[] uvs;
    public int[] indices;
    public Material material = new Material();
    public Renderer.DrawMode drawMode = Renderer.DrawMode.TRIANGLES;
    public boolean castShadow = true;
    public boolean visible = true;
    public final Bounds bounds = new Bounds();


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
        public boolean enabled = true;
        private boolean scannedAlbedo = false;
        private boolean albedoTransparent = false;
        public int alphaMaskTex = -1;
        public float alphaMaskThreshold = 0.5f;
        public boolean alphaCutout = false;

        public boolean isMasked() {return alphaMaskTex != -1 || alphaCutout;}

        public boolean isTransparent() {
            if (isMasked()) return false;
            if (scannedAlbedo || albedoTex == -1) return opacity < 1.0f || opacityTex != -1 || albedoTransparent;
            int prevActiveUnit = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            int prevBound = glGetInteger(GL_TEXTURE_BINDING_2D);
            glBindTexture(GL_TEXTURE_2D, albedoTex);

            int alphaSize = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_ALPHA_SIZE);
            if (alphaSize > 0) {
                int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
                int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
                ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                for (int i = 3; i < pixels.limit(); i += 4) {
                    if ((pixels.get(i) & 0xFF) < 255) {
                        albedoTransparent = true;
                        break;
                    }
                }
            }
            glBindTexture(GL_TEXTURE_2D, prevBound);
            glActiveTexture(prevActiveUnit);
            scannedAlbedo = true;
            return opacity < 1.0f || opacityTex != -1 || albedoTransparent;
        }

        public Material copy() {
            Material copy = new Material();
            copy.albedo = new Vector3f(this.albedo);
            copy.roughness = this.roughness;
            copy.metalness = this.metalness;
            copy.emissive = new Vector3f(this.emissive);
            copy.emissiveStrength = this.emissiveStrength;
            copy.opacity = this.opacity;

            copy.albedoTex = this.albedoTex;
            copy.normalTex = this.normalTex;
            copy.roughnessTex = this.roughnessTex;
            copy.metalnessTex = this.metalnessTex;
            copy.aoTex = this.aoTex;
            copy.emissiveTex = this.emissiveTex;
            copy.heightTex = this.heightTex;
            copy.opacityTex = this.opacityTex;
            copy.alphaMaskTex = this.alphaMaskTex;

            copy.enabled = this.enabled;
            copy.alphaMaskThreshold = this.alphaMaskThreshold;
            copy.alphaCutout = this.alphaCutout;

            return copy;
        }

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
                    "  alphaMaskTex = " + alphaMaskTex + ",\n" +
                    "  alphaMaskThreshold = " + alphaMaskThreshold + ",\n" +
                    "  alphaCutout = " + alphaCutout + "\n" +
                    "}";
        }
    }


    public ModelComponent(float[] vertices, float[] normals, float[] uvs, int[] indices) {
        this.indexCount = indices.length;
        this.vertices = vertices;
        this.uvs = uvs;
        this.normals = normals;
        this.indices = indices;



    }

    public void cleanup() {
        glDeleteBuffers(vboVerts);
        glDeleteBuffers(vboNormals);
        glDeleteBuffers(vboUVs);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }



    @Override
    protected void onObjectAttach() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vboVerts = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboVerts);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
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
        bounds.computeLocals(vertices);
    }

    @Override
    protected void onObjectDetach() {cleanup();}

    @Override
    protected void onUpdate() {
        bounds.update(owner.transform.calculateWorldMatrix());
    }

    @Override
    public Component copy() {
        ModelComponent copy = new ModelComponent(vertices, normals, uvs, indices);
        copy.material = material.copy();
        copy.name = name + "_copy";
        copy.drawMode = drawMode;
        return copy;
    }
}
