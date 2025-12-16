// src/main/java/com/championsita/red/HiloServidor.java
package com.championsita.red;

import com.championsita.jugabilidad.entrada.InputServidor;
import com.championsita.partida.ControladorDePartida;
import com.championsita.partida.herramientas.Config;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor UDP (headless) para una partida 1 vs 1.
 *
 * Responsabilidades:
 * 1) Registrar hasta 2 clientes y mantenerlos activos (timeout por inactividad).
 * 2) Recibir mensajes (lobby, configuración, input) y reenviar cuando corresponde.
 * 3) Cuando llegan ambas configuraciones finales: crear el ControladorDePartida y simular a ~60 FPS.
 * 4) Enviar (broadcast) el estado del juego a los clientes.
 *
 * Nota importante:
 * - El orden de los inputs NO puede depender del orden interno de un Map.
 * - El ControladorDePartida típicamente asocia inputs por índice, por eso devolvemos inputs en orden fijo:
 *   índice 0 -> jugador 1, índice 1 -> jugador 2.
 */
public final class HiloServidor extends Thread {

    // =========================
    // Configuración del servidor
    // =========================
    private static final int PUERTO_SERVIDOR = 4321;
    private static final long TIEMPO_MAXIMO_INACTIVIDAD_MILISEGUNDOS = 5_000L;

    // Simulación aproximada a 60 FPS
    private static final long NANOSEGUNDOS_POR_TICK = 16_666_666L;

    // Identificadores fijos para 1 vs 1
    private static final int IDENTIFICADOR_JUGADOR_1 = 1;
    private static final int IDENTIFICADOR_JUGADOR_2 = 2;

    // =========================
    // Protocolo (mensajes)
    // =========================
    private static final String MENSAJE_HANDSHAKE_ENTRANTE = "Hello_There";
    private static final String MENSAJE_HANDSHAKE_SALIENTE = "General_Kenobi";

    private static final String MENSAJE_CONECTAR = "Conectar";
    private static final String MENSAJE_CONECTADO = "Conectado";
    private static final String MENSAJE_NO_REGISTRADO = "No_registrado";

    private static final String MENSAJE_PING = "PING";
    private static final String MENSAJE_PONG = "PONG";
    private static final String MENSAJE_DESCONECTAR = "DISCONNECT";

    private static final String PREFIJO_INPUT = "INPUT:";
    private static final String PREFIJO_CONFIGURACION_FINAL = "CFG_FINAL=";

    private static final String MENSAJE_PARTIDA_INICIADA = "PARTIDA_INICIADA";
    private static final String MENSAJE_PARTIDA_ABORTADA = "PARTIDA_ABORTADA";
    private static final String MENSAJE_CONEXION_ESTABLECIDA = "conexion_establecida";

    private static final String PREFIJO_JUGADOR_DESCONECTADO = "PLAYER_DISCONNECTED:";

    // Lobby (se reenvían al rival)
    private static final String MENSAJE_LOBBY_LISTO = "READY";
    private static final String MENSAJE_LOBBY_SKIN_RIVAL = "SKIN_RIVAL";
    private static final String PREFIJO_LOBBY_CONFIGURACION = "CFG_";

    // =========================
    // Estado interno
    // =========================
    private DatagramSocket socketDatagrama;

    /**
     * Este candado protege operaciones “compuestas” que tocan varias estructuras a la vez
     * (por ejemplo: remover cliente + remover input + remover config + avisar).
     */
    private final Object candadoClientes = new Object();

    private final Map<Integer, Cliente> clientesPorIdentificador = new ConcurrentHashMap<>();
    private final Map<Integer, InputServidor> entradasPorIdentificador = new ConcurrentHashMap<>();
    private final Map<Integer, ConfigCliente> configuracionesFinalesPorIdentificador = new ConcurrentHashMap<>();
    private final Map<String, ManejadorDeMensajes> manejadoresPorTipo = new ConcurrentHashMap<>();

    private volatile boolean servidorActivo = true;
    private volatile boolean verificacionInactividadActiva = true;

    private volatile boolean partidaActiva = false;
    private Thread hiloSimulacion = null;

    private ControladorDePartida controladorDePartida = null;

    /**
     * Segundos transcurridos de la partida (se incrementa cada 1 segundo real).
     * Se usa para HUD y para cortar por tiempo.
     */
    private final AtomicInteger segundosTranscurridos = new AtomicInteger(0);

