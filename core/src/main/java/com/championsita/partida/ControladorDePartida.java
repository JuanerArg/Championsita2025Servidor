package com.championsita.partida;

import com.championsita.jugabilidad.modelo.Equipo;
import com.championsita.jugabilidad.modelo.Pelota;
import com.championsita.jugabilidad.modelo.Personaje;
import com.championsita.jugabilidad.modelo.Cancha;
import com.championsita.jugabilidad.modelo.Arco;
import com.championsita.jugabilidad.sistemas.SistemaColisiones;
import com.championsita.jugabilidad.sistemas.SistemaFisico;
import com.championsita.jugabilidad.sistemas.SistemaPartido;
import com.championsita.partida.herramientas.Config;
import com.championsita.partida.herramientas.MundoPartida;
import com.championsita.partida.herramientas.PartidaFactory;
import com.championsita.partida.modosdejuego.ModoDeJuego;
import com.championsita.partida.modosdejuego.implementaciones.ModoBase;
import com.championsita.partida.modosdejuego.implementaciones.ModoEspecial;
import com.championsita.partida.modosdejuego.implementaciones.UnoContraUno;
import com.championsita.partida.nucleo.ContextoModoDeJuego;

import java.util.ArrayList;
import java.util.List;

/**
 * Controlador de la partida en el SERVIDOR (headless).
 *
 * - NO dibuja nada, solo maneja lógica.
 * - Se crea recién cuando el servidor ya tiene la Config
 *   armada a partir de las dos ConfigCliente.
 * - Expone:
 *      - tick(delta)         → avanzar simulación
 *      - recibirMensaje(id, msg) → input de jugadores
 *      - generarEstado()     → serializar estado para enviar a clientes
 */
public class ControladorDePartida {

    /** Configuración fusionada de ambos clientes (server-side). */
    private final Config config;

    /** Modo de juego (servidor). */
    private ModoDeJuego modoJuego;

    /** Entidades principales. */
    private final ArrayList<Personaje> jugadores = new ArrayList<>();
    private Pelota pelota;
    private Cancha cancha;

    /** Sistemas lógicos. */
    private SistemaFisico fisica;
    private SistemaColisiones colisiones;
    private SistemaPartido partido;

    public ControladorDePartida(Config config) {
        this.config = config;

        // ==================================================
        // 1) Crear mundo (cancha, pelota, jugadores, etc.)
        // ==================================================
        MundoPartida mundo = PartidaFactory.crearDesdeConfig(config);
        this.cancha = mundo.cancha;
        this.pelota = mundo.pelota;
        this.jugadores.addAll(mundo.jugadores);

        // ==================================================
        // 2) Crear sistemas lógicos
        // ==================================================
        this.fisica = new SistemaFisico();
        this.colisiones = new SistemaColisiones();
        this.partido = new SistemaPartido(); // Ajustá si tu ctor real recibe parámetros

        // ==================================================
        // 3) Elegir modo de juego SERVIDOR según config
        // ==================================================
        this.modoJuego = crearModoServidorDesdeConfig(config);

        // ==================================================
        // 4) Crear contexto para el modo (sin viewport/batch)
        // ==================================================
        ContextoModoDeJuego ctx;
        if (modoJuego instanceof ModoEspecial &&
                config.habilidadesEspeciales != null &&
                !config.habilidadesEspeciales.isEmpty()) {

            ctx = new ContextoModoDeJuego(
                    null,           // viewport (no se usa en servidor)
                    null,           // batch (no se usa en servidor)
                    cancha,
                    fisica,
                    colisiones,
                    partido,
                    jugadores,
                    this,
                    config.habilidadesEspeciales
            );

        } else {
            ctx = new ContextoModoDeJuego(
                    null,           // viewport
                    null,           // batch
                    cancha,
                    fisica,
                    colisiones,
                    partido,
                    jugadores,
                    this
            );
        }

        ctx.pelota = this.pelota;

        // ==================================================
        // 5) Iniciar el modo de juego (servidor)
        // ==================================================
        this.modoJuego.iniciar(ctx);
    }

