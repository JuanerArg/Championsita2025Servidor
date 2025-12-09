package com.championsita.partida.modosdejuego;

import com.championsita.partida.nucleo.ContextoModoDeJuego;

public class ControladorPosicionesIniciales {

    public static void posicionar(ContextoModoDeJuego ctx) {
        // Jugadores
        if (ctx.jugadores.size() > 0)
            ctx.jugadores.get(0).setPos(1.5f, 2.5f);

        if (ctx.jugadores.size() > 1)
            ctx.jugadores.get(1).setPos(6.5f, 2.5f);

        // Pelota en el centro
        if (ctx.pelota != null)
            ctx.pelota.setPosicion(4f, 2.5f);
    }
}