    // =========================
    // Constructor
    // =========================
    public HiloServidor() {
        inicializarSocketDatagrama();
        registrarManejadoresDeMensajes();
        iniciarVerificacionDeInactividad();
    }

    private void inicializarSocketDatagrama() {
        try {
            socketDatagrama = new DatagramSocket(PUERTO_SERVIDOR);
            System.out.println("[SERVIDOR] Escuchando en puerto " + PUERTO_SERVIDOR + " (UDP) ...");
        } catch (SocketException excepcion) {
            throw new RuntimeException("No se pudo abrir el socket UDP en el puerto " + PUERTO_SERVIDOR, excepcion);
        }

        try {
            socketDatagrama.getBroadcast();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Apaga el servidor de forma prolija: corta hilos y cierra el socket.
     */
    public void apagarServidor() {
        servidorActivo = false;
        detenerVerificacionDeInactividad();
        detenerSimulacionSiEstaActiva();

        if (socketDatagrama != null && !socketDatagrama.isClosed()) {
            socketDatagrama.close();
        }
    }

    // =========================
    // Loop principal (recepción)
    // =========================
    @Override
    public void run() {
        while (servidorActivo) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socketDatagrama.receive(paquete);
                procesarPaquete(paquete);
            } catch (SocketException excepcion) {
                if (!servidorActivo) break; // cierre intencional
                excepcion.printStackTrace();
            } catch (Exception excepcion) {
                excepcion.printStackTrace();
            }
        }
    }

    // =========================
    // Procesamiento de paquetes
    // =========================
    private void procesarPaquete(DatagramPacket paquete) {
        String mensaje = new String(paquete.getData(), 0, paquete.getLength()).trim();

        // 1) Handshake: prueba rápida de conectividad
        if (MENSAJE_HANDSHAKE_ENTRANTE.equals(mensaje)) {
            enviar(MENSAJE_HANDSHAKE_SALIENTE, paquete.getAddress(), paquete.getPort());
            LoggerRed.log("HANDSHAKE", "Hello_There -> General_Kenobi");
            return;
        }

        // 2) Registro de cliente
        if (MENSAJE_CONECTAR.equals(mensaje)) {
            registrarNuevoCliente(paquete);
            return;
        }

        // 3) Mensajes de clientes ya registrados
        Cliente remitente = obtenerClientePorPaquete(paquete);
        if (remitente == null) {
            enviar(MENSAJE_NO_REGISTRADO, paquete.getAddress(), paquete.getPort());
            return;
        }

        // Actualizamos la marca de actividad para que no expire por timeout
        remitente.ultimoMensaje = System.currentTimeMillis();

        String tipo = detectarTipoDeMensaje(mensaje);
        ManejadorDeMensajes manejador = manejadoresPorTipo.get(tipo);

        if (manejador != null) {
            manejador.procesar(remitente, mensaje);
            return;
        }

        // Mensajes no tipados: se derivan al controlador de partida si existe
        if (controladorDePartida != null) {
            controladorDePartida.recibirMensaje(remitente.id, mensaje);
        }
    }

    /**
     * Clasifica el mensaje para enrutarlo a un manejador.
     * Mantener esta función simple hace que sea fácil agregar tipos nuevos.
     */
    private String detectarTipoDeMensaje(String mensaje) {
        if (mensaje.startsWith(PREFIJO_INPUT)) return "INPUT";
        if (mensaje.startsWith(PREFIJO_CONFIGURACION_FINAL)) return "CONFIGURACION_FINAL";
        if (mensaje.equals(MENSAJE_PING)) return "PING";
        if (mensaje.equals(MENSAJE_DESCONECTAR)) return "DESCONECTAR";

        // Lobby: se reenvían al rival
        if (mensaje.startsWith(MENSAJE_LOBBY_SKIN_RIVAL)
                || mensaje.startsWith(MENSAJE_LOBBY_LISTO)
                || mensaje.startsWith(PREFIJO_LOBBY_CONFIGURACION)) {
            return "LOBBY";
        }

        if (mensaje.equals("Modo recibido")) return "MODO_RECIBIDO";
        return "OTRO";
    }

