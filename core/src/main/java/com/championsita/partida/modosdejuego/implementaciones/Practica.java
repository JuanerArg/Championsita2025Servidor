package com.championsita.partida.modosdejuego.implementaciones;

import com.badlogic.gdx.InputProcessor;
import com.championsita.partida.modosdejuego.ModoDeJuego;

public class Practica extends ModoBase {

    @Override
    protected void onIniciar() {
        // Un jugador solo, colocación inicial fija
        if (!ctx.jugadores.isEmpty()) {
            ctx.jugadores.get(0).setPos(2f, 2.5f);
        }

        // En servidor no existen entradas de teclado,
        // así que NO se agregan controles aquí.
    }

    @Override
    public InputProcessor getProcesadorEntrada() {
        return null;
    }

    @Override
    public int getCantidadDeJugadores() {
        return 1;
    }
}
