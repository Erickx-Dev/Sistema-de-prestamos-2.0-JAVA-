# Protocolo Oficial de Git y Gobernanza de Repositorio

> **Audiencia:** Desarrolladores, Colaboradores y Agentes Autónomos de Inteligencia Artificial (IA).  
> **Alcance:** Proyecto Heladería POS (Python 3 / CustomTkinter / SQLite3 / Arquitectura en Capas SOLID).

---

## 1. Principio Fundamental de Gobernanza y Aprobación Centralizada

El repositorio cuenta con una estructura de gobernanza centralizada y estricta:

1. **Administrador Único del Repositorio:** **Erickson Sojo** es el único Tech Lead, arquitecto y administrador facultado para autorizar, coordinar y realizar integraciones a las ramas base (`develop` y `main`).
2. **Prohibición Absoluta de Merge Autónomo:** Ninguna rama de trabajo se integrará de forma directa o automática. El ciclo de vida de todo cambio culmina con la subida de la rama de trabajo y la notificación correspondiente para que Erickson Sojo revise el código, audite la ausencia de regresiones/conflictos y ejecute la fusión manual.
3. **Protección de Ramas Primarias:** Queda estrictamente vedado realizar commits directos o push sobre las ramas `main` y `develop`.

### 1.4. Regla Estricta Pre-Modificación: "Branch First, Code Second"
- **Blindaje total de `main`:** Queda terminantemente prohibido crear, modificar o eliminar archivos de código, pruebas o documentación estando posicionados sobre la rama `main` (o `develop`).
- **Paso Cero Obligatorio:** Antes de alterar cualquier archivo en el espacio de trabajo, el agente de IA debe auditar de manera autónoma la rama activa:
  ```powershell
  git branch --show-current
  ```
- Si la rama detectada es `main` (o `develop`), **el agente no debe tocar ningún archivo en esa rama**. Debe crear y conmutar de inmediato a una sub-rama de trabajo dedicada (`git switch -c <tipo>/<nombre-descriptivo>`). Solo una vez posicionado en dicha sub-rama se permite iniciar la edición o creación de archivos.

---

## 2. Flujo de Trabajo por Ramas (Feature Branching)

Todo trabajo técnico, corrección de defectos o refactorización debe aislarse en su propia rama dedicada que parta del estado actualizado de `develop` (o `main` según la fase de release).

### 2.1. Convención de Nombres de Ramas

Las ramas deben nombrarse en minúsculas, utilizando guiones como separadores (`kebab-case`), clasificadas bajo los siguientes prefijos normativos:

| Prefijo | Propósito | Ejemplo |
| :--- | :--- | :--- |
| `feature/` | Nueva funcionalidad de negocio o requerimiento funcional (RF) | `feature/pos-terminal-cobro` |
| `fix/` | Corrección de un fallo o defecto identificado | `fix/arqueo-caja-diferencia` |
| `refactor/` | Reestructuración de código sin alterar el comportamiento observable | `refactor/repositorio-ventas-acid` |
| `docs/` | Incorporación o actualización de documentación técnica y bitácoras | `docs/bitacora-sesion-01` |
| `test/` | Adición o mantenimiento de suites de pruebas unitarias o de integración | `test/caja-service-unit` |

### 2.2. Ciclo de Vida de una Rama

```text
main (local/remoto)  <─── ¡ZONA PROTEGIDA: PROHIBIDO CREAR O EDITAR ARCHIVOS!
   │
   ├── [Paso 0: git branch --show-current]
   │     └── Si es 'main' ──> ALTO: Crear sub-rama antes de cualquier edición
   │
   ├──> git switch -c <tipo>/<nombre-tarea> (local)
   │       │
   │       ├── [Modificación de archivos respetando SOLID]
   │       ├── [Verificación y pruebas locales]
   │       └── [Commit estructurado con autorización previa]
   │
   ├──> git push origin <tipo>/<nombre-tarea> (remoto)
   │
   └──> [Pull Request y revisión manual por Erickson Sojo]
```

---

## 3. Reglas de Operación y Confirmación Humana para Agentes de IA

Los agentes de IA que operen en este entorno tienen la responsabilidad operativa de blindar la rama `main` y tienen **terminantemente prohibido ejecutar acciones de mutación en el repositorio de forma autónoma sin previa verificación de rama**.

### 3.1. Detección Mandataria de Rama y Creación Preventiva de Sub-Ramas (Paso Cero de la IA)

Dado que los colaboradores delegan el trabajo de desarrollo y documentación en agentes de IA, el agente debe proteger la rama `main` antes de que ocurra cualquier modificación física en el disco:

1. **Inspección Previa Obligatoria:** Antes de invocar cualquier herramienta de escritura o edición (`write_to_file`, `replace_file_content`, scripts generadores), el agente **debe auditar de manera autónoma la rama activa** con:
   ```powershell
   git branch --show-current
   ```
