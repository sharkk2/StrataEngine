package org.sharkk2.sengine.core.classes;

public class Texture {
    private int id;
    private int width;
    private int height;
    public Texture(int id, int width, int height) {
        this.id = id;
        this.width = width;
    }

    public int getID() {return id;}
    public int getWidth() {return width;}
    public int getHeight() {return height;}
}
