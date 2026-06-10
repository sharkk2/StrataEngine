package org.sharkk2.sengine.core.systems;

import org.lwjgl.BufferUtils;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.exceptions.AssetNotFoundException;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.sengine.core.classes.GameObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.stb.STBImage.*;


import org.joml.Quaternionf;

import javax.imageio.ImageIO;


public class AssetLoader {

    private final Engine engine;
    private final Map<String, Integer> textureCache = new HashMap<>();
    private final Map<String, CachedModel> modelCache = new HashMap<>();
    public Primatives primatives = new Primatives();

    public AssetLoader(Engine engine) {
        this.engine = engine;
    }

    private record CachedModel(CachedNode root, String directory, boolean flipUVs) {}

    private static class CachedNode {
        String name;
        Matrix4f localTransform = new Matrix4f();
        List<CachedMesh> meshes = new ArrayList<>();
        List<CachedNode> children = new ArrayList<>();
    }

    private static class CachedMesh {
        float[] vertices, normals, uvs;
        int[] indices;
        ModelComponent.Material material = new ModelComponent.Material();
    }

    public void loadModel(String path, String id) {
        if (modelCache.containsKey(id)) {
            Logger.warning("Asset (" + id + ") is already loaded");
            return;
        }

        int flags = aiProcess_Triangulate | aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices | aiProcess_OptimizeMeshes | aiProcess_CalcTangentSpace;
        AIScene aiScene = aiImportFile(path, flags);

        if (aiScene == null || (aiScene.mFlags() & AI_SCENE_FLAGS_INCOMPLETE) != 0 || aiScene.mRootNode() == null) {
            Logger.error("Failed to load '" + path + "': " + aiGetErrorString());
            return;
        }

        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String directory = (lastSlash >= 0) ? path.substring(0, lastSlash + 1) : "";

        int lastDot = path.lastIndexOf('.');
        String ext = (lastDot >= 0) ? path.substring(lastDot).toLowerCase() : "";
        boolean flipUVs = ext.equals(".gltf") || ext.equals(".glb");

        CachedModel cachedModel = new CachedModel(new CachedNode(), directory, flipUVs);
        processNode(aiScene.mRootNode(), aiScene, cachedModel.root, directory, flipUVs);

        aiReleaseImport(aiScene);
        modelCache.put(id, cachedModel);
    }


    public GameObject getModel(String id) {
        CachedModel cachedModel = modelCache.get(id);
        if (cachedModel == null) {
            Logger.error("Couldn't find an asset with ID (" + id + ")");
            throw new AssetNotFoundException("Couldn't find asset: " + id);
        }
        return buildGameObject(cachedModel.root);
    }

    private GameObject buildGameObject(CachedNode node) {
        GameObject go = new GameObject(engine);
        go.setName(node.name);

        Vector3f pos = new Vector3f();
        Quaternionf rot = new Quaternionf();
        Vector3f scale = new Vector3f();
        node.localTransform.getTranslation(pos);
        node.localTransform.getUnnormalizedRotation(rot);
        node.localTransform.getScale(scale);

        go.transform.setPosition(pos);
        go.transform.setScale(scale);

        Vector3f euler = new Vector3f();
        rot.getEulerAnglesXYZ(euler); // returns radians
        go.transform.transformRotation((float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z));
        if (node.meshes.size() == 1) {
            go.attachComponent(createModelComponent(node.meshes.get(0)));
        } else if (node.meshes.size() > 1) {
            for (int i = 0; i < node.meshes.size(); i++) {
                GameObject meshChild = new GameObject(engine);
                meshChild.setName(node.name + "_mesh_" + i);
                meshChild.attachComponent(createModelComponent(node.meshes.get(i)));
                go.addChild(meshChild);
            }
        }

        for (CachedNode childNode : node.children) {go.addChild(buildGameObject(childNode));}
        return go;
    }