    // =========================
    // Manejadores de mensajes
    // =========================
    private void registrarManejadoresDeMensajes() {
        manejadoresPorTipo.put("PING", (cliente, mensaje) -> enviar(MENSAJE_PONG, cliente.ip, cliente.puerto));

        manejadoresPorTipo.put("MODO_RECIBIDO", (cliente, mensaje) -> LoggerRed.log("MODO", mensaje));

        manejadoresPorTipo.put("LOBBY", (cliente, mensaje) -> {
            enviarATodosMenos(mensaje, cliente);
            LoggerRed.log("LOBBY", "Cliente " + cliente.id + " -> " + mensaje);
        });

        manejadoresPorTipo.put("INPUT", (cliente, mensaje) -> procesarInputDeJugador(mensaje, cliente.id));

        manejadoresPorTipo.put("CONFIGURACION_FINAL", (cliente, mensaje) -> {
            ConfigCliente configuracion = parsearConfiguracionFinal(mensaje);

            synchronized (candadoClientes) {
                configuracionesFinalesPorIdentificador.put(cliente.id, configuracion);
            }

            LoggerRed.log("CFG", "CFG_FINAL recibida del jugador " + cliente.id);

            if (configuracionesFinalesPorIdentificador.size() == 2) {
                iniciarPartidaSiCorresponde();
            }
        });

        manejadoresPorTipo.put("DESCONECTAR", (cliente, mensaje) -> manejarDesconexion(cliente));
    }

    private void manejarDesconexion(Cliente cliente) {
        synchronized (candadoClientes) {
            clientesPorIdentificador.remove(cliente.id);
            entradasPorIdentificador.remove(cliente.id);
            configuracionesFinalesPorIdentificador.remove(cliente.id);
        }

        broadcast(PREFIJO_JUGADOR_DESCONECTADO + cliente.id);

        // Si una partida estaba en curso y queda menos de 2, abortamos para evitar estados inconsistentes
        if (partidaActiva && clientesPorIdentificador.size() < 2) {
            abortarPartidaPorDesconexion("Partida abortada por desconexión de un jugador.");
        }
    }

    // =========================
    // Input
    // =========================
    private void procesarInputDeJugador(String mensaje, int identificadorJugador) {
        String inputCrudo = mensaje.substring(PREFIJO_INPUT.length());
        String[] partes = inputCrudo.split(",");

        entradasPorIdentificador.putIfAbsent(identificadorJugador, new InputServidor());
        InputServidor input = entradasPorIdentificador.get(identificadorJugador);

        for (String parte : partes) {
            String[] claveValor = parte.split("=");
            if (claveValor.length != 2) continue;

            boolean activo = "1".equals(claveValor[1]);

            switch (claveValor[0]) {
                case "u": input.arriba = activo; break;
                case "d": input.abajo = activo; break;
                case "l": input.izquierda = activo; break;
                case "r": input.derecha = activo; break;
                case "a": input.accion = activo; break;
                case "s": input.sprint = activo; break;
                default: break;
            }
        }
    }

    /**
     * Devuelve los inputs SIEMPRE en el mismo orden (jugador 1, jugador 2).
     * Esto evita bugs donde el orden de un Map cambia y se cruzan los controles.
     */
    public ArrayList<InputServidor> obtenerEntradasOrdenadasPorIdentificador() {
        InputServidor entradaJugadorUno = entradasPorIdentificador.getOrDefault(IDENTIFICADOR_JUGADOR_1, new InputServidor());
        InputServidor entradaJugadorDos = entradasPorIdentificador.getOrDefault(IDENTIFICADOR_JUGADOR_2, new InputServidor());

        ArrayList<InputServidor> lista = new ArrayList<>(2);
        lista.add(entradaJugadorUno);
        lista.add(entradaJugadorDos);
        return lista;
    }

    // =========================
    // Configuración final
    // =========================
    private ConfigCliente parsearConfiguracionFinal(String mensaje) {
        String cargaUtil = mensaje.substring(PREFIJO_CONFIGURACION_FINAL.length());
        ConfigCliente configuracion = new ConfigCliente();

        // Formato: id:1;campo:verde;goles:3;tiempo:60;modo:especial;skin:...;habilidad:...
        for (String par : cargaUtil.split(";")) {
            String[] claveValor = par.split(":");
            if (claveValor.length != 2) continue;

            switch (claveValor[0]) {
                case "id":        configuracion.id = Integer.parseInt(claveValor[1]); break;
                case "campo":     configuracion.campo = claveValor[1]; break;
                case "goles":     configuracion.goles = Integer.parseInt(claveValor[1]); break;
                case "tiempo":    configuracion.tiempo = Integer.parseInt(claveValor[1]); break;
                case "modo":      configuracion.modo = claveValor[1]; break;
                case "skin":      configuracion.skinsJugadores.add(claveValor[1]); break;
                case "habilidad": configuracion.habilidadesEspeciales.add(claveValor[1]); break;
                default: break;
            }
        }

        return configuracion;
    }

