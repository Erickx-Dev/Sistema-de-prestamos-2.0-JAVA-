package com.credinoda.service;

import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AccesoDenegadoException;
import com.credinoda.domain.model.Permiso;
import com.credinoda.domain.model.Rol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de AutorizacionService y Permisos (CU-018, RN-024, VP-020)")
class AutorizacionServiceTest {

    private AutorizacionService autorizacionService;

    @BeforeEach
    void setUp() {
        this.autorizacionService = new AutorizacionService();
    }

    @AfterEach
    void tearDown() {
        ContextoSeguridad.limpiar();
    }

    @Test
    @DisplayName("VP-020: La matriz de permisos otorga todos los permisos al Administrador")
    void testMatrizPermisosAdministrador() {
        for (Permiso permiso : Permiso.values()) {
            assertThat(Permiso.tienePermiso(Rol.ADMINISTRADOR, permiso))
                    .as("El Administrador debe poseer el permiso: " + permiso)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("VP-020: La matriz de permisos otorga operaciones core al Operador pero deniega gestión y respaldos")
    void testMatrizPermisosOperador() {
        // Permisos concedidos
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.CLIENTES_VER)).isTrue();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.CLIENTES_CREAR)).isTrue();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.CLIENTES_EDITAR)).isTrue();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.CREDITOS_VER)).isTrue();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.CREDITOS_CREAR)).isTrue();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.PAGOS_VER)).isTrue();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.PAGOS_REGISTRAR)).isTrue();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.REPORTES_VER)).isTrue();

        // Permisos denegados
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.BACKUP_GESTIONAR)).isFalse();
        assertThat(Permiso.tienePermiso(Rol.OPERADOR, Permiso.USUARIOS_GESTIONAR)).isFalse();
    }

    @Test
    @DisplayName("VP-020: Sesión activa de Operador valida requirePermission con éxito para pagos y falla para backup")
    void testVerificacionEnSesionOperador() {
        SesionUsuario sesionOperador = new SesionUsuario(1, "cajero1", "Pedro Gómez", Rol.OPERADOR);
        ContextoSeguridad.setSesionActual(sesionOperador);

        assertThat(autorizacionService.hasPermission(Permiso.PAGOS_REGISTRAR)).isTrue();
        assertThat(autorizacionService.hasPermission(Permiso.BACKUP_GESTIONAR)).isFalse();
        assertThat(autorizacionService.hasPermission(Permiso.USUARIOS_GESTIONAR)).isFalse();

        // requirePermission no lanza excepción si tiene el permiso
        assertThatCode(() -> autorizacionService.requirePermission(Permiso.PAGOS_REGISTRAR))
                .doesNotThrowAnyException();

        // requirePermission lanza AccesoDenegadoException si no tiene el permiso
        assertThatThrownBy(() -> autorizacionService.requirePermission(Permiso.BACKUP_GESTIONAR))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("No cuenta con privilegios suficientes");

        assertThatThrownBy(() -> autorizacionService.requirePermission(Permiso.USUARIOS_GESTIONAR))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("VP-020: Sesión activa de Administrador tiene acceso total con requirePermission")
    void testVerificacionEnSesionAdministrador() {
        SesionUsuario sesionAdmin = new SesionUsuario(2, "admin_boss", "Erickson Sojo", Rol.ADMINISTRADOR);
        ContextoSeguridad.setSesionActual(sesionAdmin);

        for (Permiso permiso : Permiso.values()) {
            assertThat(autorizacionService.hasPermission(permiso)).isTrue();
            assertThatCode(() -> autorizacionService.requirePermission(permiso))
                    .doesNotThrowAnyException();
        }
    }
}
