package com.championsita;

import com.championsita.jugabilidad.entrada.EntradaJugador;
import com.championsita.jugabilidad.entrada.InputServidor;
import com.championsita.jugabilidad.modelo.Equipo;
import com.championsita.partida.ControladorDePartida;
import com.championsita.partida.herramientas.Config;
import com.championsita.red.HiloServidor;

import java.util.ArrayList;

public class Principal {

    public static void main(String[] args) {

        System.out.println("=== Servidor Championsita Iniciando ===");

        try {
            // ================================
            // HILO SERVIDOR UDP
            // ================================
            HiloServidor servidor = new HiloServidor();
            servidor.start();

            System.out.println("Servidor iniciado correctamente.");
            System.out.println("Esperando conexiones...");



        } catch (Exception e) {
            System.err.println("ERROR al iniciar el servidor:");
            e.printStackTrace();
        }
    }
}
