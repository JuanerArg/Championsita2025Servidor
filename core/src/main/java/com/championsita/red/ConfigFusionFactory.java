package com.championsita.red;

import com.championsita.jugabilidad.modelo.Equipo;
import com.championsita.jugabilidad.modelo.HabilidadesEspeciales;
import com.championsita.red.ConfigCliente;
import com.championsita.partida.herramientas.Config;

import java.util.ArrayList;
import java.util.List;

public class ConfigFusionFactory {

    /**
     * Convierte dos ConfigCliente (una por jugador)
     * en una Config lógica del servidor.
     */
    public static Config fusionar(ConfigCliente c1, ConfigCliente c2) {

        Config.Builder b = new Config.Builder();

        // ==============================================
        //  MODO (tomamos el del jugador 1)
        // ==============================================
        b.modo(c1.modo);

        // ==============================================
        //  GOLES PARA GANAR
        // ==============================================
        b.goles(c1.goles); // Cliente envía 1, 3, 5 → se usa directo

        // ==============================================
        //  TIEMPO PARTIDO (mapear 1/2/3 → segundos)
        // ==============================================
        float tiempoSeg = mapearTiempo(c1.tiempo);
        b.tiempo(tiempoSeg);

        // ==============================================
        //  EQUIPOS
        //  (si no vienen del cliente, asignamos fijo: ROJO / AZUL)
        // ==============================================
        b.agregarEquipo(Equipo.ROJO);
        b.agregarEquipo(Equipo.AZUL);

        // ==============================================
        //  HABILIDADES ESPECIALES (si el modo es "especial")
        // ==============================================
        List<HabilidadesEspeciales> habs = new ArrayList<>();

        for (String h : c1.habilidadesEspeciales) {
            habs.add(HabilidadesEspeciales.valueOf(h));
        }
        for (String h : c2.habilidadesEspeciales) {
            habs.add(HabilidadesEspeciales.valueOf(h));
        }

        if (!habs.isEmpty()) {
            b.habilidades(habs);
        }

        return b.build();
    }

    // ============================================================
    //   MAPEO TIEMPO CLIENTE → SEGUNDOS
    // ============================================================
    private static float mapearTiempo(int codigo) {
        switch (codigo) {
            case 1: return 60f;   // corto
            case 2: return 120f;  // medio
            case 3: return 180f;  // largo
            default: return 60f;
        }
    }
}
