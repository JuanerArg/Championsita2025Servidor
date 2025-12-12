package com.championsita.partida.modosdejuego.implementaciones;

import com.championsita.jugabilidad.entrada.EntradaJugador;
import com.championsita.jugabilidad.modelo.Personaje;
import com.championsita.partida.modosdejuego.ModoDeJuego;
import com.championsita.partida.nucleo.ContextoModoDeJuego;
import com.championsita.jugabilidad.constantes.Constantes;

/**
 * Versión headless del modo base.
 * No usa viewport, ni SpriteBatch, ni LibGDX.
 * Solo contiene lógica de simulación.
 */
public abstract class ModoBase implements ModoDeJuego {

    protected ContextoModoDeJuego ctx;
    protected boolean terminado = false;

    @Override
    public final void iniciar(ContextoModoDeJuego contextoModoDeJuego) {
        this.ctx = contextoModoDeJuego;
        onIniciar();
    }

    /**
     * Llamado al iniciar el modo.
     */
    protected abstract void onIniciar();

    @Override
    public void actualizar(float delta) {
        actualizarEntrada(delta);
        actualizarFisicas(delta);
        actualizarColisiones(delta);
        actualizarPelota(delta);

        ctx.partido.verificarGol(ctx.pelota, ctx.cancha);
    }

    // ------------------------------
    // LÓGICA DE ENTRADA
    // ------------------------------
    protected void actualizarEntrada(float delta) {

        for (EntradaJugador entrada : ctx.controles) {
            if (entrada != null) {
                entrada.actualizar(delta);
            }
        }
    }

    // ------------------------------
    // FÍSICAS HEADLESS
    // ------------------------------
    protected void actualizarFisicas(float delta) {

        float W = Constantes.MUNDO_ANCHO;
        float H = Constantes.MUNDO_ALTO;

        // Limitar al mundo
        for (Personaje pj : ctx.jugadores) {
            if (pj != null) ctx.fisica.limitarPersonajeAlMundo(pj, W, H);
        }
    }

    // ------------------------------
    // COLISIONES HEADLESS
    // ------------------------------
    protected void actualizarColisiones(float delta) {

        // jugador vs jugador
        for (int i = 0; i < ctx.jugadores.size(); i++) {
            for (int j = i + 1; j < ctx.jugadores.size(); j++) {
                Personaje a = ctx.jugadores.get(i);
                Personaje b = ctx.jugadores.get(j);
                if (a != null && b != null) {
                    ctx.colisiones.separar(a, b);
                }
            }
        }

        // jugador vs pelota
        if (ctx.pelota != null) {
            for (Personaje pj : ctx.jugadores) {
                if (pj != null)
                    ctx.colisiones.procesarPelota(pj, ctx.pelota);
            }
        }
    }

    // ------------------------------
    // PELOTA HEADLESS
    // ------------------------------
    protected void actualizarPelota(float delta) {
        if (ctx.pelota != null) {

            ctx.fisica.actualizarPelota(ctx.pelota, delta);

            ctx.fisica.rebotarPelota(
                    ctx.pelota,
                    Constantes.MUNDO_ANCHO,
                    Constantes.MUNDO_ALTO,
                    ctx.cancha
            );
        }
    }

    // ------------------------------
    // ESTADO DEL MODO
    // ------------------------------
    @Override
    public boolean finalizado() {
        return terminado;
    }

    @Override
    public void liberar() {
        // nada para liberar en headless
    }

    @Override
    public int getCantidadDeJugadores() {
        return 2;
    }
}
