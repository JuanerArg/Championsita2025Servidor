package com.championsita.jugabilidad.personajes;

import com.championsita.jugabilidad.modelo.AtributosPersonaje;
import com.championsita.jugabilidad.modelo.Personaje;
import com.championsita.jugabilidad.modelo.Equipo;

public class Normal extends Personaje {

    public Normal(String nombre, Equipo equipo) {
        super(
                nombre,
                null, // sin configuracion visual
                new AtributosPersonaje(
                        1.0f,
                        1.8f,
                        100f,
                        5f,
                        10f,
                        0.9f,
                        2f
                )
        );

        this.setEquipo(equipo);
    }
}