    // =========================================================
    //  Modo de juego según config (server-side)
    // =========================================================
    private ModoDeJuego crearModoServidorDesdeConfig(Config cfg) {

        // Si tenés un campo cfg.modo (String tipo "1v1", "especial", "practica"),
        // podrías hacer algo así:
        //
        // switch (cfg.modo) {
        //     case "especial": return new ModoEspecial();
        //     case "practica": return new ModoPracticaServidor(...);
        //     default:         return new UnoContraUno();
        // }

        if (cfg != null && cfg.habilidadesEspeciales != null && !cfg.habilidadesEspeciales.isEmpty()) {
            // Si hay habilidades especiales configuradas, asumimos modo especial.
            return new ModoEspecial();
        }

        // Por defecto: Uno contra Uno
        return new UnoContraUno();
    }

    // =========================================================
    //  Tick de simulación (llamado desde un loop en el servidor)
    // =========================================================
    public void tick(float delta) {
        if (modoJuego == null) return;

        // Si tus modos viejos también implementan ModoDeJuego, esto alcanza.
        modoJuego.actualizar(delta);
    }

    // =========================================================
    //  Utilidad: jugadores por equipo (lo usaba ModoEspecial)
    // =========================================================
    public ArrayList<Personaje> getJugadoresDelEquipo(Equipo equipo) {
        ArrayList<Personaje> lista = new ArrayList<>();

        for (Personaje pj : jugadores) {
            if (pj != null && pj.getEquipo() == equipo) {
                lista.add(pj);
            }
        }

        return lista;
    }

    // =========================================================
    //  Integración con red: mensajes de los jugadores
    //  (llamado por HiloServidor.recibirMensaje)
    // =========================================================
    public void recibirMensaje(int idJugador, String mensaje) {
        // Acá deberías mapear "mensaje" a input para el jugador idJugador.
        // Ejemplos posibles de protocolo:
        //
        //  - "INPUT;id=1;u=0;d=1;l=0;r=1;chutar=0"
        //  - "MOVE:1:ARRIBA"
        //
        // Por ahora lo dejo como log / TODO para que lo completes después.
        System.out.println("Mensaje de jugador " + idJugador + ": " + mensaje);

        // TODO: acá podés:
        //  - Obtener el Personaje correspondiente a idJugador
        //  - Actualizar algún objeto de entrada (EntradaJugador) que
        //    tu SistemaFisico / ModoDeJuego use en actualizar(delta).
    }

