# Bitácora de Sesión de Desarrollo: SESION-03

> **Proyecto:** CrediNoda 2.0 (Migración de Python a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 3 — Capa de Servicios Core (`ClienteService`, `CreditoService`, `PagoService`)  
> **Protocolo Base:** [`docs/git_protocol.md`](../git_protocol.md) y [`AGENTS.md`](../../AGENTS.md)

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-03`
- **Fecha de Ejecución:** 2026-10-16
- **Responsables Técnicos:**
  - **Tech Lead y Administrador Único:** Erickson Sojo *(Supervisión, Aprobación de Git y Gestión de PR)*
  - **Desarrollador / Agente de IA:** Antigravity (Google DeepMind)
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

## 2. Trazabilidad de Tareas (Roadmap - Fase 3)

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

---

## 3. Desglose Técnico Exhaustivo por Archivo y Componente

### 3.1. `ClienteService.java` (`com.credinoda.service`)
- `crearCliente(String, String, String)`: Sanitiza campos con `.trim()`, verifica no vacuidad, valida que no exista el documento en BD (`RN-002`) y persiste mediante `ClienteRepository`.
- `actualizarCliente(int, String, String, String)`: Verifica existencia, actualiza solo campos no nulos asegurando que el nuevo documento no colisione con otro cliente.
- `obtenerClientePorId(int)`: Retorna cliente o lanza `ClienteNoEncontradoException`.
- `buscarClientes(String)`: Búsqueda general por coincidencia en nombre o documento.
- `obtenerCreditosCliente(int)`: Consulta los créditos del prestatario.

### 3.2. `CreditoService.java` (`com.credinoda.service`)
- `calcularIntereses(BigDecimal, BigDecimal, int)`: $I = C \times i \times t$ (`RoundingMode.HALF_UP` a 2 decimales).
- `calcularNumeroCuotas(int, FrecuenciaPago)`: Semanal $\times 4$, Quincenal $\times 2$.
- `generarPlanPagos(BigDecimal, int, FrecuenciaPago, LocalDate)`: Cuota base `total / N` y ajuste de residuo en la cuota $N$ (`RN-006`). Fechas con múltiplos de 7 o 15 días (`RN-007`).
- `crearCredito(int, BigDecimal, BigDecimal, int, FrecuenciaPago, LocalDate)`: Creación atómica transaccional (`connection.setAutoCommit(false)` $\to$ `commit()` / `rollback()`).
- `calcularSaldoPendiente(int)`: Total a pagar menos abonos acumulados (`RN-010`).
- `determinarEstadoCredito(int)`: Evalúa cuotas; si todas están `PAGADA`, actualiza a `PAGADO` (`RN-011`). Preserva `CANCELADO`.
- `actualizarCuotasEnMora(LocalDate)`: Ejecuta actualización masiva optimizada (`RN-008`).

### 3.3. `PagoService.java` (`com.credinoda.service`)
- `registrarPago(int, int, BigDecimal, LocalDate)`:
  - Valida `monto > 0`, existencia de crédito y cuota, pertenencia de la cuota al crédito y estado no `PAGADA`.
  - Calcula el saldo insoluto de la cuota y previene sobrepagos: `monto > saldoCuota` $\implies$ `MontoInvalidoException`.
  - En bloque transaccional: registra el pago, transiciona cuota (`PARCIAL` o `PAGADA`) y evalúa crédito mediante `creditoService.determinarEstadoCredito`.
- `obtenerDatosComprobante(int)`: Obtiene `DatosComprobante` para comprobante y QR.
- `calcularSaldoPendienteCuota(int)`: Saldo de la cuota individual.

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
[INFO] Running com.credinoda.service.ClienteServiceTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.service.CreditoServiceTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.service.MoraCuotasTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.credinoda.service.PagoServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 120, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **Cobertura de Vectores de Prueba:**
  - `VP-001`: Interés simple a 1 mes superado al centavo (`120000.00`).
  - `VP-002`: Interés simple a 3 meses superado al centavo (`360000.00`).
  - `VP-003`: Interés simple a 6 meses superado al centavo (`360000.00`).
  - `VP-004`: Ajuste de centavos semanal 12 cuotas superado (`11333.33` x11, `11333.37` x1, total `136000.00`).
  - `VP-005`: Ajuste de centavos semanal 12 cuotas superado (`113333.33` x11, `113333.37` x1, total `1360000.00`).
  - `VP-006`: Proyección de vencimientos semanal (+7) y quincenal (+15) superada.
  - `VP-007`: Secuencia de abonos y liquidación de cuota superada.
  - `VP-008`: Prevención de sobrepago por 1 centavo (`360000.01`) superada con rechazo estricto.

---

## 5. Checklist Obligatorio de Calidad Pre-Cierre

- [x] **Aislamiento de Capas:** Cero importaciones de JavaFX en `service`.
- [x] **Transacciones ACID:** Operaciones de crédito y pago protegidas con rollback explícito.
- [x] **Precisión Decimal:** Todos los cálculos monetarios con `BigDecimal` y `RoundingMode.HALF_UP`.
- [x] **Integridad de Pruebas:** 100% de tests en verde sin atenuaciones ni aserciones relajadas.
- [x] **Gobernanza Git:** Trabajo contenido en la sub-rama `feature/fase-3-servicios-core`.

---

## 6. Próximo Objetivo Prioritario (Fase 4)

- **Rama a crear:** `feature/fase-4-servicios-reportes-pdf-backup`
- **Alcance:**
  - `ReporteService`: 10 KPIs financieros (`RN-014`), ganancia líquida proporcional (`VP-010`), morosidad (`VP-011`), distribución de cartera y series temporales semanales.
  - `PdfService`: Maquetación de recibos PDF tamaño carta con OpenPDF y matriz QR ZXing (`RN-012`, `VP-009`).
  - `BackupService`: Respaldo en caliente SQLite (`RN-017`) y restauración con triple validación y copia preventiva (`RN-018`).
