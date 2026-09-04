package org.sharkk2.sengine.core.systems;

import org.luaj.vm2.LuaError;
import org.lwjgl.BufferUtils;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.classes.LuaScript;
import org.sharkk2.sengine.core.classes.animation.Animation;
import org.sharkk2.sengine.core.classes.animation.Joint;
import org.sharkk2.sengine.core.classes.animation.JointTransform;
import org.sharkk2.sengine.core.classes.animation.Keyframe;
import org.sharkk2.sengine.core.classes.exceptions.AssetNotFoundException;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.sengine.core.classes.GameObject;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.stb.STBImage.*;


import org.joml.Quaternionf;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;


public class AssetLoader {

    private final Engine engine;
    private final Map<String, Integer> textureCache = new HashMap<>();
    private final Map<String, CachedModel> modelCache = new HashMap<>();
    private final Map<String, LuaScript> luaCache = new HashMap<>();
    public static final int TEXTURE_FLIPPED = 1;
    public static final int TEXTURE_BLENDED = 1 << 1;
    public static final int TEXTURE_REPEATED = 1 << 2;
    public static final int TEXTURE_NONE = 0;

    public record AudioData(byte[] pcmData, int channels, int bitsPerSample, int sampleRate, boolean bigEndian) {}
    private final Map<String, AudioData> audioCache = new HashMap<>();
    public Primitives primitives = new Primitives();

    public AssetLoader(Engine engine) {
        this.engine = engine;
    }

    private static class CachedMesh {
        float[] vertices, normals, uvs;
        int[] indices;
        int[] boneIds;
        float[] boneWeights;
        ModelComponent.Material material = new ModelComponent.Material();
    }

    private record CachedModel(CachedNode root, String directory, boolean flipUVs, Joint skeleton, List<Animation> animations) {}

    private static class CachedNode {
        String name;
        Matrix4f localTransform = new Matrix4f();
        List<CachedMesh> meshes = new ArrayList<>();
        List<CachedNode> children = new ArrayList<>();
    }


    public void loadModel(String path, String id) {
        long time = System.currentTimeMillis();
        if (modelCache.containsKey(id)) {
            Logger.warning("Asset (" + id + ") is already loaded");
            return;
        }

        int flags = aiProcess_Triangulate | aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices | aiProcess_OptimizeMeshes | aiProcess_CalcTangentSpace | aiProcess_LimitBoneWeights;
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

        boolean hasSkeleton = aiScene.mNumAnimations() > 0;
        if (!hasSkeleton && aiScene.mMeshes() != null) {
            for (int i = 0; i < aiScene.mNumMeshes(); i++) {
                if (AIMesh.create(aiScene.mMeshes().get(i)).mNumBones() > 0) {
                    hasSkeleton = true;
                    break;
                }
            }
        }

        Joint skeleton = null;
        Map<String, Joint> jointsByName = null;
        List<Animation> animations = new ArrayList<>();
        if (hasSkeleton) {
            jointsByName = new HashMap<>();
            skeleton = buildJointHierarchy(aiScene.mRootNode(), jointsByName);
            animations = extractAnimations(aiScene, jointsByName, skeleton);
        }

        CachedModel cachedModel = new CachedModel(new CachedNode(), directory, flipUVs, skeleton, animations);
        processNode(aiScene.mRootNode(), aiScene, cachedModel.root(), directory, flipUVs, id, jointsByName);

        aiReleaseImport(aiScene);
        modelCache.put(id, cachedModel);
        Logger.info("Loaded model asset '" + id + "' from: [" + path + "] (took " + Math.round(System.currentTimeMillis() - time) + "ms)");

    }


    public GameObject getModel(String id) {
        CachedModel cachedModel = modelCache.get(id);
        if (cachedModel == null) {
            Logger.error("Couldn't find an asset with ID (" + id + ")");
            throw new AssetNotFoundException("Couldn't find asset: " + id);
        }
        return engine.getThreadService().runMainThreadBlocking(() -> buildGameObject(cachedModel.root));
    }

    private Joint buildJointHierarchy(AINode aiNode, Map<String, Joint> jointsByName) {
        Matrix4f localTransform = convert(aiNode.mTransformation());
        Joint joint = new Joint(jointsByName.size(), aiNode.mName().dataString(), localTransform);
        jointsByName.put(joint.name, joint);

        if (aiNode.mChildren() != null) {
            for (int i = 0; i < aiNode.mNumChildren(); i++) {
                joint.children.add(buildJointHierarchy(AINode.create(aiNode.mChildren().get(i)), jointsByName));
            }
        }
        return joint;
    }

    private Matrix4f convert(AIMatrix4x4 t) {
        return new Matrix4f(
                t.a1(), t.b1(), t.c1(), t.d1(),
                t.a2(), t.b2(), t.c2(), t.d2(),
                t.a3(), t.b3(), t.c3(), t.d3(),
                t.a4(), t.b4(), t.c4(), t.d4()
        );
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
        go.transform.scale(scale);
        go.transform.rotate(rot);
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

        for (CachedNode childNode : node.children) {
            go.addChild(
                engine.getThreadService().runMainThreadBlocking(()->buildGameObject(childNode))
            );
        }
        return go;
    }

