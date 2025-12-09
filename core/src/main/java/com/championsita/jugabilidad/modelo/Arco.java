package com.championsita.jugabilidad.modelo;

import com.badlogic.gdx.math.Rectangle;

public class Arco {

    private Rectangle size;

    public Arco(float x, float y, float w, float h) {
        size = new Rectangle(x, y, w, h);
    }

    public Rectangle getHitbox() { return size; }

    public float getX() { return size.x; }
    public float getY() { return size.y; }
    public float getWidth() { return size.width; }
    public float getHeight() { return size.height; }
}
