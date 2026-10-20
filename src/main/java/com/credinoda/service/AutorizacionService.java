package com.credinoda.service;

import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AccesoDenegadoException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.Permiso;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.domain.model.Rol;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Servicio centralizado de autorización que aplica la matriz de permisos por rol (RN-024, RN-026, RN-027, VP-020).
 * Opera contra la sesión activa en {@link ContextoSeguridad} o un proveedor inyectable y audita accesos denegados.
 */
public class AutorizacionService implements Autorizador {

    private final Supplier<SesionUsuario> sesionSupplier;
    private final AuditoriaService auditoriaService;

    public AutorizacionService() {
        this(ContextoSeguridad::getSesionActual, null);
    }

    public AutorizacionService(AuditoriaService auditoriaService) {
        this(ContextoSeguridad::getSesionActual, auditoriaService);
    }

    public AutorizacionService(Supplier<SesionUsuario> sesionSupplier) {
        this(sesionSupplier, null);
    }

    public AutorizacionService(Supplier<SesionUsuario> sesionSupplier, AuditoriaService auditoriaService) {
        this.sesionSupplier = Objects.requireNonNull(sesionSupplier, "El proveedor de sesión no puede ser nulo");
        this.auditoriaService = auditoriaService;
    }

    @Override
    public boolean hasPermission(Permiso permiso) {
        if (permiso == null) {
            return false;
        }
        SesionUsuario sesion = sesionSupplier.get();
        if (sesion == null) {
            return false;
        }
        return hasPermission(sesion.rol(), permiso);
    }

    @Override
    public void requirePermission(Permiso permiso) throws AccesoDenegadoException {
        Objects.requireNonNull(permiso, "El permiso a requerir no puede ser nulo");
        if (!hasPermission(permiso)) {
            SesionUsuario sesion = sesionSupplier.get();
            String rolName = sesion != null && sesion.rol() != null ? sesion.rol().name() : "ANONIMO";
            String username = sesion != null ? sesion.username() : "ANONIMO";
            Long usuarioId = sesion != null && sesion.usuarioId() > 0 ? (long) sesion.usuarioId() : null;

            if (auditoriaService != null) {
                auditoriaService.registrarConUsuario(
                        username,
                        rolName,
                        usuarioId,
                        AccionAuditoria.ACCESO_DENEGADO,
                        "SEGURIDAD",
                        permiso.name(),
                        String.format("Rol '%s' intentó ejecutar operación que requiere '%s'", rolName, permiso.name()),
                        ResultadoAuditoria.DENEGADO
                );
            }

            throw new AccesoDenegadoException(
                    permiso,
                    String.format("Acceso denegado: No cuenta con privilegios suficientes. El rol '%s' no tiene el permiso requerido '%s'", rolName, permiso.name())
            );
        }
    }

    @Override
    public boolean hasPermission(Rol rol, Permiso permiso) {
        if (rol == null || permiso == null) {
            return false;
        }
        return permiso.tienePermiso(rol);
    }
}
