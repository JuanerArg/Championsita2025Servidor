package com.championsita.jugabilidad.sistemas;

import com.badlogic.gdx.math.Rectangle;
import com.championsita.jugabilidad.modelo.*;

public class SistemaColisiones {

    // Separación simple entre jugadores
    public void separar(Personaje a, Personaje b) {
        Rectangle A = a.getHitbox();
        Rectangle B = b.getHitbox();

        if (!A.overlaps(B)) return;

        float dx = (a.getX() + A.width/2f) - (b.getX() + B.width/2f);
        float dy = (a.getY() + A.height/2f) - (b.getY() + B.height/2f);

        if (Math.abs(dx) > Math.abs(dy)) {
            // empujar en X
            if (dx > 0) a.setPos(a.getX() + 0.05f, a.getY());
            else        a.setPos(a.getX() - 0.05f, a.getY());
        } else {
            // empujar en Y
            if (dy > 0) a.setPos(a.getX(), a.getY() + 0.05f);
            else        a.setPos(a.getX(), a.getY() - 0.05f);
        }
    }

    public void procesarPelota(Personaje p, Pelota pelota) {

        Rectangle A = p.getHitbox();
        Rectangle B = pelota.getHitbox();

        if (!A.overlaps(B)) return;

        // Dirección jugador → pelota
        float dx = (B.x + B.width/2f) - (A.x + A.width/2f);
        float dy = (B.y + B.height/2f) - (A.y + A.height/2f);
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len != 0) { dx /= len; dy /= len; }

        // Aplicar fuerza básica
        float fuerza = p.estaSprintPresionado() ? 1.4f : 1.0f;

        pelota.aplicarImpulso(dx, dy, fuerza);
    }
}