    private ModelComponent createModelComponent(CachedMesh cachedData) {
        ModelComponent mc = new ModelComponent(cachedData.vertices, cachedData.normals, cachedData.uvs, cachedData.indices);
        mc.material = cachedData.material;
        return mc;
    }


    private void processNode(AINode aiNode, AIScene aiScene, CachedNode outNode, String directory, boolean flipUVs) {
        outNode.name = aiNode.mName().dataString();

        AIMatrix4x4 t = aiNode.mTransformation();
        outNode.localTransform.set(
                t.a1(), t.b1(), t.c1(), t.d1(),
                t.a2(), t.b2(), t.c2(), t.d2(),
                t.a3(), t.b3(), t.c3(), t.d3(),
                t.a4(), t.b4(), t.c4(), t.d4()
        );

        if (aiNode.mMeshes() != null) {
            IntBuffer meshIndices = aiNode.mMeshes();
            for (int i = 0; i < aiNode.mNumMeshes(); i++) {
                AIMesh aiMesh = AIMesh.create(aiScene.mMeshes().get(meshIndices.get(i)));
                outNode.meshes.add(processMesh(aiMesh, aiScene, directory, flipUVs));
            }
        }

        if (aiNode.mChildren() != null) {
            for (int i = 0; i < aiNode.mNumChildren(); i++) {
                CachedNode childNode = new CachedNode();
                outNode.children.add(childNode);
                processNode(AINode.create(aiNode.mChildren().get(i)), aiScene, childNode, directory, flipUVs);
            }
        }
    }

    private CachedMesh processMesh(AIMesh aiMesh, AIScene aiScene, String directory, boolean flipUVs) {
        CachedMesh mesh = new CachedMesh();
        int vertexCount = aiMesh.mNumVertices();

        mesh.vertices = new float[vertexCount * 3];
        mesh.normals = new float[vertexCount * 3];
        mesh.uvs = new float[vertexCount * 2];

        AIVector3D.Buffer posBuffer = aiMesh.mVertices();
        AIVector3D.Buffer normBuffer = aiMesh.mNormals();
        AIVector3D.Buffer uvBuffer = aiMesh.mTextureCoords(0);

        for (int i = 0; i < vertexCount; i++) {
            AIVector3D pos = posBuffer.get(i);
            mesh.vertices[i * 3] = pos.x();
            mesh.vertices[i * 3 + 1] = pos.y();
            mesh.vertices[i * 3 + 2] = pos.z();

            if (normBuffer != null) {
                AIVector3D norm = normBuffer.get(i);
                mesh.normals[i * 3] = norm.x();
                mesh.normals[i * 3 + 1] = norm.y();
                mesh.normals[i * 3 + 2] = norm.z();
            }

            if (uvBuffer != null) {
                AIVector3D uv = uvBuffer.get(i);
                mesh.uvs[i * 2] = uv.x();
                mesh.uvs[i * 2 + 1] = flipUVs ? 1.0f - uv.y() : uv.y();
            }
        }

        AIFace.Buffer faceBuffer = aiMesh.mFaces();
        mesh.indices = new int[aiMesh.mNumFaces() * 3];
        int idx = 0;
        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            AIFace face = faceBuffer.get(i);
            IntBuffer faceIndices = face.mIndices();
            for (int j = 0; j < face.mNumIndices(); j++) {
                mesh.indices[idx++] = faceIndices.get(j);
            }
        }

        int matIndex = aiMesh.mMaterialIndex();
        if (matIndex >= 0 && aiScene.mMaterials() != null) {
            AIMaterial aiMaterial = AIMaterial.create(aiScene.mMaterials().get(matIndex));

            mesh.material.albedoTex = loadMaterialTexture(aiMaterial, aiTextureType_BASE_COLOR, aiScene, directory);
            if (mesh.material.albedoTex == -1) mesh.material.albedoTex = loadMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, aiScene, directory);

