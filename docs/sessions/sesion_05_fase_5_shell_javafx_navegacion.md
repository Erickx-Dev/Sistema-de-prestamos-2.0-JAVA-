# Bitácora de Sesión de Desarrollo: SESION-05

> **Proyecto:** CrediNoda 2.0 (Migración a Java 21 LTS / JavaFX 21 / SQLite JDBC)  
> **Fase del Roadmap:** Fase 5 — Shell JavaFX, Sistema de Estilos CSS, Enrutador Reactivo y Componentes Base  
> **Documento Base:** [`docs/ROADMAP_JAVA.md`](../ROADMAP_JAVA.md) y [`docs/templates/session_summary_template.md`](../templates/session_summary_template.md)  

---

## 1. Metadatos de la Sesión

- **Identificador de Sesión:** `SESION-05`
- **Fecha de Ejecución:** `2026-10-16`
- **Responsable(s) Técnico(s) Presente(s):**
  - **Líder de Proyecto / Administrador Único:** Erickson Sojo *(Tech Lead / Aprobación)*
  - **Desarrollador / Agente IA:** Antigravity (Google DeepMind)
- **Rama Git Asociada:** `feature/fase-5-shell-javafx-navegacion`
- **Objetivo General de la Sesión:**
  - Implementar la infraestructura gráfica de escritorio de la **Fase 5** de CrediNoda 2.0 en **JavaFX 21**:
    1. Extracción y fidelidad visual 1:1 con el proyecto de Google Stitch `"Credinoda 2.0 Dashboard JavaFX"` (`projects/523971302287300550`) y el Design System `"Credinoda 2.0 Desktop"` (paleta verde bosque corporativo `#164E3B`, `#21865A`, `#F3F6F4`, `#DCE5DE`).
    2. Construcción de `Main.java` (Launcher para evitar restricciones JPMS de `Application`) y `CrediNodaApplication.java` (`MainShell` con `BorderPane` en dos regiones limpias: `Top = NavigationBar` horizontal de `56px` y `Center = ContentArea` con `ToastNotification` superpuesta).
    3. Implementación de `NavAction`, `NavigationBar` (Navbar superior horizontal conforme a la pantalla `"DashboardView - Inicio (Navbar Superior)"`) y `ViewRouter` con caché de vistas, invalidación por `dataVersion` y verificación de cambio de día natural (`RN-008`).
    4. Creación de los 4 componentes reutilizables base (`SectionHeader`, `MetricCard`, `ToastNotification`, `ConfirmDialog`) y sus pruebas unitarias (`ViewRouterTest`, `MetricCardTest`).
- **Casos de Uso y Requerimientos Abordados:**
  - [x] **RN-008:** Barrido idempotente de mora (`actualizarEstadosMora()`) al iniciar la aplicación y automáticamente en cada navegación cuando cambia la fecha natural del sistema (`LocalDate.now()`).
  - [x] **Arquitectura UI (`MainShell`):** Estructura exclusiva de 2 regiones (`Top = NavigationBar` horizontal `56px`, `Center = ContentArea`) sin barras laterales parásitas, encabezados duplicados ni pies de página fijos (`Left`, `Right` y `Bottom` en `null`).
  - [x] **Caché Reactivo de Vistas:** `ViewRouter` con versionado `dataVersion` e interfaz funcional `RefreshableView`.

---

## 2. Trazabilidad de Tareas (Roadmap)

