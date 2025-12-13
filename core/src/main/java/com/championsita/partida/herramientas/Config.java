package com.championsita.partida.herramientas;

import com.championsita.jugabilidad.modelo.Equipo;
import com.championsita.jugabilidad.modelo.HabilidadesEspeciales;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración puramente lógica del servidor.
 * No depende de menús, skins ni campos gráficos.
 */
public class Config {

    public final String modo; // "1v1", "practica", "especial"
    public final int golesParaGanar;
    public final float tiempoPartido; // en segundos
    public final List<Equipo> equiposJugadores;
    public final ArrayList<String> skinsJugadores;//nuevo
    public final ArrayList<HabilidadesEspeciales> habilidadesEspeciales;

    private Config(Builder b) {
        this.modo = b.modo;
        this.golesParaGanar = b.golesParaGanar;
        this.tiempoPartido = b.tiempoPartido;
        this.skinsJugadores = new ArrayList<>(b.skinsJugadores);//nuevo
        this.equiposJugadores = new ArrayList<> (b.equiposJugadores);
        this.habilidadesEspeciales = new ArrayList<> (b.habilidadesEspeciales);
    }

    public static class Builder {

        private String modo = "1v1";
        private int golesParaGanar = 1;
        private float tiempoPartido = 60f; // valor default

        private ArrayList<String> skinsJugadores = new ArrayList<>();//nuevo
        private List<Equipo> equiposJugadores = new ArrayList<>();
        private List<HabilidadesEspeciales> habilidadesEspeciales = new ArrayList<>();

        public Builder modo(String m) {
            this.modo = m;
            return this;
        }

        public Builder goles(int g) {
            this.golesParaGanar = g;
            return this;
        }

        public Builder tiempo(float tSegundos) {
            this.tiempoPartido = tSegundos;
            return this;
        }

        public Builder agregarEquipo(Equipo e) {
            this.equiposJugadores.add(e);
            return this;
        }
        //nuevo
        public Builder agregarSkin(String sJ){
            this.skinsJugadores.add(sJ);
            return this;
        }

        public Builder habilidades(List<HabilidadesEspeciales> h) {
            this.habilidadesEspeciales.addAll(h);
            return this;
        }

        public Config build() {

            if (equiposJugadores.isEmpty()) {
                throw new IllegalStateException("El servidor necesita asignar equipos a los jugadores.");
            }

            return new Config(this);
        }
    }
}
