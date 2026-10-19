# Bitácora de Sesión de Desarrollo: SESION-11

> **Proyecto:** CrediNoda 2.0 (Migración a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 6.3: Usuarios, Autenticación BCrypt, Roles y Autorización Centralizada (RBAC)  
> **Documento Base:** [`docs/ROADMAP_JAVA.md`](../ROADMAP_JAVA.md) y [`docs/templates/session_summary_template.md`](../templates/session_summary_template.md)  

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-11`
- **Fecha de Ejecución:** `2026-10-18`
- **Responsable(s) Técnico(s) Presente(s):**
  - **Líder de Proyecto / Administrador Único:** Erickson Sojo *(Tech Lead / Aprobación)*
  - **Desarrollador / Agente IA:** Antigravity (Google DeepMind)
- **Rama Git de la Sesión:** `feature/fase-6.3-autenticacion-roles`
- **Objetivo General de la Sesión:**
  1. Diseñar, planificar e implementar integralmente la **Fase 6.3: Usuarios, Autenticación BCrypt, Roles y Autorización Centralizada (`hasPermission` / `requirePermission`)**.
  2. Implementar la seguridad de contraseñas mediante hashing criptográfico **BCrypt** (costo 12) con sal aleatoria nativa utilizando la biblioteca autorizada `at.favre.lib:bcrypt:0.10.2`.
  3. Establecer el modelo de control de acceso basado en roles (**RBAC**) distinguiendo roles `ADMINISTRADOR` (acceso integral, auditoría, respaldos y usuarios) y `OPERADOR` (cobranza y créditos a clientes preexistentes, sin crear ni editar clientes, sin respaldos ni administración de usuarios).
  4. Implementar el control de fuerza bruta con bloqueo automático de cuenta por 15 minutos tras 5 intentos fallidos consecutivos (`RN-023`), preservando mensajes genéricos para evitar la enumeración de credenciales.
  5. Asegurar la invariante de seguridad que prohíbe terminantemente la desactivación o degradación del último administrador activo del sistema (`RN-025`).
  6. Diseñar la autorización en doble capa: mandatoria a nivel de servicios core (`requirePermission`) y reactiva a nivel de interfaz visual (`hasPermission`, `PermissionGuard`).
  7. Incorporar en el asistente inicial `SetupWizard` el paso de creación obligatoria del primer usuario Administrador.
  8. Desarrollar las interfaces de usuario en JavaFX: `LoginDialog`, `UsuariosView`, `UsuarioDialog` y `CambiarContrasenaDialog`.
  9. Habilitar el cierre de sesión interactivo y seguro desde el bloque de marca `CrediNoda 2.0` en la barra de navegación horizontal persistente.
  10. Resolver de forma quirúrgica los problemas de renderizado y restaurar los estilos institucionales de Google Stitch en modales y el centrado de tablas en `ClientesView`, `DashboardView`, `ClienteDetailView` y `CreditoDetailView`.
  11. Mantener el 100% de la suite de pruebas unitarias automatizadas (**210 de 210 tests en VERDE**).
- **Casos de Uso y Reglas de Negocio Abordadas:**
  - [x] **CU-018 (`RN-022`, `RN-023`, `RN-026`):** Iniciar y cerrar sesión (BCrypt costo 12, bloqueo temporal y cierre interactivo en marca).
  - [x] **CU-019 (`RN-024`, `RN-025`):** Gestión de usuarios y roles (RBAC, salvaguarda de $\ge 1$ administrador y baja lógica).
  - [x] **CU-020 (`RN-022`):** Cambio y restablecimiento de contraseña segura.
  - [x] **Vectores de Prueba:** `VP-018` (Hash y verificación BCrypt), `VP-019` (Bloqueo temporal por intentos fallidos), `VP-020` (Matriz rol-permiso y rechazo de accesos no autorizados), `VP-021` (Blindaje del último administrador).

---

## 2. Trazabilidad de Tareas

### Tareas Completadas en la Sesión
- [x] `T-GOV-01`: Auditoría de rama inicial, emisión de solicitud de pre-autorización Git y creación de la sub-rama `feature/fase-6.3-autenticacion-roles`.
- [x] `T-PLAN-01`: Redacción del plan técnico de arquitectura `plan_fase_6_3_autenticacion_roles.md` y aprobación del Tech Lead.
- [x] `T-DEP-01`: Declaración de la dependencia autorizada `at.favre.lib:bcrypt:0.10.2` en `pom.xml`.
- [x] `T-DOM-01`: Creación del modelo y DTOs de seguridad: `Usuario.java`, `Rol.java`, `Permiso.java`, `SesionUsuario.java` y excepciones `AutenticacionException.java`, `UsuarioBloqueadoException.java`, `AccesoDenegadoException.java`.
- [x] `T-PERS-01`: Modificación de `SchemaInitializer.java` para aprovisionar la tabla relacional `usuarios` con restricciones DDL estrictas.
- [x] `T-PERS-02`: Implementación de `UsuarioRepository.java` con mapeo JDBC tipado y transaccionalidad segura.
- [x] `T-SRV-01`: Implementación de `PasswordHasher.java` y `BCryptPasswordHasher.java` con costo 12.
- [x] `T-SRV-02`: Implementación de `ContextoSeguridad.java` con gestión de sesión activa por hilo y sesión estática con fallback a sesión de sistema.
- [x] `T-SRV-03`: Implementación de `AutorizacionService.java` y `Autorizador.java` bajo matriz RBAC estricta.
- [x] `T-SRV-04`: Implementación de `AutenticacionService.java` con mitigación de ataques de fuerza bruta y tracking de accesos.
- [x] `T-SRV-05`: Implementación de `UsuarioService.java` garantizando la regla `RN-025` del último administrador.
- [x] `T-SRV-06`: Instrumentación con `requirePermission` en `ClienteService`, `CreditoService`, `PagoService`, `BackupService` y `ReporteService`.
- [x] `T-COM-01`: Adición en `Validators.java` de la regla de contraseña segura (`validarPasswordSegura`).
- [x] `T-UI-01`: Integración del paso de alta de Administrador en `SetupWizard.java`.
- [x] `T-UI-02`: Construcción del diálogo de inicio de sesión `LoginDialog.java`.
- [x] `T-UI-03`: Construcción de `UsuariosView.java` y `UsuarioDialog.java` para administración de personal.
- [x] `T-UI-04`: Construcción de `CambiarContrasenaDialog.java`.
- [x] `T-UI-05`: Implementación de `PermissionGuard.java` y adaptación de visibilidad en `ClientesView`, `ClienteDetailView` y `CreditoNewView`.
- [x] `T-UI-06`: Adaptación de `NavigationBar.java` para aplicar permisos dinámicamente y ejecutar el diálogo de confirmación de logout al pulsar la marca `CrediNoda 2.0`.
- [x] `T-UI-07`: Adaptación de `ViewRouter.java` con guardias de ruta y limpieza de caché (`clearCache()`).
- [x] `T-UI-08`: Orquestación del ciclo de vida en `CrediNodaApplication.java` (Login previo a `MainShell` y reautenticación).
- [x] `T-UI-09`: Corrección del parser CSS en `credinoda.css` (llave faltante en `.btn-secondary:hover`) y restauración de cabeceras verdes institucionales `#164E3B` en todas las ventanas modales.
- [x] `T-UI-10`: Estandarización y centrado horizontal de columnas y celdas en tablas de `ClientesView`, `DashboardView`, `ClienteDetailView` y `CreditoDetailView` con sobrecarga en `TableColumnHelper.java`.
- [x] `T-TEST-01`: Suites unitarias automatizadas: `UsuarioRepositoryTest`, `BCryptPasswordHasherTest`, `AutenticacionServiceTest`, `AutorizacionServiceTest`, `UsuarioServiceTest` y `TableColumnHelperTest`.
- [x] `T-QA-01`: Verificación de compilación limpia y 100% de tests en verde (**210/210 tests exitosos en Surefire**).
- [x] `T-DOC-01`: Actualización del checklist y métricas en [`ROADMAP_JAVA.md`](../ROADMAP_JAVA.md).
- [x] `T-GIT-01`: Registro formal de los commits `3153a4a` y `54f709f` con Conventional Commits.

---

## 3. Desglose Técnico Exhaustivo por Archivo y Función (Nivel Auditoría)

### 1. Archivo: `src/main/java/com/credinoda/domain/model/Usuario.java`
- **Estado:** `NUEVO`
- **Capa:** `domain/model`
- **Atributos:** `id` (Long), `username` (String), `passwordHash` (String), `salt` (String), `nombreCompleto` (String), `rol` (Rol), `activo` (boolean), `intentosFallidos` (int), `fechaBloqueo` (LocalDateTime), `ultimoAcceso` (LocalDateTime), `fechaCreacion` (LocalDateTime).
- **Métodos Clave:** `estaBloqueado() : boolean` (evalúa si la fecha de bloqueo es posterior al instante actual), `incrementarIntentos()`, `resetearIntentos()`.

### 2. Archivo: `src/main/java/com/credinoda/domain/model/Rol.java`
- **Estado:** `NUEVO`
- **Capa:** `domain/model`
- **Constantes:** `ADMINISTRADOR`, `OPERADOR`.
- **Métodos:** `nombreLegible() : String`.

### 3. Archivo: `src/main/java/com/credinoda/domain/model/Permiso.java`
- **Estado:** `NUEVO`
- **Capa:** `domain/model`
- **Constantes:** `CLIENTES_VER`, `CLIENTES_CREAR`, `CLIENTES_EDITAR`, `CREDITOS_VER`, `CREDITOS_CREAR`, `PAGOS_VER`, `PAGOS_REGISTRAR`, `REPORTES_VER`, `USUARIOS_VER`, `USUARIOS_GESTIONAR`, `BACKUP_GESTIONAR`, `AUDITORIA_VER`.

### 4. Archivo: `src/main/java/com/credinoda/domain/dto/SesionUsuario.java`
- **Estado:** `NUEVO`
- **Capa:** `domain/dto`
- **Estructura:** Record `SesionUsuario(Long id, String username, String nombreCompleto, Rol rol, LocalDateTime momentoInicio)`.
- **Métodos:** `esAdministrador() : boolean`.

### 5. Archivo: `src/main/java/com/credinoda/persistence/repository/UsuarioRepository.java`
- **Estado:** `NUEVO`
- **Capa:** `persistence/repository`
- **Métodos Implementados:**
  - `crear(Usuario) : Usuario`: Inserta registro con retorno de clave generada.
  - `obtenerPorId(Long) : Optional<Usuario>`
  - `obtenerPorUsername(String) : Optional<Usuario>`
  - `listarTodos() : List<Usuario>`: Listado ordenado por ID ascendente.
  - `actualizarDatos(Long, String, Rol) : void`
  - `actualizarPassword(Long, String) : void`
  - `actualizarUltimoAcceso(Long, LocalDateTime) : void`
  - `registrarIntentoFallido(Long, int, LocalDateTime) : void`
  - `limpiarIntentosFallidos(Long) : void`
  - `cambiarEstadoActivo(Long, boolean) : void`
  - `contarAdministradoresActivos() : int`

### 6. Archivo: `src/main/java/com/credinoda/service/BCryptPasswordHasher.java`
- **Estado:** `NUEVO`
- **Capa:** `service`
- **Responsabilidad:** Cifra con `BCrypt.withDefaults().hashToString(12, password.toCharArray())` y verifica con `BCrypt.verifyer().verify(password.toCharArray(), hash)`.

### 7. Archivo: `src/main/java/com/credinoda/service/ContextoSeguridad.java`
- **Estado:** `NUEVO`
- **Capa:** `service`
- **Responsabilidad:** Mantiene la sesión del usuario autenticado en `ThreadLocal<SesionUsuario>` con respaldo estático para JavaFX. Proporciona `sistema()` para operaciones de inicialización y migración.

### 8. Archivo: `src/main/java/com/credinoda/service/AutorizacionService.java`
- **Estado:** `NUEVO`
- **Capa:** `service`
- **Matriz de Privilegios:**
  - `ADMINISTRADOR`: Posee todos los 12 permisos de `Permiso`.
  - `OPERADOR`: Posee `CLIENTES_VER`, `CREDITOS_VER`, `CREDITOS_CREAR`, `PAGOS_VER`, `PAGOS_REGISTRAR`, `REPORTES_VER`.
- **Métodos:**
  - `hasPermission(SesionUsuario, Permiso) : boolean`
  - `requirePermission(Permiso) : void`: Lanza `AccesoDenegadoException` si la sesión activa carece del privilegio.

### 9. Archivo: `src/main/java/com/credinoda/service/AutenticacionService.java`
- **Estado:** `NUEVO`
- **Capa:** `service`
- **Lógica de Autenticación:**
  - Sanitiza el nombre de usuario y valida obligatoriedad.
  - Si el usuario existe y está bloqueado, calcula minutos restantes y lanza `UsuarioBloqueadoException`.
  - Si la contraseña falla: incrementa intentos fallidos; si alcanza 5, bloquea por 15 minutos (`LocalDateTime.now().plusMinutes(15)`). Lanza mensaje genérico "Credenciales de acceso incorrectas o cuenta inactiva".
  - Si la contraseña es válida: limpia intentos fallidos, actualiza `ultimoAcceso` y retorna `SesionUsuario`.

### 10. Archivo: `src/main/java/com/credinoda/service/UsuarioService.java`
- **Estado:** `NUEVO`
- **Capa:** `service`
- **Salvaguardas de Negocio:**
  - `cambiarEstadoActivo(Long, boolean)`: Impide desactivar la cuenta del último administrador activo del sistema (`RN-025`).
  - `actualizarUsuario(Long, String, Rol)`: Impide degradar el rol del último administrador activo a operador.
  - `cambiarPassword(...)`: Exige validación previa de la contraseña anterior para cambio voluntario; permite restablecimiento directo si es ejecutado por un Administrador.

### 11. Archivos de Vistas y Diálogos UI:
- **`UsuarioDialog.java`:** Modal de alta y edición con cabecera verde institucional, campos para nombre completo, username, contraseña, selección de rol y estado activo.
- **`CambiarContrasenaDialog.java`:** Modal adaptado para cambio voluntario con contraseña anterior o reseteo administrativo.
- **`UsuariosView.java`:** Vista de tabla con columnas centradas (ID, Usuario, Nombre, Rol, Estado, Seguridad, Último Acceso, Acciones), badge visual de fallos/bloqueo y botones con control de permisos.
- **`TableColumnHelper.java`:** Incorpora sobrecarga `monedaColumn(title, extractor, alignment, prefWidth)` con centrado garantizado en cabecera y celdas.

---

## 4. Auditoría de Calidad y Verificación de Pruebas

```powershell
mvn clean test
```

### Resultados de la Suite Automatizada
| Módulo / Clase de Prueba | Pruebas | Fallos | Errores | Estado |
| :--- | :---: | :---: | :---: | :---: |
| `ConfiguracionLocalStoreTest` | 7 | 0 | 0 | **VERDE** |
| `FormattersTest` | 4 | 0 | 0 | **VERDE** |
| `ValidatorsTest` | 50 | 0 | 0 | **VERDE** |
| `CuotaTest` | 2 | 0 | 0 | **VERDE** |
| `DatabaseConnectionTest` | 2 | 0 | 0 | **VERDE** |
| `ClienteRepositoryTest` | 8 | 0 | 0 | **VERDE** |
| `CreditoRepositoryTest` | 6 | 0 | 0 | **VERDE** |
| `PagoRepositoryTest` | 4 | 0 | 0 | **VERDE** |
| `UsuarioRepositoryTest` | 8 | 0 | 0 | **VERDE** |
| `SchemaInitializerTest` | 5 | 0 | 0 | **VERDE** |
| `AutenticacionServiceTest` | 5 | 0 | 0 | **VERDE** |
| `AutorizacionServiceTest` | 4 | 0 | 0 | **VERDE** |
| `BackupServiceTest` | 6 | 0 | 0 | **VERDE** |
| `BCryptPasswordHasherTest` | 4 | 0 | 0 | **VERDE** |
| `ClienteServiceTest` | 19 | 0 | 0 | **VERDE** |
| `ConfiguracionInicialServiceTest` | 5 | 0 | 0 | **VERDE** |
| `CreditoServiceTest` | 11 | 0 | 0 | **VERDE** |
| `MoraCuotasTest` | 7 | 0 | 0 | **VERDE** |
| `PagoServiceTest` | 9 | 0 | 0 | **VERDE** |
| `PdfServiceTest` | 5 | 0 | 0 | **VERDE** |
| `ReporteServiceTest` | 7 | 0 | 0 | **VERDE** |
| `UsuarioServiceTest` | 5 | 0 | 0 | **VERDE** |
| `MetricCardTest` | 5 | 0 | 0 | **VERDE** |
| `TableColumnHelperTest` | 5 | 0 | 0 | **VERDE** |
| `ViewRouterTest` | 7 | 0 | 0 | **VERDE** |
| `DashboardViewTest` | 2 | 0 | 0 | **VERDE** |
| `PagosViewFilterTest` | 3 | 0 | 0 | **VERDE** |
| `ReportesViewTest` | 5 | 0 | 0 | **VERDE** |
| **TOTAL GENERAL** | **210** | **0** | **0** | **100% VERDE** |

---

## 5. Historial de Commits en la Sesión

1. `3153a4a`: `feat(auth): implementar sistema completo de autenticacion rbac y gestion de usuarios`
2. `54f709f`: `style(ui): centrar columnas de tablas y restaurar diseno institucional stitch en modales`
