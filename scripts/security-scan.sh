#!/usr/bin/env bash
#
# Escaneo unificado de seguridad de dependencias, SAST y secretos para RestoHub (CAL-14).
# Uso: ./scripts/security-scan.sh [--skip-secrets] [--skip-frontend] [--skip-backend]
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

SKIP_SECRETS=false
SKIP_FRONTEND=false
SKIP_BACKEND=false

for arg in "$@"; do
    case "$arg" in
        --skip-secrets)  SKIP_SECRETS=true ;;
        --skip-frontend) SKIP_FRONTEND=true ;;
        --skip-backend)  SKIP_BACKEND=true ;;
        *)
            echo "Argumento desconocido: $arg"
            echo "Uso: $0 [--skip-secrets] [--skip-frontend] [--skip-backend]"
            exit 1
            ;;
    esac
done

echo ""
echo "========================================================"
echo " [RestoHub] Escaneo Unificado de Seguridad (CAL-14)"
echo "========================================================"
echo ""

FAILED_STAGES=()

# -----------------------------------------------------------------------------
# ETAPA 1: ESCANEO DE SECRETOS Y CREDENCIALES
# -----------------------------------------------------------------------------
if [ "$SKIP_SECRETS" = false ]; then
    echo "[1/3] Ejecutando escaneo de secretos en archivos rastreados..."
    FOUND_SECRETS=0

    # Patrones de deteccion
    PATTERNS=(
        "-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"
        "AKIA[0-9A-Z]{16}"
        "ghp_[a-zA-Z0-9]{36}"
        "github_pat_[a-zA-Z0-9_]{82}"
        "tskey-auth-[a-zA-Z0-9_-]{20,}"
        "xox[baprs]-[0-9a-zA-Z]{10,48}"
        "rk_live_[0-9a-zA-Z]{24,}"
        "sk_live_[0-9a-zA-Z]{24,}"
    )

    TRACKED_FILES=$(git -C "$REPO_ROOT" ls-files)

    for file in $TRACKED_FILES; do
        # Omitir archivos conocidos o de documentacion de escaneo
        case "$file" in
            pnpm-lock.yaml|.terraform.lock.hcl|docs/cal-14-escaneo-dependencias-codigo.md|scripts/security-scan.ps1|scripts/security-scan.sh)
                continue
                ;;
        esac

        FULL_PATH="${REPO_ROOT}/${file}"
        if [ ! -f "$FULL_PATH" ]; then
            continue
        fi

        for pattern in "${PATTERNS[@]}"; do
            if grep -E -q "$pattern" "$FULL_PATH" 2>/dev/null; then
                echo "  [ALERTA] Posible secreto detectado en: $file"
                FOUND_SECRETS=$((FOUND_SECRETS + 1))
            fi
        done
    done

    if [ "$FOUND_SECRETS" -eq 0 ]; then
        echo "  [OK] 0 secretos detectados en archivos rastreados."
        echo ""
    else
        echo "  [FALLO] Se detectaron $FOUND_SECRETS posibles secretos en el repositorio."
        echo ""
        FAILED_STAGES+=("Secret Scanning")
    fi
else
    echo "[1/3] Escaneo de secretos omitido por parametro."
    echo ""
fi

# -----------------------------------------------------------------------------
# ETAPA 2: FRONTEND SCA (DEPENDENCIAS)
# -----------------------------------------------------------------------------
if [ "$SKIP_FRONTEND" = false ]; then
    echo "[2/3] Ejecutando auditoria de dependencias en frontend (pnpm audit --prod)..."
    FRONTEND_DIR="${REPO_ROOT}/frontend"

    if pnpm --dir "$FRONTEND_DIR" audit --prod --audit-level=high; then
        echo "  [OK] 0 vulnerabilidades de severidad alta/critica en dependencias de produccion."
        echo ""
    else
        echo "  [FALLO] pnpm audit encontro vulnerabilidades de severidad alta/critica."
        echo ""
        FAILED_STAGES+=("Frontend SCA")
    fi
else
    echo "[2/3] Auditoria de dependencias frontend omitida por parametro."
    echo ""
fi

# -----------------------------------------------------------------------------
# ETAPA 3: BACKEND SAST (SPOTBUGS + FINDSECBUGS)
# -----------------------------------------------------------------------------
if [ "$SKIP_BACKEND" = false ]; then
    echo "[3/3] Ejecutando analisis SAST en backend (SpotBugs + FindSecBugs)..."
    BACKEND_DIR="${REPO_ROOT}/backend"

    if (cd "$BACKEND_DIR" && ./mvnw compile spotbugs:check); then
        echo "  [OK] 0 vulnerabilidades detectadas por SpotBugs + FindSecBugs."
        echo ""
    else
        echo "  [FALLO] SpotBugs encontro defectos o vulnerabilidades en el backend."
        echo ""
        FAILED_STAGES+=("Backend SAST")
    fi
else
    echo "[3/3] Analisis SAST backend omitido por parametro."
    echo ""
fi

# -----------------------------------------------------------------------------
# RESUMEN FINAL
# -----------------------------------------------------------------------------
echo "========================================================"
if [ ${#FAILED_STAGES[@]} -eq 0 ]; then
    echo " [EXITO] Todas las compuertas de seguridad pasaron limpiamente."
    echo "========================================================"
    echo ""
    exit 0
else
    echo " [FALLO] Las siguientes compuertas de seguridad fallaron:"
    for stage in "${FAILED_STAGES[@]}"; do
        echo "   - $stage"
    done
    echo "========================================================"
    echo ""
    exit 1
fi
