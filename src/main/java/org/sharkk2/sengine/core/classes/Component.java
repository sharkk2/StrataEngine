package org.sharkk2.sengine.core.classes;

import java.util.UUID;

public abstract class Component {
    protected GameObject owner;
    protected UUID componentID = UUID.randomUUID();
    public String name = "object_component";

    protected abstract void onObjectAttach();
    protected abstract void onObjectDetach();
    protected abstract void onUpdate();


    public void onAttach(GameObject owner) {this.owner = owner; onObjectAttach();}
    public void onDetach() {onObjectDetach();}
    public GameObject getOwner() {return owner;}
    public UUID getID() {return componentID;}

}
