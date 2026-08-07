package org.sharkk2.sengine.core;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.systems.components.CameraComponent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Helpers {
    public static String readFile(String path) {
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

}