    private ModelComponent createModelComponent(CachedMesh cachedData) {
        ModelComponent mc = new ModelComponent(cachedData.vertices, cachedData.normals, cachedData.uvs, cachedData.indices, cachedData.boneIds, cachedData.boneWeights);
        mc.material = cachedData.material;
        return mc;
    }

    public Joint getSkeleton(String id) {
        CachedModel cachedModel = modelCache.get(id);
        return cachedModel != null ? cachedModel.skeleton() : null;
    }

    public List<Animation> getAnimations(String id) {
        CachedModel cachedModel = modelCache.get(id);
        return cachedModel != null ? cachedModel.animations() : Collections.emptyList();
    }

    private void processNode(AINode aiNode, AIScene aiScene, CachedNode outNode, String directory, boolean flipUVs, String id, Map<String, Joint> joints) {
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
                outNode.meshes.add(processMesh(aiMesh, aiScene, directory, flipUVs, id, joints));
            }
        }

        if (aiNode.mChildren() != null) {
            for (int i = 0; i < aiNode.mNumChildren(); i++) {
                CachedNode childNode = new CachedNode();
                outNode.children.add(childNode);
                processNode(AINode.create(aiNode.mChildren().get(i)), aiScene, childNode, directory, flipUVs, id, joints);
            }
        }
    }

    private CachedMesh processMesh(AIMesh aiMesh, AIScene aiScene, String directory, boolean flipUVs, String id, Map<String, Joint> joints) {
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

        if (joints != null && aiMesh.mNumBones() > 0) {
            // so what we're tryna do here is take assimp's bone list and its weights and put them
            // in the vertex data, so from each vertex we can tell what bones affect it and by how much
            // asssimp just gives u a list of bones with each bone having a list of vertices it affects "and the weights" and we're trying
            // to inverse that
            mesh.boneIds = new int[vertexCount * ModelComponent.MAX_BONE_INFLUENCE];
            mesh.boneWeights = new float[vertexCount * ModelComponent.MAX_BONE_INFLUENCE];

            for (int b = 0; b < aiMesh.mNumBones(); b++) {
                AIBone aiBone = AIBone.create(aiMesh.mBones().get(b));
                Joint joint = joints.get(aiBone.mName().dataString());
                if (joint == null) continue; // shouldn't happen
                joint.inverseBindTransform = convert(aiBone.mOffsetMatrix());

                AIVertexWeight.Buffer weights = aiBone.mWeights();
                for (int w = 0; w < aiBone.mNumWeights(); w++) {
                    AIVertexWeight vw = weights.get(w);
                    int vId = vw.mVertexId();
                    float weight = vw.mWeight();
                    // find an untaken slot for the bone and its weight
                    for (int slot = 0; slot < ModelComponent.MAX_BONE_INFLUENCE; slot++) {
                        if (mesh.boneWeights[vId * ModelComponent.MAX_BONE_INFLUENCE + slot] == 0f) {
                            mesh.boneIds[vId * ModelComponent.MAX_BONE_INFLUENCE + slot] = joint.id;
                            mesh.boneWeights[vId * ModelComponent.MAX_BONE_INFLUENCE + slot] = weight;
                            break;
                        }
                    }
                }
            }

            // if a vertex was originally influenced by more than MAX_BONE_INFLUENCE then the total weights might not be a solid 1.0
            // so we js go through them, divide each weight by their sum so they can all add up to 1
            for (int v = 0; v < vertexCount; v++) {
                float sum = 0f;
                for (int slot = 0; slot < ModelComponent.MAX_BONE_INFLUENCE; slot++)
                    sum += mesh.boneWeights[v * ModelComponent.MAX_BONE_INFLUENCE + slot];
                if (sum > 0f) {
                    for (int slot = 0; slot < ModelComponent.MAX_BONE_INFLUENCE; slot++)
                        mesh.boneWeights[v * ModelComponent.MAX_BONE_INFLUENCE + slot] /= sum;
                }
            }
        }

        int matIndex = aiMesh.mMaterialIndex();
        if (matIndex >= 0 && aiScene.mMaterials() != null) {
            AIMaterial aiMaterial = AIMaterial.create(aiScene.mMaterials().get(matIndex));

            mesh.material.albedoTex = loadMaterialTexture(aiMaterial, aiTextureType_BASE_COLOR, aiScene, directory, id);
            if (mesh.material.albedoTex == -1) mesh.material.albedoTex = loadMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, aiScene, directory, id);

            mesh.material.normalTex = loadMaterialTexture(aiMaterial, aiTextureType_NORMALS, aiScene, directory, id);
            if (mesh.material.normalTex == -1) mesh.material.normalTex = loadMaterialTexture(aiMaterial, aiTextureType_HEIGHT, aiScene, directory, id); // map_Bump fallback
            mesh.material.roughnessTex = loadMaterialTexture(aiMaterial, aiTextureType_DIFFUSE_ROUGHNESS, aiScene, directory, id);
            mesh.material.metalnessTex = loadMaterialTexture(aiMaterial, aiTextureType_METALNESS, aiScene, directory, id);
            if (mesh.material.metalnessTex == -1) mesh.material.metalnessTex = loadMaterialTexture(aiMaterial, aiTextureType_SPECULAR, aiScene, directory, id);
            mesh.material.aoTex = loadMaterialTexture(aiMaterial, aiTextureType_AMBIENT_OCCLUSION, aiScene, directory, id);
            if (mesh.material.aoTex == -1) {
                mesh.material.aoTex = loadMaterialTexture(aiMaterial, aiTextureType_LIGHTMAP, aiScene, directory, id);
                if (mesh.material.aoTex == -1) {
                    mesh.material.aoTex = loadMaterialTexture(aiMaterial, aiTextureType_AMBIENT, aiScene, directory, id);
                }
            }


            mesh.material.emissiveTex = loadMaterialTexture(aiMaterial, aiTextureType_EMISSIVE, aiScene, directory, id);
            mesh.material.opacityTex = loadMaterialTexture(aiMaterial, aiTextureType_OPACITY, aiScene, directory, id);

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


                AIString alphaModeStr = AIString.calloc();
                if (aiGetMaterialString(aiMaterial, "$mat.gltf.alphaMode", aiTextureType_NONE, 0, alphaModeStr) == aiReturn_SUCCESS
                        && "MASK".equals(alphaModeStr.dataString())) {
                    mesh.material.alphaCutout = true;
                    maxOut.put(0, 1);
                    if (aiGetMaterialFloatArray(aiMaterial, "$mat.gltf.alphaCutoff", aiTextureType_NONE, 0, floatOut, maxOut) == aiReturn_SUCCESS) {
                        mesh.material.alphaMaskThreshold = floatOut.get(0);
                    }
                }

                alphaModeStr.free();
                if (mesh.material.alphaCutout && mesh.material.opacityTex != -1) {
                    mesh.material.alphaMaskTex = mesh.material.opacityTex;
                    mesh.material.opacityTex = -1;
                }
            }
        }

        return mesh;
    }

    private int loadMaterialTexture(AIMaterial material, int texType, AIScene aiScene, String directory, String modelid) {
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
            String cacheKey = modelid + "__embedded__" + texIndex;

            if (textureCache.containsKey(cacheKey)) return textureCache.get(cacheKey);

            int[] w = new int[1], h = new int[1];
            ByteBuffer pixels = extractEmbeddedPixels(aiScene, texIndex, w, h);
            if (pixels != null) {
                int glId = engine.getThreadService().runMainThreadBlocking(() -> uploadPixels(pixels, w[0], h[0], TEXTURE_BLENDED | TEXTURE_REPEATED | TEXTURE_FLIPPED));
                textureCache.put(cacheKey, glId);
                return glId;
            }
            return -1;
        }

        String fullPath = (directory + relativePath).replace('\\', '/');
        if (textureCache.containsKey(fullPath)) return textureCache.get(fullPath);

        int glId = engine.getThreadService().runMainThreadBlocking(() -> loadTexture(fullPath, TEXTURE_BLENDED | TEXTURE_REPEATED| TEXTURE_FLIPPED));
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

    private List<Animation> extractAnimations(AIScene aiScene, Map<String, Joint> jointsByName, Joint skeleton) {
        List<Animation> animations = new ArrayList<>();
        if (aiScene.mAnimations() == null) return animations;

        for (int a = 0; a < aiScene.mNumAnimations(); a++) {
            AIAnimation aiAnim = AIAnimation.create(aiScene.mAnimations().get(a));
            double ticksPerSecond = aiAnim.mTicksPerSecond() != 0 ? aiAnim.mTicksPerSecond() : 25.0;

            TreeSet<Double> times = new TreeSet<>();
            for (int c = 0; c < aiAnim.mNumChannels(); c++) {
                AINodeAnim channel = AINodeAnim.create(aiAnim.mChannels().get(c));
                for (int k = 0; k < channel.mNumPositionKeys(); k++) times.add(channel.mPositionKeys().get(k).mTime());
                for (int k = 0; k < channel.mNumRotationKeys(); k++) times.add(channel.mRotationKeys().get(k).mTime());
            }

            List<Keyframe> keyframes = new ArrayList<>();
            for (double tick : times) {
                JointTransform[] transforms = new JointTransform[jointsByName.size()];
                for (Joint j : jointsByName.values()) transforms[j.id] = bindPoseTransform(j);

                for (int c = 0; c < aiAnim.mNumChannels(); c++) {
                    AINodeAnim channel = AINodeAnim.create(aiAnim.mChannels().get(c));
                    Joint joint = jointsByName.get(channel.mNodeName().dataString());
                    if (joint == null) continue;
                    transforms[joint.id] = sampleChannel(channel, tick);
                }
                keyframes.add(new Keyframe((float) (tick / ticksPerSecond), Arrays.asList(transforms)));
            }

            float duration = (float) (aiAnim.mDuration() / ticksPerSecond);
            animations.add(new Animation(aiAnim.mName().dataString(), duration, keyframes, skeleton));
        }
        return animations;
    }

    private JointTransform bindPoseTransform(Joint joint) {
        Vector3f pos = new Vector3f();
        Quaternionf rot = new Quaternionf();
        joint.localBindTransform.getTranslation(pos);
        joint.localBindTransform.getUnnormalizedRotation(rot);
        return new JointTransform(pos, rot);
    }

    private JointTransform sampleChannel(AINodeAnim channel, double tick) {
        return new JointTransform(samplePosition(channel, tick), sampleRotation(channel, tick));
    }

    private Vector3f samplePosition(AINodeAnim channel, double tick) {
        int count = channel.mNumPositionKeys();
        if (count == 0) return new Vector3f();
        AIVectorKey.Buffer keys = channel.mPositionKeys();
        if (count == 1 || tick <= keys.get(0).mTime()) return toVec3(keys.get(0).mValue());
        if (tick >= keys.get(count - 1).mTime()) return toVec3(keys.get(count - 1).mValue());

        for (int i = 0; i < count - 1; i++) {
            AIVectorKey a = keys.get(i), b = keys.get(i + 1);
            if (tick >= a.mTime() && tick <= b.mTime()) {
                float t = (float) ((tick - a.mTime()) / (b.mTime() - a.mTime()));
                return toVec3(a.mValue()).lerp(toVec3(b.mValue()), t);
            }
        }
        return toVec3(keys.get(count - 1).mValue());
    }

    private Quaternionf sampleRotation(AINodeAnim channel, double tick) {
        int count = channel.mNumRotationKeys();
        if (count == 0) return new Quaternionf();
        AIQuatKey.Buffer keys = channel.mRotationKeys();
        if (count == 1 || tick <= keys.get(0).mTime()) return toQuat(keys.get(0).mValue());
        if (tick >= keys.get(count - 1).mTime()) return toQuat(keys.get(count - 1).mValue());

        for (int i = 0; i < count - 1; i++) {
            AIQuatKey a = keys.get(i), b = keys.get(i + 1);
            if (tick >= a.mTime() && tick <= b.mTime()) {
                float t = (float) ((tick - a.mTime()) / (b.mTime() - a.mTime()));
                return toQuat(a.mValue()).slerp(toQuat(b.mValue()), t);
            }
        }
        return toQuat(keys.get(count - 1).mValue());
    }

    private Vector3f toVec3(AIVector3D v) { return new Vector3f(v.x(), v.y(), v.z()); }
    private Quaternionf toQuat(AIQuaternion q) { return new Quaternionf(q.x(), q.y(), q.z(), q.w()); }

    private int uploadPixels(ByteBuffer data, int width, int height, int flags) {
        boolean blended = (flags & TEXTURE_BLENDED) != 0;
        boolean repeated = (flags & TEXTURE_REPEATED) != 0;

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glGenerateMipmap(GL_TEXTURE_2D);

        int wrap = repeated ? GL_REPEAT : GL_CLAMP_TO_EDGE;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);

        if (blended) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }

        glBindTexture(GL_TEXTURE_2D, 0);
        return id;
    }

    public int loadTexture(String path, int flags) {
        boolean flipped = (flags & TEXTURE_FLIPPED) != 0;

        int[] width = new int[1], height = new int[1], channels = new int[1];
        stbi_set_flip_vertically_on_load(!flipped);
        ByteBuffer image = stbi_load(path, width, height, channels, 4);
        if (image == null) {
            Logger.error("Could not load texture '" + path + "': " + stbi_failure_reason());
            return -1;
        }

        int id = uploadPixels(image, width[0], height[0], flags);
        stbi_image_free(image);
        return id;
    }

    public int loadEmptyTexture(int width, int height) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return id;
    }

    public int loadEmptyTexture3D(int width, int height, int depth) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, id);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB16F, width, height, depth, 0, GL_RGB, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_3D, 0);
        return id;
    }

    public int loadEmptyDepthCubeTexture(int width, int height) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, id);
        for (int face = 0; face < 6; face++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, 0, GL_DEPTH_COMPONENT, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        return id;
    }

    public int loadEmptyDepthTexture(int width, int height) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);

        glBindTexture(GL_TEXTURE_2D, 0);
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
                Logger.error("Oops! failed to load cube map texture ):");
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

    // todo: cache this maybe??
    public int loadLutTexture(String cubeTexPath) {
        List<Float> values = new ArrayList<>();
        int size = 0;
        try {
            for (String line : Files.readAllLines(Paths.get(cubeTexPath))) {
                line = line.trim();

                if (line.startsWith("LUT_3D_SIZE")) {
                    size = Integer.parseInt(line.split(" ")[1]);
                } else if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
                    String[] p = line.split("\\s+");
                    values.add(Float.parseFloat(p[0]));
                    values.add(Float.parseFloat(p[1]));
                    values.add(Float.parseFloat(p[2]));
                }
            }
        } catch (IOException e) {
            Logger.error("Oops! failed to load LUT texture ):");

            System.err.println("Failed to load LUT texture: " + e.getMessage());
            return -1;
        }

        FloatBuffer buffer = BufferUtils.createFloatBuffer(values.size()); // less headache
        for (float f : values) {buffer.put(f);}
        buffer.flip();

        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, texID);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB16F, size, size, size, 0, GL_RGB, GL_FLOAT, buffer);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        return texID;
    }

    public AudioData loadAudioFile(String path) {
        if (audioCache.containsKey(path)) return audioCache.get(path);
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(new File(path))) {
            AudioFormat format = stream.getFormat();
            byte[] audioBytes = stream.readAllBytes();
            AudioData dat = new AudioData(
                    audioBytes,
                    format.getChannels(),
                    format.getSampleSizeInBits(),
                    (int) format.getSampleRate(),
                    format.isBigEndian()
            );
            audioCache.put(path, dat);
            return dat;

        } catch (UnsupportedAudioFileException | IOException e) {
            Logger.error("Failed to load audio file: " + path, e);
            return null;
        }
    }

    public float[] tileUVs(float[] baseUVs, float width, float height, float textureWorldSize) {
        float uScale = width / textureWorldSize;
        float vScale = height / textureWorldSize;
        float[] scaled = new float[baseUVs.length];
        for (int i = 0; i < baseUVs.length; i += 2) {
            scaled[i] = baseUVs[i] * uScale;
            scaled[i + 1] = baseUVs[i + 1] * vScale;
        }
        return scaled;
    }

    public float[] tileCubeUVs(float[] baseUVs, float width, float height, float depth, float textureWorldSize) {
        float[] scaled = new float[baseUVs.length];

        float widthScale = width / textureWorldSize;
        float heightScale = height / textureWorldSize;
        float depthScale = depth / textureWorldSize;

        float[][] faceScales = {
                {widthScale, heightScale}, // Front
                {widthScale, heightScale}, // Back
                {depthScale, heightScale}, // Left
                {depthScale, heightScale}, // Right
                {widthScale, depthScale},  // Top
                {widthScale, depthScale},  // Bottom
        };

        for (int face = 0; face < 6; face++) {
            float uScale = faceScales[face][0];
            float vScale = faceScales[face][1];

            for (int vert = 0; vert < 4; vert++) {
                int i = (face * 4 + vert) * 2;
                scaled[i] = baseUVs[i] * uScale;
                scaled[i + 1] = baseUVs[i + 1] * vScale;
            }
        }

        return scaled;
    }

    /**Used when a lua script with the given name was already loaded and cached*/
    public LuaScript loadLuaScript(String name) {
        if (luaCache.containsKey(name)) return luaCache.get(name);
        return null;
    }

    public LuaScript loadLuaScript(String path, String name, boolean nocache) {
        if (luaCache.containsKey(name) && !nocache) return luaCache.get(name);
        Path p = Path.of(path);
        if (!p.getFileName().toString().endsWith(".lua")) {
            Logger.error("Invalid Lua file: " + path);
            return null;
        }

        try {
            String source = Files.readString(Path.of(path));
            if (!Helpers.validateLua(source)) {
                Logger.error("Failed to load Lua script (" + path + "): failed to compile");
                return null;
            }
            LuaScript script = new LuaScript(source, name, path);
            if (!nocache) luaCache.put(name, script);
            return script;
        }
        catch (IOException | LuaError e) {
            Logger.error("Failed to load Lua script (" + path + "): " + e.getMessage());
            return null;
        }
    }

    public LuaScript loadLuaScript(String path, String name) {
        return loadLuaScript(path, name, false);
    }




    public class Primitives {
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
                    0, 0, 1,   0, 0, 1,   0, 0, 1,   0, 0, 1,
                    0, 0, -1,  0, 0, -1,  0, 0, -1,  0, 0, -1,
                    -1, 0, 0,  -1, 0, 0,  -1, 0, 0,  -1, 0, 0,
                    1, 0, 0,   1, 0, 0,   1, 0, 0,   1, 0, 0,
                    0, 1, 0,   0, 1, 0,   0, 1, 0,   0, 1, 0,
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
                    0, 1, 2,    2, 3, 0,      // Front
                    4, 5, 6,    6, 7, 4,      // Back
                    8, 9, 10,   10, 11, 8,    // Left
                    12, 13, 14, 14, 15, 12,   // Right
                    16, 17, 18, 18, 19, 16,   // Top
                    20, 21, 22, 22, 23, 20,   // Bottom
            };

            return new ModelComponent(vertices, normals, uvs, indices);
        }

        public ModelComponent triangle() {
            float[] vertices = {-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0, 0.5f, 0};

            float[] normals = {
                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,
            };

            float[] uvs = {0, 0, 1, 0, 0.5f, 1};
            int[] indices = {0, 1, 2};
            return new ModelComponent(vertices, normals, uvs, indices);
        }

        public ModelComponent plane() {
            float[] vertices = {-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0};

            float[] normals = {
                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,
            };

            float[] uvs = {0, 0, 1, 0, 1, 1, 0, 1};
            int[] indices = {0, 1, 2, 2, 3, 0};
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

                vertices[bot * 3] = x;
                vertices[bot * 3 + 1] = -0.5f;
                vertices[bot * 3 + 2] = z;
                normals[bot * 3] = nx;
                normals[bot * 3 + 1] = 0;
                normals[bot * 3 + 2] = nz;
                uvs[bot * 2] = u;
                uvs[bot * 2 + 1] = 0;

                vertices[top * 3] = x;
                vertices[top * 3 + 1] = 0.5f;
                vertices[top * 3 + 2] = z;
                normals[top * 3] = nx;
                normals[top * 3 + 1] = 0;
                normals[top * 3 + 2] = nz;
                uvs[top * 2] = u;
                uvs[top * 2 + 1] = 1;
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

                    vertices[centerIdx * 3] = 0;
                    vertices[centerIdx * 3 + 1] = y;
                    vertices[centerIdx * 3 + 2] = 0;
                    normals[centerIdx * 3] = 0;
                    normals[centerIdx * 3 + 1] = ny;
                    normals[centerIdx * 3 + 2] = 0;
                    uvs[centerIdx * 2] = 0.5f;
                    uvs[centerIdx * 2 + 1] = 0.5f;

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

        public ModelComponent wireframeBox() {
            float[] vertices = {
                    -0.5f, -0.5f, -0.5f, // 0
                    0.5f, -0.5f, -0.5f, // 1
                    0.5f,  0.5f, -0.5f, // 2
                    -0.5f,  0.5f, -0.5f, // 3
                    -0.5f, -0.5f,  0.5f, // 4
                    0.5f, -0.5f,  0.5f, // 5
                    0.5f,  0.5f,  0.5f, // 6
                    -0.5f,  0.5f,  0.5f, // 7
            };

            float[] normals = new float[8 * 3];
            float[] uvs = new float[8 * 2];

            int[] indices = {
                    0, 1,  1, 2,  2, 3,  3, 0, // bottom face edges
                    4, 5,  5, 6,  6, 7,  7, 4, // top face edges
                    0, 4,  1, 5,  2, 6,  3, 7, // vertical edges
            };

            ModelComponent box = new ModelComponent(vertices, normals, uvs, indices);
            box.setDrawMode(Renderer.DrawMode.LINES);
            box.material.enabled = false;
            return box;
        }

        public ModelComponent line() {
            float[] vertices = {
                    0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f
            };

            float[] normals = new float[2 * 3];
            float[] uvs = new float[2 * 2];

            int[] indices = {
                    0, 1 // Single edge line
            };

            ModelComponent line = new ModelComponent(vertices, normals, uvs, indices);
            line.setDrawMode(Renderer.DrawMode.LINES);
            line.material.enabled = false;
            return line;
        }

        public ModelComponent cone(int slices, boolean capped) {
            int sideVerts = (slices + 1) * 2; // bottom ring + apex ring (one apex per slice for correct normals)
            int capVerts = capped ? slices + 1 : 0;
            float[] vertices = new float[(sideVerts + capVerts) * 3];
            float[] normals = new float[(sideVerts + capVerts) * 3];
            float[] uvs = new float[(sideVerts + capVerts) * 2];

            float normalY = 0.5f;
            float normalLen = (float) Math.sqrt(1.0f + normalY * normalY); // sqrt(cos²+sin²+0.5²) simplifies per-slice

            for (int i = 0; i <= slices; i++) {
                float theta = (float) (2 * Math.PI * i / slices);
                float cosT = (float) Math.cos(theta);
                float sinT = (float) Math.sin(theta);
                float u = (float) i / slices;

                // Side: bottom ring vertex
                int bot = i;
                vertices[bot * 3] = cosT * 0.5f;
                vertices[bot * 3 + 1] = -0.5f;
                vertices[bot * 3 + 2] = sinT * 0.5f;
                float nx = cosT / normalLen;
                float nz = sinT / normalLen;
                float ny = normalY / normalLen;
                normals[bot * 3] = nx;
                normals[bot * 3 + 1] = ny;
                normals[bot * 3 + 2] = nz;
                uvs[bot * 2] = u;
                uvs[bot * 2 + 1] = 0;

                int top = i + (slices + 1);
                vertices[top * 3] = 0;
                vertices[top * 3 + 1] = 0.5f;
                vertices[top * 3 + 2] = 0;
                normals[top * 3] = nx;
                normals[top * 3 + 1] = ny;
                normals[top * 3 + 2] = nz;
                uvs[top * 2] = u + 0.5f / slices;
                uvs[top * 2 + 1] = 1;
            }

            int sideIndexCount = slices * 3;
            int capIndexCount = capped ? slices * 3 : 0;
            int[] indices = new int[sideIndexCount + capIndexCount];
            int idx = 0;

            for (int i = 0; i < slices; i++) {
                int bot = i;
                int botNext = i + 1;
                int top = i + (slices + 1);

                indices[idx++] = bot;
                indices[idx++] = botNext;
                indices[idx++] = top;
            }

            if (capped) {
                int base = sideVerts;
                int centerIdx = base;

                vertices[centerIdx * 3] = 0;
                vertices[centerIdx * 3 + 1] = -0.5f;
                vertices[centerIdx * 3 + 2] = 0;
                normals[centerIdx * 3] = 0;
                normals[centerIdx * 3 + 1] = -1;
                normals[centerIdx * 3 + 2] = 0;
                uvs[centerIdx * 2] = 0.5f;
                uvs[centerIdx * 2 + 1] = 0.5f;

                for (int i = 0; i < slices; i++) {
                    float theta = (float) (2 * Math.PI * i / slices);
                    int vi = base + 1 + i;

                    vertices[vi * 3] = (float) Math.cos(theta) * 0.5f;
                    vertices[vi * 3 + 1] = -0.5f;
                    vertices[vi * 3 + 2] = (float) Math.sin(theta) * 0.5f;

                    normals[vi * 3] = 0;
                    normals[vi * 3 + 1] = -1;
                    normals[vi * 3 + 2] = 0;

                    uvs[vi * 2] = (float) Math.cos(theta) * 0.5f + 0.5f;
                    uvs[vi * 2 + 1] = (float) Math.sin(theta) * 0.5f + 0.5f;

                    int next = base + 1 + (i + 1) % slices;
                    indices[idx++] = centerIdx;
                    indices[idx++] = next;
                    indices[idx++] = vi;
                }
            }

            return new ModelComponent(vertices, normals, uvs, indices);
        }

        public ModelComponent grid(int divisions) {
            int lineVerts = divisions + 1;
            int vCount = lineVerts * lineVerts;
            float[] vertices = new float[vCount * 3];
            float[] normals = new float[vCount * 3];
            float[] uvs = new float[vCount * 2];
            for (int row = 0; row <= divisions; row++) {
                float v = (float) row / divisions;
                float z = v - 0.5f;
                for (int col = 0; col <= divisions; col++) {
                    float u = (float) col / divisions;
                    float x = u - 0.5f;
                    int i = row * lineVerts + col;

                    vertices[i * 3] = x;
                    vertices[i * 3 + 1] = 0;
                    vertices[i * 3 + 2] = z;

                    normals[i * 3] = 0;
                    normals[i * 3 + 1] = 1;
                    normals[i * 3 + 2] = 0;

                    uvs[i * 2] = u;
                    uvs[i * 2 + 1] = v;
                }
            }

            int horizontalSegments = divisions * lineVerts;
            int verticalSegments = divisions * lineVerts;
            int[] indices = new int[(horizontalSegments + verticalSegments) * 2];
            int idx = 0;

            for (int row = 0; row < lineVerts; row++) {
                for (int col = 0; col < divisions; col++) {
                    int start = row * lineVerts + col;
                    indices[idx++] = start;
                    indices[idx++] = start + 1;
                }
            }

            for (int col = 0; col < lineVerts; col++) {
                for (int row = 0; row < divisions; row++) {
                    int start = row * lineVerts + col;
                    indices[idx++] = start;
                    indices[idx++] = start + lineVerts;
                }
            }
            ModelComponent mc = new ModelComponent(vertices, normals, uvs, indices);
            mc.setDrawMode(Renderer.DrawMode.LINES);
            return mc;
        }

        public ModelComponent torus(float majorRadius, float minorRadius, int majorSegments, int minorSegments) {
            int vCount = (majorSegments + 1) * (minorSegments + 1);
            float[] vertices = new float[vCount * 3];
            float[] normals = new float[vCount * 3];
            float[] uvs = new float[vCount * 2];
            int[] indices = new int[majorSegments * minorSegments * 6];

            for (int maj = 0; maj <= majorSegments; maj++) {
                float u = (float) maj / majorSegments;
                float phi = (float) (2 * Math.PI * maj / majorSegments);
                float cosPhi = (float) Math.cos(phi);
                float sinPhi = (float) Math.sin(phi);

                for (int min = 0; min <= minorSegments; min++) {
                    float v = (float) min / minorSegments;
                    float theta = (float) (2 * Math.PI * min / minorSegments);
                    float cosTheta = (float) Math.cos(theta);
                    float sinTheta = (float) Math.sin(theta);

                    // Centre of the tube at this major angle
                    float cx = majorRadius * cosPhi;
                    float cz = majorRadius * sinPhi;

                    float nx = cosPhi * cosTheta;
                    float ny = sinTheta;
                    float nz = sinPhi * cosTheta;

                    int i = maj * (minorSegments + 1) + min;

                    vertices[i * 3] = cx + minorRadius * nx;
                    vertices[i * 3 + 1] = minorRadius * ny;
                    vertices[i * 3 + 2] = cz + minorRadius * nz;

                    normals[i * 3] = nx;
                    normals[i * 3 + 1] = ny;
                    normals[i * 3 + 2] = nz;

                    uvs[i * 2] = u;
                    uvs[i * 2 + 1] = v;
                }
            }

            int idx = 0;
            for (int maj = 0; maj < majorSegments; maj++) {
                for (int min = 0; min < minorSegments; min++) {
                    int tl = maj * (minorSegments + 1) + min;
                    int tr = tl + 1;
                    int bl = tl + (minorSegments + 1);
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

        // Capsule: cylinder body with hemispherical caps.
        // radius: radius of the body and caps.
        // height: total height including both caps (minimum 2*radius).
        // slices: radial segments. stacks: latitudinal segments per hemisphere.
        public ModelComponent capsule(float radius, float height, int slices, int stacks) {
            float bodyHeight = Math.max(0, height - 2 * radius);
            float halfBody = bodyHeight * 0.5f;

            // Vertices: top cap + body rings + bottom cap
            // Top hemisphere: (stacks+1) rings of (slices+1) verts
            // Bottom hemisphere: (stacks+1) rings of (slices+1) verts
            // They share no rings so UVs are clean.
            int ringsPerCap = stacks + 1;
            int bodyRings = 2; // top and bottom edge of the cylindrical body
            int totalRings = ringsPerCap * 2 + bodyRings;
            int vCount = totalRings * (slices + 1);

            float[] vertices = new float[vCount * 3];
            float[] normals = new float[vCount * 3];
            float[] uvs = new float[vCount * 2];

            int ring = 0;

            // Top hemisphere (apex at +halfBody+radius, opening downward)
            for (int stack = 0; stack <= stacks; stack++) {
                float phi = (float) (Math.PI * 0.5 * stack / stacks); // 0 at apex, PI/2 at equator
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);
                float ringY = halfBody + radius * cosPhi;
                float v = (float) stack / (stacks * 2 + 1); // normalised over full height

                for (int slice = 0; slice <= slices; slice++) {
                    float theta = (float) (2 * Math.PI * slice / slices);
                    float cosT = (float) Math.cos(theta);
                    float sinT = (float) Math.sin(theta);

                    int i = ring * (slices + 1) + slice;
                    float nx = cosT * sinPhi;
                    float ny = cosPhi;
                    float nz = sinT * sinPhi;

                    vertices[i * 3] = nx * radius;
                    vertices[i * 3 + 1] = ringY;
                    vertices[i * 3 + 2] = nz * radius;

                    normals[i * 3] = nx;
                    normals[i * 3 + 1] = ny;
                    normals[i * 3 + 2] = nz;

                    uvs[i * 2] = (float) slice / slices;
                    uvs[i * 2 + 1] = 1 - v;
                }
                ring++;
            }

            // Body — top edge ring (y = +halfBody)
            {
                float v = (float) stacks / (stacks * 2 + 1);
                for (int slice = 0; slice <= slices; slice++) {
                    float theta = (float) (2 * Math.PI * slice / slices);
                    float cosT = (float) Math.cos(theta);
                    float sinT = (float) Math.sin(theta);

                    int i = ring * (slices + 1) + slice;
                    vertices[i * 3] = cosT * radius;
                    vertices[i * 3 + 1] = halfBody;
                    vertices[i * 3 + 2] = sinT * radius;

                    normals[i * 3] = cosT;
                    normals[i * 3 + 1] = 0;
                    normals[i * 3 + 2] = sinT;

                    uvs[i * 2] = (float) slice / slices;
                    uvs[i * 2 + 1] = 1 - v;
                }
                ring++;
            }

            // Body — bottom edge ring (y = -halfBody)
            {
                float v = (float) (stacks + 1) / (stacks * 2 + 1);
                for (int slice = 0; slice <= slices; slice++) {
                    float theta = (float) (2 * Math.PI * slice / slices);
                    float cosT = (float) Math.cos(theta);
                    float sinT = (float) Math.sin(theta);

                    int i = ring * (slices + 1) + slice;
                    vertices[i * 3] = cosT * radius;
                    vertices[i * 3 + 1] = -halfBody;
                    vertices[i * 3 + 2] = sinT * radius;

                    normals[i * 3] = cosT;
                    normals[i * 3 + 1] = 0;
                    normals[i * 3 + 2] = sinT;

                    uvs[i * 2] = (float) slice / slices;
                    uvs[i * 2 + 1] = 1 - v;
                }
                ring++;
            }

            // Bottom hemisphere (opening upward, nadir at -halfBody-radius)
            for (int stack = 0; stack <= stacks; stack++) {
                float phi = (float) (Math.PI * 0.5 * (stacks - stack) / stacks); // PI/2 at equator, 0 at nadir
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);
                float ringY = -halfBody - radius * cosPhi;
                float v = (float) (stacks + 2 + stack) / (stacks * 2 + 1);

                for (int slice = 0; slice <= slices; slice++) {
                    float theta = (float) (2 * Math.PI * slice / slices);
                    float cosT = (float) Math.cos(theta);
                    float sinT = (float) Math.sin(theta);

                    int i = ring * (slices + 1) + slice;
                    float nx = cosT * sinPhi;
                    float ny = -cosPhi;
                    float nz = sinT * sinPhi;

                    vertices[i * 3] = nx * radius;
                    vertices[i * 3 + 1] = ringY;
                    vertices[i * 3 + 2] = nz * radius;

                    normals[i * 3] = nx;
                    normals[i * 3 + 1] = ny;
                    normals[i * 3 + 2] = nz;

                    uvs[i * 2] = (float) slice / slices;
                    uvs[i * 2 + 1] = 1 - v;
                }
                ring++;
            }

            // Indices — quad strips between consecutive rings
            int totalRingsForQuads = totalRings - 1;
            int[] indices = new int[totalRingsForQuads * slices * 6];
            int idx = 0;

            for (int r = 0; r < totalRings - 1; r++) {
                for (int slice = 0; slice < slices; slice++) {
                    int tl = r * (slices + 1) + slice;
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
    }


}
