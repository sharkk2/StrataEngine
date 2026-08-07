package org.sharkk2.sengine.core.classes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.exceptions.AudioException;
import org.sharkk2.sengine.core.systems.components.CameraComponent;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL10.AL_GAIN;
import static org.lwjgl.openal.AL10.AL_ORIENTATION;
import static org.lwjgl.openal.AL10.alListenerf;
import static org.lwjgl.openal.AL10.alListenerfv;

public class AudioListener {
    private final Engine engine;
    private final Vector3f lastPosition = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    public GameObject attachedObject;
    private boolean isFirstUpdate = true;
    public float volume = 1f;

    public AudioListener(Engine engine, GameObject object) {
        attachedObject = object;
        this.engine = engine;
    }

    public AudioListener(Engine engine) {
        CameraComponent cam = engine.getCameraService().getPrimaryCamera();
        if (cam == null) throw new AudioException("No valid listener is set! either set an active camera or pass a GameObject");
        attachedObject = cam.getOwner();
        this.engine = engine;
    }

    public void setObject(GameObject object) {
        if (object == null) {
            CameraComponent cam = engine.getCameraService().getPrimaryCamera();
            if (cam == null) throw new AudioException("No valid listener is set! either set an active camera or pass a GameObject");
            attachedObject = cam.getOwner();
        } else {
            attachedObject = object;
        }
    }

    public void update() {
        Vector3f currentPos = attachedObject.transform.getPosition();
        if (isFirstUpdate || engine.getDeltaTime() <= 0f) {
            velocity.set(0,0,0);
            isFirstUpdate = false;
        } else {
            currentPos.sub(lastPosition, velocity).div(engine.getDeltaTime());
        }

        lastPosition.set(currentPos);
        alListener3f(AL_POSITION, currentPos.x, currentPos.y, currentPos.z);
        alListener3f(AL_VELOCITY, velocity.x, velocity.y, velocity.z);
        Vector3f forward = attachedObject.transform.getDirection();
        Vector3f up = attachedObject.transform.getUp();



        float[] orientation = {forward.x, forward.y, forward.z, up.x, up.y, up.z};
        alListenerfv(AL_ORIENTATION, orientation);
        alListenerf(AL_GAIN, volume);
    }

}