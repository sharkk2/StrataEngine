package org.sharkk2.sengine.core.systems.components;

import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
    public static final int INSTANCE_FLOATS = 29;
    public static final int INSTANCE_STRIDE = INSTANCE_FLOATS * Float.BYTES;
    private static int instanceVbo = -1;
    public Material material = new Material();
    private Renderer.DrawMode drawMode = Renderer.DrawMode.TRIANGLES;
    private boolean castShadow = true;
    public final Bounds bounds = new Bounds();
    public static final int MAX_BONE_INFLUENCE = 4;
    public int vboBoneIds, vboBoneWeights;
    public int[] boneIds;
    public float[] boneWeights;
    private Renderer.RenderMethod renderMethod = Renderer.RenderMethod.RENDER_DEFERRED;
    public boolean rigged = false;
    public boolean frustumCulled = true;
    public int hash;
    private boolean hashDirty = true;
    public String batchID = "";

    public void setCastShadow(boolean v) {this.castShadow = v; markHashDirty();}
    public boolean castsShadow() {return castShadow;}
    public void setDrawMode(Renderer.DrawMode drawMode) {this.drawMode = drawMode; markHashDirty();}
    public Renderer.DrawMode getDrawMode() {return drawMode;}
    public void setRenderMethod(Renderer.RenderMethod method) {this.renderMethod = method; markHashDirty();}
    public Renderer.RenderMethod getRenderMethod() {return renderMethod;}

    public static class Material {
        public Vector3f albedo = new Vector3f(1, 1, 1);
        public float roughness = 0.5f;
        public float metalness = 0.0f;
        public Vector3f emissive = new Vector3f(0, 0, 0);
        public float emissiveStrength = 1f;
        public float opacity = 1.0f;

        public boolean enabled = true;
        private boolean scannedAlbedo = false;
        private boolean albedoTransparent = false;
        public float alphaMaskThreshold = 0.5f;
        public boolean alphaCutout = false;
        public enum MaterialTexture {ALBEDO, NORMAL, ROUGHNESS, METALNESS, AO, EMISSIVE, HEIGHT, OPACITY, ALPHA_MASK}
        private final int[] textures = new int[MaterialTexture.values().length];

        {Arrays.fill(textures, -1);}
        public int getTexture(MaterialTexture slot) {return textures[slot.ordinal()];}
        public boolean hasTexture(MaterialTexture slot) {return textures[slot.ordinal()] != -1;}

        public void setTexture(MaterialTexture slot, int id) {
            int old = textures[slot.ordinal()];
            if (old == id) return;
            textures[slot.ordinal()] = id;
            if (slot == MaterialTexture.ALBEDO) {
                scannedAlbedo = false;
                albedoTransparent = false;
            }
        }


        public int getAlbedoTexture() { return getTexture(MaterialTexture.ALBEDO); }
        public void setAlbedoTexture(int id) { setTexture(MaterialTexture.ALBEDO, id); }
        public int getNormalTexture() { return getTexture(MaterialTexture.NORMAL); }
        public void setNormalTexture(int id) { setTexture(MaterialTexture.NORMAL, id); }
        public int getRoughnessTexture() { return getTexture(MaterialTexture.ROUGHNESS); }
        public void setRoughnessTexture(int id) { setTexture(MaterialTexture.ROUGHNESS, id); }
        public int getMetalnessTexture() { return getTexture(MaterialTexture.METALNESS); }
        public void setMetalnessTexture(int id) { setTexture(MaterialTexture.METALNESS, id); }
        public int getAoTexture() { return getTexture(MaterialTexture.AO); }
        public void setAoTexture(int id) { setTexture(MaterialTexture.AO, id); }
        public int getEmissiveTexture() { return getTexture(MaterialTexture.EMISSIVE); }
        public void setEmissiveTexture(int id) { setTexture(MaterialTexture.EMISSIVE, id); }
        public int getHeightTexture() { return getTexture(MaterialTexture.HEIGHT); }
        public void setHeightTexture(int id) { setTexture(MaterialTexture.HEIGHT, id); }
        public int getOpacityTexture() { return getTexture(MaterialTexture.OPACITY); }
        public void setOpacityTexture(int id) { setTexture(MaterialTexture.OPACITY, id); }
        public int getAlphaMaskTexture() { return getTexture(MaterialTexture.ALPHA_MASK); }
        public void setAlphaMaskTexture(int id) { setTexture(MaterialTexture.ALPHA_MASK, id); }

        public boolean isMasked() {return getAlphaMaskTexture() != -1 || alphaCutout;}

        public boolean isTransparent() {
            if (isMasked()) return false;
            if (scannedAlbedo || getAlbedoTexture() == -1) return opacity < 1.0f || getOpacityTexture() != -1 || albedoTransparent;
            int prevActiveUnit = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            int prevBound = glGetInteger(GL_TEXTURE_BINDING_2D);
            glBindTexture(GL_TEXTURE_2D, getAlbedoTexture());

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
            return opacity < 1.0f || getOpacityTexture() != -1 || albedoTransparent;
        }

        public Material copy() {
            Material copy = new Material();
            copy.albedo = new Vector3f(this.albedo);
            copy.roughness = this.roughness;
            copy.metalness = this.metalness;
            copy.emissive = new Vector3f(this.emissive);
            copy.emissiveStrength = this.emissiveStrength;
            copy.opacity = this.opacity;

            for (MaterialTexture slot : MaterialTexture.values()) {
                copy.setTexture(slot, getTexture(slot));
            }

            copy.enabled = this.enabled;
            copy.alphaMaskThreshold = this.alphaMaskThreshold;
            copy.alphaCutout = this.alphaCutout;

            return copy;
        }

        @Override
        public String toString() {
            StringBuilder string = new StringBuilder("Material {\n" +
                    "  albedo = " + (albedo != null ? String.format("[%.2f, %.2f, %.2f]", albedo.x, albedo.y, albedo.z) : "null") + ",\n" +
                    "  roughness = " + roughness + ",\n" +
                    "  metalness = " + metalness + ",\n" +
                    "  emissive = " + (emissive != null ? String.format("[%.2f, %.2f, %.2f]", emissive.x, emissive.y, emissive.z) : "null") + ",\n" +
                    "  emissiveStrength = " + emissiveStrength + ",\n" +
                    "  opacity = " + opacity + ",\n" +
                    "  alphaMaskThreshold = " + alphaMaskThreshold + ",\n" +
                    "  alphaCutout = " + alphaCutout + "\n");

            for (MaterialTexture slot : MaterialTexture.values()) {
                string.append(" ").append(slot.name()).append(" = ").append(getTexture(slot)).append(",\n");
            }
            return string.append("}").toString();
        }


        public int hash(boolean includeValues) {
            int result = 1;
            if (includeValues) {
               result = 31 * result + (albedo != null ? albedo.hashCode() : 0);
               result = 31 * result + Float.floatToIntBits(roughness);
               result = 31 * result + Float.floatToIntBits(metalness);
               result = 31 * result + (emissive != null ? emissive.hashCode() : 0);
               result = 31 * result + Float.floatToIntBits(emissiveStrength);
               result = 31 * result + Float.floatToIntBits(opacity);
            }

            for (int texture : textures) {result = 31 * result + texture;}
            result = 31 * result + (enabled ? 1 : 0);
            result = 31 * result + (albedoTransparent ? 1:0);
            result = 31 * result + Float.floatToIntBits(alphaMaskThreshold);
            result = 31 * result + (alphaCutout ? 1 : 0);
            return result;
        }


    }


    public ModelComponent(float[] vertices, float[] normals, float[] uvs, int[] indices) {
        this(vertices, normals, uvs, indices, null, null);
        this.rigged = false;
    }

    public ModelComponent(float[] vertices, float[] normals, float[] uvs, int[] indices, int[] boneIds, float[] boneWeights) {
        this.indexCount = indices.length;
        this.vertices = vertices;
        this.uvs = uvs;
        this.normals = normals;
        this.indices = indices;
        this.boneIds = boneIds;
        this.boneWeights = boneWeights;
        if (boneIds != null) this.rigged = true;
    }

    public void cleanup() {
        glDeleteBuffers(vboVerts);
        glDeleteBuffers(vboNormals);
        glDeleteBuffers(vboUVs);
        glDeleteBuffers(ebo);
        if (boneIds != null) glDeleteBuffers(vboBoneIds);
        if (boneWeights != null) glDeleteBuffers(vboBoneWeights);
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

        if (boneIds != null && boneWeights != null) {
            vboBoneIds = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboBoneIds);
            glBufferData(GL_ARRAY_BUFFER, boneIds, GL_STATIC_DRAW);
            glVertexAttribIPointer(3, 4, GL_INT, 4 * Integer.BYTES, 0);
            glEnableVertexAttribArray(3);

            vboBoneWeights = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboBoneWeights);
            glBufferData(GL_ARRAY_BUFFER, boneWeights, GL_STATIC_DRAW);
            glVertexAttribPointer(4, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);
            glEnableVertexAttribArray(4);
        }

        createInstanceVBO();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        for (int i = 0; i < 4; i++) {
            int loc = 5 + i;
            glEnableVertexAttribArray(loc);
            glVertexAttribPointer(loc, 4, GL_FLOAT, false, INSTANCE_STRIDE, (long) i * 4 * Float.BYTES);
            glVertexAttribDivisor(loc, 1);
        }
        // from now on our location is at 9
        glEnableVertexAttribArray(9); // albedo vector
        glVertexAttribPointer(9, 3, GL_FLOAT, false, INSTANCE_STRIDE, 16L * Float.BYTES);
        glVertexAttribDivisor(9, 1);

        glEnableVertexAttribArray(10); // emissive vector
        glVertexAttribPointer(10, 3, GL_FLOAT, false, INSTANCE_STRIDE, 19L * Float.BYTES);
        glVertexAttribDivisor(10, 1);

        glEnableVertexAttribArray(11); // emissive strength, roughness, metalness, opacity are as a .xyzw *in order* of a vec4
        glVertexAttribPointer(11, 4, GL_FLOAT, false, INSTANCE_STRIDE, 22L * Float.BYTES);
        glVertexAttribDivisor(11, 1);

        glEnableVertexAttribArray(12); // alpha cutout boolean + threshold as a .xy of a vec2
        glVertexAttribPointer(12, 2, GL_FLOAT, false, INSTANCE_STRIDE, 26L * Float.BYTES);
        glVertexAttribDivisor(12, 1);

        glEnableVertexAttribArray(13);
        glVertexAttribIPointer(13, 1, GL_INT, INSTANCE_STRIDE, 28L * Float.BYTES);
        glVertexAttribDivisor(13, 1);

        glBindVertexArray(0);

        bounds.computeLocals(vertices);
    }

    private void createInstanceVBO() {
        if (instanceVbo != -1) return;
        instanceVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, new float[INSTANCE_FLOATS], GL_DYNAMIC_DRAW);
    }

    @Override
    protected void onObjectDetach() {cleanup();}

    @Override
    protected void onUpdate() {
        bounds.update(owner.transform.calculateWorldMatrix());
    }

    @Override
    public Component copy() {
        ModelComponent copy = new ModelComponent(vertices, normals, uvs, indices, boneIds, boneWeights);
        copy.material = material.copy();
        copy.name = name + "_copy";
        copy.drawMode = drawMode;
        return copy;
    }

    public void markHashDirty() {hashDirty = true;}

    @Override
    public int hashCode() {
        if (hashDirty) {
            int result = 1;
            result = 31 * result + Arrays.hashCode(vertices);
            result = 31 * result + Arrays.hashCode(normals);
            result = 31 * result + Arrays.hashCode(uvs);
            result = 31 * result + Arrays.hashCode(indices);
            result = 31 * result + (drawMode != null ? drawMode.hashCode() : 0);
            result = 31 * result + (castShadow ? 1 : 0);
            result = 31 * result + (renderMethod != null ? renderMethod.hashCode() : 0);
            hash = result;
            hashDirty = false;
        }
        return hash;
    }


    public void batch() {
        if (owner == null) {
            Logger.error("Can't batch an orphan component");
            return;
        }
        Renderer renderer = owner.getEngine().getRenderer();
        String nbatchID = hashCode() + ":" + material.hash(false);
        if (batchID.equals(nbatchID)) return;
        batchID = nbatchID;
        Renderer.Batch batch = renderer.getRenderBatch(batchID);
        if (batch != null) {batch.models.add(this); return;}
        batch = renderer.createRenderBatch(batchID);
        batch.models.add(this);
    }

    public void unbatch() {
        if (owner == null) {
            Logger.error("Can't unbatch an orphan component");
            return;
        }

        Renderer renderer = owner.getEngine().getRenderer();
        Renderer.Batch batch = renderer.getRenderBatch(batchID);
        if (batch != null) {
            batch.models.remove(this);
            if (batch.models.isEmpty()) renderer.removeRenderBatch(batchID);
        }
        batchID = "";
    }

    public static int getInstanceVbo() {return instanceVbo;}
}
