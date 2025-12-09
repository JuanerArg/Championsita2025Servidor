package com.championsita;

import com.championsita.jugabilidad.modelo.Equipo;
import com.championsita.partida.ControladorDePartida;
import com.championsita.partida.herramientas.Config;
import com.championsita.red.HiloServidor;

public class Principal {

    public static void main(String[] args) {

        System.out.println("=== Servidor Championsita Iniciando ===");

        try {
            // ================================
            // CREAR CONFIG MINIMA VÁLIDA
            // ================================
            Config config = new Config.Builder()
                    .modo("1v1")
                    .goles(1)
                    .tiempo(60)
                    .agregarEquipo(Equipo.ROJO)
                    .agregarEquipo(Equipo.AZUL)
                    .build();


            // ================================
            // CONTROLADOR DE PARTIDA
            // ================================
            ControladorDePartida controlador = new ControladorDePartida(config);

            // ================================
            // HILO SERVIDOR UDP
            // ================================
            HiloServidor servidor = new HiloServidor();
            servidor.start();

            System.out.println("Servidor iniciado correctamente.");
            System.out.println("Esperando conexiones...");

            // ================================
            // LOOP DE SIMULACIÓN
            // ================================
            iniciarLoopSimulacion(controlador);

        } catch (Exception e) {
            System.err.println("ERROR al iniciar el servidor:");
            e.printStackTrace();
        }
    }

    private static void iniciarLoopSimulacion(ControladorDePartida controlador) {
        new Thread(() -> {
            long last = System.nanoTime();
            final long FRAME_TIME = 16_666_666; // ~60 ticks por segundo

            while (true) {
                long now = System.nanoTime();
                float delta = (now - last) / 1_000_000_000f;

                controlador.tick(delta);

                last = now;

                long sleep = FRAME_TIME - (System.nanoTime() - now);
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep / 1_000_000);
                    } catch (InterruptedException ignored) {}
                }
            }
        }, "Servidor-TickLoop").start();
    }
}
