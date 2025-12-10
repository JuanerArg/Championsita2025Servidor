package com.championsita.red;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración simple enviada desde el cliente al servidor.
 * NO contiene enums ni objetos pesados.
 * Solo datos elegidos por los menús.
 */
public class ConfigCliente {

    // Skins seleccionadas (orden: J1, J2)
    public List<String> skinsJugadores = new ArrayList<>();

    // Nombre del campo (string simple, ej: "BOSQUE", "NIEVE")
    public String campo;

    // Goles objetivo (1, 3, 5)
    public int goles;

    // Tiempo de partido (1=corto, 2=medio, 3=largo)
    public int tiempo;

    // Modo de juego ("1v1", "practica", "especial")
    public String modo;

    // Equipos (OPCIONAL) — versiones string, ej: "ROJO", "AZUL"
    public List<String> equiposJugadores = new ArrayList<>();

    // Habilidades (solo para modo especial)
    public List<String> habilidadesEspeciales = new ArrayList<>();


    /** Builder simple para los menús */
    public static class Builder {

        private final ConfigCliente c = new ConfigCliente();

        public Builder agregarSkin(String skin) {
            c.skinsJugadores.add(skin);
            return this;
        }

        public Builder agregarEquipo(String equipo) {
            if (equipo != null)
                c.equiposJugadores.add(equipo);
            return this;
        }

        /** habilidades ya convertidas a Strings (Enum.name()) */
        public Builder agregarHabilidades(List<String> habilidades) {
            if (habilidades != null)
                c.habilidadesEspeciales.addAll(habilidades);
            return this;
        }

        public Builder campo(String campoNombre) {
            c.campo = campoNombre;
            return this;
        }

        public Builder goles(int goles) {
            c.goles = goles;
            return this;
        }

        public Builder tiempo(int tiempo) {
            c.tiempo = tiempo;
            return this;
        }

        public Builder modo(String modo) {
            c.modo = modo;
            return this;
        }

        public ConfigCliente build() {

            if (c.modo == null)
                c.modo = "1v1";

            return c;
        }
    }
}