            mesh.material.normalTex = loadMaterialTexture(aiMaterial, aiTextureType_NORMALS, aiScene, directory);
            if (mesh.material.normalTex == -1) mesh.material.normalTex = loadMaterialTexture(aiMaterial, aiTextureType_HEIGHT, aiScene, directory); // map_Bump fallback
            mesh.material.roughnessTex = loadMaterialTexture(aiMaterial, aiTextureType_DIFFUSE_ROUGHNESS, aiScene, directory);
            mesh.material.metalnessTex = loadMaterialTexture(aiMaterial, aiTextureType_METALNESS, aiScene, directory);
            if (mesh.material.metalnessTex == -1) mesh.material.metalnessTex = loadMaterialTexture(aiMaterial, aiTextureType_SPECULAR, aiScene, directory);
            mesh.material.aoTex = loadMaterialTexture(aiMaterial, aiTextureType_AMBIENT_OCCLUSION, aiScene, directory);
            if (mesh.material.aoTex == -1) {
                mesh.material.aoTex = loadMaterialTexture(aiMaterial, aiTextureType_LIGHTMAP, aiScene, directory);
                if (mesh.material.aoTex == -1) {
                    mesh.material.aoTex = loadMaterialTexture(aiMaterial, aiTextureType_AMBIENT, aiScene, directory);
                }
            }


