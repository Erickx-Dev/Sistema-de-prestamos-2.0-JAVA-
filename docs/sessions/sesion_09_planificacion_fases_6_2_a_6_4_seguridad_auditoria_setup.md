# Bitácora de Sesión de Desarrollo: SESION-09

> **Proyecto:** CrediNoda 2.0 (Migración a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Tema Central:** Planificación Arquitectónica y Actualización de Roadmap: Setup Wizard (Google Drive), Seguridad (BCrypt/Roles) y Auditoría Centralizada  
> **Documento Base:** [`docs/ROADMAP_JAVA.md`](../ROADMAP_JAVA.md) y [`docs/templates/session_summary_template.md`](../templates/session_summary_template.md)  

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-09`
- **Fecha de Ejecución:** `2026-10-17`
- **Responsable(s) Técnico(s) Presente(s):**
  - **Líder de Proyecto / Administrador Único:** Erickson Sojo *(Tech Lead / Aprobación)*
  - **Desarrollador / Agente IA:** Antigravity (Google DeepMind)
- **Rama Git de la Sesión:** `docs/roadmap-fases-6.2-6.4-seguridad-auditoria`
- **Objetivo General de la Sesión:**
  1. Realizar una auditoría e inspección técnica exhaustiva de la base de código actual para determinar los puntos de acoplamiento de tres nuevos requerimientos: **Asistente de Configuración Inicial (Setup Wizard y rutas dinámicas)**, **Roles de Usuario, Autenticación y Autorización (BCrypt y `hasPermission`)** y **Módulo de Auditoría Centralizado (Bitácora inmutable)**.
  2. Resolver las restricciones arquitectónicas derivadas de pruebas automatizadas inmutables (ej. `MetricCardTest` con botones de Navbar, `ViewRouterTest` con la regla de dos regiones del `MainShell`, y firmas existentes de constructores en la capa de servicios).
  3. Definir la estrategia de almacenamiento de configuración híbrida multiplataforma para software comercial distribuible: Windows (`%APPDATA%\CrediNoda\credinoda.properties`), Linux (`~/.config/credinoda/credinoda.properties`) y macOS, con soporte de aislamiento mediante `-Dcredinoda.config.dir` para pruebas unitarias.
  4. Diseñar la estrategia más viable y robusta para respaldos en la nube: **Sincronización transparente con Google Drive para Escritorio (Opción 1)**, eliminando carpetas fijas hardcodeadas (`backups/`) y asegurando operación 100% *offline-first* sin dependencias frágiles de APIs externas.
  5. Definir la matriz de roles y permisos del sistema: `ADMINISTRADOR` (control total, auditoría, respaldos y usuarios) vs. `OPERADOR` (enfoque exclusivo en cobranza y créditos a clientes preexistentes, sin crear ni editar clientes, sin auditoría ni respaldos).
  6. Diseñar el flujo de cierre de sesión interactivo accesible para cualquier rol autenticado al pulsar sobre el bloque de marca `CrediNoda 2.0` en la esquina superior izquierda de la Navbar.
  7. Actualizar formalmente [`AGENTS.md`](../../AGENTS.md) (incorporando `at.favre.lib:bcrypt:0.10.2` en dependencias autorizadas y corrigiendo la descripción de la Navbar superior horizontal de 56px) y [`docs/ROADMAP_JAVA.md`](../ROADMAP_JAVA.md) con las Fases 6.2, 6.3 y 6.4, nuevos casos de uso (`CU-016` a `CU-021`), reglas de negocio (`RN-019` a `RN-029`) y vectores de prueba (`VP-015` a `VP-024`).

---

## 2. Trazabilidad de Tareas

### Tareas Completadas en la Sesión
- [x] `T-INSP-01`: Inspección integral del modelo de persistencia, servicios core, bootstrap (`CrediNodaApplication`), componentes de navegación y 20 suites de pruebas automatizadas.
- [x] `T-ANALYSIS-01`: Elaboración y publicación del informe de arquitectura `analisis_auditoria_seguridad_setup.md` detallando componentes afectados, puntos de refactorización y decisiones requeridas.
- [x] `T-DECISION-01`: Aprobación de la librería criptográfica BCrypt (`at.favre.lib:bcrypt:0.10.2`) con costo 12.
- [x] `T-DECISION-02`: Aprobación de la estrategia de configuración híbrida nativa multiplataforma (Windows `%APPDATA%`, Linux `~/.config`, macOS).
- [x] `T-DECISION-03`: Aprobación de la arquitectura de respaldos con Google Drive para Escritorio (Opción 1: sincronización de carpeta nativa 100% offline-first).
- [x] `T-DECISION-04`: Definición de la matriz de permisos para `ADMINISTRADOR` y `OPERADOR`, y diseño del cierre de sesión en el bloque de marca de la Navbar.
- [x] `T-DECISION-05`: Autorización para adaptar `MetricCardTest.java` en la Fase 6.3 para evaluar el renderizado dinámico de la Navbar según el rol activo.
- [x] `T-PLAN-01`: Elaboración del plan maestro integral `plan_maestro_auditoria_seguridad_setup.md`.
- [x] `T-GOV-01`: Verificación de rama inicial en `main` y conmutación a la sub-rama dedicada `docs/roadmap-fases-6.2-6.4-seguridad-auditoria` con pre-autorización de Git.
- [x] `T-DOC-01`: Actualización de [`AGENTS.md`](../../AGENTS.md) incorporando BCrypt, ramas de trabajo de Fases 6.2 a 6.4 y alineación técnica de la Navbar superior horizontal de 56px.
- [x] `T-DOC-02`: Actualización de [`docs/ROADMAP_JAVA.md`](../ROADMAP_JAVA.md) integrando diagrama de arquitectura enriquecido, matriz de Casos de Uso (`CU-016` a `CU-021`), especificación completa de Fases 6.2, 6.3 y 6.4, actualización de Fase 7 y criterios de aceptación globales.
- [x] `T-GIT-01`: Emisión de solicitud de commit y registro formal del commit `2b3815b` tras confirmación explícita del Tech Lead.

### Tareas Pendientes para las Siguientes Sesiones
- [ ] `T-F62-01`: Inicio de la **Fase 6.2** en la rama `feature/fase-6.2-setup-wizard-rutas` (Configuración híbrida, `RutasSistema`, `ConfiguracionInicialService` y `SetupWizard`).
- [ ] `T-F63-01`: Implementación de la **Fase 6.3** en `feature/fase-6.3-autenticacion-roles` (BCrypt, `UsuarioRepository`, `AutorizacionService`, `LoginDialog` y logout en marca).
- [ ] `T-F64-01`: Implementación de la **Fase 6.4** en `feature/fase-6.4-auditoria` (Triggers SQLite inmutables, `AuditoriaService` transaccional y `AuditoriaView`).

---

## 3. Desglose Técnico Exhaustivo por Archivo Modificado

### 1. Archivo: `AGENTS.md`
- **Estado:** `MODIFICADO`
- **Capa:** Gobernanza y Protocolo Operativo
- **Detalle de Cambios:**
  - **Sección 2.3:** Se agregaron las ramas normativas correspondientes a las nuevas fases:
    - `feature/fase-6.1-graficos-reportes`
    - `feature/fase-6.2-setup-wizard-rutas`
    - `feature/fase-6.3-autenticacion-roles`
    - `feature/fase-6.4-auditoria`
  - **Sección 3:** Se incorporó formalmente la librería `at.favre.lib:bcrypt` (versión `0.10.2`) en la tabla de Dependencias Autorizadas con el propósito de almacenamiento seguro y verificación de contraseñas (costo 12).
  - **Sección 4:** Se corrigió la descripción arquitectónica de la interfaz gráfica (`MainShell`): se formalizó la **Barra de Navegación Persistente (`NavigationBar`) como superior horizontal fija de 56px (`Top`)**, con marca interactiva a la izquierda (cierre de sesión), botones dinámicos según rol y acción de respaldo/menú a la derecha, corrigiendo la referencia obsoleta a una barra lateral de 220px.

### 2. Archivo: `docs/ROADMAP_JAVA.md`
- **Estado:** `MODIFICADO`
- **Capa:** Planificación y Roadmap
- **Detalle de Cambios:**
  - **Sección 2 (Arquitectura Objetivo):**
    - Diagrama Mermaid actualizado incorporando `UsuarioRepository`, `AuditoriaRepository`, `AuditoriaService`, `AutorizacionService`, `AutenticacionService`, `ConfiguracionInicialService`, `RutasSistema`, `ConfiguracionLocalStore`, `SetupWizard`, `LoginDialog`, `UsuariosView` y `AuditoriaView`.
    - Actualización textual de especificaciones de las 5 capas con las nuevas entidades, records DTO y servicios.
    - Corrección de la ventana principal: `MainShell` compuesta exclusivamente por `NavigationBar` superior horizontal de 56px (`Top`) y `ContentArea` (`Center`).
  - **Sección 3 (Matriz de Casos de Uso):**
    - Se incorporaron las filas para `CU-016` y `CU-017` (`RN-019` a `RN-021`, vectores `VP-015` a `VP-017`).
    - Se incorporaron las filas para `CU-018`, `CU-019` y `CU-020` (`RN-022` a `RN-026`, vectores `VP-018` a `VP-021`).
    - Se incorporó la fila para `CU-021` (`RN-027` a `RN-029`, vectores `VP-022` a `VP-024`).
  - **Sección 4 (Roadmap Paso a Paso):**
    - **Fase 6.2:** Especificación completa con objetivos, archivos a crear/modificar, casos de uso, vectores de prueba, lista de tareas y criterios de finalización para el Setup Wizard y Google Drive para Escritorio.
    - **Fase 6.3:** Especificación completa para usuarios, hash BCrypt costo 12, matriz de roles `ADMINISTRADOR` vs. `OPERADOR`, restricciones visuales y de servicio, logout en marca de Navbar y adaptación autorizada de `MetricCardTest.java`.
    - **Fase 6.4:** Especificación completa para la infraestructura de auditoría centralizada con bitácora inmutable por triggers SQLite, transaccionalidad atómica y vista analítica `AuditoriaView`.
    - **Fase 7:** Ampliación de escenarios de pruebas de integración End-to-End (escenario 4: primer inicio con wizard; escenario 5: control de acceso de operador y auditoría del administrador) y empaquetado nativo sin rutas fijas.
  - **Sección 5 (Criterios de Aceptación Globales):**
    - Añadidos criterios formales para Seguridad y Control de Acceso, Trazabilidad y Auditoría, e Instalación, Portabilidad y Respaldos.

---

## 4. Impacto en Persistencia y Base de Datos

- **¿Hubo alteraciones al modelo de datos en esta sesión?:** `NO` *(Fase de diseño y documentación. El DDL se implementará en las Fases 6.2, 6.3 y 6.4).*
- **Diseño DDL Proyectado para las siguientes fases:**
  - **Tabla `usuarios` (Fase 6.3):**
    ```sql
    CREATE TABLE IF NOT EXISTS usuarios (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        login TEXT NOT NULL UNIQUE COLLATE NOCASE,
        nombre_completo TEXT NOT NULL,
        password_hash TEXT NOT NULL,
        rol TEXT NOT NULL CHECK (rol IN ('ADMINISTRADOR','OPERADOR')),
        activo INTEGER NOT NULL DEFAULT 1,
        intentos_fallidos INTEGER NOT NULL DEFAULT 0,
        bloqueado_hasta TEXT,
        debe_cambiar_password INTEGER NOT NULL DEFAULT 0,
        ultimo_acceso TEXT,
        fecha_creacion TEXT NOT NULL
    );
    ```
  - **Tabla `auditoria` y Triggers Inmutables (Fase 6.4):**
    ```sql
    CREATE TABLE IF NOT EXISTS auditoria (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        fecha_hora TEXT NOT NULL,
        usuario_id INTEGER,
        usuario_login TEXT NOT NULL,
        accion TEXT NOT NULL,
        entidad TEXT NOT NULL,
        entidad_id TEXT,
        detalle TEXT,
        resultado TEXT NOT NULL DEFAULT 'EXITO',
        equipo TEXT,
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
    );

    CREATE TRIGGER IF NOT EXISTS trg_auditoria_no_update BEFORE UPDATE ON auditoria
    BEGIN SELECT RAISE(ABORT, 'La bitacora de auditoria es inmutable'); END;
    CREATE TRIGGER IF NOT EXISTS trg_auditoria_no_delete BEFORE DELETE ON auditoria
    BEGIN SELECT RAISE(ABORT, 'La bitacora de auditoria es inmutable'); END;
    ```
- **Invariante Crítico:** No se aplicarán sentencias `ALTER TABLE` sobre las 4 tablas de negocio existentes (`clientes`, `creditos`, `cuotas`, `pagos`). La restauración de respaldos reemplazará exclusivamente esas 4 tablas, preservando intactos los registros de usuarios y auditoría de la base de datos activa.

---

## 5. Checklist de Calidad y Gobernanza

- [x] **Blindaje de `main` Cumplido:** Cero modificaciones directas sobre la rama `main`; todo el trabajo se realizó en la sub-rama dedicada `docs/roadmap-fases-6.2-6.4-seguridad-auditoria`.
- [x] **Aprobación Previa de Git:** Se emitió la solicitud formal de confirmación y se esperó la autorización explícita de Erickson Sojo antes de ejecutar el commit.
- [x] **Integridad de Pruebas Automatizadas:** Ninguna prueba unitaria fue alterada ni relajada en esta sesión.
- [x] **Arquitectura en 5 Capas Preservada:** El diseño respeta estrictamente el desacoplamiento de capas y la independencia de UI en servicios y persistencia.
- [x] **Formato Conventional Commits:** Mensaje de commit estructurado en español imperativo (`docs(roadmap): ...`).

---

## 6. Próximo Objetivo Prioritario

- **Siguiente Paso Técnico:**
  1. Subir la rama actual al repositorio remoto (`git push -u origin docs/roadmap-fases-6.2-6.4-seguridad-auditoria`) y registrar el Pull Request para revisión y fusión a `main` por parte de Erickson Sojo.
  2. Iniciar la **Fase 6.2** con la creación de la rama `feature/fase-6.2-setup-wizard-rutas`, implementando el resolvedor de configuración multiplataforma y el Asistente de Configuración Inicial con soporte de Google Drive para escritorio.
