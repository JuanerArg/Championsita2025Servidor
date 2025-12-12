package com.championsita.red;

import com.championsita.jugabilidad.entrada.EntradaJugador;
import com.championsita.jugabilidad.entrada.InputServidor;
import com.championsita.partida.ControladorDePartida;
import com.championsita.partida.herramientas.Config;
import com.championsita.red.ConfigFusionFactory;

import java.net.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HiloServidor extends Thread {

    private DatagramSocket socket;

    // Jugadores conectados
    private final Map<Integer, Cliente> jugadores = new HashMap<>();
    private final Map<Integer, InputServidor> inputs = new HashMap<>();
    private int ultimoID = 0;

    // Configs enviadas por los clientes
    private final Map<Integer, ConfigCliente> configs = new HashMap<>();

    // Controlador lógico del partido (solo después de recibir ambas configs)
    private ControladorDePartida controlador = null;

    private static final long TIMEOUT_MS = 5000;


    // ============================================================
    //  CONSTRUCTOR
    // ============================================================
    public HiloServidor() {
        try {
            socket = new DatagramSocket(4321);
            System.out.println("[SERVIDOR] Escuchando en puerto 4321...");
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        // Eliminación automática de clientes inactivos
        new Thread(() -> {
            while (true) {
                limpiarJugadoresInactivos();
                try { Thread.sleep(1000); } catch (Exception ignored) {}
            }
        }, "Servidor-Limpiador").start();
    }


    // ============================================================
    //  LOOP PRINCIPAL
    // ============================================================
    @Override
    public void run() {
        while (true) {
            try {
                byte[] buf = new byte[1024];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);
                procesar(dp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    // ============================================================
    //  PROCESAR MENSAJES
    // ============================================================
    private void procesar(DatagramPacket dp) {
        String msg = new String(dp.getData(), 0, dp.getLength()).trim();

        // ---------------------- HANDSHAKE -----------------------
        if (msg.equals("Hello_There")) {
            enviar("General_Kenobi", dp.getAddress(), dp.getPort());
            return;
        }

        // -------------------- REGISTRAR CLIENTE -----------------
        if (msg.equals("Conectar")) {
            registrarCliente(dp);
            return;
        }

        Cliente remitente = obtenerJugador(dp);
        if (remitente == null) {
            enviar("No_registrado", dp.getAddress(), dp.getPort());
            return;
        }
        remitente.ultimoMensaje = System.currentTimeMillis();

        if(msg.startsWith("INPUT:")){
            procesarInput(msg, remitente.id);

        }

        // -------------------- CONFIG FINAL -----------------------
        if (msg.startsWith("CFG_FINAL=")) {

            ConfigCliente cfg = procesarConfig(msg);
            configs.put(remitente.id, cfg);

            System.out.println("[SERVIDOR] Recibí config final del jugador " + remitente.id);

            if (configs.size() == 2) iniciarPartida();

            return;
        }

        // ------------------ MENSAJES DE LOBBY -------------------
        if (msg.startsWith("SKIN_RIVAL") || msg.startsWith("READY") || msg.startsWith("CFG_")) {
            enviarATodosMenos(msg, remitente);
            System.out.println("CLIENTE " + remitente.id + " : " + msg);
            return;
        }

        if(msg.startsWith("HAB_ESP:")){
            //aca poner como guardamos las habilidades en los distintos jugadores
        }

        // ---------------------- HEARTBEAT ------------------------
        if (msg.equals("PING")) {
            enviar("PONG", remitente.ip, remitente.puerto);
            return;
        }

        if (msg.equals("DISCONNECT")) {
            jugadores.remove(remitente.id);
            broadcast("PLAYER_DISCONNECTED:" + remitente.id);
            return;
        }

        // --------------------- MENSAJES DE JUEGO ----------------
        if (controlador != null) {
            controlador.recibirMensaje(remitente.id, msg);
        }
    }

    private void procesarInput(String msg, int id) {
        String input = msg.substring("INPUT:".length());
        String[] partes = input.split(",");

        InputServidor inputServidor = inputs.get(id);
        if(inputServidor == null) inputs.put(id, new InputServidor());

        for (String p : partes) {
            String[] keyValues = p.split("=");
            if (keyValues.length != 2) continue;

            boolean value = keyValues[1].equals("1");

            switch (keyValues[0]) {
                case "u": inputs.get(id).arriba    = value; break;
                case "d": inputs.get(id).abajo     = value; break;
                case "l": inputs.get(id).izquierda = value; break;
                case "r": inputs.get(id).derecha   = value; break;
                case "a": inputs.get(id).accion    = value; break;
                case "s": inputs.get(id).sprint    = value; break;
            }
        }
    }


    // ============================================================
    //  INICIAR PARTIDA DESPUÉS DE RECIBIR 2 CFG_FINAL
    // ============================================================
    private void iniciarPartida() {

        System.out.println("[SERVIDOR] Ambas configuraciones recibidas. Iniciando partida...");

        ConfigCliente c1 = configs.get(1);
        ConfigCliente c2 = configs.get(2);

        // Fusiona ConfigCliente → Config del servidor
        Config cfgServidor = ConfigFusionFactory.fusionar(c1, c2);

        // Crear controlador lógico usando esa config
        controlador = new ControladorDePartida(cfgServidor);

        // Avisar a los clientes que arranca la partida
        //controlador.
        String estado = controlador.generarEstado();
        System.out.println(estado);
        broadcast(estado);
        broadcast("PARTIDA_INICIADA");

        iniciarLoopServidor();
    }


    // ============================================================
    //  LOOP DE SIMULACIÓN DEL SERVIDOR (60 FPS)
    // ============================================================
    private void iniciarLoopServidor() {

        new Thread(() -> {
            long last = System.nanoTime();

            while (true) {
                long now = System.nanoTime();
                float delta = (now - last) / 1_000_000_000f;
                final long FRAME_TIME = 16_666_666; // ~60 ticks por segundo

                controlador.tick(delta, new ArrayList<>(this.inputs.values()));


                last = now;
                String estado = controlador.generarEstado();
                System.out.println(estado);
                broadcast(estado);

                long sleep = FRAME_TIME - (System.nanoTime() - now);
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep / 1_000_000);
                    } catch (InterruptedException ignored) {}
                }
            }

        }, "Servidor-Tick").start();
    }


    // ============================================================
    //  PARSEAR CFG_FINAL
    // ============================================================
    private ConfigCliente procesarConfig(String msg) {

        msg = msg.substring("CFG_FINAL=".length());
        ConfigCliente cfg = new ConfigCliente();

        String[] partes = msg.split(";");

        for (String p : partes) {

            String[] kv = p.split(",");
            if (kv.length != 2) continue;

            switch (kv[0]) {
                case "campo": cfg.campo = kv[1]; break;
                case "goles": cfg.goles = Integer.parseInt(kv[1]); break;
                case "tiempo": cfg.tiempo = Integer.parseInt(kv[1]); break;
                case "modo": cfg.modo = kv[1]; break;
                    case "skin": cfg.skinsJugadores.add(kv[1]); break;
                case "habilidad": cfg.habilidadesEspeciales.add(kv[1]); break;
            }
        }

        return cfg;
    }


    // ============================================================
    //  REGISTRAR CLIENTE
    // ============================================================
    private void registrarCliente(DatagramPacket dp) {

        if (obtenerJugador(dp) != null) return;

        int id = ++ultimoID;
        Cliente c = new Cliente(id, dp.getAddress(), dp.getPort());
        jugadores.put(id, c);

        enviar("Conectado", c.ip, c.puerto);

        System.out.println("[SERVIDOR] Jugador conectado con ID=" + id);

        if (jugadores.size() == 2)
            broadcast("conexion_establecida");
    }


    // ============================================================
    //  UTILIDADES
    // ============================================================
    private Cliente obtenerJugador(DatagramPacket dp) {
        for (Cliente c : jugadores.values()) {
            if (c.esEste(dp)) return c;
        }
        return null;
    }

    public void enviar(String msg, InetAddress ip, int puerto) {
        try {
            DatagramPacket p = new DatagramPacket(msg.getBytes(), msg.length(), ip, puerto);
            socket.send(p);
        } catch (IOException ignored) {}
    }

    public void broadcast(String msg) {
        jugadores.values().forEach(c -> enviar(msg, c.ip, c.puerto));
    }

    public void enviarATodosMenos(String msg, Cliente remitente) {
        for (Cliente c : jugadores.values()) {
            if (c != remitente) enviar(msg, c.ip, c.puerto);
        }
    }

    private void limpiarJugadoresInactivos() {
        long ahora = System.currentTimeMillis();

        jugadores.values().removeIf(c -> {
            boolean muerto = (ahora - c.ultimoMensaje > TIMEOUT_MS);
            if (muerto) broadcast("PLAYER_DISCONNECTED:" + c.id);
            return muerto;
        });
    }

    public ArrayList<InputServidor> getInputs(){
        return new ArrayList<>(this.inputs.values());
    }



    // ============================================================
    //  CLASE CLIENTE INTERNA
    // ============================================================
    private static class Cliente {
        int id;
        InetAddress ip;
        int puerto;
        long ultimoMensaje = System.currentTimeMillis();

        Cliente(int id, InetAddress ip, int puerto) {
            this.id = id;
            this.ip = ip;
            this.puerto = puerto;
        }

        boolean esEste(DatagramPacket dp) {
            return ip.equals(dp.getAddress()) && puerto == dp.getPort();
        }
    }
}