### Tareas Completadas en la Sesión
- [x] `F5-T1`: Extraer tokens de color, tipografía y layout desde Google Stitch (`Credinoda 2.0 Dashboard JavaFX`) y codificar `src/main/resources/css/credinoda.css` más el ícono `src/main/resources/assets/icon.png`.
- [x] `F5-T2`: Implementar `NavAction` (`INICIO`, `CLIENTES`, `NUEVO_CREDITO`, `PAGOS`, `REPORTES`, `BACKUP`) con títulos e íconos Unicode.
- [x] `F5-T3`: Implementar `NavigationBar` como barra de navegación superior horizontal (`HBox`, `56px` de alto, `BorderPane.top`) con identidad de marca y las 6 pestañas principales.
- [x] `F5-T4`: Implementar `ViewRouter` con registro de proveedores (`Supplier<Node>`), caché `Map<NavAction, Node>`, control de `dataVersion`, interfaz `RefreshableView` y verificación automática de cambio de día natural (`RN-008`).
- [x] `F5-T5`: Implementar los 4 componentes base reutilizables: `SectionHeader`, `MetricCard`, `ToastNotification` (con `FadeTransition` + `PauseTransition`) y `ConfirmDialog` (con soporte de palabra clave de seguridad para operaciones destructivas).
- [x] `F5-T6`: Implementar `Main.java` y `CrediNodaApplication.java` conectando el grafo completo de dependencias (SQLite JDBC, 3 repositorios, 6 servicios) y cierre seguro en `stop()`.
- [x] `F5-T7`: Desarrollar las suites de pruebas unitarias de UI (`ViewRouterTest` con 7 tests y `MetricCardTest` con 4 tests) inicializando el toolkit JavaFX de forma segura.
- [x] `F5-T8`: Ejecutar `mvn clean test` verificando **148/148 tests en VERDE** (0 fallos, 0 errores).

### Tareas Pendientes o en Progreso
- [ ] Ninguna de la Fase 5. La fase se encuentra 100% completada, probada y documentada.

### Tareas Bloqueadas / Impedimentos
- Ninguno.

---

## 3. Desglose Técnico Exhaustivo por Archivo y Función (Nivel Auditoría)

### Archivo: `src/main/java/com/credinoda/Main.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `root / launcher`
- **Clase(s) / Módulo:** `Main`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `main` | `args: String[]` | `void` | Punto de entrada estándar de la JVM que delega en `Application.launch(CrediNodaApplication.class, args)` evitando errores de módulos JavaFX en fat-jars. |

---

### Archivo: `src/main/java/com/credinoda/ui/CrediNodaApplication.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui`
- **Clase(s) / Módulo:** `CrediNodaApplication extends Application`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `start` | `primaryStage: Stage` | `void` | Inicializa `DatabaseConnection`, `SchemaInitializer`, repositorios y servicios; ejecuta `creditoService.actualizarEstadosMora()` (`RN-008`); ensambla `MainShell` (`BorderPane`) con `NavigationBar` en `Top` y `ContentArea` en `Center`; carga `credinoda.css` y abre en `NavAction.INICIO`. |
| `stop` | Ninguno | `void` | Cierra de forma limpia y segura la conexión SQLite activa al cerrar la ventana principal. |
| `registrarVistasBase` | Ninguno | `void` | Registra contenedores base para cada `NavAction` con `SectionHeader` listos para ser reemplazados por las vistas definitivas en la Fase 6. |

---

### Archivo: `src/main/java/com/credinoda/ui/navigation/NavAction.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui/navigation`
- **Clase(s) / Módulo:** `enum NavAction`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `getTitulo` | Ninguno | `String` | Retorna la etiqueta visible en la barra de navegación (`Inicio`, `Clientes`, `Nuevo Crédito`, `Pagos`, `Reportes`, `Copia de Seguridad`). |
| `getIcono` | Ninguno | `String` | Retorna el glifo Unicode asociado a la opción de navegación. |
| `getTextoBoton` | Ninguno | `String` | Retorna la combinación formateada `icono + "  " + titulo`. |

---

### Archivo: `src/main/java/com/credinoda/ui/navigation/NavigationBar.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui/navigation`
- **Clase(s) / Módulo:** `NavigationBar extends HBox`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `NavigationBar` | `onActionSelected: Consumer<NavAction>` | Constructor | Construye la barra de navegación superior horizontal (`56px` de alto fijo, clase CSS `.navigation-bar`), bloque de marca (`CrediNoda 2.0`), botones `.nav-button` y espaciador flexible. |
| `setActive` | `action: NavAction` | `void` | Alterna la clase CSS `.nav-button-active` exclusivamente sobre el botón correspondiente a la vista activa. |
| `getActiveAction` | Ninguno | `NavAction` | Devuelve la acción de navegación actualmente seleccionada. |
| `getButton` | `action: NavAction` | `Button` | Devuelve la instancia del botón para pruebas automatizadas e inspección de estado. |

