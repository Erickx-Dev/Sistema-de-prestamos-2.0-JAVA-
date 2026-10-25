package com.credinoda.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas Unitarias de SystemHardwareIdProvider (RN-031, VP-026)")
class SystemHardwareIdProviderTest {

    @Test
    @DisplayName("VP-026: Hardware ID detectado en la máquina actual cumple con el formato CN20-XXXX-XXXX-XXXX-XXXX")
    void testFormatoHardwareIdActual() {
        SystemHardwareIdProvider provider = new SystemHardwareIdProvider();
        String hwId = provider.obtenerHardwareId();

        assertThat(hwId).isNotNull();
        assertThat(hwId).matches("^CN20-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}$");
    }

    @Test
    @DisplayName("VP-026: La obtención del Hardware ID es estrictamente determinista y aprovecha la caché")
    void testDeterminismoYCache() {
        SystemHardwareIdProvider provider = new SystemHardwareIdProvider();
        String id1 = provider.obtenerHardwareId();
        String id2 = provider.obtenerHardwareId();

        assertThat(id1).isEqualTo(id2);

        provider.invalidarCache();
        String id3 = provider.obtenerHardwareId();
        assertThat(id3).isEqualTo(id1);
    }

    @Test
    @DisplayName("VP-026: Descarta placeholders genéricos de BIOS OEM sin alterar la unicidad")
    void testFiltradoPlaceholdersOem() {
        List<String> conPlaceholders = List.of(
                "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF",
                "To be filled by O.E.M.",
                "Default string",
                "MACHINE-REAL-GUID-12345"
        );
        List<String> sinPlaceholders = List.of(
                "MACHINE-REAL-GUID-12345"
        );

        String id1 = SystemHardwareIdProvider.formatearDesdeComponentes(conPlaceholders);
        String id2 = SystemHardwareIdProvider.formatearDesdeComponentes(sinPlaceholders);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).matches("^CN20-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}-[A-F0-9]{4}$");
    }

    @Test
    @DisplayName("Equipos con identificadores diferentes generan Hardware IDs distintos")
    void testEquiposDistintosGeneranIdsDistintos() {
        String idEquipoA = SystemHardwareIdProvider.formatearDesdeComponentes(List.of("UUID-EQUIPO-A", "CPU-111"));
        String idEquipoB = SystemHardwareIdProvider.formatearDesdeComponentes(List.of("UUID-EQUIPO-B", "CPU-222"));

        assertThat(idEquipoA).isNotEqualTo(idEquipoB);
        assertThat(idEquipoA).startsWith("CN20-");
        assertThat(idEquipoB).startsWith("CN20-");
    }
}
