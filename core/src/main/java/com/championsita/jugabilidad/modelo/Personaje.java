package com.championsita.jugabilidad.modelo;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.championsita.jugabilidad.sistemas.DiccionarioHabilidades;
import com.championsita.jugabilidad.sistemas.ModificadorHabilidad;

import java.util.EnumMap;
import java.util.Map;

public class Personaje {

    // Identidad / lógica
    private final String nombre;
    private final AtributosPersonaje atributos;
    private Equipo equipo;

    // Estado de stamina / sprint
    private float staminaActual;
    private boolean bloqueoRecarga = false;
    private float tiempoBloqueoRecarga = 0f;
    private float distanciaEsteFrame = 0f;

    // Estado de movimiento
    private float x, y;
    private float ancho = 0.6f;
    private float alto  = 1.0f;
    private float tiempoAnimacion = 0f;     // aunque no dibujemos, lo usamos para sincronizar
    private boolean estaMoviendo = false;
    private boolean espacioPresionado = false;
    private boolean sprintActivo    = false;
    private Direccion direccionActual = Direccion.ABAJO;

    private final Rectangle hitbox = new Rectangle();
    private float hbAncho, hbAlto, hbOffsetX, hbOffsetY;

    // Habilidades especiales
    private HabilidadesEspeciales habilidadActual = HabilidadesEspeciales.NEUTRO;
    private ModificadorHabilidad modificador = DiccionarioHabilidades.obtener(HabilidadesEspeciales.NEUTRO);

    private float timerCongelado      = 0f;
    private float timerLentitud       = 0f;
    private float timerBuffVelocidad  = 0f;
    private float timerDebuffVelocidad = 0f;

    // (en cliente había animaciones por dirección; acá no las necesitamos,
    // pero dejamos el mapa si alguna lógica lo usa, aunque vacío)
    private final Map<Direccion, Object> animacionesFake = new EnumMap<>(Direccion.class);

    // -----------------------
    // Constructor
    // -----------------------
    public Personaje(String nombre,
                     Equipo equipo,
                     AtributosPersonaje atributos) {

        this.nombre    = nombre;
        this.equipo    = equipo;
        this.atributos = atributos;

        this.staminaActual = atributos.getStaminaMaxima();

        // hitbox genérico centrado
        this.ancho = 0.6f;
        this.alto  = 0.6f;

        this.x = 1f;
        this.y = 1f;

        this.hbAncho   = ancho * 0.60f;
        this.hbAlto    = alto  * 0.80f;
        this.hbOffsetX = (ancho - hbAncho) / 2f;
        this.hbOffsetY = 0f;

        hitbox.set(x + hbOffsetX, y + hbOffsetY, hbAncho, hbAlto);
    }

