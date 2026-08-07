package org.sharkk2.sengine.core.systems;

import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.sharkk2.game.Game;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.AudioListener;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.exceptions.AudioException;
import org.sharkk2.sengine.core.systems.components.AudioComponent;
import org.sharkk2.sengine.core.systems.components.CameraComponent;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.*;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.openal.ALC11.*;
import static org.lwjgl.system.MemoryUtil.NULL;


public class AudioService {
    private final Engine engine;
    private final long device;
    private final long context;
    public AudioListener listener;

    private final Deque<Integer> availableSources = new ArrayDeque<>();
    private final Map<AssetLoader.AudioData, Integer> audioBuffers = new HashMap<>();
    private final Map<AudioComponent, Integer> componentSources = new HashMap<>();
    private final Map<AudioComponent, Vector3f> lastPositions = new HashMap<>();
    private final List<Integer> oneShotSources = new ArrayList<>();

    public final int MAX_AUDIOSOURCES;

    public AudioService(Engine engine) {
        this.engine = engine;
        device = alcOpenDevice((ByteBuffer) null);

        if (device == NULL) {
            throw new AudioException("Failed to initialize audio service: couldn't open audio device");
        }

        context = alcCreateContext(device, (int[]) null);

        if (context == NULL) {
            throw new AudioException("Failed to initialize audio service: couldn't create audio context");
        }

        alcMakeContextCurrent(context);
        AL.createCapabilities(ALC.createCapabilities(device));
        alDistanceModel(AL_LINEAR_DISTANCE_CLAMPED);
       // alDopplerFactor(0.0f);
        MAX_AUDIOSOURCES = probeMaxSources();
        for (int i = 0; i < MAX_AUDIOSOURCES; i++) {
            availableSources.push(alGenSources());
        }
    }

    private int probeMaxSources() {
        List<Integer> probe = new ArrayList<>();
        int cap = 256; // sane upper bound so we don't loop forever
        for (int i = 0; i < cap; i++) {
            int source = alGenSources();
            if (alGetError() != AL_NO_ERROR) {break;}
            probe.add(source);
        }

        for (int source : probe) {alDeleteSources(source);}
        return probe.size();
    }

    public void setListener(AudioListener listener) {this.listener = listener;}
    public void register(AudioComponent comp) {
        if (componentSources.size() >= MAX_AUDIOSOURCES) {
            Logger.error("No room for an extra audio component (max: " + MAX_AUDIOSOURCES + ")");
            return;
        }

        int buffer = getBuffer(comp.audioData);
        int source = availableSources.poll();

        alSourcei(source, AL_BUFFER, buffer);
        alSourcei(source, AL_LOOPING, comp.looping ? AL_TRUE : AL_FALSE);
        alSourcef(source, AL_GAIN, comp.volume);
        alSourcef(source, AL_PITCH, comp.pitch);
        alSourcef(source, AL_ROLLOFF_FACTOR, comp.fadeRate);
        alSourcef(source, AL_REFERENCE_DISTANCE, comp.minDistance);
        alSourcef(source, AL_MAX_DISTANCE, comp.maxDistance);
        if (comp.type == AudioComponent.AudioType.DIRECTED_AUDIO) {
            alSourcef(source, AL_CONE_INNER_ANGLE, comp.innerConeAngle);
            alSourcef(source, AL_CONE_OUTER_ANGLE, comp.outerConeAngle);
            alSourcef(source, AL_CONE_OUTER_GAIN, comp.outerConeVolume);
        }

        componentSources.put(comp, source);
        if (componentSources.size() == MAX_AUDIOSOURCES) Logger.warning("Audio component limit reached");
    }

    public void unregister(AudioComponent comp) {
        Integer source = componentSources.remove(comp);
        if (source != null) {
            alSourceStop(source);
            alSourcei(source, AL_BUFFER, 0);
            availableSources.push(source);
        }
        lastPositions.remove(comp);
    }

