# Bitácora de Sesión de Desarrollo: SESION-10

> **Proyecto:** CrediNoda 2.0 (Migración a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 6.2: Configuración Híbrida Multiplataforma, Rutas Dinámicas y Asistente de Configuración Inicial (Setup Wizard con Google Drive Desktop)  
> **Documento Base:** [`docs/ROADMAP_JAVA.md`](../ROADMAP_JAVA.md) y [`docs/templates/session_summary_template.md`](../templates/session_summary_template.md)  

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-10`
- **Fecha de Ejecución:** `2026-10-18`
- **Responsable(s) Técnico(s) Presente(s):**
  - **Líder de Proyecto / Administrador Único:** Erickson Sojo *(Tech Lead / Aprobación)*
  - **Desarrollador / Agente IA:** Antigravity (Google DeepMind)
- **Rama Git de la Sesión:** `feature/fase-6.2-setup-wizard-rutas`
- **Objetivo General de la Sesión:**
  1. Diseñar, planificar e implementar integralmente la **Fase 6.2: Configuración Híbrida Multiplataforma, Rutas Dinámicas y Asistente de Configuración Inicial (Setup Wizard con Google Drive Desktop)**.
  2. Desacoplar la aplicación de rutas relativas fijas (`user.dir/data`, `user.dir/backups`) y migrar a un almacenamiento de configuración por usuario estándar (`ConfiguracionLocalStore` en `%APPDATA%\CrediNoda` en Windows, `~/.config/credinoda` en Linux y macOS `Application Support`) con escritura atómica y soporte de override mediante `-Dcredinoda.config.dir` para testing.
  3. Implementar el record inmutable `RutasSistema` y refactorizar `AppConfig`, `DatabaseConnection`, `SchemaInitializer`, `BackupService` y `PdfService` para operar con rutas dinámicas inyectadas.
  4. Agregar validaciones rigurosas en `Validators.java`: prueba real de escritura/borrado de archivo testigo (`validarPermisosDirectorio`), detección de carpetas en la nube (`esCarpetaEnLaNube`) y protección de carpetas de sistema (`esDirectorioProtegido`).
  5. Implementar `ConfiguracionInicialService` para evaluar la máquina de estados de arranque (`NO_CONFIGURADO`, `UBICACION_NO_DISPONIBLE`, `SIN_ADMINISTRADOR`, `LISTO`), validar carpetas, aprovisionar esquema relacional limpio y gestionar la reubicación de base de datos.
  6. Diseñar y construir en JavaFX el asistente guiado `SetupWizard` (4 pasos) y el diálogo `UbicacionNoDisponibleDialog` que previene terminantemente la recreación silenciosa de bases de datos ausentes (`RN-021`).
  7. Resolver quirúrgicamente los 10 problemas y advertencias detectados en el panel de *Problems* de Visual Studio Code.
  8. Alcanzar y verificar el 100% de la suite de pruebas unitarias automatizadas (**182 de 182 tests en VERDE**).
- **Casos de Uso y Reglas de Negocio Abordadas:**
  - [x] **CU-016 (`RN-019`, `RN-020`):** Configuración de ubicación de datos y respaldos en primer inicio (Setup Wizard y Google Drive para Escritorio).
  - [x] **CU-017 (`RN-021`):** Recuperación y reubicación de base de datos cuando la ruta configurada no está disponible (cero recreación silenciosa).
  - [x] **Vectores de Prueba:** `VP-015` (Primer inicio $\to$ Wizard; segundo inicio $\to$ Directo), `VP-016` (Base borrada $\to$ `UBICACION_NO_DISPONIBLE`), `VP-017` (Validación de carpetas, advertencia de nube y reutilización de BD válida).

---

## 2. Trazabilidad de Tareas

### Tareas Completadas en la Sesión
- [x] `T-GOV-01`: Auditoría de rama inicial en `main`, emisión de solicitud de pre-autorización Git y conmutación a la sub-rama `feature/fase-6.2-setup-wizard-rutas` tras aprobación explícita de Erickson Sojo.
- [x] `T-PLAN-01`: Elaboración del plan técnico formal de arquitectura `plan_fase_6_2_setup_wizard_rutas.md` y aprobación del Tech Lead.
- [x] `T-DOM-01`: Implementación de DTOs y excepciones del dominio: `EstadoInstalacion.java`, `ResultadoValidacionUbicacion.java` y `ConfiguracionException.java`.
- [x] `T-COM-01`: Implementación de `RutasSistema.java` con métodos de fábrica, creación idempotente de directorios y chequeo de existencia.
- [x] `T-COM-02`: Implementación de `ConfiguracionLocalStore.java` con estrategia multiplataforma nativa, persistencia en Properties, escritura atómica (`.tmp` + `ATOMIC_MOVE`) y override `-Dcredinoda.config.dir`.
- [x] `T-COM-03`: Refactorización de `AppConfig.java` eliminando rutas fijas y preservando constantes financieras inmutables.
- [x] `T-COM-04`: Incorporación en `Validators.java` de `validarPermisosDirectorio(dir)`, `esCarpetaEnLaNube(dir)` y `esDirectorioProtegido(dir)`.
- [x] `T-PERS-01`: Refactorización de `DatabaseConnection.java` y `SchemaInitializer.java` retirando sobrecargas sin argumentos y garantizando que `getConnection(Path)` asegure el directorio contenedor.
- [x] `T-SRV-01`: Refactorización de `BackupService.java` y `PdfService.java` para recibir rutas operativas desde `RutasSistema`, manteniendo constructores de conveniencia con resolución automática.
- [x] `T-SRV-02`: Implementación integral de `ConfiguracionInicialService.java` desacoplado de UI.
- [x] `T-UI-01`: Desarrollo de `SetupWizard.java` (4 pasos guiados con DirectoryChooser, advertencias reactivas y resumen).
- [x] `T-UI-02`: Desarrollo de `UbicacionNoDisponibleDialog.java` (Reintentar, Reubicar o Salir).
- [x] `T-UI-03`: Refactorización del ciclo de vida de arranque en `CrediNodaApplication.java` (`determinarEstadoInstalacion()` $\to$ Wizard / Dialog / MainShell).
- [x] `T-UI-04`: Actualización de `BackupDialog.java` y estilos en `credinoda.css` (`.wizard-root`, `.wizard-brand`).
- [x] `T-TEST-01`: Implementación de suites unitarias `ConfiguracionLocalStoreTest.java` (7 tests) y `ConfiguracionInicialServiceTest.java` (5 tests).
- [x] `T-CLEAN-01`: Resolución completa de los 10 problemas y advertencias señalados en el IDE de Visual Studio Code (métodos duplicados, constructores y dependencias).
- [x] `T-QA-01`: Ejecución limpia de `mvn clean test` validando el 100% de la suite automatizada del proyecto (**182/182 tests en VERDE**).
- [x] `T-DOC-01`: Actualización del checklist de la Fase 6.2 en [`ROADMAP_JAVA.md`](../ROADMAP_JAVA.md).
- [x] `T-GIT-01`: Registro formal del commit `d5817cc` con mensaje estandarizado Conventional Commits.

---

## 3. Desglose Técnico Exhaustivo por Archivo y Función (Nivel Auditoría)

### 1. Archivo: `src/main/java/com/credinoda/common/RutasSistema.java`
- **Estado:** `NUEVO`
- **Capa:** `common`
- **Estructura:** Record inmutable `RutasSistema(Path configDir, Path dataDir, Path databasePath, Path backupsDir, Path comprobantesDir)`.
- **Métodos:**
  - `de(Path dataDir, Path backupsDir) : RutasSistema`: Deduce rutas estándar y resuelve `configDir` multiplataforma.
  - `de(Path configDir, Path dataDir, Path backupsDir) : RutasSistema`: Variante con `configDir` explícito para pruebas.
  - `asegurarDirectoriosExistentes() : void`: Crea las carpetas físicas si no existen.
  - `existeBaseDatos() : boolean`: Verifica la presencia física de `creditos.db`.

### 2. Archivo: `src/main/java/com/credinoda/common/ConfiguracionLocalStore.java`
- **Estado:** `NUEVO`
- **Capa:** `common`
- **Clase:** `ConfiguracionLocalStore` (final, utilitaria)
- **Métodos:**
  - `resolverDirectorioConfiguracion() : Path`: Evalúa override `-Dcredinoda.config.dir`, `%APPDATA%`, `~/.config` o macOS.
  - `resolverArchivoConfiguracion() : Path`: Retorna la ruta al archivo `credinoda.properties`.
  - `existeConfiguracion() : boolean`: Determina si el archivo `.properties` existe.
  - `cargarRutas() : Optional<RutasSistema>`: Parsea de forma segura las propiedades persistidas.
  - `guardarRutas(RutasSistema) : void`: Escritura atómica mediante `.tmp` y `ATOMIC_MOVE`.
  - `borrarConfiguracion() : boolean`: Elimina la configuración para limpieza de pruebas.

### 3. Archivo: `src/main/java/com/credinoda/common/AppConfig.java`
- **Estado:** `MODIFICADO`
- **Capa:** `common`
- **Detalle de Cambios:**
  - Se eliminaron las constantes `BASE_DIR`, `DATA_DIR`, `DATABASE_PATH`, `BACKUPS_DIR`, `COMPROBANTES_DIR` y el método `ensureDirectoriesExist()`.
  - Se preservaron intactas las constantes financieras `DEFAULT_MONTHLY_RATE`, `WEEKS_PER_MONTH` y `FORTNIGHTS_PER_MONTH`.

### 4. Archivo: `src/main/java/com/credinoda/common/Validators.java`
- **Estado:** `MODIFICADO`
- **Capa:** `common`
- **Nuevos Métodos:**
  - `validarPermisosDirectorio(Path) : ResultadoValidacion`: Realiza prueba real de creación/borrado de archivo temporal testigo.
  - `esCarpetaEnLaNube(Path) : boolean`: Detecta firmas de OneDrive, Google Drive, Dropbox, iCloud y variables de entorno.
  - `esDirectorioProtegido(Path) : boolean`: Bloquea carpetas raíz y de sistema operativo (`Windows`, `Program Files`, raíces de disco).

### 5. Archivo: `src/main/java/com/credinoda/domain/dto/EstadoInstalacion.java`
- **Estado:** `NUEVO`
- **Capa:** `domain/dto`
- **Valores:** `NO_CONFIGURADO`, `UBICACION_NO_DISPONIBLE`, `SIN_ADMINISTRADOR`, `LISTO`.

### 6. Archivo: `src/main/java/com/credinoda/domain/dto/ResultadoValidacionUbicacion.java`
- **Estado:** `NUEVO`
- **Capa:** `domain/dto`
- **Estructura:** Record `ResultadoValidacionUbicacion(boolean valida, boolean esNube, boolean baseExistenteValida, String mensaje)`.

### 7. Archivo: `src/main/java/com/credinoda/domain/exception/ConfiguracionException.java`
- **Estado:** `NUEVO`
- **Capa:** `domain/exception`
- **Jerarquía:** Hereda de `CrediNodaException` (no chequeada).

### 8. Archivo: `src/main/java/com/credinoda/persistence/DatabaseConnection.java`
- **Estado:** `MODIFICADO`
- **Capa:** `persistence`
- **Detalle de Cambios:**
  - Se retiró la sobrecarga sin argumentos `getConnection()`.
  - Se actualizó `getConnection(Path dbPath)` para crear automáticamente el directorio padre si no existe, previniendo excepciones de SQLite.

### 9. Archivo: `src/main/java/com/credinoda/persistence/SchemaInitializer.java`
- **Estado:** `MODIFICADO`
- **Capa:** `persistence`
- **Detalle de Cambios:**
  - Se retiró el método sin argumentos `inicializarBaseDatos()`.

### 10. Archivo: `src/main/java/com/credinoda/service/BackupService.java`
- **Estado:** `MODIFICADO`
- **Capa:** `service`
- **Detalle de Cambios:**
  - Se actualizaron los constructores para soportar `BackupService(Connection, RutasSistema)`, `BackupService(Connection, Path)` y `BackupService(Connection)` con resolución automática de rutas.
  - Se mantuvo `getBackupsDir()` como getter público.

### 11. Archivo: `src/main/java/com/credinoda/service/PdfService.java`
- **Estado:** `MODIFICADO`
- **Capa:** `service`
- **Detalle de Cambios:**
  - Se incorporaron los constructores `PdfService(RutasSistema)`, `PdfService(Path)` y `PdfService()`.
  - Se mantuvo `getDefaultOutputDir()` como getter público.

### 12. Archivo: `src/main/java/com/credinoda/service/ConfiguracionInicialService.java`
- **Estado:** `NUEVO`
- **Capa:** `service`
- **Métodos:**
  - `determinarEstadoInstalacion() : EstadoInstalacion`: Verifica archivo de configuración y presencia física de `creditos.db` sin recreación silenciosa.
  - `obtenerRutasConfiguradas() : Optional<RutasSistema>`: Obtiene rutas persistidas.
  - `validarDirectorioDatos(Path) : ResultadoValidacionUbicacion`: Comprueba permisos, alerta de nube y valida base existente reutilizable con `BackupService.validarArchivoBackup`.
  - `validarDirectorioBackups(Path) : ResultadoValidacionUbicacion`: Valida carpeta de respaldos (apta para Google Drive).
  - `guardarConfiguracion(Path, Path) : RutasSistema`: Crea carpetas, aprovisiona DDL si es base nueva y persiste properties atómicamente.
  - `reubicarBaseDatos(Path) : RutasSistema`: Valida y actualiza configuración ante base de datos movida.

### 13. Archivo: `src/main/java/com/credinoda/ui/setup/SetupWizard.java`
- **Estado:** `NUEVO`
- **Capa:** `ui/setup`
- **Descripción:** Ventana modal e independiente de 4 pasos para configurar carpeta de base de datos local y carpeta de respaldos con Google Drive para Escritorio.

### 14. Archivo: `src/main/java/com/credinoda/ui/setup/UbicacionNoDisponibleDialog.java`
- **Estado:** `NUEVO`
- **Capa:** `ui/setup`
- **Descripción:** Modal de contingencia que ofrece Reintentar, Reubicar archivo `.db` o Salir, protegiendo contra pérdida de datos.

### 15. Archivo: `src/main/java/com/credinoda/ui/CrediNodaApplication.java`
- **Estado:** `MODIFICADO`
- **Capa:** `ui`
- **Detalle de Cambios:**
  - `start(Stage)` evalúa `determinarEstadoInstalacion()` y bifurca hacia `SetupWizard`, `UbicacionNoDisponibleDialog` o `MainShell`.
  - Se incorporó `initializeBackend(RutasSistema)` y `iniciarConRutas(Stage, RutasSistema)`.
  - Se preservó `buildMainShell()` intacto para pruebas unitarias.

### 16. Archivo: `src/main/java/com/credinoda/ui/components/BackupDialog.java`
- **Estado:** `MODIFICADO`
- **Capa:** `ui/components`
- **Detalle de Cambios:**
  - El botón de respaldo rápido refleja dinámicamente el nombre de la carpeta configurada (`backupService.getBackupsDir()`).

### 17. Archivo: `src/main/resources/css/credinoda.css`
- **Estado:** `MODIFICADO`
- **Capa:** `resources/css`
- **Detalle de Cambios:**
  - Clases agregadas: `.wizard-root`, `.wizard-brand`.

### 18. Archivos de Pruebas: `ConfiguracionLocalStoreTest.java` y `ConfiguracionInicialServiceTest.java`
- **Estado:** `NUEVOS`
- **Capa:** `test`
- **Detalle:** 12 pruebas unitarias automatizadas cubriendo los vectores `VP-015`, `VP-016` y `VP-017`.

---

## 4. Impacto en Esquema de Persistencia y Base de Datos

- **¿Hubo alteraciones al modelo de datos en esta sesión?:** `NO`
- **Comportamiento DDL:** `SchemaInitializer` aprovisiona limpiamente las 4 tablas relacionales de negocio sobre cualquier ruta dinámica proporcionada por `RutasSistema`. Si la base de datos ya existía en la ruta seleccionada y supera la validación triple de `BackupService.validarArchivoBackup`, se reutiliza sin alterar los datos preexistentes.
- **Invariante Crítico (`RN-021`):** Cero recreación silenciosa de bases de datos ausentes.

---

## 5. Checklist de Calidad y Gobernanza

- [x] **Separación Estricta de Capas:** Capa `service` y `common` libres al 100% de dependencias de `javafx.*`.
- [x] **Higiene del IDE:** Panel *Problems* de Visual Studio Code en **0 errores y 0 advertencias**.
- [x] **Blindaje de `main`:** Ningún archivo fue tocado directamente en `main`; todo el trabajo se ejecutó en la rama `feature/fase-6.2-setup-wizard-rutas`.
- [x] **Aprobación Previa de Git:** Se solicitó confirmación textual explícita al Tech Lead Erickson Sojo antes de ejecutar el commit.
- [x] **Integridad de Pruebas Automatizadas:** Ninguna prueba existente fue rebajada ni relajada; la suite creció de 170 a 182 tests en VERDE.
- [x] **Formato Conventional Commits:** Mensaje de commit imperativo en español (`feat(setup): ...`).

---

## 6. Próximo Objetivo Prioritario

- **Siguiente Paso Técnico:**
  1. Subir la rama `feature/fase-6.2-setup-wizard-rutas` al repositorio remoto (`git push -u origin feature/fase-6.2-setup-wizard-rutas`) y crear el Pull Request para revisión y fusión a `main` por parte de Erickson Sojo.
  2. Iniciar la **Fase 6.3: Usuarios, Autenticación BCrypt, Roles y Autorización Centralizada (`hasPermission` / `requirePermission`)** en la rama `feature/fase-6.3-autenticacion-roles`.
