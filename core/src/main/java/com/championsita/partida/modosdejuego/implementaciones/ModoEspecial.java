package com.championsita.partida.modosdejuego.implementaciones;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.championsita.jugabilidad.modelo.Equipo;
import com.championsita.jugabilidad.modelo.HabilidadesEspeciales;
import com.championsita.jugabilidad.modelo.Personaje;
import com.championsita.partida.modosdejuego.ControladorPosicionesIniciales;
import com.championsita.partida.modosdejuego.implementaciones.ModoBase;
import java.util.ArrayList;

/**
 * Versión headless del modo especial.
 * No utiliza entrada gráfica ni selección de habilidades desde menú.
 * El servidor únicamente aplica habilidades ya definidas.
 */
public class ModoEspecial extends ModoBase {

    private final ArrayList<Personaje> jugadoresEnOrden = new ArrayList<>();

    @Override
    protected void onIniciar() {

        // asegurar equipos
        if (ctx.jugadores.get(0).getEquipo() == null) {
            ctx.jugadores.get(0).setEquipo(Equipo.ROJO);
            ctx.jugadores.get(1).setEquipo(Equipo.AZUL);
        }

        // orden por equipo
        jugadoresEnOrden.addAll(ctx.controlador.getJugadoresDelEquipo(Equipo.ROJO));
        jugadoresEnOrden.addAll(ctx.controlador.getJugadoresDelEquipo(Equipo.AZUL));

        // posicionar jugadores sin multiplexer (headless)
        ControladorPosicionesIniciales.posicionar(ctx);

        // asignar habilidades si el cliente/env/config las definió
        aplicarHabilidadesIniciales();
    }

    /**
     * En el servidor no existe una selección por menú.
     * Las habilidades deben venir en ctx.habilidadesEspeciales
     * o definirse mediante configuración previa.
     */
    private void aplicarHabilidadesIniciales() {
        if (ctx.habilidadesEspeciales == null || ctx.habilidadesEspeciales.isEmpty()) return;

        int count = Math.min(jugadoresEnOrden.size(), ctx.habilidadesEspeciales.size());

        for (int i = 0; i < count; i++) {
            Personaje pj = jugadoresEnOrden.get(i);
            HabilidadesEspeciales hab = ctx.habilidadesEspeciales.get(i);

            if (hab != HabilidadesEspeciales.NEUTRO) {
                pj.asignarHabilidad(hab);
                pj.aplicarEfectosPermanentesDeHabilidad();
            }
        }
    }

    @Override
    public InputProcessor getProcesadorEntrada() {
        return null;
    }
}
