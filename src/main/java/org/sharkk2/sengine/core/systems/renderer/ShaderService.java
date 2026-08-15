package org.sharkk2.sengine.core.systems.renderer;

import org.joml.Matrix4fc;
import org.joml.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.classes.exceptions.ShaderLoadException;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import static org.lwjgl.opengl.GL43.*;

public class ShaderService {
    private final Map<String, Shader> shaders = new HashMap<>();
    private final Map<String, UBO> ubos = new HashMap<>();
    private final Map<String, SSBO> ssbos = new HashMap<>();
    private final Engine engine;

    public ShaderService(Engine engine) {this.engine = engine;}

    public Shader get(String vertexPath, String fragmentPath) {
        String key = vertexPath + "|" + fragmentPath;
        return shaders.computeIfAbsent(key, k -> load(vertexPath, fragmentPath));
    }

    public Shader getCompute(String computePath) {
        String key = "compute|" + computePath;
        return shaders.computeIfAbsent(key, k -> loadCompute(computePath));
    }

    private Shader load(String vertexPath, String fragmentPath) {
        int vertex = compile(GL_VERTEX_SHADER, Helpers.readFile(vertexPath));
        int fragment = compile(GL_FRAGMENT_SHADER, Helpers.readFile(fragmentPath));
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            Logger.error("Shader link failed");
            throw new ShaderLoadException("Failed to link shader! (" + vertexPath +" - " + fragmentPath + ")");
        }

        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return new Shader(program, vertexPath, fragmentPath);
    }

    private Shader loadCompute(String computePath) {
        int compute = compile(GL_COMPUTE_SHADER, Helpers.readFile(computePath));
        int program = glCreateProgram();
        glAttachShader(program, compute);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            Logger.error("Compute shader link failed");
            throw new ShaderLoadException("Failed to link compute shader! (" + computePath + ")");
        }

        glDeleteShader(compute);
        return new Shader(program, computePath);
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

    public void destroyAll() {
        shaders.values().forEach(Shader::destroy);
        shaders.clear();
        ubos.values().forEach(UBO::destroy);
        ubos.clear();
        ssbos.values().forEach(SSBO::destroy);
        ssbos.clear();
    }

    public static class Shader {
        private final int id;
        private String vertPath;
        private String fragPath;
        private String computePath;
        private final boolean isCompute;
        private final Map<String, Integer> locationCache = new HashMap<>();
        private Shader(int id) { this.id = id; this.isCompute = false; }
        private Shader(int id, String vertPath, String fragPath) { this.id = id; this.vertPath = vertPath; this.fragPath = fragPath; this.isCompute = false; }
        private Shader(int id, String computePath) { this.id = id; this.computePath = computePath; this.isCompute = true; }

        public void use() {glUseProgram(id); }
        public int getId() {return id; }
        public boolean isCompute() {return isCompute; }
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

        public void dispatch(int groupsX, int groupsY, int groupsZ) {
            if (!isCompute) throw new IllegalStateException("dispatch() called on a non-compute shader (" + vertPath + " / " + fragPath + ")");
            use();
            glDispatchCompute(groupsX, groupsY, groupsZ);
        }

        public void dispatch(int groupsX, int groupsY, int groupsZ, int barrierBits) {
            dispatch(groupsX, groupsY, groupsZ);
            glMemoryBarrier(barrierBits);
        }

        public String getVertPath() {return vertPath;}
        public String getFragPath() {return fragPath;}
        public String getComputePath() {return computePath;}
        public void destroy() {glDeleteProgram(id); locationCache.clear();}
    }
}