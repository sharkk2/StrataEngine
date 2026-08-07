package org.sharkk2.sengine.core.systems.components;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.systems.AssetLoader;


public class AudioComponent extends Component {
    public enum AudioType {POINT_AUDIO, DIRECTED_AUDIO}
    public final AssetLoader.AudioData audioData;
    public AudioType type = AudioType.POINT_AUDIO;
    public boolean playing = false;
    public boolean looping = false;
    public boolean paused = false;
    public float volume = 1.0f;
    public float pitch = 1.0f;
    public float minDistance = 0.1f;
    public float maxDistance = 100f;
    public float fadeRate = 1f;

    public float innerConeAngle = 360f;
    public float outerConeAngle = 360f;
    public float outerConeVolume = 0.1f;
    public final Vector3f direction = new Vector3f(0,0,-1);
    public boolean preferObjectDirection = true;


    public AudioComponent(AssetLoader.AudioData audioData) {
        this.audioData = audioData;
    }

    @Override
    protected void onObjectAttach() {
       owner.getEngine().getAudioService().register(this);
    }

    @Override
    protected void onObjectDetach() {
        owner.getEngine().getAudioService().unregister(this);
    }


    @Override
    protected void onUpdate() {

    }

    @Override
    public Component copy() {
        AudioComponent audioComponent = new AudioComponent(audioData);
        audioComponent.type = type;
        audioComponent.volume = volume;
        audioComponent.playing = playing;
        audioComponent.looping = looping;
        audioComponent.direction.set(direction);
        audioComponent.preferObjectDirection = preferObjectDirection;
        audioComponent.outerConeAngle = outerConeAngle;
        audioComponent.innerConeAngle = innerConeAngle;
        audioComponent.maxDistance = maxDistance;
        audioComponent.minDistance = minDistance;
        audioComponent.fadeRate = fadeRate;
        audioComponent.pitch = pitch;
        audioComponent.paused = paused;
        audioComponent.name = name += "_copy";
        return audioComponent;

    }
}