---

### Archivo: `src/main/java/com/credinoda/ui/navigation/ViewRouter.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui/navigation`
- **Clase(s) / Módulo:** `ViewRouter` e interfaz `ViewRouter.RefreshableView`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `ViewRouter` | `contentArea: StackPane, navigationBar: NavigationBar, creditoService: CreditoService, clock: Supplier<LocalDate>` | Constructor | Inicializa el contenedor central, el mapa de proveedores, el caché de nodos, el mapa `viewVersionMap` y la fecha de verificación de mora (`lastMoraCheckDate`). |
| `registerView` | `action: NavAction, viewSupplier: Supplier<Node>` | `void` | Registra la fábrica perezosa (`Supplier<Node>`) para una ruta de navegación. |
| `navigateTo` | `action: NavAction` | `void` | Verifica cambio de fecha natural (`verificarCambioDeDia()`), crea o recupera la vista en caché, invoca `refresh()` si `viewVersion < dataVersion`, monta la vista en `contentArea` preservando el `ToastNotification` en el tope, y sincroniza `NavigationBar.setActive(action)`. |
| `invalidateCache` | Ninguno | `void` | Incrementa `dataVersion` (`++dataVersion`) tras cualquier mutación de datos para forzar el refresco de las vistas al visitarlas. |
| `verificarCambioDeDia` | Ninguno | `boolean` | Si `LocalDate.now()` es distinto a `lastMoraCheckDate`, ejecuta `creditoService.actualizarEstadosMora()`, actualiza `lastMoraCheckDate` e incrementa `dataVersion` (`RN-008`). |

---

### Archivo: `src/main/java/com/credinoda/ui/components/SectionHeader.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui/components`
- **Clase(s) / Módulo:** `SectionHeader extends HBox`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `SectionHeader` | `title: String, subtitle: String, actions: Node...` | Constructor | Cabecera estandarizada con título `.section-title` (`22px`), subtítulo `.section-subtitle` (`13px`), espaciador horizontal y contenedor derecho de botones de acción. |
| `setTitle` / `setSubtitle` | `text: String` | `void` | Actualiza dinámicamente el título o subtítulo del encabezado. |

---

### Archivo: `src/main/java/com/credinoda/ui/components/MetricCard.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui/components`
- **Clase(s) / Módulo:** `MetricCard extends VBox` y `enum MetricCard.SemanticVariant`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `MetricCard` | `title: String, value: String, subtitle: String, variant: SemanticVariant` | Constructor | Construye tarjeta KPI con clases `.metric-card` y variante semántica (`NEUTRAL`, `PRIMARY`, `SUCCESS`, `WARNING`, `DANGER`). |
| `update` | `newValue: String, newSubtitle: String` | `void` | Actualiza reactivamente el valor principal y el subtítulo de la tarjeta. |
| `setVariant` | `variant: SemanticVariant` | `void` | Reemplaza las clases CSS semánticas en tarjeta y valor numérico sin reconstruir el nodo. |

---

### Archivo: `src/main/java/com/credinoda/ui/components/ToastNotification.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui/components`
- **Clase(s) / Módulo:** `ToastNotification extends HBox` y `enum ToastNotification.ToastType`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `show` | `message: String, type: ToastType` | `void` | Muestra banner flotante no bloqueante (`SUCCESS`, `WARNING`, `ERROR`, `INFO`) en la parte superior central con animación `FadeTransition` (220ms entrada, 3.5s pausa, 280ms salida). |
| `showSuccess` / `showWarning` / `showError` / `showInfo` | `message: String` | `void` | Métodos de conveniencia para cada tipo semántico de alerta. |

---

### Archivo: `src/main/java/com/credinoda/ui/components/ConfirmDialog.java`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `ui/components`
- **Clase(s) / Módulo:** `ConfirmDialog`
- **Detalle Granular de Métodos:**

