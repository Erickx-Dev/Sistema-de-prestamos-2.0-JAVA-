# Bitácora de Sesión de Desarrollo: SESION-02

> **Proyecto:** CrediNoda 2.0 (Migración de Python a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 2 — Capa de Persistencia SQLite Limpia y Repositorios JDBC  
> **Protocolo Base:** [`docs/git_protocol.md`](../git_protocol.md) y [`AGENTS.md`](../../AGENTS.md)

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-02`
- **Fecha de Ejecución:** 2026-10-16
- **Responsables Técnicos:**
  - **Tech Lead y Administrador Único:** Erickson Sojo *(Supervisión, Aprobación de Git y Gestión de PR)*
  - **Desarrollador / Agente de IA:** Antigravity (Google DeepMind)
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

## 2. Trazabilidad de Tareas (Roadmap - Fase 2)

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
- [ ] `F3-T01`: Inicio de la Fase 3 (Capa de Servicios Core: `ClienteService`, `CreditoService`, `PagoService`) tras aprobación y merge de la Fase 2.

### Bloqueos / Impedimentos
- **Ninguno.** La persistencia funciona de forma determinista y rápida sin problemas de concurrencia ni fallos de bloqueo SQLite.

---

## 3. Desglose Técnico Exhaustivo por Archivo y Componente

### 3.1. Infraestructura de Conexión y Esquema
- **`DatabaseConnection.java` (`com.credinoda.persistence`):**
  - Configura `SQLiteConfig.enforceForeignKeys(true)` y `setBusyTimeout(5000)`.
  - Ejecuta explícitamente `PRAGMA foreign_keys = ON;` al inicializar la conexión.
  - Provee sobrecargas `getConnection()`, `getConnection(Path)` y `getConnection(String jdbcUrl)`.
- **`SchemaInitializer.java` (`com.credinoda.persistence`):**
  - Sentencias DDL para `clientes`, `creditos`, `cuotas`, `pagos`.
  - Columna `cuotas.en_mora INTEGER NOT NULL DEFAULT 0` nativa en el `CREATE TABLE`.
  - Índices sobre claves foráneas y fechas de vencimiento.

### 3.2. Repositorios JDBC (`com.credinoda.persistence.repository`)
- **`ClienteRepository.java`:**
  - `crear(Cliente)`: Inserta y asigna clave primaria mediante `RETURN_GENERATED_KEYS`.
  - `obtenerPorId(int)` / `obtenerPorDocumento(String)`: Búsquedas con `Optional<Cliente>`.
  - `buscarGeneral(String)`: Consulta combinada `WHERE nombre LIKE ? OR documento LIKE ?`.
  - `actualizar(Cliente)`: Actualiza nombre, teléfono y documento.
  - `listarTodos()`: Listado ordenado alfabéticamente por nombre.
- **`CreditoRepository.java`:**
  - `crear(Credito)` / `crearCuota(Cuota)`: Inserción de créditos y cuotas con claves generadas.
  - `obtenerPorCliente(int)`: Historial de créditos de un cliente ordenado por ID descendente.
  - `actualizarEstado(int, EstadoCredito)`: Transición de estado de crédito.
  - `actualizarEstadoCuota(int, EstadoCuota)`: Actualiza cuota y limpia `en_mora = 0` si pasa a `PAGADA`.
  - `actualizarMoraCuotas(LocalDate)`: Sentencia masiva de actualización en SQLite (`WHERE en_mora != (CASE ...)`).
  - `obtenerCuotasPendientes(int)`: JOIN para el Dashboard ordenado por vencimiento ascendente de créditos activos.
- **`PagoRepository.java`:**
  - `crear(Pago)`: Inserción de abonos.
  - `obtenerTotalPagadoCuota(int)` / `obtenerTotalPagadoCredito(int)`: Agregación con `BigDecimal.add()` en memoria.
  - `listarTodosConDetalle(String, LocalDate, LocalDate)`: Consulta enriquecida con filtrado dinámico.
  - `obtenerDetallePago(int)`: Cálculo relacional de saldos y porcentajes para comprobantes de pago.

---

## 4. Resultados de Verificación y Pruebas Automatizadas

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.credinoda.common.FormattersTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.common.ValidatorsTest
[INFO] Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.domain.model.CuotaTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.persistence.DatabaseConnectionTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.persistence.repository.ClienteRepositoryTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.persistence.repository.CreditoRepositoryTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.persistence.repository.PagoRepositoryTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.persistence.SchemaInitializerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 75, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **Cobertura:** 75 tests ejecutados al 100% en verde.
- **Aislamiento:** Cero mutación de archivos `.db` en disco durante las pruebas unitarias (uso exclusivo de `:memory:` y `@TempDir`).

---

## 5. Checklist Obligatorio de Calidad Pre-Cierre

- [x] **Separación Estricta de Capas:** Cero importaciones de JavaFX en `persistence`.
- [x] **Integridad Referencial:** Claves foráneas activas (`PRAGMA foreign_keys = ON;`) verificadas en tests.
- [x] **Precisión Monetaria:** Almacenamiento exacto con `toPlainString()` y lectura con `new BigDecimal(rs.getString())`.
- [x] **Base de Datos Limpia:** Cero código o comprobaciones de migración retroactiva de Python.
- [x] **Gobernanza Git:** Trabajo contenido en la sub-rama `feature/fase-2-persistencia-sqlite`.

---

## 6. Próximo Objetivo Prioritario (Fase 3)

- **Rama a crear:** `feature/fase-3-servicios-core`
- **Alcance:**
  - `ClienteService`: Validaciones de negocio, sanitización, unicidad de documento antes de persistir.
  - `CreditoService`: Cálculo financiero de interés simple mensual ($I = C \times i \times t$), factores de periodicidad (semanal 4, quincenal 2), cronograma de cuotas con redondeo `HALF_UP` y ajuste exacto de centavos en la última cuota (`RN-006`), creación atómica transaccional y sincronización periódica de mora.
  - `PagoService`: Registro de abonos totales/parciales, liquidación de cuotas y crédito, y prevención estricta de sobrepagos (`RN-009`).
