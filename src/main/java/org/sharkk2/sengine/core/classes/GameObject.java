package org.sharkk2.sengine.core.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.*;
import java.util.function.Consumer;

public class GameObject {
    private final Engine engine;
    private final Map<Class<? extends Component>, Component> components = new HashMap<>();
    public final UUID id = UUID.randomUUID();
    private String name = "GameObject";
    public Transform transform = new Transform();
    public GameObject parent;
    public Renderer.RenderMethod renderMethod = Renderer.RenderMethod.RENDER_DEFERRED;
    public List<GameObject> children = new ArrayList<>();

    public GameObject(Engine engine) {
        this.engine = engine;
    }

    public void addChild(GameObject child) {
        child.parent = this;
        child.renderMethod = renderMethod;

        children.add(child);
    }

    public void update() {
        for (Component c : components.values()) c.onUpdate();
        for (GameObject child : children) child.update();
    }

    public void update(boolean updateChildren) {
        for (Component c : components.values()) c.onUpdate();
        if (updateChildren) {
            for (GameObject child : children) child.update();
        }
    }

    public class Transform {
        public float x,y,z;
        public float pitch,yaw,roll;
        public float width = 1, height = 1, depth = 1;
        public void transformPos(float x, float y, float z) {this.x += x; this.y += y; this.z += z;}
        public void transformScale(float w, float h, float d) {this.width += w; this.height += h; this.depth += d;}
        public void transformRotation(float p, float y, float r) {this.pitch += p; this.yaw += y; this.roll += r;}
        public Vector3f getPosition() {return new Vector3f(x,y,z);}
        public Vector3f getScale() {return new Vector3f(width, height, depth);}
        public void setPosition(float x, float y, float z) {this.x = x; this.y=y; this.z=z;}
        public void setPosition(Vector3f pos) {this.x = pos.x; this.y = pos.y; this.z = pos.z;}
        public void setScale(float w, float h, float d) {this.width = w; this.height = h; this.depth = d;}
        public void setScale(Vector3f scale) {this.width = scale.x; this.height = scale.y; this.depth = scale.z;}
        public void setScale(float v) {this.width = v; this.height = v; this.depth =v;}
        public void setRotation(float p, float y, float r) {this.pitch = p; this.yaw = y; this.roll = r;}
        public void setRotation(Vector3f rot) {this.pitch = rot.x; this.yaw = rot.y; this.roll = rot.z;}
        public Matrix4f calculateWorldMatrix() {
            Matrix4f local = new Matrix4f()
                    .translate(x, y, z)
                    .rotateXYZ((float) Math.toRadians(pitch),(float) Math.toRadians(yaw),(float) Math.toRadians(roll))
                    .scale(width, height, depth);

            if (parent != null) {return new Matrix4f(parent.transform.calculateWorldMatrix()).mul(local);}
            return local;
        }
    }

    public void attachComponent(Component c) {
        components.put(c.getClass(), c);
        c.onAttach(this);
    }

    public <T extends Component> T getComponent(Class<T> type) {return type.cast(components.get(type));}

    public void detatchComponent(Component component) {
        components.remove(component); component.onDetach();
    }

    public boolean hasComponent(Class<? extends Component> component) {return components.containsKey(component);}
    public Engine getEngine() {return engine;}
    public void setName(String name) {this.name = name;}
    public String getName() {return name;}
    public String getRootName() {
        GameObject current = this;
        while (current.parent != null) {current = current.parent;}
        return current.name;
    }

    public void destroy() {
        components.forEach((aClass, component) -> component.onDetach());
        components.clear();
        children.clear();
    }

    public void cascade(Consumer<GameObject> action) {
        action.accept(this);
        for (GameObject child : children) child.cascade(action);
    }

    public <T extends Component> void cascade(Class<T> type, Consumer<T> action) {
        cascade(go -> {
            T comp = go.getComponent(type);
            if (comp != null) action.accept(comp);
        });
    }

}