| Nombre de la Función / Método | Parámetros y Tipos | Tipo Retorno | Propósito y Lógica Implementada |
| :--- | :--- | :--- | :--- |
| `showConfirm` | `owner: Window, title: String, header: String, message: String, confirmText: String, danger: boolean` | `boolean` | Despliega modal de confirmación estilizado con `credinoda.css` y retorna `true` únicamente si el usuario confirma la acción. |
| `showKeywordConfirm` | `owner: Window, title: String, header: String, message: String, requiredKeyword: String, confirmText: String` | `boolean` | Modal de seguridad reforzada (ej. para restauración de BD o eliminación crítica) que mantiene deshabilitado el botón de confirmación hasta que el texto ingresado coincida con `requiredKeyword`. |

---

### Archivo: `src/main/resources/css/credinoda.css`
- **Estado:** `NUEVO`
- **Capa de Arquitectura:** `resources/css`
- **Propósito:** Hoja de estilos corporativa basada en el sistema de diseño `"Credinoda 2.0 Desktop"` de Google Stitch (`#164E3B`, `#21865A`, `#F3F6F4`, `#FFFFFF`, `#26352D`, `#647067`, `#DCE5DE`), definiendo `.navigation-bar` superior, `.nav-button`, `.metric-card`, `.btn-primary`, `.btn-secondary`, `.btn-danger`, `.table-view`, `.badge-*` y `.toast-*`.

---

## 4. Impacto en Esquema de Persistencia y Base de Datos

- **¿Hubo alteraciones al modelo de datos en esta sesión?:** `NO`
- **Archivos de Persistencia Afectados:** `SIN CAMBIOS`
- **Estructura DDL:** Se mantiene intacto el esquema relacional de las 4 tablas (`clientes`, `creditos`, `cuotas`, `pagos`). `CrediNodaApplication` inicializa limpiamente el esquema en el arranque y cierra la conexión en `stop()`.

---

## 5. Checklist Obligatorio de Calidad Pre-Cierre

- [x] **Separación Estricta de Capas:** Las clases de `ui` se comunican únicamente con `service`, `common` y `domain`. Cero consultas SQL o accesos directos a repositorios desde la interfaz gráfica.
- [x] **Regla Estructural de Oro (`MainShell`):** `BorderPane` compuesto exclusivamente por `Top = NavigationBar` (Navbar horizontal `56px`) y `Center = ContentArea` (`StackPane`). `Left`, `Right` y `Bottom` son `null`.
- [x] **Principios SOLID Cumplidos:**
  - *SRP:* Separación clara entre enrutamiento (`ViewRouter`), barra de navegación (`NavigationBar`) y componentes visuales atómicos (`MetricCard`, `SectionHeader`, `ToastNotification`, `ConfirmDialog`).
  - *DIP:* `ViewRouter` recibe un reloj inyectable (`Supplier<LocalDate>`) facilitando pruebas deterministas de cambio de día natural (`RN-008`).
- [x] **Invariabilidad Estricta de Pruebas (Sección 6.1 `AGENTS.md`):** Ningún test existente fue modificado ni rebajado. Se añadieron 11 nuevas pruebas unitarias en `ViewRouterTest` y `MetricCardTest`.
- [x] **Suite Automatizada al 100%:** `148/148` pruebas en VERDE (`mvn clean test`).

---

## 6. Deuda Técnica y Puntos Críticos para la Siguiente Sesión

- **Riesgos o Limitaciones Detectadas:**
  - Ninguno. El puente entre los 6 servicios de backend y el `MainShell` en JavaFX está operativo y verificado.
- **Refactorizaciones Pendientes:**
  - Sustituir los contenedores base registrados en `CrediNodaApplication.registrarVistasBase()` por las 6 vistas definitivas durante la Fase 6.
- **Próximo Objetivo Prioritario (Fase 6):**
  - Implementar en una nueva rama `feature/fase-6-vistas-dialogos-graficos` las 6 vistas funcionales (`DashboardView`, `ClientesView`, `NuevoCreditoView`, `PagosView`, `ReportesView`, `BackupView`), los diálogos modales y los gráficos vectoriales nativos de JavaFX.