    // ==================================================
    // Lógica de velocidad + stamina + habilidades
    // ==================================================
    private void actualizarVelocidadYStamina(boolean sprint, float delta) {

        // 0. Timers
        if (timerCongelado > 0) {
            timerCongelado -= delta;
            if (timerCongelado < 0) timerCongelado = 0;
        }
        if (timerLentitud > 0) {
            timerLentitud -= delta;
            if (timerLentitud < 0) timerLentitud = 0;
        }
        if (timerBuffVelocidad > 0) {
            timerBuffVelocidad -= delta;
            if (timerBuffVelocidad < 0) timerBuffVelocidad = 0;
        }
        if (timerDebuffVelocidad > 0) {
            timerDebuffVelocidad -= delta;
            if (timerDebuffVelocidad < 0) timerDebuffVelocidad = 0;
        }

        // 1. Congelado (PEQUEÑIN)
        if (habilidadActual == HabilidadesEspeciales.PEQUEÑIN && staminaActual <= 0) {
            if (timerCongelado <= 0) {
                timerCongelado = 1.0f;
            }
        }
        if (timerCongelado > 0) {
            distanciaEsteFrame = 0;
            return;
        }

        // 2. Velocidad base / sprint
        float velocidad = atributos.getVelocidadBase();

        if (sprint && staminaActual > 0f) {
            velocidad = atributos.getVelocidadSprint();
            staminaActual -= atributos.getConsumoSprintPorSegundo() * delta;
            if (staminaActual < 0f) staminaActual = 0f;

            if (staminaActual < atributos.getLimiteStaminaBloqueo()) {
                velocidad = atributos.getVelocidadBase();
                bloqueoRecarga = true;
                tiempoBloqueoRecarga = 0f;
            }
        } else {
            if (bloqueoRecarga) {
                if (!sprint) {
                    tiempoBloqueoRecarga += delta;
                    if (tiempoBloqueoRecarga >= atributos.getTiempoBloqueoRecargaSegundos()) {
                        bloqueoRecarga = false;
                    }
                }
            } else {
                staminaActual += atributos.getRecargaStaminaPorSegundo() * delta;
                if (staminaActual > atributos.getStaminaMaxima())
                    staminaActual = atributos.getStaminaMaxima();
            }
        }

        // 3. Lentitud (ATLETA)
        if (habilidadActual == HabilidadesEspeciales.ATLETA && staminaActual <= 0) {
            if (timerLentitud <= 0) {
                timerLentitud = 2.0f;
            }
        }
        if (timerLentitud > 0) {
            velocidad *= 0.6f;
        }

        // 4. EXTREMISTA buff/debuff
        if (timerBuffVelocidad > 0)  velocidad *= 1.25f;
        if (timerDebuffVelocidad > 0) velocidad *= 0.75f;

        distanciaEsteFrame = velocidad * delta;
    }

    private Direccion calcularDireccion(boolean izq, boolean der, boolean arr, boolean ab) {
        if (izq && arr) return Direccion.ARRIBA_IZQUIERDA;
        if (der && arr) return Direccion.ARRIBA_DERECHA;
        if (izq && ab)  return Direccion.ABAJO_IZQUIERDA;
        if (der && ab)  return Direccion.ABAJO_DERECHA;
        if (izq) return Direccion.IZQUIERDA;
        if (der) return Direccion.DERECHA;
        if (arr) return Direccion.ARRIBA;
        if (ab)  return Direccion.ABAJO;
        return direccionActual;
    }

    private void moverYActualizarDireccion(
            boolean izquierda, boolean derecha,
            boolean arriba, boolean abajo
    ) {
        estaMoviendo = izquierda || derecha || arriba || abajo;
        if (!estaMoviendo) return;

        direccionActual = calcularDireccion(izquierda, derecha, arriba, abajo);

        float dx = 0f, dy = 0f;
        if (izquierda) dx -= 1f;
        if (derecha)   dx += 1f;
        if (arriba)    dy += 1f;
        if (abajo)     dy -= 1f;

        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len != 0f) {
            dx /= len;
            dy /= len;
        }

        x += dx * distanciaEsteFrame;
        y += dy * distanciaEsteFrame;