            mesh.material.emissiveTex = loadMaterialTexture(aiMaterial, aiTextureType_EMISSIVE, aiScene, directory);
            mesh.material.opacityTex = loadMaterialTexture(aiMaterial, aiTextureType_OPACITY, aiScene, directory);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                AIColor4D color = AIColor4D.malloc(stack);
                if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                    mesh.material.albedo.set(color.r(), color.g(), color.b());
                }
                if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_EMISSIVE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                    mesh.material.emissive.set(color.r(), color.g(), color.b());
                }

                FloatBuffer floatOut = stack.mallocFloat(1);
                IntBuffer maxOut = stack.mallocInt(1);
                maxOut.put(0, 1);

                if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_EMISSIVE_INTENSITY, aiTextureType_NONE, 0, floatOut, maxOut) == aiReturn_SUCCESS) {
                    mesh.material.emissiveStrength = floatOut.get(0);
                }

                maxOut.put(0, 1);
                if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, floatOut, maxOut) == aiReturn_SUCCESS) {
                    mesh.material.opacity = floatOut.get(0);
                }
            }
        }

        return mesh;
    }

    private int loadMaterialTexture(AIMaterial material, int texType, AIScene aiScene, String directory) {
        if (aiGetMaterialTextureCount(material, texType) <= 0) return -1;

        AIString aiPath = AIString.calloc();
        int result = aiGetMaterialTexture(material, texType, 0, aiPath, (IntBuffer) null, null, null, null, null, null);
        if (result != aiReturn_SUCCESS) {
            aiPath.free();
            return -1;
        }

        String relativePath = aiPath.dataString();
        aiPath.free();

        if (relativePath.startsWith("*")) {
            int texIndex = Integer.parseInt(relativePath.substring(1));
            String cacheKey = aiScene.address() + "__embedded__" + texIndex;

            if (textureCache.containsKey(cacheKey)) return textureCache.get(cacheKey);

            int[] w = new int[1], h = new int[1];
            ByteBuffer pixels = extractEmbeddedPixels(aiScene, texIndex, w, h);
            if (pixels != null) {
                int glId = uploadPixels(pixels, w[0], h[0]);
                textureCache.put(cacheKey, glId);
                return glId;
            }
            return -1;
        }

        String fullPath = (directory + relativePath).replace('\\', '/');
        if (textureCache.containsKey(fullPath)) return textureCache.get(fullPath);

        int glId = loadTexture(fullPath);
        if (glId != -1) textureCache.put(fullPath, glId);

        return glId;
    }

    private ByteBuffer extractEmbeddedPixels(AIScene aiScene, int index, int[] w, int[] h) {
        AITexture aiTex = AITexture.create(aiScene.mTextures().get(index));
        int[] ch = new int[1];
        stbi_set_flip_vertically_on_load(false);

        if (aiTex.mHeight() == 0) {
            return stbi_load_from_memory(aiTex.pcDataCompressed(), w, h, ch, 4);
        } else {
            w[0] = aiTex.mWidth();
            h[0] = aiTex.mHeight();
            AITexel.Buffer texels = aiTex.pcData();
            ByteBuffer data = org.lwjgl.BufferUtils.createByteBuffer(w[0] * h[0] * 4);
            for (int i = 0; i < w[0] * h[0]; i++) {
                AITexel t = texels.get(i);
                data.put(t.r()).put(t.g()).put(t.b()).put(t.a());
            }
            data.flip();
            return data;
        }
    }

    private int uploadPixels(ByteBuffer data, int width, int height) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
        return id;
    }

    public int loadTexture(String path) {
        int[] width = new int[1], height = new int[1], channels = new int[1];
        stbi_set_flip_vertically_on_load(false);
        ByteBuffer image = stbi_load(path, width, height, channels, 4);
        if (image == null) {
            Logger.error("Could not load texture '" + path + "': " + stbi_failure_reason());
            return -1;
        }

        int id = uploadPixels(image, width[0], height[0]);
        stbi_image_free(image);
        return id;
    }

    public void cleanup() {
        for (int texID : textureCache.values()) {
            glDeleteTextures(texID);
        }
        textureCache.clear();
        modelCache.clear();
    }

    public int loadCubeMapTexture(String[] faces) {
        if (faces.length != 6)
            throw new IllegalArgumentException("Cubemap requires exactly 6 textures");

        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, texID);
        for (int i = 0; i < 6; i++) {
            try (InputStream in = AssetLoader.class.getClassLoader().getResourceAsStream(faces[i])) {
                if (in == null) throw new RuntimeException("Failed to load cubemap face: " + faces[i]);

                BufferedImage image = ImageIO.read(in);
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = new int[width * height];
                image.getRGB(0, 0, width, height, pixels, 0, width);
                ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = pixels[y * width + x];
                        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                        buffer.put((byte) (pixel & 0xFF));         // B
                        buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                    }
                }

                buffer.flip();
                glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load cubemap face: " + faces[i], e);
            }
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        return texID;
    }


    public class Primatives {
        public ModelComponent cube() {
            float[] vertices = {
                    -0.5f, -0.5f,  0.5f,
                    0.5f, -0.5f,  0.5f,
                    0.5f,  0.5f,  0.5f,
                    -0.5f,  0.5f,  0.5f,
                    -0.5f, -0.5f, -0.5f,
                    0.5f, -0.5f, -0.5f,
                    0.5f,  0.5f, -0.5f,
                    -0.5f,  0.5f, -0.5f,
                    -0.5f, -0.5f, -0.5f,
                    -0.5f, -0.5f,  0.5f,
                    -0.5f,  0.5f,  0.5f,
                    -0.5f,  0.5f, -0.5f,
                    0.5f, -0.5f,  0.5f,
                    0.5f, -0.5f, -0.5f,
                    0.5f,  0.5f, -0.5f,
                    0.5f,  0.5f,  0.5f,
                    -0.5f,  0.5f,  0.5f,
                    0.5f,  0.5f,  0.5f,
                    0.5f,  0.5f, -0.5f,
                    -0.5f,  0.5f, -0.5f,
                    -0.5f, -0.5f, -0.5f,
                    0.5f, -0.5f, -0.5f,
                    0.5f, -0.5f,  0.5f,
                    -0.5f, -0.5f,  0.5f,
            };

            float[] normals = {
                    0, 0, 1,  0, 0, 1,  0, 0, 1,  0, 0, 1,
                    0, 0, -1,  0, 0, -1,  0, 0, -1,  0, 0, -1,
                    -1, 0, 0,  -1, 0, 0,  -1, 0, 0,  -1, 0, 0,
                    1, 0, 0,  1, 0, 0,  1, 0, 0,  1, 0, 0,
                    0, 1, 0,  0, 1, 0,  0, 1, 0,  0, 1, 0,
                    0, -1, 0,  0, -1, 0,  0, -1, 0,  0, -1, 0,
            };

            float[] uvs = {
                    0, 0,  1, 0,  1, 1,  0, 1,
                    1, 0,  0, 0,  0, 1,  1, 1,
                    0, 0,  1, 0,  1, 1,  0, 1,
                    0, 0,  1, 0,  1, 1,  0, 1,
                    0, 0,  1, 0,  1, 1,  0, 1,
                    0, 1,  1, 1,  1, 0,  0, 0,
            };

            int[] indices = {
                    0, 1, 2, 2, 3, 0,       // Front
                    4, 5, 6, 6, 7, 4,        // Back
                    8, 9, 10, 10, 11, 8,     // Left
                    12, 13, 14, 14, 15, 12,  // Right
                    16, 17, 18, 18, 19, 16,  // Top
                    20, 21, 22, 22, 23, 20,  // Bottom
            };

            return new ModelComponent(vertices, normals, uvs, indices);
        }

        public ModelComponent triangle() {
            float[] vertices = {-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0, 0.5f, 0,};

            float[] normals = {
                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,
            };

            float[] uvs = {0, 0, 1, 0, 0.5f, 1,};
            int[] indices = {0, 1, 2};
            return new ModelComponent(vertices, normals, uvs, indices);
        }

        public ModelComponent quad() {
            float[] vertices = {-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0};

            float[] normals = {
                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,
            };

            float[] uvs = {0, 0, 1, 0, 1, 1, 0, 1,};
            int[] indices = { 0, 1, 2,  2, 3, 0 };
            return new ModelComponent(vertices, normals, uvs, indices);
        }

        public ModelComponent sphere(int stacks, int slices) {
            int vCount = (stacks + 1) * (slices + 1);
            float[] vertices = new float[vCount * 3];
            float[] normals = new float[vCount * 3];
            float[] uvs = new float[vCount * 2];
            int[] indices = new int[stacks * slices * 6];
            for (int stack = 0; stack <= stacks; stack++) {
                float phi = (float) (Math.PI * stack / stacks);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);
                for (int slice = 0; slice <= slices; slice++) {
                    float theta = (float) (2 * Math.PI * slice / slices);
                    float sinTheta = (float) Math.sin(theta);
                    float cosTheta = (float) Math.cos(theta);

                    int i = stack * (slices + 1) + slice;
                    float nx = cosTheta * sinPhi;
                    float ny = cosPhi;
                    float nz = sinTheta * sinPhi;

                    vertices[i * 3] = nx * 0.5f;
                    vertices[i * 3 + 1] = ny * 0.5f;
                    vertices[i * 3 + 2] = nz * 0.5f;

                    normals[i * 3] = nx;
                    normals[i * 3 + 1] = ny;
                    normals[i * 3 + 2] = nz;

                    uvs[i * 2] = (float) slice / slices;
                    uvs[i * 2 + 1] = (float) stack / stacks;
                }
            }

            int idx = 0;
            for (int stack = 0; stack < stacks; stack++) {
                for (int slice = 0; slice < slices; slice++) {
                    int tl = stack * (slices + 1) + slice;
                    int tr = tl + 1;
                    int bl = tl + (slices + 1);
                    int br = bl + 1;

                    indices[idx++] = tl;
                    indices[idx++] = bl;
                    indices[idx++] = tr;
                    indices[idx++] = tr;
                    indices[idx++] = bl;
                    indices[idx++] = br;
                }
            }

            return new ModelComponent(vertices, normals, uvs, indices);
        }

        public ModelComponent cylinder(int slices, boolean capped) {
            int ringVerts = slices + 1;
            int sideVerts = ringVerts * 2;
            int capVerts = capped ? (slices + 1) * 2 : 0;
            float[] vertices = new float[(sideVerts + capVerts) * 3];
            float[] normals = new float[(sideVerts + capVerts) * 3];
            float[] uvs = new float[(sideVerts + capVerts) * 2];

            // Side vertices — bottom ring then top ring
            for (int i = 0; i <= slices; i++) {
                float theta = (float) (2 * Math.PI * i / slices);
                float x = (float) Math.cos(theta) * 0.5f;
                float z = (float) Math.sin(theta) * 0.5f;
                float nx = (float) Math.cos(theta);
                float nz = (float) Math.sin(theta);
                float u = (float) i / slices;

                int bot = i;
                int top = i + ringVerts;

                vertices[bot * 3] = x;  vertices[bot * 3 + 1] = -0.5f;  vertices[bot * 3 + 2] = z;
                normals[bot * 3] = nx;  normals[bot * 3 + 1] = 0;        normals[bot * 3 + 2] = nz;
                uvs[bot * 2] = u;       uvs[bot * 2 + 1] = 0;

                vertices[top * 3] = x;  vertices[top * 3 + 1] = 0.5f;  vertices[top * 3 + 2] = z;
                normals[top * 3] = nx;  normals[top * 3 + 1] = 0;       normals[top * 3 + 2] = nz;
                uvs[top * 2] = u;       uvs[top * 2 + 1] = 1;
            }

            int sideIndexCount = slices * 6;
            int capIndexCount = capped ? slices * 3 * 2 : 0;
            int[] indices = new int[sideIndexCount + capIndexCount];
            int idx = 0;

            for (int i = 0; i < slices; i++) {
                int bot = i;
                int top = i + ringVerts;

                indices[idx++] = bot;
                indices[idx++] = bot + 1;
                indices[idx++] = top;
                indices[idx++] = top;
                indices[idx++] = bot + 1;
                indices[idx++] = top + 1;
            }

            if (capped) {
                int base = sideVerts;

                for (int cap = 0; cap < 2; cap++) {
                    float y = cap == 0 ? -0.5f : 0.5f;
                    float ny = cap == 0 ? -1 : 1;
                    int centerIdx = base + cap * (slices + 1);
                    int fanBase = centerIdx + 1;

                    // Center vertex
                    vertices[centerIdx * 3] = 0;   vertices[centerIdx * 3 + 1] = y;  vertices[centerIdx * 3 + 2] = 0;
                    normals[centerIdx * 3] = 0;    normals[centerIdx * 3 + 1] = ny; normals[centerIdx * 3 + 2] = 0;
                    uvs[centerIdx * 2] = 0.5f;     uvs[centerIdx * 2 + 1] = 0.5f;

                    for (int i = 0; i < slices; i++) {
                        float theta = (float) (2 * Math.PI * i / slices);
                        int vi = fanBase + i;

                        vertices[vi * 3] = (float) Math.cos(theta) * 0.5f;
                        vertices[vi * 3 + 1] = y;
                        vertices[vi * 3 + 2] = (float) Math.sin(theta) * 0.5f;

                        normals[vi * 3] = 0;
                        normals[vi * 3 + 1] = ny;
                        normals[vi * 3 + 2] = 0;

                        uvs[vi * 2] = (float) Math.cos(theta) * 0.5f + 0.5f;
                        uvs[vi * 2 + 1] = (float) Math.sin(theta) * 0.5f + 0.5f;

                        int next = fanBase + (i + 1) % slices;
                        if (cap == 0) {
                            indices[idx++] = centerIdx;
                            indices[idx++] = next;
                            indices[idx++] = vi;
                        } else {
                            indices[idx++] = centerIdx;
                            indices[idx++] = vi;
                            indices[idx++] = next;
                        }
                    }
                }
            }

            return new ModelComponent(vertices, normals, uvs, indices);
        }
    }
}