2. **Acción Bloqueante ante `main` (o `develop`):**
   - Si la rama detectada es `main` (o `develop`), **queda terminantemente prohibido alterar, crear o borrar archivos en dicha rama**.
   - El agente debe generar de inmediato un nombre de sub-rama normalizado (`feature/`, `fix/`, `refactor/`, `docs/`, `test/`) y ejecutar inmediatamente el cambio de rama:
     ```powershell
     git switch -c <tipo>/<nombre-descriptivo>
     ```
   - El agente notificará en su respuesta al colaborador que detectó la presencia en la rama protegida `main` y que ha conmutado a la nueva sub-rama antes de iniciar las ediciones.
   - Solo cuando el espacio de trabajo se encuentre posicionado en la sub-rama, el agente procederá a crear o modificar los archivos correspondientes.

### 3.2. Acciones Sujetas a Autorización Explícita

Antes de emitir comandos como `git add`, `git commit`, `git checkout -b`, `git switch -c`, `git merge` o `git push`, el agente **debe presentar obligatoriamente en el chat el siguiente reporte de pre-autorización**:

```markdown
### 📋 Solicitud de Confirmación de Operación Git

1. **Rama Actual:** `<nombre-rama-actual>`
2. **Rama Destino / Tracking:** `<nombre-rama-destino>`
3. **Archivos a Agregar al Stage (git add):**
   - `ruta/al/archivo_1.py`
   - `ruta/al/archivo_2.sql`
   *(Nota: Prohibido usar `git add .` o agregar archivos de base de datos local, .venv o caches)*
4. **Mensaje de Commit Propuesto (Conventional Commits):**
   `tipo(alcance): descripción breve en español imperativo`
   
   - *Cuerpo detallado (si aplica): explicación del cambio y justificación técnica.*
5. **Comando Exacto a Disparar:**
   `git add <archivos> && git commit -m "..."`

⚠️ **Esperando confirmación textual explícita de Erickson Sojo para proceder.**
```

> **Regla de Bloqueo:** El agente NO ejecutará la herramienta de comando hasta recibir una confirmación positiva ("Acepto", "Procede", "Confirmado", "Adelante").

---

## 4. Estándar de Mensajes de Commit (Conventional Commits)

Los commits deben redactarse en español técnico, en modo imperativo y siguiendo el formato estándar de Conventional Commits:

$$\text{<tipo>}(\text{<alcance>}):\ \text{<descripción breve>}$$

### Tipos Permitidos:
- **`feat`**: Implementación de una nueva funcionalidad (`feat(pos): registrar venta con descuento atómico`).
- **`fix`**: Corrección de un error o inconsistencia (`fix(caja): corregir fórmula de cálculo en arqueo`).
- **`docs`**: Cambios exclusivos en documentación (`docs(governance): actualizar protocolo git`).
- **`refactor`**: Refactorización de código sin añadir funcionalidad ni reparar bugs (`refactor(database): migrar singleton de conexión a context manager`).
- **`test`**: Creación o actualización de pruebas automatizadas (`test(services): agregar tests para pos_service`).
- **`style`**: Ajustes de formato o estilos visuales en CustomTkinter sin alterar lógica (`style(ui): estandarizar paleta de colores en sidebar`).
- **`chore`**: Tareas de mantenimiento general, configuración o dependencias (`chore(deps): actualizar requirements.txt`).

---

## 5. Clasificación y Matriz de Control de Comandos

| Nivel de Riesgo | Clasificación | Comandos | Política de Ejecución |
| :--- | :--- | :--- | :--- |
| **Bajo** | **Solo Lectura (Permitidos)** | `git status`<br>`git diff`<br>`git log`<br>`git branch`<br>`git remote -v` | El agente puede ejecutarlos de forma autónoma para inspeccionar el estado del repositorio. |
| **Medio** | **Supervisados (Confirmación Obligatoria)** | `git branch <nombre>`<br>`git checkout -b <nombre>`<br>`git switch -c <nombre>`<br>`git add <archivo>`<br>`git commit -m "..."`<br>`git push origin <rama>` | Requiere reporte previo en el chat y aprobación textual explícita del usuario.<br>*(Excepción: La conmutación preventiva con `git switch -c` al detectar `main` antes de editar es mandataria según la sección 3.1).* |
| **Crítico** | **Estrictamente Vetados (Prohibición Total)** | `git push --force` / `git push -f`<br>`git reset --hard`<br>`git clean -fd`<br>`git branch -D`<br>`git merge` directo a ramas principales (`main`/`develop`) | **PROHIBIDOS**. Bajo ninguna circunstancia el agente propondrá ni ejecutará estos comandos destructivos. |

---

## 6. Manejo de Conflictos y Contingencias

1. En caso de detectarse discrepancias o divergencias con el remoto, el agente emitirá un reporte detallado utilizando `git status` y `git diff`.
2. Si se presenta un conflicto de fusión durante una integración, el agente detendrá cualquier acción inmediata, presentará los archivos en conflicto con sus marcas (`<<<<<<<`, `=======`, `>>>>>>>`) y esperará la directriz de resolución manual por parte de Erickson Sojo.
3. Jamás se sobrescribirá el historial de Git para subsanar un error operativo.
