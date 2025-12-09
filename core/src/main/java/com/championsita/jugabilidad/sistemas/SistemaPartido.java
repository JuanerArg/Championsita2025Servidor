package com.championsita.jugabilidad.sistemas;

import com.badlogic.gdx.math.Rectangle;
import com.championsita.jugabilidad.modelo.*;

public class SistemaPartido {

    private int golesRojo = 0;
    private int golesAzul = 0;

    public void verificarGol(Pelota pelota, Cancha cancha) {

        Rectangle p = pelota.getHitbox();

        if (p.overlaps(cancha.getArcoIzq().getHitbox())) {
            golesAzul++;
            reiniciarPelota(pelota);
        }

        if (p.overlaps(cancha.getArcoDer().getHitbox())) {
            golesRojo++;
            reiniciarPelota(pelota);
        }
    }

    private void reiniciarPelota(Pelota pelota) {
        pelota.setPosicion(4f, 2.5f);
        pelota.setVelocidad(0, 0);
    }

    public int getGolesRojo() { return golesRojo; }
    public int getGolesAzul() { return golesAzul; }
}
