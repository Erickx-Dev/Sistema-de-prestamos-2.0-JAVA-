# Bitácora de Sesión de Desarrollo: SESION-03

> **Proyecto:** CrediNoda 2.0 (Migración de Python a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 3 — Capa de Servicios Core (`ClienteService`, `CreditoService`, `PagoService`)  
> **Plantilla Base:** [`docs/templates/session_summary_template.md`](../templates/session_summary_template.md)  
> **Protocolo Operativo:** [`docs/git_protocol.md`](../git_protocol.md) y [`AGENTS.md`](../../AGENTS.md)

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-03`
- **Fecha de Ejecución:** 2026-10-16
- **Responsable(s) Técnico(s) Presente(s):**
  - **Líder de Proyecto / Administrador:** Erickson Sojo *(Tech Lead / Aprobación Git)*
  - **Desarrollador / Agente IA:** Antigravity (Google DeepMind)
- **Rama Git Asociada:** `feature/fase-3-servicios-core`
- **Objetivo General de la Sesión:**
  - Implementar la lógica financiera y transaccional central de CrediNoda 2.0.
  - Desarrollar `ClienteService` con validaciones de no vacuidad, sanitización (`.trim()`) y unicidad de documento (`RN-001`, `RN-002`).
  - Desarrollar `CreditoService` con la fórmula exacta de interés simple mensual ($I = C \times i \times t$), factores de periodicidad (semanal 4, quincenal 2), proyección de vencimientos cada 7 o 15 días, ajuste residual exacto de centavos en la última cuota para garantizar $\sum \text{cuotas} == \text{total\_pagar}$, creación atómica bajo transacción JDBC y sincronización masiva de mora (`RN-004` a `RN-008`).
  - Desarrollar `PagoService` con registro de abonos parciales/totales, prevención estricta de sobrepagos al centavo (`RN-009`, `VP-008`), libertad de orden en el pago de cuotas y transiciones de estado hacia `PAGADO` (`RN-011`).
  - Ejecutar la suite completa de pruebas unitarias alcanzando 120 tests en verde (100% pass rate).

- **Casos de Uso y Reglas de Negocio Abordadas:**
  - [x] **CU-001, CU-002, CU-003, CU-004:** Gestión integral de clientes y su cartera.
  - [x] **CU-005, CU-006, CU-007, CU-008:** Simulación, otorgamiento atómico de crédito, cálculo dinámico de saldos y sincronización de mora.
  - [x] **CU-009, CU-010, CU-011:** Registro de cobranzas, historial y datos consolidados de comprobante.
  - [x] **RN-001:** Sanitización con trim y obligatoriedad en clientes.
  - [x] **RN-002:** Unicidad de documento en creación y edición.
  - [x] **RN-004:** Interés simple mensual ($I = C \times i \times t$) con redondeo `HALF_UP`.
  - [x] **RN-005:** Periodicidades canónicas (semanal x4, quincenal x2).
  - [x] **RN-006:** Ajuste de residuo en la última cuota garantizando $\sum cuotas == total\_pagar$.
  - [x] **RN-007:** Proyección de vencimientos cada 7 días en semanal y cada 15 días en quincenal.
  - [x] **RN-008:** Criterio de mora estricto sin alterar el estado `ACTIVO` del crédito.
  - [x] **RN-009:** Prevención estricta de sobrepago al centavo y libertad de orden en cuotas.
  - [x] **RN-010:** Saldos insolutos calculados por resta de pagos acumulados.
  - [x] **RN-011:** Transiciones de estado automáticas hacia `PARCIAL`, `PAGADA` y crédito `PAGADO`.

---

## 2. Trazabilidad de Tareas (Roadmap)

### Tareas Completadas en la Sesión
- [x] `F3-T01`: Implementación de `ClienteService` (alta, edición, validación de documento duplicado, consulta por ID, documento y búsqueda general).
- [x] `F3-T02`: Implementación de `CreditoService` (cálculo de interés simple, número de cuotas, plan con ajuste de centavos, creación atómica transaccional JDBC, saldo pendiente, transición de estado y mora masiva).
- [x] `F3-T03`: Implementación de `PagoService` (registro de pagos, validación contra sobrepago al centavo, transición automática de estados y consulta para comprobante).
- [x] `F3-T04`: Creación de la suite `ClienteServiceTest` (18 tests parametrizados para campos vacíos, duplicados y búsquedas).
- [x] `F3-T05`: Creación de la suite `CreditoServiceTest` (11 tests cubriendo `VP-001` a `VP-006`, validaciones numéricas y atomicidad).
- [x] `F3-T06`: Creación de la suite `PagoServiceTest` (9 tests cubriendo `VP-007`, `VP-008`, sobrepagos y transiciones).
- [x] `F3-T07`: Creación de la suite `MoraCuotasTest` (7 tests cubriendo vencimientos, extinción de mora y etiqueta calculada).
- [x] `F3-T08`: Ejecución y aprobación al 100% de la suite completa (`mvn clean test`, 120/120 tests en verde).
- [x] `F3-T09`: Actualización de la documentación en `docs/ROADMAP_JAVA.md`.

### Tareas Pendientes o en Progreso
- [ ] `F4-T01`: Inicio de la Fase 4 (Servicios Especializados: Reportes, PDF con QR y Backup/Restauración).

### Tareas Bloqueadas / Impedimentos
- **Ninguno.** Las transacciones atómicas y cálculos de centavos operan con exactitud matemática.

---

## 3. Desglose Técnico Exhaustivo por Archivo y Función (Nivel Auditoría)

### Archivo: `src/main/java/com/credinoda/service/ClienteService.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `service`
- **Clase(s) / Módulo:** `ClienteService`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `crearCliente` | `nombre: String, telefono: String, documento: String` | `Cliente` | Sanitiza campos con `.trim()`, verifica no vacuidad (`RN-001`), valida unicidad de documento (`RN-002`) y persiste vía repositorio. |
| `actualizarCliente` | `id: int, nombre: String, tel: String, doc: String` | `Cliente` | Valida existencia, actualiza campos no nulos asegurando que el nuevo documento no colisione con otro prestatario. |
| `obtenerClientePorId` | `id: int` | `Cliente` | Obtiene cliente por ID o lanza `ClienteNoEncontradoException`. |
| `buscarPorDocumento` | `documento: String` | `Optional<Cliente>` | Búsqueda por documento único exacto. |
| `buscarClientes` | `termino: String` | `List<Cliente>` | Si término es vacío retorna todos; si tiene texto busca por coincidencia en nombre o documento (`RN-003`). |
| `listarTodos` | `(ninguno)` | `List<Cliente>` | Lista todos los clientes en orden alfabético. |
| `obtenerCreditosCliente` | `clienteId: int` | `List<Credito>` | Valida existencia del cliente y retorna su historial de créditos (`CU-004`). |
| `sanitizarObligatorio` | `valor: String, mensaje: String` | `String` | Valida que el texto no sea nulo ni vacío tras `strip()`; de lo contrario lanza `DatosInvalidosException`. |

---

### Archivo: `src/main/java/com/credinoda/service/CreditoService.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `service`
- **Clase(s) / Módulo:** `CreditoService`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `calcularIntereses` | `capital: BigDecimal, tasa: BigDecimal, plazo: int` | `BigDecimal` | $I = \text{round\_half\_up}(C \times i \times t, 2)$ validando $C > 0$, $i \ge 0$, $t > 0$ (`RN-004`). |
| `calcularTotalPagar` | `capital: BigDecimal, intereses: BigDecimal` | `BigDecimal` | $C + I$ con escala 2 decimales. |
| `calcularNumeroCuotas` | `plazoMeses: int, frecuencia: FrecuenciaPago` | `int` | Semanal $\times 4$, Quincenal $\times 2$ (`RN-005`). |
| `generarPlanPagos` | `total: BigDecimal, n: int, freq: FrecuenciaPago, fIni: LocalDate` | `List<Cuota>` | Cuota base $total / n$ y ajuste de residuo en la cuota $n$ (`RN-006`). Vencimientos cada 7 o 15 días (`RN-007`). |
| `crearCredito` | `cliId: int, cap: BigDecimal, tasa: BigDecimal, plazo: int, freq: FrecuenciaPago, fIni: LocalDate` | `Credito` | Transacción atómica JDBC (`connection.setAutoCommit(false)`): persiste crédito y sus $n$ cuotas (`CU-006`). |
| `obtenerCreditoPorId` | `id: int` | `Credito` | Consulta crédito por ID o lanza `CreditoNoEncontradoException`. |
| `obtenerCuotasCredito`| `creditoId: int` | `List<Cuota>` | Retorna cronograma de cuotas ordenado. |
| `calcularSaldoPendiente` | `creditoId: int` | `BigDecimal` | Total a pagar menos pagos acumulados en el crédito (`RN-010`). |
| `determinarEstadoCredito` | `creditoId: int` | `EstadoCredito` | Evalúa cuotas: si todas están `PAGADA` transiciona a `PAGADO` (`RN-011`). Preserva `CANCELADO`. |
| `actualizarCuotasEnMora` | `fechaReferencia: LocalDate` | `int` | Ejecuta actualización masiva optimizada delegando en `CreditoRepository` (`RN-008`). |
| `obtenerProximasCuotas`| `limite: int` | `List<ProximoPagoItem>` | Consulta cuotas pendientes de créditos activos para Dashboard (`CU-012`, `RN-013`). |

---

### Archivo: `src/main/java/com/credinoda/service/PagoService.java`
- **Estado:** NUEVO
- **Capa de Arquitectura:** `service`
- **Clase(s) / Módulo:** `PagoService`
- **Detalle Granular de Métodos / Funciones:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `registrarPago` | `creditoId: int, cuotaId: int, monto: BigDecimal, fPago: LocalDate` | `Pago` | Valida monto $>0$, crédito, cuota, pertenencia, no pagada, bloquea sobrepago (`RN-009`). Transacción atómica: inserta pago, actualiza estado de cuota y evalúa liquidación de crédito (`RN-011`). |
| `listarPagosConDetalle` | `filtro: String, fIni: LocalDate, fFin: LocalDate` | `List<PagoDetalleItem>` | Historial consolidado con filtros de texto y fechas (`CU-010`, `RN-016`). |
| `obtenerDatosComprobante` | `pagoId: int` | `DatosComprobante` | Obtiene registro consolidado para recibos PDF y QR (`CU-011`, `RN-012`). |
| `calcularSaldoPendienteCuota` | `cuotaId: int` | `BigDecimal` | Calcula saldo insoluto de una cuota individual (`RN-010`). |

---

## 4. Impacto en Esquema de Persistencia y Base de Datos

- **¿Hubo alteraciones al modelo de datos en esta sesión?:** NO (La estructura DDL de las 4 tablas e índices se mantiene intacta desde la Fase 2).
- **Archivos de Persistencia Afectados:** Ninguno (Cero mutaciones DDL).

---

## 5. Checklist Obligatorio de Calidad Pre-Cierre

- [x] **Aislamiento Estricto de Capas:** Cero importaciones de paquetes gráficos `javafx.*` en `com.credinoda.service`.
- [x] **Transacciones ACID:** Creación de créditos y aplicación de pagos encapsuladas bajo control transaccional explícito (`setAutoCommit(false)` con `commit`/`rollback`).
- [x] **Precisión Decimal:** Todos los cálculos con `BigDecimal` y `RoundingMode.HALF_UP`, cumpliendo al centavo los vectores `VP-001` a `VP-008`.
- [x] **Integridad de Pruebas:** 100% de tests en verde (120 pruebas automatizadas) sin rebajar ni modificar aserciones de prueba.
- [x] **Gobernanza Git:** Trabajo contenido en la sub-rama `feature/fase-3-servicios-core`.

---

## 6. Deuda Técnica y Puntos Críticos para la Siguiente Sesión

- **Riesgos o Limitaciones Detectadas:** Ninguno.
- **Refactorizaciones Pendientes:** Ninguna.
- **Próximo Objetivo Prioritario:** Fase 4 — Servicios Especializados (`ReporteService`, `PdfService`, `BackupService`).
