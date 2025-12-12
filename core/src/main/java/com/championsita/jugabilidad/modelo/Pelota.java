package com.championsita.jugabilidad.modelo;

import com.badlogic.gdx.math.Rectangle;

public class Pelota {

    // Posición
    private float x, y;

    // Tamaño lógico (no visual, pero el cliente puede dibujar usando esto)
    private float ancho = 0.25f;
    private float alto  = 0.25f;

    // Velocidad
    private float vx = 0f;
    private float vy = 0f;

    // Fricción simple
    private static final float FRICCION = 0.95f;

    // Hitbox AABB
    private final Rectangle hitbox = new Rectangle();

    public Pelota(float xInicial, float yInicial) {
        this.x = xInicial;
        this.y = yInicial;
        hitbox.set(x, y, ancho, alto);
    }

    // =========================
    // Actualización básica
    // =========================
    public void actualizar(float delta) {
        // Integración de velocidad
        x += vx * delta;
        y += vy * delta;

        // Fricción
        vx *= FRICCION;
        vy *= FRICCION;

        // Actualizar hitbox
        hitbox.setPosition(x, y);
    }

    // =========================
    // Impulsos / rebotes
    // =========================

    /**
     * Aplica un impulso en la dirección (dx,dy) normalizada.
     */
    public void aplicarImpulso(float dx, float dy, float fuerza) {
        vx += dx * fuerza;
        vy += dy * fuerza;
    }

    /**
     * Invierte la velocidad en X (rebote horizontal).
     */
    public void rebotarX() {
        vx = -vx;
    }

    /**
     * Invierte la velocidad en Y (rebote vertical).
     */
    public void rebotarY() {
        vy = -vy;
    }

    /**
     * Setea velocidad directa.
     */
    public void setVelocidad(float vx, float vy) {
        this.vx = vx;
        this.vy = vy;
    }

    // =========================
    // Posición
    // =========================
    public float getX() { return x; }
    public float getY() { return y; }

    public void setX(float x) {
        this.x = x;
        hitbox.setX(x);
    }

    public void setY(float y) {
        this.y = y;
        hitbox.setY(y);
    }

    public void setPosicion(float nuevaX, float nuevaY) {
        this.x = nuevaX;
        this.y = nuevaY;
        hitbox.setPosition(nuevaX, nuevaY);
    }

    // =========================
    // Tamaño
    // =========================
    public float getAncho()  { return ancho; }
    public float getAlto() { return alto; }

    public void setTamaño(float ancho, float alto) {
        this.ancho = ancho;
        this.alto = alto;
        hitbox.setSize(ancho, alto);
    }

    // =========================
    // Velocidad (getters)
    // =========================
    public float getVelocidadX() { return vx; }
    public float getVelocidadY() { return vy; }

    // =========================
    // Hitbox
    // =========================
    public Rectangle getHitbox() { return hitbox; }
}
