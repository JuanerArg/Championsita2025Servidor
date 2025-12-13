package com.championsita.red;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LoggerRed {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    public static void log(String categoria, String mensaje) {
        String timestamp = sdf.format(new Date());
        System.out.println("[" + timestamp + "] [" + categoria + "] " + mensaje);
        try {
            Thread.sleep(200); // pausa para legibilidad (ajustable)
        } catch (InterruptedException ignored) {}
    }
}
