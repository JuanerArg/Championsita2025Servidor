package com.championsita.partida.modosdejuego.implementaciones;

import com.badlogic.gdx.InputProcessor;
import com.championsita.partida.modosdejuego.ControladorPosicionesIniciales;
import com.championsita.partida.modosdejuego.implementaciones.ModoBase;
import com.championsita.partida.nucleo.ContextoModoDeJuego;
import com.championsita.partida.modosdejuego.ModoDeJuego;

public class UnoContraUno extends ModoBase {

    @Override
    protected void onIniciar() {
        // Posicionamiento simple, sin input gráfico
        ControladorPosicionesIniciales.posicionar(ctx);
    }

    @Override
    public InputProcessor getProcesadorEntrada() {
        return null;
    }

    @Override
    public int getCantidadDeJugadores() {
        return 2;
    }
}
