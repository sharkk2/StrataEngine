package org.sharkk2.sengine.core.systems;

import org.joml.Matrix4fc;
import org.joml.*;
import org.lwjgl.opengl.ARBBindlessTexture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.classes.exceptions.ShaderLoadException;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL.getCapabilities;
import static org.lwjgl.opengl.GL43.*;

public class ShaderService {
    private final Map<String, Shader> shaders = new HashMap<>();
    private final Map<String, UBO> ubos = new HashMap<>();
    private final Map<String, SSBO> ssbos = new HashMap<>();
    private final List<BindlessTexture> bindlessTextures = new ArrayList<>();
    public boolean bindlessTexturesSupported = false;
    private final Engine engine;

    public ShaderService(Engine engine) {
        this.engine = engine;
        bindlessTexturesSupported = checkBindlessTexturesSupport();
        if (!bindlessTexturesSupported) Logger.warning("Bindless textures are not supported!");
    }

    public Shader get(String vertexPath, String fragmentPath) {
        return getOrLoad(Shader.ShaderType.DRAW, vertexPath, fragmentPath);
    }

    public Shader getCompute(String computePath) {
        return getOrLoad(Shader.ShaderType.COMPUTE, computePath);
    }

    public Shader getGeometry(String vertexPath, String geometryPath, String fragmentPath) {
        return getOrLoad(Shader.ShaderType.GEOMETRY, vertexPath, geometryPath, fragmentPath);
    }

    private Shader getOrLoad(Shader.ShaderType type, String... paths) {
        String key = type + "|" + String.join("|", paths);
        return shaders.computeIfAbsent(key, k -> load(type, paths));
    }

