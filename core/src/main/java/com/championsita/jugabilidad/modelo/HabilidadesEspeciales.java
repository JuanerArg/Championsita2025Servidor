package com.championsita.jugabilidad.modelo;

public enum HabilidadesEspeciales {
    NEUTRO("NEUTRO"),
    GRANDOTE("GRANDOTE"),
    PEQUEÑIN("PEQUEÑIN"),
    EMPUJON("EMPUJON"),
    ZURDO("ZURDO"),
    DIESTRO("DIESTRO"),
    ATLETA("ATLETA"),
    EXTREMISTA("EXTREMISTA");

    private final String nombre;

    HabilidadesEspeciales(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