        hitbox.setPosition(x + hbOffsetX, y + hbOffsetY);
    }

    // ==================================================
    // API pública (servidor / entrada de red)
    // ==================================================

    public void actualizarEstadoJugador(
            boolean arriba, boolean abajo,
            boolean izquierda, boolean derecha,
            boolean sprint, float delta
    ) {
        this.sprintActivo = sprint;
        actualizarVelocidadYStamina(sprint, delta);
        moverYActualizarDireccion(izquierda, derecha, arriba, abajo);
    }

    /** Versión simplificada sin sprint (por compatibilidad vieja). */
    public void moverDesdeEntrada(boolean arriba, boolean abajo,
                                  boolean izquierda, boolean derecha,
                                  float delta) {
        distanciaEsteFrame = atributos.getVelocidadBase() * delta;
        moverYActualizarDireccion(izquierda, derecha, arriba, abajo);
    }

    /** Actualización de timers por frame (NO mueve). */
    public void actualizar(float delta) {
        if (estaMoviendo) tiempoAnimacion += delta;
        if (timerCongelado > 0)     timerCongelado     -= delta;
        if (timerLentitud > 0)      timerLentitud      -= delta;
        if (timerBuffVelocidad > 0) timerBuffVelocidad -= delta;
        if (timerDebuffVelocidad > 0) timerDebuffVelocidad -= delta;
    }

    public void limitarMovimiento(float anchoMundo, float altoMundo) {
        x = MathUtils.clamp(x, 0, anchoMundo - ancho);
        y = MathUtils.clamp(y, 0, altoMundo - alto);
        hitbox.setPosition(x + hbOffsetX, y + hbOffsetY);
    }

    // ==================================================
    // Habilidades
    // ==================================================
    public void asignarHabilidad(HabilidadesEspeciales habilidad) {
        this.habilidadActual = habilidad;
        this.modificador = DiccionarioHabilidades.obtener(habilidad);
        timerCongelado = timerLentitud = timerBuffVelocidad = timerDebuffVelocidad = 0f;
    }

    public void aplicarEfectosPermanentesDeHabilidad() {

        // 1. Reescalar tamaño lógico
        this.ancho *= modificador.escalaSprite;
        this.alto  *= modificador.escalaSprite;

        this.hbAncho *= modificador.escalaSprite;
        this.hbAlto  *= modificador.escalaSprite;
        hitbox.setSize(hbAncho, hbAlto);

        // 2. Actualizar atributos
        this.atributos.update(
                atributos.getVelocidadBase() * modificador.velocidadBase,
                atributos.getVelocidadSprint() * modificador.velocidadSprint,
                atributos.getStaminaMaxima() * modificador.staminaMax,
                atributos.getConsumoSprintPorSegundo() * modificador.consumoSprint,
                atributos.getRecargaStaminaPorSegundo() * modificador.recargaStamina,
                atributos.getLimiteStaminaBloqueo(),
                atributos.getTiempoBloqueoRecargaSegundos()
        );

        this.staminaActual = atributos.getStaminaMaxima();
    }

    public void activarBuffVelocidad(float duracion) {
        timerBuffVelocidad = duracion;
    }

    public void activarDebuffVelocidad(float duracion) {
        timerDebuffVelocidad = duracion;
    }

    // ==================================================
    // Getters / setters
    // ==================================================
    public String getNombre() { return nombre; }

    public float getX() { return x; }
    public float getY() { return y; }

    public void setPos(float x, float y) {
        this.x = x;
        this.y = y;
        hitbox.setPosition(x + hbOffsetX, y + hbOffsetY);
    }

    /** Mantengo el nombre viejo para compatibilidad con TODO tu código. */
    public void setPosicion(float x, float y) {
        setPos(x, y);
    }

    public Rectangle getHitbox() { return hitbox; }

    public float getAncho() { return ancho; }
    public float getAlto()  { return alto; }

    public Direccion getDireccion() { return direccionActual; }

    public float getStaminaActual() { return staminaActual; }
    public float getStaminaMaxima() { return atributos.getStaminaMaxima(); }

    public boolean estaSprintPresionado() { return sprintActivo; }

    public void setEspacioPresionado(boolean v) { this.espacioPresionado = v; }
    public boolean estaEspacioPresionado() { return espacioPresionado; }

    public HabilidadesEspeciales getHabilidadActual() { return habilidadActual; }

    public Equipo getEquipo() { return equipo; }
    public void setEquipo(Equipo eq) { this.equipo = eq; }

    public float getTiempoAnimacion() {
        return tiempoAnimacion;
    }

    public void setTiempoAnimacion(float tiempoAnimacion) {
        this.tiempoAnimacion = tiempoAnimacion;
    }
}
