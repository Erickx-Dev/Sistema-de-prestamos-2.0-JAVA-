# Bitácora de Sesión de Desarrollo: SESION-01

> **Proyecto:** CrediNoda 2.0 (Migración de Python a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 1 — Cimientos del Proyecto, Modelo de Dominio, DTOs, Excepciones y Utilidades Transversales  
> **Protocolo Base:** [`docs/git_protocol.md`](../git_protocol.md) y [`AGENTS.md`](../../AGENTS.md)

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-01`
- **Fecha de Ejecución:** 2026-10-16
- **Responsables Técnicos:**
  - **Tech Lead y Administrador Único:** Erickson Sojo *(Supervisión, Aprobación de Git y Gestión de PR)*
  - **Desarrollador / Agente de IA:** Antigravity (Google DeepMind)
- **Rama Git Asociada:** `feature/fase-1-dominio-maven`
- **Objetivo General de la Sesión:**
  - Configurar la infraestructura del proyecto con Apache Maven y Java 21 LTS.
  - Implementar el modelo de dominio inmutable, enumeraciones canónicas y jerarquía de excepciones de negocio no chequeadas (`RuntimeException`).
  - Implementar records DTO fuertemente tipados.
  - Desarrollar utilidades transversales desacopladas de la UI (`AppConfig`, `Validators.parsearMonto()` tolerante a miles/decimales y `Formatters.formatoMoneda()` estricto).
  - Diseñar e implementar suites de pruebas automatizadas JUnit 5 + AssertJ garantizando 100% de éxito.

- **Casos de Uso y Reglas de Negocio Abordadas:**
  - [x] **Base Transversal:** Preparación de estructuras para `CU-001` a `CU-015`.
  - [x] **RN-001:** Validación de no vacuidad y sanitización trim (`Validators.validarTextoNoVacio`).
  - [x] **RN-005:** Periodicidades canónicas (`FrecuenciaPago`: SEMANAL x4, QUINCENAL x2).
  - [x] **RN-008:** Lógica de visualización y cálculo dinámico de mora en cuotas (`Cuota.etiquetaEstado()`).
  - [x] **RN-015:** Algoritmo tolerante de análisis sintáctico monetario (`Validators.parsearMonto()`) y formateo condicional contable (`Formatters.formatoMoneda()`).

---

## 2. Trazabilidad de Tareas (Roadmap - Fase 1)

### Tareas Completadas en la Sesión
- [x] `F1-T01`: Configuración completa de `pom.xml` con Java 21 LTS, JavaFX 21, SQLite JDBC, OpenPDF 2.0.x, ZXing 3.5.x, JUnit 5.10.x y AssertJ 3.25.x.
- [x] `F1-T02`: Implementación de la jerarquía de excepciones de negocio en `com.credinoda.domain.exception` encabezada por `CrediNodaException extends RuntimeException`.
- [x] `F1-T03`: Implementación de modelos de dominio inmutables en `com.credinoda.domain.model` (`Cliente`, `Credito`, `Cuota`, `Pago`, `FrecuenciaPago`, `EstadoCredito`, `EstadoCuota`).
- [x] `F1-T04`: Implementación de records DTO inmutables en `com.credinoda.domain.dto` (`ProximoPagoItem`, `PagoDetalleItem`, `DatosComprobante`, `MetricasReporte`, `DistribucionCartera`, `ProyeccionSemanalItem`, `BackupInfo`).
- [x] `F1-T05`: Implementación de `AppConfig` con constantes globales y resolución multiplataforma de rutas (`data/creditos.db`, `backups/`, `recibos/`).
- [x] `F1-T06`: Implementación de `Validators` con algoritmo heurístico `parsearMonto()` probado contra 44 variantes de sintaxis numérica (`VP-013`).
- [x] `F1-T07`: Implementación de `Formatters` con formateo contable exacto sin decimales superfluos para montos enteros (`VP-014`).
- [x] `F1-T08`: Creación y ejecución de la suite de pruebas unitarias (`ValidatorsTest`, `FormattersTest`, `CuotaTest`) alcanzando 51 tests en verde (100% pass rate).
- [x] `F1-T09`: Commit estructurado bajo Conventional Commits tras aprobación formal de Erickson Sojo (`a35f4c5`).

### Tareas Pendientes o en Progreso
- [ ] `F2-T01`: Inicio de la Fase 2 en sub-rama `feature/fase-2-persistencia-sqlite` tras merge/aprobación de la Pull Request de la Fase 1.

### Bloqueos / Impedimentos
- **Ninguno.** La compilación, ejecución de pruebas y sincronización con el repositorio remoto se encuentran en estado óptimo.

---

## 3. Desglose Técnico Exhaustivo por Archivo y Componente

### 3.1. Configuración del Build
- **Archivo:** `pom.xml`
- **Estado:** NUEVO
- **Propósito:** Definir dependencias exactas, plugins del compilador Java 21 (`source`/`target` 21) y plugin Surefire para JUnit 5.

### 3.2. Capa Domain - Excepciones (`com.credinoda.domain.exception`)
- **Archivos:**
  - `CrediNodaException.java`: Clase base no chequeada (`extends RuntimeException`).
  - `ClienteNoEncontradoException.java`
  - `CreditoNoEncontradoException.java`
  - `CuotaNoEncontradaException.java`
  - `PagoInvalidoException.java`
  - `MontoInvalidoException.java`
  - `DatosInvalidosException.java`
  - `PdfServiceException.java`
  - `BackupException.java`
- **Diseño:** Cada excepción provee constructores sobrecargados `(String mensaje)` y `(String mensaje, Throwable causa)` para permitir encapsulamiento de fallos JDBC/IO sin fuga de abstracciones.

### 3.3. Capa Domain - Modelos y Enumeraciones (`com.credinoda.domain.model`)
- **Archivos:**
  - `FrecuenciaPago.java`: Enum (`SEMANAL(4, 7)`, `QUINCENAL(2, 15)`).
  - `EstadoCredito.java`: Enum (`ACTIVO`, `PAGADO`).
  - `EstadoCuota.java`: Enum (`PENDIENTE`, `PARCIAL`, `PAGADA`).
  - `Cliente.java`: Entidad con `id`, `nombre`, `documento`, `telefono`, `direccion`, `fechaRegistro`.
  - `Credito.java`: Entidad con `id`, `clienteId`, `montoPrestado`, `tasaInteres`, `plazoMeses`, `frecuencia`, `totalPagar`, `fechaInicio`, `estado`.
  - `Cuota.java`: Entidad con `id`, `creditoId`, `numeroCuota`, `montoCuota`, `fechaVencimiento`, `estado`, `enMora`. Incluye método `etiquetaEstado()` que produce dinámicamente `"PENDIENTE (EN MORA)"`, `"PARCIAL (EN MORA)"`, etc.
  - `Pago.java`: Entidad con `id`, `cuotaId`, `montoPagado`, `fechaPago`, `referencia`.

### 3.4. Capa Domain - DTOs (`com.credinoda.domain.dto`)
- **Archivos:**
  - `ProximoPagoItem.java`: DTO record para las 10 próximas cuotas del Dashboard.
  - `PagoDetalleItem.java`: DTO record para el historial general de pagos con JOIN pre-calculado.
  - `DatosComprobante.java`: DTO record con datos enriquecidos para generación de recibos PDF y QR.
  - `MetricasReporte.java`: DTO record con los 10 KPIs analíticos de negocio.
  - `DistribucionCartera.java`: DTO record para categorización de cartera al día vs en mora.
  - `ProyeccionSemanalItem.java`: DTO record para series cronológicas del gráfico nativo.
  - `BackupInfo.java`: DTO record para metadatos de archivos de copia de seguridad.

### 3.5. Capa Common - Utilidades Transversales (`com.credinoda.common`)
- **Archivos:**
  - `AppConfig.java`: Configuración inmutable del sistema (tasa por defecto 12%, frecuencias, directorios).
  - `Formatters.java`:
    - `formatoMoneda(BigDecimal)`: Formato contable estricto (`$1.000.000` si no hay centavos, `$113.333,33` si los tiene).
    - `formatoFecha(LocalDate)`: Formateador `dd/MM/yyyy`.
    - `formatoPorcentaje(BigDecimal)`: Formateador `0.00%`.
  - `Validators.java`:
    - `parsearMonto(String)`: Algoritmo heurístico que detecta y procesa notación española (`1.234,56`), anglosajona (`1,234.56`), mixta, enteros sin separadores y valida montos estrictamente positivos.
    - `validarTextoNoVacio(String, String)`: Validación de obligatoriedad con trim.
    - `validarEnteroPositivo(int, String)`: Validación de cotas enteras.

---

## 4. Resultados de Verificación y Pruebas Automatizadas

La suite completa fue ejecutada localmente con Apache Maven 3.9.9 y Microsoft OpenJDK 21 LTS:

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
[INFO] 
[INFO] Results:
[INFO] Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **Cobertura de Vectores de Prueba:**
  - `VP-013`: 44 pruebas unitarias de parseo monetario (33 casos válidos + 11 casos inválidos con lanzamiento de `MontoInvalidoException`).
  - `VP-014`: 3 pruebas contables de formateo de moneda estricto.
  - `RN-008`: 2 pruebas de cálculo dinámico de etiqueta de estado y mora.
- **Integridad de Pruebas:** Ningún test fue rebajado, omitido ni relajado (cumplimiento estricto de la Sección 6.1 de `AGENTS.md`).

---

## 5. Checklist Obligatorio de Calidad Pre-Cierre

- [x] **Separación Estricta de Capas:** Cero importaciones de paquetes gráficos `javafx.*` en `domain` o `common`.
- [x] **Aislamiento de Dependencias:** Cero lógica SQL ni referencias a JDBC en la capa de dominio.
- [x] **Inmutabilidad y Tipado Fuerte:** Uso exhaustivo de records DTO, `BigDecimal` para aritmética financiera y `LocalDate` para fechas.
- [x] **Gobernanza Git:** No se realizó ninguna edición sobre `main`. Todo el trabajo fue desarrollado en la sub-rama `feature/fase-1-dominio-maven`.
- [x] **Sincronización:** Verificación con `git fetch origin` confirmando que `origin/main` y `main` local están alineados (`b948689`) sin conflictos.

---

## 6. Próximo Objetivo Prioritario (Fase 2)

- **Rama a crear:** `feature/fase-2-persistencia-sqlite`
- **Alcance:**
  - Implementar `DatabaseConnection` con activación forzosa de `PRAGMA foreign_keys = ON`.
  - Implementar `SchemaInitializer` para aprovisionar las 4 tablas relacionales (`clientes`, `creditos`, `cuotas`, `pagos`) con `cuotas.en_mora` nativo.
  - Implementar `ClienteRepository`, `CreditoRepository` y `PagoRepository` con mapeo exacto de `BigDecimal` (`toPlainString()` / `new BigDecimal(rs.getString())`).
  - Suites de pruebas de persistencia en SQLite `:memory:` y archivos temporales.
