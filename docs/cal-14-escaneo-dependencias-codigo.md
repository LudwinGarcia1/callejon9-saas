# CAL-14 — Escaneo Automatizado de Dependencias, Código (SAST) y Secretos

## Contexto y Objetivos

Para proteger la integridad multi-inquilino y la postura de seguridad de RestoHub conforme al ciclo 2 del roadmap técnico, se implementó un sistema integral de escaneo continuo dividido en tres compuertas:

1. **SCA Frontend (Software Composition Analysis)**: Auditoría automatizada de vulnerabilidades conocidas (CVEs) en dependencias de producción con `pnpm audit`.
2. **SAST Backend (Static Application Security Testing)**: Análisis estático de código Java/Spring Boot con **SpotBugs** y el plugin **FindSecBugs**, detectando fallas de inyección SQL/JPA, debilidades criptográficas, path traversal, manejo inseguro de sesiones y potenciales evasiones de RLS.
3. **Escaneo de Secretos (Secret Leak Prevention)**: Detección preventiva de credenciales, claves privadas, tokens JWT o claves de API (AWS, GitHub, Tailscale, Slack, Stripe) en los archivos rastreados por Git antes de su integración o despliegue.

---

## Arquitectura del Escaneo

### 1. Frontend SCA (`pnpm audit`)

- **Separación estricta de dependencias**: La herramienta de diseño `shadcn` se ubicó en `devDependencies`, garantizando que únicamente las librerías necesarias en tiempo de ejecución se auditen en el paquete de producción.
- **Resolución controlada de dependencias transitivas**: Se incorporó el bloque `pnpm.overrides` en `frontend/package.json` para garantizar versiones seguras de `nanoid` (>=3.3.18), `postcss` (>=8.5.25), `sharp` (>=0.35.5) y `source-map-js` (>=1.2.2), manteniendo la compatibilidad estricta con Next.js 15 (`15.5.28`) y React 19 sin forzar migraciones mayores disruptivas a Next 16.
- **Comando de verificación**:
  ```bash
  cd frontend
  pnpm audit --prod --audit-level=high
  ```
  Resultado actual: **0 vulnerabilidades detectadas**.

### 2. Backend SAST (`SpotBugs` + `FindSecBugs`)

- **Configuración en `backend/pom.xml`**:
  - `spotbugs-maven-plugin: 4.8.6.6`
  - `findsecbugs-plugin: 1.13.0`
  - Esfuerzo: `Max`, Umbral: `Medium`.
  - Enlazado a la fase del ciclo de vida Maven: `verify`.
- **Filtro de exclusiones documentadas (`backend/spotbugs-exclude.xml`)**:
  - `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`: Suprime advertencias de copia defensiva en registros Java (DTO records), modelos de dominio y singletons de Spring inyectados en controladores y servicios.
  - `CT_CONSTRUCTOR_THROW`: Suprime advertencias por validación de invariantes en constructores.
  - `SPRING_CSRF_PROTECTION_DISABLED`: Documenta y justifica que RestoHub es una API REST stateless protegida mediante JWT en cookies `HttpOnly` con directiva `SameSite=Strict`, con llamadas del navegador limitadas a rutas relativas `/api/v1/*` reescritas por Next.js.
- **Null Safety Refactoring**:
  - Se corrigieron potenciales desreferencias nulas en `AuthService`, `TenantOnboardingService` y `TenantAwareTransactionManager` mediante aserciones y comprobaciones explícitas de estado (`if (entity == null)`), asegurando 0 advertencias de SpotBugs (`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`).
- **Comando de verificación**:
  ```bash
  cd backend
  ./mvnw compile spotbugs:check
  ```
  Resultado actual: **0 defectos y 0 vulnerabilidades detectadas**.

### 3. Escaneo de Secretos y Credenciales

Se desarrollaron dos utilidades operativas unificadas:
- `scripts/security-scan.ps1`: Para desarrollo local en PowerShell (Windows).
- `scripts/security-scan.sh`: Para entornos Linux y agentes CI/CD (POSIX Bash).

Ambos scripts inspeccionan la lista de archivos rastreados (`git ls-files`) buscando patrones de:
- Bloques de clave privada (`-----BEGIN PRIVATE KEY-----`, etc.).
- Tokens personales de GitHub (`ghp_*`, `github_pat_*`).
- Claves de acceso AWS (`AKIA*`).
- Claves de autenticación de Tailscale (`tskey-auth-*`).
- Tokens de Slack y claves de Stripe (`sk_live_*`, `rk_live_*`).

---

## Integración en CI/CD

### Pipeline de Jenkins (`Jenkinsfile`)

En la máquina virtual de CI (`vm-jenkins` en Azure), el pipeline incorpora una etapa previa a la compilación y pruebas:

```groovy
stage('Security Scan') {
    steps {
        sh '''#!/usr/bin/env bash
            set -euo pipefail
            echo "[CI] Ejecutando escaneo unificado de seguridad y secretos (CAL-14)..."
            chmod +x scripts/security-scan.sh
            ./scripts/security-scan.sh
        '''
    }
}
```

Adicionalmente, en `Backend Quality Gates`, la ejecución de `./mvnw -B verify` ejecuta automáticamente `spotbugs:check`.

### Pipeline de GitHub Actions (`.github/workflows/ci.yml`)

- Se agregó el paso `pnpm audit --prod --audit-level=high` al trabajo de `frontend`.
- Se introdujo el trabajo paralelo `security` para escaneo de secretos antes del despliegue:
  ```yaml
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run secret leak scan
        run: |
          chmod +x scripts/security-scan.sh
          ./scripts/security-scan.sh --skip-backend --skip-frontend
  ```

---

## Guía Operativa

Para ejecutar el escaneo completo en local antes de crear un commit o pull request:

**En Windows (PowerShell):**
```powershell
.\scripts\security-scan.ps1
```

**En Linux / macOS:**
```bash
./scripts/security-scan.sh
```

**Opciones disponibles:**
- `-SkipSecrets` / `--skip-secrets`: Omite la compuerta de secretos.
- `-SkipFrontend` / `--skip-frontend`: Omite la auditoría de dependencias frontend.
- `-SkipBackend` / `--skip-backend`: Omite el análisis SAST de backend.
