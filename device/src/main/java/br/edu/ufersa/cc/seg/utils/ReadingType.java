package br.edu.ufersa.cc.seg.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReadingType {

    CO2("CO₂", 400, 1500, 2),
    CO("CO", 0.1, 50, 2),
    NO2("NO₂", 5, 200, 2),
    SO2("SO₂", 0, 100, 2),
    PM2("PM2", 5, 120, 2),
    PM10("PM10", 10, 200, 2),
    TEMPERATURA("Temperatura", 10, 45, 2),
    UMIDADE("Umidade", 15, 95, 2),
    RUIDO("Ruído", 35, 95, 2),
    UV("UV", 0, 13, 2);

    private final String name;
    private final double min;
    private final double max;
    private final int scale;

}
