package com.championsita.jugabilidad.modelo;

import com.badlogic.gdx.math.Rectangle;
import com.championsita.jugabilidad.constantes.Constantes;

public class Cancha {

    private Arco arcoIzq;
    private Arco arcoDer;

    public Cancha(float anchoArco, float altoArco) {

        float y = (Constantes.MUNDO_ALTO / 2f) - altoArco / 2f;
        float xIzq = -0.3f;
        float xDer = Constantes.MUNDO_ANCHO - anchoArco + 0.3f;

        arcoIzq = new Arco(xIzq, y, anchoArco, altoArco);
        arcoDer = new Arco(xDer, y, anchoArco, altoArco);
    }

    public Arco getArcoIzq() { return arcoIzq; }
    public Arco getArcoDer() { return arcoDer; }
}
