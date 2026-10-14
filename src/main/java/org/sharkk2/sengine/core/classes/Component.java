package org.sharkk2.sengine.core.classes;

import java.util.UUID;

public abstract class Component {
    protected GameObject owner;
    protected UUID componentID = UUID.randomUUID();
    public String name = "object_component";

    protected abstract void onObjectAttach();
    /**Component gets unregistered from the Engine's `ComponentRegistry` after this is triggered*/
    protected abstract void onObjectDetach();
    protected abstract void onUpdate();
    public abstract Component copy();


    public void onAttach(GameObject owner) {
        this.owner = owner;
        owner.getEngine().getComponentRegistry().register(this);
        onObjectAttach();
    }

    public void onDetach() {
        owner.getEngine().getComponentRegistry().unregister(this);
        onObjectDetach();
    }
    public GameObject getOwner() {return owner;}
    public UUID getID() {return componentID;}

}
