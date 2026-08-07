package org.sharkk2.sengine.core.systems;

import org.joml.Vector3f;
import org.sharkk2.game.Game;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.components.ColliderComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;

import java.util.*;

public class CollisionService {
    private final Engine engine;
    private final Map<UUID, ColliderComponent> colliders = new HashMap<>();
    public record Collision(GameObject collider, Vector3f mtv, float length) {}
    private final List<ColliderComponent> objColliders = new ArrayList<>();

    public CollisionService(Engine engine) {
        this.engine = engine;
    }

    public void register(ColliderComponent cc) {
        if (colliders.containsKey(cc.getID())) {
            Logger.error("Collider (" + cc.getID() + ") is already registered");
            return;
        }

        colliders.put(cc.getID(), cc);
    }

    public void unregister(ColliderComponent cc) {
        colliders.remove(cc.getID());
    }

    public Collision checkCollision(ColliderComponent collider) {
        return checkCollision(collider, Collections.emptyList());
    }


    public Collision checkCollision(ColliderComponent collider, List<ColliderComponent> blacklist) {
        Vector3f best = null;
        GameObject colliderObject = null;
        float bestLen = -1;

        for (ColliderComponent cc : colliders.values()) {
            if (cc.getID().equals(collider.getID()) || blacklist.contains(cc)) {continue;}
            Vector3f mtv = collider.testCollision(cc.getBounds());
            if (mtv != null) {
                float len = mtv.lengthSquared();
                if (len > bestLen) {
                    best = new Vector3f(mtv);
                    bestLen = len;
                    colliderObject = cc.getOwner();
                }
            }
        }
        if (best == null) return null;
        return new Collision(colliderObject, best, bestLen);
    }



    public Collision checkCollision(GameObject colliderObject) {
        objColliders.clear();
        Collision fc = null;
        float bestLen = -1;
        collectColliders(colliderObject);
        if (objColliders.isEmpty()) return null;
        for (ColliderComponent cc : objColliders) {
            Collision cls = checkCollision(cc, objColliders);
            if (cls != null && cls.length > bestLen) {
                fc = cls;
                bestLen = cls.length;
            }
        }
        return fc;
    }

    private void collectColliders(GameObject col) {
        if (col.hasComponent(ColliderComponent.class)) objColliders.add(col.getComponent(ColliderComponent.class));
        for (GameObject child : col.children) collectColliders(child);
    }

    public List<Collision> checkCollisionAll(ColliderComponent collider, List<ColliderComponent> blacklist) {
        List<Collision> results = new ArrayList<>();
        for (ColliderComponent cc : colliders.values()) {
            if (cc.getID().equals(collider.getID()) || blacklist.contains(cc)) continue;
            Vector3f mtv = collider.testCollision(cc.getBounds());
            if (mtv != null) {
                results.add(new Collision(cc.getOwner(), new Vector3f(mtv), mtv.lengthSquared()));
            }
        }
        return results;
    }

    public List<Collision> checkCollisionAll(GameObject colliderObject) {
        objColliders.clear();
        collectColliders(colliderObject);
        List<Collision> all = new ArrayList<>();
        if (objColliders.isEmpty()) return all;
        for (ColliderComponent cc : objColliders) {all.addAll(checkCollisionAll(cc, objColliders));}
        return all;
    }


    public float maxAxisPenetration(List<CollisionService.Collision> collisions, int axis) {
        float best = 0f;
        for (CollisionService.Collision c : collisions) {
            float v = axis == 0 ? c.mtv().x : axis == 1 ? c.mtv().y : c.mtv().z;
            if (Math.abs(v) > Math.abs(best)) best = v;
        }
        return best;
    }


}
