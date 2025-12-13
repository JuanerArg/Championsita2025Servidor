package com.championsita.jugabilidad.modelo;

import com.badlogic.gdx.math.Rectangle;

public class Pelota {

    private static final float FRICCION        = 0.95f;
    private static final float FUERZA_DISPARO  = 2.5f;
    private static final float FUERZA_EMPUJE   = 1f;
    private static final float KICK_FORCE      = 0.35f;
    private static final float UMBRAL_MOV      = 0.01f;

    private Personaje ultimoJugadorQueLaToco;
    private Personaje jugadorTocandoPelota;

    private float x, y;
    private float width, height;
    private float velocidadX = 0f;
    private float velocidadY = 0f;
    private Rectangle hitbox;
    public boolean curvaActiva = false;
    public int curvaSigno = 0;

    private boolean huboContactoEsteFrame = false;
    private boolean huboContactoPrevio    = false;

    public Pelota(float xInicial, float yInicial, float escala) {
        this.width = 3 * escala;
        this.height = 3 * escala;
        this.x = xInicial;
        this.y = yInicial;
        hitbox = new Rectangle(x, y, width, height);
    }

    public void registrarContacto(Personaje jugador, float dx, float dy, boolean disparo) {
        huboContactoEsteFrame = true;
        this.jugadorTocandoPelota = jugador;

        if (disparo) {
            velocidadX = dx * FUERZA_DISPARO;
            velocidadY = dy * FUERZA_DISPARO;
        } else {
            if (!huboContactoPrevio) {
                velocidadX += dx * KICK_FORCE;
                velocidadY += dy * KICK_FORCE;
            }
        }
    }

    public void actualizar(float delta) {
        x += velocidadX * delta;
        y += velocidadY * delta;

        velocidadX *= FRICCION;
        velocidadY *= FRICCION;

        if (Math.abs(velocidadX) < UMBRAL_MOV) velocidadX = 0f;
        if (Math.abs(velocidadY) < UMBRAL_MOV) velocidadY = 0f;

        hitbox.setPosition(x, y);

        huboContactoPrevio    = huboContactoEsteFrame;
        huboContactoEsteFrame = false;
    }

    public void detenerPelota() {
        this.velocidadX = 0.0F;
        this.velocidadY = 0.0F;
    }

    public void limpiarContacto() {
        this.huboContactoEsteFrame = false;
        this.huboContactoPrevio = false;
    }

    public void setCurvaActiva(boolean activa, int signo) {
        this.curvaActiva = activa;
        this.curvaSigno = signo;
    }

    public void setVelocidad(float velocidadX, float velocidadY) {
        this.velocidadX = velocidadX;
        this.velocidadY = velocidadY;
    }

    // Getters y Setters
    public Rectangle getHitbox() { return hitbox; }
    public float getX() { return x; }
    public void setX(float x) { this.x = x;}
    public float getY() { return y; }
    public void setY(float y) { this.y = y;}
    public void setPosicion(float nuevaX, float nuevaY) {
        this.x = nuevaX;
        this.y = nuevaY;
        hitbox.setPosition(nuevaX, nuevaY);
    }

    public static float getFuerzaEmpuje()  { return FUERZA_EMPUJE; }
    public static float getFuerzaDisparo() { return FUERZA_DISPARO; }

    public float getWidth()  { return width; }
    public float getHeight() { return height; }

    public float getVelocidadX() { return velocidadX; }
    public float getVelocidadY() { return velocidadY; }
    public void setVelocidadX(float vx) { this.velocidadX = vx; }
    public void setVelocidadY(float vy) { this.velocidadY = vy; }

    public Personaje getUltimoJugadorQueLaToco() { return ultimoJugadorQueLaToco; }
    public void setUltimoJugadorQueLaToco(Personaje pj){ ultimoJugadorQueLaToco = pj; }

    public Personaje getJugadorTocandoPelota() { return jugadorTocandoPelota; }
    public void setJugadorTocandoPelota(Personaje jugador) { this.jugadorTocandoPelota =  jugador; }
    public void resetJugadorTocando() { this.jugadorTocandoPelota = null; }
}
