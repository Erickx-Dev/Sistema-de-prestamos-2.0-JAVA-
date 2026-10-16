# Bitácora de Sesión de Desarrollo: SESION-02

> **Proyecto:** CrediNoda 2.0 (Migración de Python a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 2 — Capa de Persistencia SQLite Limpia y Repositorios JDBC  
> **Plantilla Base:** [`docs/templates/session_summary_template.md`](../templates/session_summary_template.md)  
> **Protocolo Operativo:** [`docs/git_protocol.md`](../git_protocol.md) y [`AGENTS.md`](../../AGENTS.md)

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-02`
- **Fecha de Ejecución:** 2026-10-16
- **Responsable(s) Técnico(s) Presente(s):**
  - **Líder de Proyecto / Administrador:** Erickson Sojo *(Tech Lead / Aprobación Git)*
  - **Desarrollador / Agente IA:** Antigravity (Google DeepMind)
- **Rama Git Asociada:** `feature/fase-2-persistencia-sqlite`
- **Objetivo General de la Sesión:**
  - Implementar la infraestructura de persistencia SQLite local y los repositorios JDBC de CrediNoda 2.0.
  - Asegurar la activación estricta de `PRAGMA foreign_keys = ON;` para integridad referencial.
  - Aprovisionar el esquema relacional limpio desde cero con las 4 tablas (`clientes`, `creditos`, `cuotas`, `pagos`) con `cuotas.en_mora` nativo, eliminando cualquier código de migración hacia Python.
  - Diseñar e implementar los repositorios JDBC (`ClienteRepository`, `CreditoRepository`, `PagoRepository`) con almacenamiento exacto de `BigDecimal` y `LocalDate` como cadenas `TEXT`.
  - Desarrollar la suite de 24 nuevas pruebas unitarias en SQLite `:memory:` y archivos temporales, alcanzando 75 tests totales en verde (100% pass rate).

- **Casos de Uso y Reglas de Negocio Abordadas:**
  - [x] **CU-001, CU-002, CU-003, CU-004:** Alta, consulta, modificación y búsqueda de clientes.
  - [x] **CU-006, CU-007, CU-008:** Persistencia de créditos y cuotas, actualización de estados y sincronización masiva de mora.
  - [x] **CU-009, CU-010, CU-011:** Registro de pagos, sumatorias acumuladas, historial con filtros y consolidación para comprobante PDF.
  - [x] **CU-012:** Consulta relacional de las próximas cuotas por cobrar para el Dashboard.
  - [x] **RN-002:** Unicidad del documento de identidad garantizada por restricción `UNIQUE` en SQLite.
  - [x] **RN-003:** Búsqueda general insensible con operador `LIKE`.
  - [x] **RN-008:** Sentencia SQL masiva optimizada para actualización de mora en cuotas.
  - [x] **RN-010:** Sumatorias monetarias acumuladas en memoria con `BigDecimal.add()` para precisión absoluta.
  - [x] **RN-012:** Consulta relacional consolidada para recibos formales `REC-xxxxx`.
  - [x] **RN-013:** Filtrado de próximas cuotas excluyendo créditos pagados.
  - [x] **RN-016:** Historial de pagos enriquecido con JOIN múltiple y filtrado flexible.

---

## 2. Trazabilidad de Tareas (Roadmap)

### Tareas Completadas en la Sesión
- [x] `F2-T01`: Implementación de `DatabaseConnection` con activación obligatoria de `PRAGMA foreign_keys = ON;` y soporte para bases en memoria y disco.
- [x] `F2-T02`: Implementación de `SchemaInitializer` con DDL limpio de las 4 tablas (`clientes`, `creditos`, `cuotas`, `pagos`) e índices optimizados, con `en_mora` nativo.
- [x] `F2-T03`: Implementación de `ClienteRepository` con operaciones CRUD completas y consultas parametrizadas `PreparedStatement`.
- [x] `F2-T04`: Implementación de `CreditoRepository` con persistencia de créditos, cuotas, actualización de estados, `actualizarMoraCuotas` masivo y `obtenerCuotasPendientes(10)`.
- [x] `F2-T05`: Implementación de `PagoRepository` con registro de pagos, sumatorias con `BigDecimal.add()`, `listarTodosConDetalle` y `obtenerDetallePago`.
- [x] `F2-T06`: Implementación de suites de pruebas unitarias (`DatabaseConnectionTest`, `SchemaInitializerTest`, `ClienteRepositoryTest`, `CreditoRepositoryTest`, `PagoRepositoryTest`).
- [x] `F2-T07`: Verificación de calidad y ejecución del build (`mvn clean test` con 75/75 tests en verde).
- [x] `F2-T08`: Actualización del Roadmap formal en `docs/ROADMAP_JAVA.md`.

### Tareas Pendientes o en Progreso
- [ ] `F3-T01`: Inicio de la Fase 3 (Capa de Servicios Core) tras integración en `main`.

### Tareas Bloqueadas / Impedimentos
- **Ninguno.** La persistencia opera de forma determinista y aislada.

---

## 3. Desglose Técnico Exhaustivo por Archivo y Función (Nivel Auditoría)

### Archivo: `src/main/java/com/credinoda/persistence/DatabaseConnection.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `persistence`
- **Clase(s) / Módulo:** `DatabaseConnection`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `getConnection` | `(ninguno)` | `Connection` | Abre conexión a `AppConfig.DATABASE_PATH`, asegura directorios locales y aplica claves foráneas. |
| `getConnection` | `dbPath: Path` | `Connection` | Abre conexión a partir de una ruta de archivo local específica con `PRAGMA foreign_keys = ON;`. |
| `getConnection` | `jdbcUrl: String` | `Connection` | Configura `SQLiteConfig.enforceForeignKeys(true)`, timeout de 5000ms y ejecuta `PRAGMA foreign_keys = ON;`. |

---

### Archivo: `src/main/java/com/credinoda/persistence/SchemaInitializer.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `persistence`
- **Clase(s) / Módulo:** `SchemaInitializer`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `inicializar` | `conn: Connection` | `void` | Ejecuta sentencias DDL limpias para `clientes`, `creditos`, `cuotas`, `pagos` y 5 índices. |
| `inicializarBaseDatos` | `(ninguno)` | `void` | Abre conexión predeterminada, ejecuta `inicializar(conn)` y cierra limpiamente. |
| `inicializarBaseDatos` | `dbPath: Path` | `void` | Inicializa base de datos sobre una ruta de archivo específica. |
| `inicializarBaseDatos` | `jdbcUrl: String` | `void` | Inicializa base de datos a partir de una URL JDBC (ej. `:memory:`). |

---

### Archivo: `src/main/java/com/credinoda/persistence/repository/ClienteRepository.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `persistence/repository`
- **Clase(s) / Módulo:** `ClienteRepository`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `crear` | `cliente: Cliente` | `Cliente` | Inserta cliente en SQLite y asigna la clave autoincremental retornada por `getGeneratedKeys()`. |
| `obtenerPorId` | `id: int` | `Optional<Cliente>` | Búsqueda por clave primaria con `PreparedStatement`. |
| `obtenerPorDocumento` | `documento: String` | `Optional<Cliente>` | Consulta por documento único con coincidencia exacta (`RN-002`). |
| `buscarGeneral` | `query: String` | `List<Cliente>` | Consulta parcial `WHERE nombre LIKE ? OR documento LIKE ? ORDER BY nombre ASC` (`RN-003`). |
| `buscarPorNombre` | `query: String` | `List<Cliente>` | Búsqueda parcial por nombre `WHERE nombre LIKE ? ORDER BY nombre ASC`. |
| `actualizar` | `cliente: Cliente` | `void` | Ejecuta `UPDATE clientes SET nombre = ?, telefono = ?, documento = ? WHERE id = ?`. |
| `listarTodos` | `(ninguno)` | `List<Cliente>` | Consulta la totalidad de prestatarios ordenados alfabéticamente por nombre. |
| `mapRow` | `rs: ResultSet` | `Cliente` | Mapea la fila relacional actual a la entidad `Cliente`. |

---

### Archivo: `src/main/java/com/credinoda/persistence/repository/CreditoRepository.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `persistence/repository`
- **Clase(s) / Módulo:** `CreditoRepository`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `crear` | `credito: Credito` | `Credito` | Inserta crédito con valores monetarios formateados mediante `toPlainString()` y asigna clave autoincremental. |
| `crearCuota` | `cuota: Cuota` | `Cuota` | Inserta cuota individual en la tabla `cuotas` asignando el ID generado. |
| `obtenerPorId` | `id: int` | `Optional<Credito>` | Consulta crédito por ID. |
| `obtenerPorCliente` | `clienteId: int` | `List<Credito>` | Consulta créditos de un cliente ordenados descendentemente por ID. |
| `listarTodos` | `(ninguno)` | `List<Credito>` | Lista todos los créditos registrados en el sistema. |
| `obtenerCuotasPorCredito` | `creditoId: int` | `List<Cuota>` | Retorna cronograma de cuotas ordenado por `numero_cuota ASC`. |
| `obtenerCuotaPorId` | `cuotaId: int` | `Optional<Cuota>` | Consulta cuota por clave primaria. |
| `actualizarEstado` | `creditoId: int, nuevoEstado: EstadoCredito` | `void` | Actualiza la columna `estado` en la tabla `creditos`. |
| `actualizarEstadoCuota` | `cuotaId: int, nuevoEstado: EstadoCuota` | `void` | Actualiza estado de cuota y limpia `en_mora = 0` si pasa a `PAGADA`. |
| `actualizarMoraCuotas` | `fechaReferencia: LocalDate` | `int` | Actualización masiva en SQLite: marca `en_mora = 1` si `fecha_vencimiento < corte` y estado no es `PAGADA`, solo mutando filas divergentes (`RN-008`). |
| `obtenerCuotasPendientes` | `limite: int` | `List<ProximoPagoItem>` | JOIN para Dashboard de cuotas pendientes/parciales de créditos activos (`CU-012`, `RN-013`). |

---

### Archivo: `src/main/java/com/credinoda/persistence/repository/PagoRepository.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `persistence/repository`
- **Clase(s) / Módulo:** `PagoRepository`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `crear` | `pago: Pago` | `Pago` | Inserta pago con monto exacto en texto y asigna ID autoincremental. |
| `obtenerPorId` | `id: int` | `Optional<Pago>` | Consulta pago por clave primaria. |
| `obtenerPorCuota` | `cuotaId: int` | `List<Pago>` | Consulta pagos aplicados a una cuota específica. |
| `obtenerPorCredito` | `creditoId: int` | `List<Pago>` | Consulta pagos aplicados a un crédito completo. |
| `obtenerTotalPagadoCuota` | `cuotaId: int` | `BigDecimal` | Suma en memoria con `BigDecimal.add()` todos los abonos de la cuota (`RN-010`). |
| `obtenerTotalPagadoCredito`| `creditoId: int` | `BigDecimal` | Suma en memoria con `BigDecimal.add()` todos los abonos del crédito (`RN-010`). |
| `listarTodosConDetalle` | `filtro: String, fIni: LocalDate, fFin: LocalDate` | `List<PagoDetalleItem>` | Historial consolidado con JOIN cuota/crédito/cliente y filtros dinámicos (`CU-010`, `RN-016`). |
| `obtenerDetallePago` | `pagoId: int` | `Optional<DatosComprobante>` | Consulta completa para recibo formal `REC-xxxxx` con saldos y avance calculados (`CU-011`, `RN-012`). |

---

## 4. Impacto en Esquema de Persistencia y Base de Datos

- **¿Hubo alteraciones al modelo de datos en esta sesión?:** SÍ (Aprovisionamiento inicial de la base de datos limpia de CrediNoda 2.0).
- **Archivos de Persistencia Afectados:**
  - `DatabaseConnection.java`: Conexión JDBC SQLite con `PRAGMA foreign_keys = ON;`.
  - `SchemaInitializer.java`: DDL limpio de las 4 tablas e índices optimizados.

### Detalle de Modificaciones DDL / DML
- **Tablas Creadas:** `clientes`, `creditos`, `cuotas`, `pagos`.
- **Sentencias DDL Aplicadas:**
  ```sql
  CREATE TABLE IF NOT EXISTS clientes (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      nombre TEXT NOT NULL,
      telefono TEXT NOT NULL,
      documento TEXT NOT NULL UNIQUE,
      fecha_registro TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS creditos (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      cliente_id INTEGER NOT NULL,
      capital TEXT NOT NULL,
      tasa_mensual TEXT NOT NULL,
      plazo_meses INTEGER NOT NULL,
      total_intereses TEXT NOT NULL,
      total_pagar TEXT NOT NULL,
      frecuencia_pago TEXT NOT NULL,
      fecha_inicio TEXT NOT NULL,
      estado TEXT NOT NULL DEFAULT 'ACTIVO',
      FOREIGN KEY (cliente_id) REFERENCES clientes(id)
  );

  CREATE TABLE IF NOT EXISTS cuotas (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      credito_id INTEGER NOT NULL,
      numero_cuota INTEGER NOT NULL,
      fecha_vencimiento TEXT NOT NULL,
      monto TEXT NOT NULL,
      estado TEXT NOT NULL DEFAULT 'PENDIENTE',
      en_mora INTEGER NOT NULL DEFAULT 0,
      FOREIGN KEY (credito_id) REFERENCES creditos(id)
  );

  CREATE TABLE IF NOT EXISTS pagos (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      credito_id INTEGER NOT NULL,
      cuota_id INTEGER NOT NULL,
      fecha_pago TEXT NOT NULL,
      monto TEXT NOT NULL,
      FOREIGN KEY (credito_id) REFERENCES creditos(id),
      FOREIGN KEY (cuota_id) REFERENCES cuotas(id)
  );

  CREATE INDEX IF NOT EXISTS idx_creditos_cliente ON creditos(cliente_id);
  CREATE INDEX IF NOT EXISTS idx_cuotas_credito ON cuotas(credito_id);
  CREATE INDEX IF NOT EXISTS idx_cuotas_vencimiento ON cuotas(estado, fecha_vencimiento);
  CREATE INDEX IF NOT EXISTS idx_pagos_cuota ON pagos(cuota_id);
  CREATE INDEX IF NOT EXISTS idx_pagos_credito ON pagos(credito_id);
  ```
- **Razón Técnica:** Aprovisionamiento desde cero según la directriz de descartar datos de Python y contar con `cuotas.en_mora` nativo.

---

## 5. Checklist Obligatorio de Calidad Pre-Cierre

- [x] **Separación Estricta de Capas:** Cero importaciones de JavaFX en `persistence`.
- [x] **Integridad Referencial:** Claves foráneas activas (`PRAGMA foreign_keys = ON;`) verificadas en tests.
- [x] **Precisión Monetaria:** Almacenamiento exacto con `toPlainString()` y lectura con `new BigDecimal(rs.getString())`.
- [x] **Base de Datos Limpia:** Cero código o comprobaciones de migración retroactiva de Python.
- [x] **Gobernanza Git:** Trabajo contenido en la sub-rama `feature/fase-2-persistencia-sqlite`.

---

## 6. Deuda Técnica y Puntos Críticos para la Siguiente Sesión

- **Riesgos o Limitaciones Detectadas:** Ninguno.
- **Refactorizaciones Pendientes:** Ninguna.
- **Próximo Objetivo Prioritario:** Fase 3 — Capa de Servicios Core (`ClienteService`, `CreditoService`, `PagoService`).