    public void update() {
        alDopplerFactor(engine.getValue("audio.doppler_intensity"));
        if (listener != null) listener.update();
        Iterator<Integer> it = oneShotSources.iterator();
        while (it.hasNext()) {
            int source = it.next();
            int state = alGetSourcei(source, AL_SOURCE_STATE);
            if (state == AL_STOPPED) {
                alSourcei(source, AL_BUFFER, 0);
                alSourcei(source, AL_SOURCE_RELATIVE, AL_FALSE);
                availableSources.push(source);
                it.remove();
            }
        }

        for (AudioComponent comp : componentSources.keySet()) {
            Integer source = componentSources.get(comp);
            if (source == null) return;
            Vector3f currentPos = comp.getOwner().transform.getPosition();
            Vector3f velocity = computeVelocity(comp, currentPos);
            alSource3f(source, AL_POSITION, currentPos.x, currentPos.y, currentPos.z);
            alSource3f(source, AL_VELOCITY, velocity.x, velocity.y, velocity.z);
            if (comp.type == AudioComponent.AudioType.DIRECTED_AUDIO) {
                Vector3f forward;
                if (comp.preferObjectDirection) {
                    forward = comp.getOwner().transform.getDirection();
                } else {forward = comp.direction;}
                alSource3f(source, AL_DIRECTION, forward.x, forward.y, forward.z);
            }

            alSourcef(source, AL_GAIN, comp.volume);
            alSourcef(source, AL_PITCH, comp.pitch);
            alSourcef(source, AL_ROLLOFF_FACTOR, comp.fadeRate);

            alSourcei(source, AL_LOOPING, comp.looping ? AL_TRUE : AL_FALSE);
            int state = alGetSourcei(source, AL_SOURCE_STATE);
            if (!comp.looping && state == AL_STOPPED && comp.playing) {
                comp.playing = false;
                continue;
            }

            if (comp.playing && !comp.paused) {
                if (state != AL_PLAYING) {alSourcePlay(source);}
            } else if (comp.paused) {
                if (state == AL_PLAYING) {alSourcePause(source);}
            } else {
                if (state == AL_PLAYING || state == AL_PAUSED) {alSourceStop(source);}
            }
        }
    }

    public void playAudio(AssetLoader.AudioData data) {
        playAudio(data, 1.0f, 1.0f);
    }

    public void playAudio(AssetLoader.AudioData data, float volume, float pitch) {
        Integer source = availableSources.poll();
        if (source == null) {
            Logger.warning("No available audio sources for one-shot playback");
            return;
        }

        int buffer = getBuffer(data);

        alSourcei(source, AL_BUFFER, buffer);
        alSourcei(source, AL_LOOPING, AL_FALSE);
        alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
        alSource3f(source, AL_POSITION, 0, 0, 0);
        alSource3f(source, AL_VELOCITY, 0, 0, 0);
        alSourcef(source, AL_GAIN, volume);
        alSourcef(source, AL_PITCH, pitch);

        alSourcePlay(source);
        oneShotSources.add(source);
    }

    private Vector3f computeVelocity(AudioComponent comp, Vector3f currentPos) {
        Vector3f last = lastPositions.get(comp);
        Vector3f velocity = new Vector3f();
        if (last != null && engine.getDeltaTime() > 0f) {
            currentPos.sub(last, velocity).div(engine.getDeltaTime());
        }

        lastPositions.put(comp, new Vector3f(currentPos));
        return velocity;
    }

    private int getBuffer(AssetLoader.AudioData data) {
        Integer cached = audioBuffers.get(data);
        if (cached != null) return cached;

        int format = toAlFormat(data.channels(), data.bitsPerSample());
        int buffer = alGenBuffers();

        if (data.bitsPerSample() == 8) {
            ByteBuffer direct = BufferUtils.createByteBuffer(data.pcmData().length);
            direct.put(data.pcmData());
            direct.flip();
            alBufferData(buffer, format, direct, data.sampleRate());
        } else {
            ShortBuffer direct = toShortBuffer(data.pcmData(), data.bigEndian());
            alBufferData(buffer, format, direct, data.sampleRate());
        }

        audioBuffers.put(data, buffer);
        return buffer;
    }

    private ShortBuffer toShortBuffer(byte[] bytes, boolean bigEndian) {
        ByteOrder order = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ShortBuffer heapView = ByteBuffer.wrap(bytes).order(order).asShortBuffer();

        ShortBuffer direct = BufferUtils.createShortBuffer(heapView.remaining());
        direct.put(heapView);
        direct.flip();
        return direct;
    }

    private int toAlFormat(int channels, int bitsPerSample) {
        if (channels == 1 && bitsPerSample == 8) return AL_FORMAT_MONO8;
        if (channels == 1 && bitsPerSample == 16) return AL_FORMAT_MONO16;
        if (channels == 2 && bitsPerSample == 8) return AL_FORMAT_STEREO8;
        if (channels == 2 && bitsPerSample == 16) return AL_FORMAT_STEREO16;
        throw new AudioException("Unsupported audio format: " + channels + " channels, " + bitsPerSample + " bits");
    }


}
