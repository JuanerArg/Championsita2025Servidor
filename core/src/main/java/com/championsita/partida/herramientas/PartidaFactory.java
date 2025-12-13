package com.championsita.partida.herramientas;

import com.badlogic.gdx.graphics.Texture;
import com.championsita.jugabilidad.modelo.*;
import com.championsita.jugabilidad.personajes.Normal;
import com.championsita.jugabilidad.constantes.Constantes;

import java.util.ArrayList;

public class PartidaFactory {

    public static MundoPartida crearDesdeConfig(Config config) {

        MundoPartida mundo = new MundoPartida();

        mundo.cancha = new Cancha(0.5f, 0.8f, Constantes.MUNDO_ALTO, Constantes.MUNDO_ANCHO);

        mundo.jugadores = new ArrayList<>();

        for (int i = 0; i < config.equiposJugadores.size(); i++) {

            Equipo equipo = config.equiposJugadores.get(i);

            Personaje pj =
                    new Normal("Jugador" + (i + 1), equipo);

            mundo.jugadores.add(pj);
        }

        mundo.pelota = new Pelota(
                Constantes.MUNDO_ANCHO/2f,
                Constantes.MUNDO_ALTO/2f,
                0.1f
        );

        return mundo;
    }
}