    // =========================
    // Inicio / aborto de partida
    // =========================
    private void iniciarPartidaSiCorresponde() {
        LoggerRed.log("JUEGO", "Ambas configuraciones recibidas. Iniciando partida...");

        ConfigCliente configuracionUno = configuracionesFinalesPorIdentificador.get(IDENTIFICADOR_JUGADOR_1);
        ConfigCliente configuracionDos = configuracionesFinalesPorIdentificador.get(IDENTIFICADOR_JUGADOR_2);

        if (configuracionUno == null || configuracionDos == null) {
            LoggerRed.log("JUEGO", "No se pudo iniciar: falta CFG_FINAL de algún jugador.");
            return;
        }

        Config configuracionServidor = ConfigFusionFactory.fusionar(configuracionUno, configuracionDos);
        controladorDePartida = new ControladorDePartida(configuracionServidor);

        partidaActiva = true;
        segundosTranscurridos.set(0);

        broadcast(controladorDePartida.generarEstado());
        broadcast(MENSAJE_PARTIDA_INICIADA);

        iniciarBucleDeSimulacion();
    }

    private void abortarPartidaPorDesconexion(String motivo) {
        LoggerRed.log("SERVIDOR", motivo);

        detenerSimulacionSiEstaActiva();

        controladorDePartida = null;
        partidaActiva = false;

        synchronized (candadoClientes) {
            clientesPorIdentificador.clear();
            entradasPorIdentificador.clear();
            configuracionesFinalesPorIdentificador.clear();
        }

        broadcast(MENSAJE_PARTIDA_ABORTADA);
    }

    private void detenerSimulacionSiEstaActiva() {
        partidaActiva = false;
        if (hiloSimulacion != null) {
            hiloSimulacion.interrupt();
            hiloSimulacion = null;
        }
    }