    private Shader load(Shader.ShaderType type, String... paths) {
        int[] stages = switch (type) {
            case DRAW -> new int[] { GL_VERTEX_SHADER, GL_FRAGMENT_SHADER };
            case COMPUTE -> new int[] { GL_COMPUTE_SHADER };
            case GEOMETRY -> new int[] { GL_VERTEX_SHADER, GL_GEOMETRY_SHADER, GL_FRAGMENT_SHADER };
        };

        int[] shaderIds = new int[stages.length];
        for (int i = 0; i < stages.length; i++) {
            shaderIds[i] = compile(stages[i], Helpers.readFile(paths[i]));
        }

        int program = 0;
        try {
            for (int i = 0; i < stages.length; i++) {
                try {
                    shaderIds[i] = compile(stages[i], Helpers.readFile(paths[i]));
                    Logger.info("Compiled " + getStageName(stages[i]) + " shader (" + paths[i] + ")");
                } catch (Exception e) {
                    Logger.error("Compilation failed (" + paths[i] + ")", e);
                }

            }
            program = glCreateProgram();
            for (int id : shaderIds) glAttachShader(program, id);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new ShaderLoadException("Failed to link " + type + " shader! (" + String.join(" - ", paths) + ")");
            }
            return new Shader(program, type, paths);
        } finally {
            for (int id : shaderIds) if (id != 0) glDeleteShader(id);
        }
    }

    private String getStageName(int stage) {
        return switch (stage) {
            case GL_VERTEX_SHADER -> "VERTEX";
            case GL_FRAGMENT_SHADER -> "FRAGMENT";
            case GL_GEOMETRY_SHADER -> "GEOMETRY";
            case GL_COMPUTE_SHADER -> "COMPUTE";
            default -> "UNKNOWN (" + stage + ")";
        };
    }

    private int compile(int type, String src) {
        int shader = glCreateShader(type);
        glShaderSource(shader, src);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            Logger.error("Shader failed to compile (" + src + ")");
            throw new ShaderLoadException("Failed to compile shader! " + log);
        }
        return shader;
    }

    public UBO createUBO(String name, int bindingPoint, int sizeBytes) {
        return ubos.computeIfAbsent(name, k -> new UBO(bindingPoint, sizeBytes));
    }

    public SSBO createSSBO(String name, int bindingPoint, int sizeBytes) {
        return ssbos.computeIfAbsent(name, k-> new SSBO(bindingPoint, sizeBytes));
    }

    public UBO getUBO(String name) {return ubos.get(name);}
    public SSBO getSSBO(String name) {return ssbos.get(name);}

    public BindlessTexture makeBindless(int textureId) {
        BindlessTexture isbindless = isBindless(textureId);
        if (isbindless != null) return isbindless;
        BindlessTexture tex = new BindlessTexture(textureId);
        bindlessTextures.add(tex);
        return tex;
    }

    public BindlessTexture isBindless(int textureId) {
        for (BindlessTexture bt : bindlessTextures) {if (bt.textureId == textureId) return bt;}
        return null;
    }

    public void releaseBindless(BindlessTexture tex) {
        tex.destroy();
        bindlessTextures.remove(tex);
    }

    public static class UBO {
        private final int id;
        private final int bindingPoint;
        private final int sizeBytes;

        private UBO(int bindingPoint, int sizeBytes) {
            this.bindingPoint = bindingPoint;
            this.sizeBytes = sizeBytes;
            this.id = glGenBuffers();
            glBindBuffer(GL_UNIFORM_BUFFER, id);
            glBufferData(GL_UNIFORM_BUFFER, sizeBytes, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, bindingPoint, id);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }

        public void upload(FloatBuffer data) {
            glBindBuffer(GL_UNIFORM_BUFFER, id);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, data);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }

        public void upload(FloatBuffer data, int offsetBytes) {
            glBindBuffer(GL_UNIFORM_BUFFER, id);
            glBufferSubData(GL_UNIFORM_BUFFER, offsetBytes, data);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }

        public void upload(ByteBuffer data) {
            glBindBuffer(GL_UNIFORM_BUFFER, id);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, data);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }

        public void upload(ByteBuffer data, int offsetBytes) {
            glBindBuffer(GL_UNIFORM_BUFFER, id);
            glBufferSubData(GL_UNIFORM_BUFFER, offsetBytes, data);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }

        public void upload(IntBuffer data) {
            glBindBuffer(GL_UNIFORM_BUFFER, id);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, data);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }

        public void upload(IntBuffer data, int offsetBytes) {
            glBindBuffer(GL_UNIFORM_BUFFER, id);
            glBufferSubData(GL_UNIFORM_BUFFER, offsetBytes, data);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
        }


        public int getId() { return id; }
        public int getBindingPoint() { return bindingPoint; }
        public int getSizeBytes() { return sizeBytes; }
        public void destroy() {glDeleteBuffers(id);}
    }

    public static class SSBO {
        private final int bindingPoint;
        private final int id;
        private ByteBuffer staging;
        private int sizeBytes;

        public SSBO(int bindingPoint, int initialSizeBytes) {
            this.bindingPoint = bindingPoint;
            this.sizeBytes = initialSizeBytes;
            this.staging = MemoryUtil.memAlloc(initialSizeBytes);
            this.id = glGenBuffers();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
            glBufferData(GL_SHADER_STORAGE_BUFFER, initialSizeBytes, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }

        public ByteBuffer beginUpload(int requiredBytes) {
            if (requiredBytes > sizeBytes) grow(requiredBytes);
            staging.clear();
            return staging;
        }

        public void endUpload() {
            staging.flip();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, staging);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }

        private void grow(int requiredBytes) {
            int newCap = sizeBytes;
            while (newCap < requiredBytes) newCap *= 2;
            MemoryUtil.memFree(staging);
            staging = MemoryUtil.memAlloc(newCap);
            sizeBytes = newCap;
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, id);
            glBufferData(GL_SHADER_STORAGE_BUFFER, sizeBytes, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, id);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        }

        public void destroy() {
            MemoryUtil.memFree(staging);
            glDeleteBuffers(id);
        }

        public int getId() { return id; }
        public int getBindingPoint() { return bindingPoint; }
        public int getSizeBytes() { return sizeBytes; }

    }

    public static class BindlessTexture {
        private final int textureId;
        private final long handle;
        private boolean resident;

        private BindlessTexture(int textureId) {
            this.textureId = textureId;
            this.handle = ARBBindlessTexture.glGetTextureHandleARB(textureId);
            if (handle == 0) {
                throw new ShaderLoadException("Failed to create bindless texture handle for texture " + textureId);
            }
            ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
            this.resident = true;
        }

        public long getHandle() {return handle;}
        public int getTextureId() {return textureId;}
        public boolean isResident() {return resident;}

        public void makeResident() {
            if (!resident) {
                ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
                resident = true;
            }
        }

        public void makeNonResident() {
            if (resident) {
                ARBBindlessTexture.glMakeTextureHandleNonResidentARB(handle);
                resident = false;
            }
        }

        public void destroy() {makeNonResident();}
    }


    public void destroyAll() {
        shaders.values().forEach(Shader::destroy);
        shaders.clear();
        ubos.values().forEach(UBO::destroy);
        ubos.clear();
        ssbos.values().forEach(SSBO::destroy);
        ssbos.clear();
        bindlessTextures.forEach(BindlessTexture::destroy);
        bindlessTextures.clear();
    }

    public static class Shader {
        private final int id;
        private String vertPath;
        private String fragPath;
        private String computePath;
        private String geometryPath;
        public enum ShaderType {DRAW, COMPUTE, GEOMETRY}
        public final ShaderType type;
        private final Map<String, Integer> locationCache = new HashMap<>();
        private Shader(int id, ShaderType type, String... paths) {
            this.id = id;
            this.type = type;
            switch (type) {
                case DRAW -> { this.vertPath = paths[0]; this.fragPath = paths[1]; }
                case COMPUTE -> this.computePath = paths[0];
                case GEOMETRY -> { this.vertPath = paths[0]; this.geometryPath = paths[1]; this.fragPath = paths[2]; }
            }
        }

        public void use() {glUseProgram(id); }
        public int getId() {return id; }
        public int loc(String name) {return locationCache.computeIfAbsent(name, n -> glGetUniformLocation(id, n));}
        public void setInt(String name, int v) {glUniform1i(loc(name), v); }
        public void setInt2(String name, int v1, int v2) {glUniform2i(loc(name), v1, v2); }
        public void setInt3(String name, int v1, int v2, int v3) {glUniform3i(loc(name), v1, v2, v3); }
        public void setInt4(String name, int v1, int v2, int v3, int v4) {glUniform4i(loc(name), v1, v2, v3, v4); }
        public void setFloat(String name, float v) {glUniform1f(loc(name), v);}
        public void setFloat2(String name, float v1, float v2) {glUniform2f(loc(name), v1, v2); }
        public void setFloat3(String name, float v1, float v2, float v3) {glUniform3f(loc(name), v1, v2, v3); }
        public void setFloat4(String name, float v1, float v2, float v3, float v4) {glUniform4f(loc(name), v1, v2, v3, v4); }
        public void setMat4(String name, Matrix4fc m) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(loc(name), false, m.get(stack.mallocFloat(16)));
            }
        }
        public void setVec2(String name, Vector2f v) {glUniform2f(loc(name), v.x, v.y);}
        public void setVec3(String name, Vector3f v) {glUniform3f(loc(name), v.x, v.y, v.z);}
        public void setVec4(String name, Vector4f v) {glUniform4f(loc(name), v.x, v.y, v.z, v.w);}
        public void setBindlessTexture(String name, long handle) {
            ARBBindlessTexture.glUniformHandleui64ARB(loc(name), handle);
        }

        public void setBindlessTextures(String name, long[] handles) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer buf = stack.mallocLong(handles.length);
                buf.put(handles).flip();
                ARBBindlessTexture.glUniformHandleui64vARB(loc(name), buf);
            }
        }

        public void dispatch(int groupsX, int groupsY, int groupsZ) {
            if (type != ShaderType.COMPUTE) throw new IllegalStateException("dispatch() called on a non-compute shader (" + id + ")");
            use();
            glDispatchCompute(groupsX, groupsY, groupsZ);
        }

        public void dispatch(int groupsX, int groupsY, int groupsZ, int barrierBits) {
            if (type != ShaderType.COMPUTE) throw new IllegalStateException("dispatch() called on a non-compute shader (" + id + ")");
            dispatch(groupsX, groupsY, groupsZ);
            glMemoryBarrier(barrierBits);
        }

        public String getVertPath() {return vertPath;}
        public String getFragPath() {return fragPath;}
        public String getComputePath() {return computePath;}
        public void destroy() {glDeleteProgram(id); locationCache.clear();}
    }

    private boolean checkBindlessTexturesSupport() {
        if (!getCapabilities().GL_ARB_bindless_texture) return false;

        glGetError();
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, 0);
        long handle = ARBBindlessTexture.glGetTextureHandleARB(tex);
        boolean ok = handle != 0 && glGetError() == GL_NO_ERROR;

        if (ok) {
            ARBBindlessTexture.glMakeTextureHandleResidentARB(handle);
            ok = glGetError() == GL_NO_ERROR;
            if (ok) ARBBindlessTexture.glMakeTextureHandleNonResidentARB(handle);
        }

        glDeleteTextures(tex);
        return ok;
    }
}