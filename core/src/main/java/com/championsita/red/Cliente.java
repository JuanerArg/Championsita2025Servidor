package com.championsita.red;

import java.net.DatagramPacket;
import java.net.InetAddress;

public class Cliente {
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