    // =========================
    // Bucle de simulación (~60 FPS)
    // =========================
    private void iniciarBucleDeSimulacion() {
        hiloSimulacion = new Thread(() -> {
            long ultimoNanoSegundos = System.nanoTime();
            double acumuladorDeSegundos = 0.0;

            while (partidaActiva) {
                long ahoraNanoSegundos = System.nanoTime();
                double deltaSegundos = (ahoraNanoSegundos - ultimoNanoSegundos) / 1_000_000_000.0;
                ultimoNanoSegundos = ahoraNanoSegundos;
                acumuladorDeSegundos += deltaSegundos;

                if (controladorDePartida == null) break;

                // 1) Simulación (delta real)
                controladorDePartida.tick((float) deltaSegundos, obtenerEntradasOrdenadasPorIdentificador());

                // 2) Tiempo para HUD (segundos)
                controladorDePartida.tiempo = segundosTranscurridos.get();

                // 3) Enviar estado a clientes
                String estado = controladorDePartida.generarEstado();
                broadcast(estado);

                // 4) Incrementar contador cada 1 segundo real
                if (acumuladorDeSegundos >= 1.0) {
                    segundosTranscurridos.incrementAndGet();
                    acumuladorDeSegundos -= 1.0;
                }

                // 5) Finalización por tiempo límite
                if (segundosTranscurridos.get() >= controladorDePartida.getConfig().tiempoPartido) {
                    controladorDePartida.getPartido().calcularGanadorPorFaltaDeTiempo();
                }

                // 6) Finalización por ganador
                if (controladorDePartida.getPartido().ganador != null) {
                    // Espera para asegurar que el último STATE con win=... llegue a los clientes
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                    broadcast(MENSAJE_DESCONECTAR);
                    break;
                }

                // 7) Sleep para acercarnos a 60 FPS (sin “clavar” CPU)
                long nanoSegundosAEsperar = NANOSEGUNDOS_POR_TICK - (System.nanoTime() - ahoraNanoSegundos);
                if (nanoSegundosAEsperar > 0) {
                    try {
                        Thread.sleep(nanoSegundosAEsperar / 1_000_000L);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }

            partidaActiva = false;
            hiloSimulacion = null;
        }, "Servidor-Simulacion");

        hiloSimulacion.start();
    }

    // =========================
    // Registro de clientes / timeout
    // =========================
    private void registrarNuevoCliente(DatagramPacket paquete) {
        if (obtenerClientePorPaquete(paquete) != null) return;

        int identificadorDisponible = obtenerIdentificadorDisponibleParaNuevoCliente();
        if (identificadorDisponible == -1) return; // ya hay 2 jugadores

        Cliente nuevoCliente = new Cliente(identificadorDisponible, paquete.getAddress(), paquete.getPort());

        synchronized (candadoClientes) {
            clientesPorIdentificador.put(identificadorDisponible, nuevoCliente);
        }

        enviar(MENSAJE_CONECTADO, nuevoCliente.ip, nuevoCliente.puerto);
        enviar("Registrado con ID " + identificadorDisponible, nuevoCliente.ip, nuevoCliente.puerto);

        LoggerRed.log("HANDSHAKE", "Cliente conectado con ID=" + identificadorDisponible);

        if (clientesPorIdentificador.size() == 2) {
            broadcast(MENSAJE_CONEXION_ESTABLECIDA);
        }
    }

    private int obtenerIdentificadorDisponibleParaNuevoCliente() {
        boolean existeJugadorUno = clientesPorIdentificador.containsKey(IDENTIFICADOR_JUGADOR_1);
        boolean existeJugadorDos = clientesPorIdentificador.containsKey(IDENTIFICADOR_JUGADOR_2);

        if (!existeJugadorUno) return IDENTIFICADOR_JUGADOR_1;
        if (!existeJugadorDos) return IDENTIFICADOR_JUGADOR_2;
        return -1;
    }

    /**
     * Si un cliente deja de enviar mensajes por un tiempo, se lo elimina.
     * Si estábamos jugando y queda menos de 2, abortamos la partida.
     */
    private boolean eliminarClientesInactivos() {
        long ahoraMilisegundos = System.currentTimeMillis();
        List<Integer> identificadoresInactivos = new ArrayList<>();

        for (Map.Entry<Integer, Cliente> entry : clientesPorIdentificador.entrySet()) {
            Cliente cliente = entry.getValue();
            if ((ahoraMilisegundos - cliente.ultimoMensaje) > TIEMPO_MAXIMO_INACTIVIDAD_MILISEGUNDOS) {
                identificadoresInactivos.add(entry.getKey());
            }
        }

        if (identificadoresInactivos.isEmpty()) return false;

        synchronized (candadoClientes) {
            for (int identificador : identificadoresInactivos) {
                Cliente cliente = clientesPorIdentificador.remove(identificador);
                entradasPorIdentificador.remove(identificador);
                configuracionesFinalesPorIdentificador.remove(identificador);

                if (cliente != null) {
                    broadcast(PREFIJO_JUGADOR_DESCONECTADO + cliente.id);
                    System.out.println("[SERVIDOR] Cliente " + cliente.id + " eliminado por inactividad (timeout).");
                }
            }
        }

        if (controladorDePartida != null && clientesPorIdentificador.size() < 2) {
            abortarPartidaPorDesconexion("Partida abortada por timeout de un jugador.");
            return true;
        }

        return false;
    }

    private Cliente obtenerClientePorPaquete(DatagramPacket paquete) {
        for (Cliente cliente : clientesPorIdentificador.values()) {
            if (cliente.esEste(paquete)) return cliente;
        }
        return null;
    }

    // =========================
    // Verificación de inactividad (hilo auxiliar)
    // =========================
    private void iniciarVerificacionDeInactividad() {
        Thread limpiador = new Thread(() -> {
            while (verificacionInactividadActiva) {
                if (eliminarClientesInactivos()) {
                    detenerVerificacionDeInactividad();
                    return;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Servidor-Limpiador");

        limpiador.start();
    }

    private void detenerVerificacionDeInactividad() {
        verificacionInactividadActiva = false;
    }

    // =========================
    // Comunicación
    // =========================
    public void enviar(String mensaje, InetAddress direccionIp, int puerto) {
        try {
            DatagramPacket paquete = new DatagramPacket(mensaje.getBytes(), mensaje.length(), direccionIp, puerto);
            socketDatagrama.send(paquete);
        } catch (IOException ignored) {
            // Si un cliente se cae justo al enviar, no debe tumbar el servidor.
        }
    }

    public void broadcast(String mensaje) {
        for (Cliente cliente : clientesPorIdentificador.values()) {
            enviar(mensaje, cliente.ip, cliente.puerto);
        }
    }

    public void enviarATodosMenos(String mensaje, Cliente remitente) {
        for (Cliente cliente : clientesPorIdentificador.values()) {
            if (cliente != remitente) {
                enviar(mensaje, cliente.ip, cliente.puerto);
            }
        }
    }
}
