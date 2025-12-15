package com.championsita.red;

import com.championsita.jugabilidad.entrada.InputServidor;
import com.championsita.partida.ControladorDePartida;
import com.championsita.partida.herramientas.Config;

import java.net.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class HiloServidor extends Thread {

    private static final int PUERTO = 4321;
    private static final long TIMEOUT_MS = 5000;
    private static final int TICK_NS = 16_666_666; // ~60 FPS
    private final AtomicInteger contadorTick = new AtomicInteger(0);
    private Thread hiloTick;
    private static final int UMBRAL_TICKS = 1000; // Ajustable

    private DatagramSocket socket;

    private final Map<Integer, Cliente> jugadores = new HashMap<>();
    private final Map<Integer, InputServidor> inputs = new HashMap<>();
    private final Map<String, ManejadorDeMensajes> handlers = new HashMap<>();
    private final Map<Integer, ConfigCliente> configuracionesRecibidas = new HashMap<>();

    private ControladorDePartida controlador = null;
    private boolean chequeoActivo = true;
    private volatile boolean partidaActiva = false;
    // ------------------------------------------------------------
    // CONSTRUCTOR
    // ------------------------------------------------------------

    public HiloServidor() {
        inicializarSocket();
        registrarHandlers();
        iniciarChequeoDeInactividad();
    }

    private void inicializarSocket() {
        try {
            socket = new DatagramSocket(PUERTO);
            System.out.println("[SERVIDOR] Escuchando en puerto " + PUERTO + "...");
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Limpia clientes desconectados o inactivos cada segundo.
     */
    private void iniciarChequeoDeInactividad() {
        Thread limpiador = new Thread(() -> {
            while (chequeoActivo) {
                if (limpiarClientesInactivos()) {
                    detenerChequeo();
                    return; // sale del hilo limpiamente
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return; // si lo interrumpen, termina
                }
            }
        }, "Servidor-Limpiador");

        limpiador.start();
    }

    private void detenerChequeo() {
        chequeoActivo = false;
    }

    // ------------------------------------------------------------
    // LOOP PRINCIPAL
    // ------------------------------------------------------------

    @Override
    public void run() {
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
                procesarPaquete(paquete);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ------------------------------------------------------------
    // PROCESAMIENTO DE MENSAJES
    // ------------------------------------------------------------

    /**
     * Procesa un paquete entrante y despacha su mensaje.
     */
    private void procesarPaquete(DatagramPacket paquete) {
        String mensaje = new String(paquete.getData(), 0, paquete.getLength()).trim();

        if (mensaje.equals("Hello_There")) {
            enviar("General_Kenobi", paquete.getAddress(), paquete.getPort());
            LoggerRed.log("HANDSHAKE", "Cliente respondió Hello_There → envío General_Kenobi");
            return;
        }

        if (mensaje.equals("Conectar")) {
            registrarNuevoCliente(paquete);
            return;
        }

        Cliente remitente = obtenerClientePorPaquete(paquete);
        if (remitente == null) {
            enviar("No_registrado", paquete.getAddress(), paquete.getPort());
            return;
        }

        remitente.ultimoMensaje = System.currentTimeMillis();

        String tipo = detectarTipoMensaje(mensaje);
        ManejadorDeMensajes handler = handlers.get(tipo);

        if (handler != null) {
            handler.procesar(remitente, mensaje);
        } else if (controlador != null) {
            controlador.recibirMensaje(remitente.id, mensaje);
        }
    }

    private String detectarTipoMensaje(String mensaje) {
        if (mensaje.startsWith("INPUT:")) return "INPUT";
        if (mensaje.startsWith("CFG_FINAL=")) return "CFG_FINAL";
        if (mensaje.startsWith("SKIN_RIVAL") || mensaje.startsWith("READY") || mensaje.startsWith("CFG_")) return "LOBBY";
        if (mensaje.equals("PING")) return "PING";
        if (mensaje.equals("DISCONNECT")) return "DISCONNECT";
        if (mensaje.equals("Modo recibido")) return "Modo recibido";
        return "OTRO";
    }

    // ------------------------------------------------------------
    // REGISTRO DE HANDLERS
    // ------------------------------------------------------------

    /**
     * Registra funciones manejadoras de cada tipo de mensaje.
     */
    private void registrarHandlers() {
        handlers.put("PING", (rem, msg) -> enviar("PONG", rem.ip, rem.puerto));
        handlers.put("Modo recibido", (rem, msg) -> LoggerRed.log("Modo recibido", msg));
        handlers.put("DISCONNECT", (rem, msg) -> {
            jugadores.remove(rem.id);
            inputs.remove(rem.id);
            configuracionesRecibidas.remove(rem.id);

            broadcast("PLAYER_DISCONNECTED:" + rem.id);

            if (partidaActiva && jugadores.size() < 2) {
                partidaActiva = false;
                controlador = null;

                jugadores.clear();
                inputs.clear();
                configuracionesRecibidas.clear();

                LoggerRed.log("SERVIDOR", "Partida abortada por desconexión de un jugador.");
                broadcast("PARTIDA_ABORTADA");
            }
        });

        handlers.put("CFG_FINAL", (rem, msg) -> {
            ConfigCliente config = parsearConfigFinal(msg);
            configuracionesRecibidas.put(rem.id, config);
            LoggerRed.log("CFG", "Recibí configuración final del jugador " + rem.id);

            if (configuracionesRecibidas.size() == 2) {
                iniciarPartida();
            }
        });

        handlers.put("INPUT", (rem, msg) -> procesarInputDeJugador(msg, rem.id));

        handlers.put("LOBBY", (rem, msg) -> {
            enviarATodosMenos(msg, rem);
            LoggerRed.log("LOBBY", "Cliente " + rem.id + " : " + msg);
        });
    }

    // ------------------------------------------------------------
    // INPUT
    // ------------------------------------------------------------

    /**
     * Actualiza el estado de input para un jugador.
     */
    private void procesarInputDeJugador(String mensaje, int id) {
        String inputRaw = mensaje.substring("INPUT:".length());
        String[] partes = inputRaw.split(",");

        inputs.putIfAbsent(id, new InputServidor());
        InputServidor input = inputs.get(id);

        for (String p : partes) {
            String[] kv = p.split("=");
            if (kv.length != 2) continue;

            boolean activo = kv[1].equals("1");

            switch (kv[0]) {
                case "u": input.arriba = activo; break;
                case "d": input.abajo = activo; break;
                case "l": input.izquierda = activo; break;
                case "r": input.derecha = activo; break;
                case "a": input.accion = activo; break;
                case "s": input.sprint = activo; break;
            }
        }
    }

    // ------------------------------------------------------------
    // CONFIGURACIÓN
    // ------------------------------------------------------------

    /**
     * Parsea la configuración final enviada por un cliente.
     */
    private ConfigCliente parsearConfigFinal(String mensaje) {
        mensaje = mensaje.substring("CFG_FINAL=".length());
        ConfigCliente config = new ConfigCliente();
        System.out.println("mensaje => " + mensaje);
        for (String p : mensaje.split(";")) {
            System.out.println("p => " + p);
            String[] kv = p.split(":");
            if (kv.length != 2) continue;
            System.out.println("kv => " + Arrays.toString(kv));
            switch (kv[0]) {
                case "id":        config.id = Integer.parseInt(kv[1]); break;
                case "campo":     config.campo = kv[1]; break;
                case "goles":     config.goles = Integer.parseInt(kv[1]); break;
                case "tiempo":    config.tiempo = Integer.parseInt(kv[1]); break;
                case "modo":      config.modo = kv[1]; break;
                case "skin":      config.skinsJugadores.add(kv[1]); break;
                case "habilidad": config.habilidadesEspeciales.add(kv[1]); System.out.println(kv[1]);break;
            }
        }

        return config;
    }

    // ------------------------------------------------------------
    // INICIO DE PARTIDA Y LOOP DEL SERVIDOR
    // ------------------------------------------------------------

    /**
     * Fusiona configuraciones y lanza la partida.
     */
    private void iniciarPartida() {
        LoggerRed.log("JUEGO", "Ambas configuraciones recibidas. Iniciando partida...");

        ConfigCliente c1 = configuracionesRecibidas.get(1);
        ConfigCliente c2 = configuracionesRecibidas.get(2);

        Config cfgServidor = ConfigFusionFactory.fusionar(c1, c2);
        controlador = new ControladorDePartida(cfgServidor);

        partidaActiva = true;

        broadcast(controlador.generarEstado());
        broadcast("PARTIDA_INICIADA");

        iniciarLoopTickServidor();
    }

    /**
     * Inicia el loop de simulación del servidor (60 FPS).
     */

    private void iniciarLoopTickServidor() {
        contadorTick.set(0);
        partidaActiva = true;
        hiloTick = new Thread(() -> {
            long last = System.nanoTime();
            double acumuladorDelta = 0.0;

            while (partidaActiva) {
                long now = System.nanoTime();
                double delta = (now - last) / 1_000_000_000.0; // en segundos
                last = now;
                acumuladorDelta += delta;

                if (controlador == null) break;

                // Tick normal del juego (~60 FPS)
                controlador.tick((float) delta, getInputs());
                controlador.tiempo = contadorTick.get();
                String estado = controlador.generarEstado();
                broadcast(estado);

                // Si pasó un segundo real, aumentamos contador
                if (acumuladorDelta >= 1.0) {
                    contadorTick.incrementAndGet();
                    acumuladorDelta -= 1.0;
                }

                // Terminar si se alcanzó tiempo límite
                if (contadorTick.get() >= controlador.getConfig().tiempoPartido) {
                    controlador.getPartido().calcularGanadorPorFaltaDeTiempo();
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                // Terminar si hay ganador
                if (controlador.getPartido().ganador != null) {
                    if (estado.endsWith("win=" + controlador.getPartido().ganador.toString())) {
                        try {
                            Thread.sleep(4000);
                        } catch (InterruptedException e) {
                            break;
                        }
                        broadcast("DISCONNECT");
                        break;
                    }
                }

                // Esperar hasta el próximo tick (~60 FPS)
                long sleep = TICK_NS - (System.nanoTime() - now);
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep / 1_000_000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }

            partidaActiva = false;
            hiloTick = null;
        }, "Servidor-Tick");

        hiloTick.start();
    }


    // ------------------------------------------------------------
    // CLIENTES
    // ------------------------------------------------------------

    /**
     * Registra un nuevo cliente si aún no estaba.
     */
    private void registrarNuevoCliente(DatagramPacket dp) {
        if (obtenerClientePorPaquete(dp) != null) return;

        int id = jugadores.containsKey(1) ? (jugadores.containsKey(2) ? -1 : 2) : 1;
        if (id == -1) return; // No hay lugar disponible (ya hay 2 jugadores)

        Cliente nuevo = new Cliente(id, dp.getAddress(), dp.getPort());
        jugadores.put(id, nuevo);

        enviar("Conectado", nuevo.ip, nuevo.puerto);
        enviar("Registrado con ID " + id, nuevo.ip, nuevo.puerto);

        LoggerRed.log("HANDSHAKE", "Cliente conectado con ID=" + id);

        if (jugadores.size() == 2) {
            broadcast("conexion_establecida");
        }
    }

    /**
     * Elimina clientes inactivos y avisa a los demás.
     */
    private boolean limpiarClientesInactivos() {
        long ahora = System.currentTimeMillis();
        List<Integer> idsInactivos = new ArrayList<>();

        for (Map.Entry<Integer, Cliente> entry : jugadores.entrySet()) {
            Cliente c = entry.getValue();
            if ((ahora - c.ultimoMensaje) > TIMEOUT_MS) {
                idsInactivos.add(entry.getKey());
                broadcast("PLAYER_DISCONNECTED:" + c.id);
                System.out.println("[SERVIDOR] Cliente " + c.id + " eliminado por timeout");
            }
        }

        for (int id : idsInactivos) {
            jugadores.remove(id);
            inputs.remove(id);
            configuracionesRecibidas.remove(id);
        }

        if (!idsInactivos.isEmpty() && controlador != null && jugadores.size() < 2) {
            controlador = null;
            partidaActiva = false;

            // ⚠️ limpiar completamente
            jugadores.clear();
            inputs.clear();
            configuracionesRecibidas.clear();

            LoggerRed.log("SERVIDOR", "Partida abortada por desconexión de un jugador.");
            broadcast("PARTIDA_ABORTADA");
            return true;
        }

        return false;
    }



    /**
     * Busca un cliente que coincida con el paquete recibido.
     */
    private Cliente obtenerClientePorPaquete(DatagramPacket dp) {
        for (Cliente c : jugadores.values()) {
            if (c.esEste(dp)) return c;
        }
        return null;
    }

    // ------------------------------------------------------------
    // COMUNICACIÓN
    // ------------------------------------------------------------

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
        jugadores.values().forEach(c -> {
            if (c != remitente) enviar(msg, c.ip, c.puerto);
        });
    }

    public ArrayList<InputServidor> getInputs() {
        return new ArrayList<>(inputs.values());
    }
}
