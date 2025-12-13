package com.championsita;

import java.util.Random;

public class Utiles {

    private static final Random random = new Random();

    /**
     * Genera un código de 4 cifras aleatorio (ej: "0384", "4721").
     * Siempre tiene longitud 4, con ceros a la izquierda si es necesario.
     */
    public static int generarCodigo4Cifras() {
        return random.nextInt(10000);
    }
}