    // =========================================================
    //  Serialización del estado para los clientes
    // =========================================================
    /**
     * Genera el estado del partido en el formato que el cliente
     * espera en procesarEstadoPartida:
     *
     * El HiloServidor envía:
     *   "STATE;" + generarEstado()
     *
     * Y el cliente hace:
     *   msg.split(";") y procesa líneas:
     *      - J...   → jugador
     *      - PEL... → pelota
     *      - ARC_I  → arco izquierdo
     *      - ARC_D  → arco derecho
     *      - HUD    → marcador + tiempo
     */
    public String    generarEstado() {
        StringBuilder sb = new StringBuilder();
        sb.append("STATE");
        // -------------------------------------------------
        // Jugadores: J0: x=..,y=..,w=..,h=..,mov=0/1,dir=..,ta=..,st=..,stm=..
        // -------------------------------------------------
        for (int i = 0; i < jugadores.size(); i++) {
            Personaje pj = jugadores.get(i);
            if (pj == null) continue;

            float x = pj.getX();         // Ajustá si tu Personaje usa otro getter
            float y = pj.getY();
            float w = pj.getAncho();     // Idem: si no existe, podés usar ancho fijo
            float h = pj.getAlto();

            // Por ahora supongo que no tengo acceso directo al "estaMoviendo" ni "direccion"
            // de Personaje del servidor. Podés enriquecer esto más adelante.
            int mov = 0;                 // 0 = quieto, 1 = moviendo (TODO)
            String dir = "abajo";        // TODO: ajustar según tu lógica de dirección
            float tiempoAnim = 0f;       // TODO: si querés mandar stateTime del personaje
            float staminaActual = 100f;  // TODO: obtener de Personaje si existe
            float staminaMaxima = 100f;  // TODO: idem

            if (!sb.isEmpty()) {
                sb.append(";");
            }
            sb.append("J").append(i).append(",")
                    .append("x=").append(x).append(",")
                    .append("y=").append(y).append(",")
                    .append("w=").append(w).append(",")
                    .append("h=").append(h).append(",")
                    .append("mov=").append(mov).append(",")
                    .append("dir=").append(dir).append(",")
                    .append("ta=").append(tiempoAnim).append(",")
                    .append("st=").append(staminaActual).append(",")
                    .append("stm=").append(staminaMaxima);
        }

        // -------------------------------------------------
        // Pelota: PEL:x=..,y=..,w=..,h=..,st=..,a=0/1
        // -------------------------------------------------
        if (pelota != null) {
            if (sb.length() > 0) sb.append(";");
            float px = pelota.getX();
            float py = pelota.getY();
            float pw = pelota.getAncho();
            float ph = pelota.getAlto();

            float stateTime = 0f; // TODO: si tu Pelota tiene un stateTime lógico
            int animar = 1;       // 1 = se anima / 0 = estática (ajustá si necesitas)

            sb.append("PEL").append(",")
                    .append("x=").append(px).append(",")
                    .append("y=").append(py).append(",")
                    .append("w=").append(pw).append(",")
                    .append("h=").append(ph).append(",")
                    .append("st=").append(stateTime).append(",")
                    .append("a=").append(animar);
        }

        // -------------------------------------------------
        // Arcos: ARC_I:x=..,y=..,w=..,h=..
        //        ARC_D:x=..,y=..,w=..,h=..
        // -------------------------------------------------
        if (cancha != null) {
            Arco arcoIzq = cancha.getArcoIzq(); // Ajustá si el getter se llama distinto
            Arco arcoDer = cancha.getArcoDer();

            if (arcoIzq != null) {
                if (sb.length() > 0) sb.append(";");
                sb.append("ARC_I").append(",")
                        .append("x=").append(arcoIzq.getX()).append(",")
                        .append("y=").append(arcoIzq.getY()).append(",")
                        .append("w=").append(arcoIzq.getWidth()).append(",")
                        .append("h=").append(arcoIzq.getHeight());
            }

            if (arcoDer != null) {
                if (sb.length() > 0) sb.append(";");
                sb.append("ARC_D").append(",")
                        .append("x=").append(arcoDer.getX()).append(",")
                        .append("y=").append(arcoDer.getY()).append(",")
                        .append("w=").append(arcoDer.getWidth()).append(",")
                        .append("h=").append(arcoDer.getHeight());
            }
        }

        // -------------------------------------------------
        // HUD: HUD:gr=..,ga=..,t=..
        // -------------------------------------------------
        if (partido != null) {
            if (sb.length() > 0) sb.append(";");

            int golesRojo  = partido.getGolesRojo();  // Ajustá nombres de getters si hace falta
            int golesAzul  = partido.getGolesAzul();

            sb.append("HUD:")
                    .append("gr=").append(golesRojo).append(",")
                    .append("ga=").append(golesAzul);
        }

        return sb.toString();
    }

    // =========================================================
    //  Getters útiles por si los necesitás desde el servidor
    // =========================================================
    public Config getConfig() {
        return config;
    }

    public List<Personaje> getJugadores() {
        return jugadores;
    }

    public Pelota getPelota() {
        return pelota;
    }

    public Cancha getCancha() {
        return cancha;
    }

    public SistemaPartido getPartido() {
        return partido;
    }
}
