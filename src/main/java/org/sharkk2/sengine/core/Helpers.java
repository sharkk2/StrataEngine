package org.sharkk2.sengine.core;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.sharkk2.game.Game;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.components.CameraComponent;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;

public class Helpers {
    public static Vector3f calculateCenter(Vector3f... points) {
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (Vector3f p : points) {
            if (p.x < min.x) min.x = p.x;
            if (p.y < min.y) min.y = p.y;
            if (p.z < min.z) min.z = p.z;
            if (p.x > max.x) max.x = p.x;
            if (p.y > max.y) max.y = p.y;
            if (p.z > max.z) max.z = p.z;
        }

        float cx = (min.x + max.x) * 0.5f; //midpoint formula
        float cy = (min.y + max.y) * 0.5f;
        float cz = (min.z + max.z) * 0.5f;
        return new Vector3f(cx, cy, cz);
    }

    public static BufferedImage captureTexture(int tex, int width, int height) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        glBindTexture(GL_TEXTURE_2D, tex);
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (x + (height - 1 - y) * width) * 4;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                int a = buffer.get(i + 3) & 0xFF;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    public static String readFile(String path) {
        return readFile(path, true);
    }

    public static String readFile(String path, boolean classpath) {
        // todo: refactor this
        if (classpath) {
            try (InputStream in = Helpers.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    Logger.error("Couldn't find file: " + path);
                    return null;
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Logger.error("Failed to read file", e);
                return null;
            }
        } else {
            try  {
                return Files.readString(Path.of(path));
            } catch (IOException e) {
                Logger.error("Failed to read file", e);
                return null;
            }

        }

    }

    public static Path resolveClasspathFile(String classpathRelative) {
        var url = Helpers.class.getClassLoader().getResource(classpathRelative);
        if (url == null) return null;
        if (!"file".equals(url.getProtocol())) return null;
        try {
            return Path.of(url.toURI());
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    private static final Matrix4f viewProjScratch = new Matrix4f();
    private static final Vector4f clipScratch = new Vector4f();

    public static boolean projectWorldToScreen(float worldX, float worldY, float worldZ, Vector2f outUV, CameraComponent camera) {
        viewProjScratch.set(camera.getProjectionMatrix(camera.getOwner().getEngine().getWindowAspectRatio()))
                .mul(camera.getViewMatrix());

        clipScratch.set(worldX, worldY, worldZ, 1.0f);
        viewProjScratch.transform(clipScratch);

        if (clipScratch.w <= 0.0f) return false;

        float invW = 1.0f / clipScratch.w;
        outUV.set(clipScratch.x * invW * 0.5f + 0.5f, clipScratch.y * invW * 0.5f + 0.5f);
        return true;
    }

    public static boolean projectWorldToScreen(Vector3f worldPos, Vector2f outUV, CameraComponent camera) {
        return projectWorldToScreen(worldPos.x, worldPos.y, worldPos.z, outUV, camera);
    }


    public static boolean projectDirToScreen(Vector3f direction, float distance, Vector2f outUV, CameraComponent camera) {
        Vector3f camPos = camera.getOwner().transform.getPosition();
        float wx = camPos.x - direction.x * distance;
        float wy = camPos.y - direction.y * distance;
        float wz = camPos.z - direction.z * distance;
        return projectWorldToScreen(wx, wy, wz, outUV, camera);
    }

    public static Vector3f directionFromPitchYaw(float pitchDeg, float yawDeg) {
        float pitch = (float) Math.toRadians(pitchDeg);
        float yaw = (float) Math.toRadians(yawDeg);

        float x = (float) (Math.cos(pitch) * Math.cos(yaw));
        float y = (float) Math.sin(pitch);
        float z = (float) (Math.cos(pitch) * -Math.sin(yaw));

        return new Vector3f(x, y, z).negate().normalize();
    }

    public static boolean validateLua(String script) {
        try {
            JsePlatform.standardGlobals().load(script, "check");
            return true;
        } catch (LuaError e) {
            return false;
        }
    }

    public static void translateObjects(List<GameObject> objects, float dx, float dy, float dz) {
        for (GameObject obj : objects) {
            obj.transform.transformPos(dx, dy, dz);
        }
    }

    public static void rotateObjects(List<GameObject> objects, float dp, float dy, float dr) {
        for (GameObject obj : objects) {
            obj.transform.transformRotation(dp, dy ,dr);
        }
    }

    public static void scaleObjects(List<GameObject> objects, float dw, float dh, float dd) {
        for (GameObject obj : objects) {
            obj.transform.transformScale(dw, dh, dd);
        }
    }

    public static boolean containsAlpha(ByteBuffer imagePixels, int width, int height, int channels) {
        if (channels < 4) return false;
        int totalPixels = width * height;
        for (int i = 0; i < totalPixels; i++) {
            int alpha = imagePixels.get(i * 4 + 3) & 0xFF;
            if (alpha < 255) {
                return true;
            }
        }
        return false;
    }
}
