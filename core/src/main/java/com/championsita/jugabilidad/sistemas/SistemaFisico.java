package com.championsita.jugabilidad.sistemas;

import com.championsita.jugabilidad.modelo.Cancha;
import com.championsita.jugabilidad.modelo.Personaje;
import com.championsita.jugabilidad.modelo.Pelota;

public class SistemaFisico {


    /** Mantiene al personaje dentro del mundo. */
    public void limitarPersonajeAlMundo(Personaje p, float anchoMundo, float altoMundo) {
        float x = p.getX();
        float y = p.getY();

        if (x < 0) x = 0;
        if (x > anchoMundo - p.getAncho()) x = anchoMundo - p.getAncho();
        if (y < 0) y = 0;
        if (y > altoMundo - p.getAlto()) y = altoMundo - p.getAlto();

        p.setPosicion(x, y);
    }

    /** Avanza física básica de la pelota. */
    public void actualizarPelota(Pelota pelota, float delta) {
        pelota.actualizar(delta);
    }

    /** Rebote básico en servidor (no analiza texturas ni gráficos). */
    public void rebotarPelota(Pelota pelota, float anchoMundo, float altoMundo, Cancha cancha) {

        float x = pelota.getX();
        float y = pelota.getY();
        float w = pelota.getHitbox().width;
        float h = pelota.getHitbox().height;

        if (x < 0)          pelota.setPosicion(0, y);
        if (x > anchoMundo - w) pelota.setPosicion(anchoMundo - w, y);

        if (y < 0)          pelota.setPosicion(x, 0);
        if (y > altoMundo - h) pelota.setPosicion(x, altoMundo - h);
    }
}
