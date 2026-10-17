#!/usr/bin/env bash
# Verificación de Checks de Jenkins en Pull Requests (CAL-89)
set -euo pipefail

COMMIT_SHA="${1:-$(git rev-parse HEAD)}"
OWNER_REPO="${2:-LudwinGarcia1/callejon9-saas}"
SHORT_SHA="${COMMIT_SHA:0:7}"

echo "=== Verificación de Checks de Jenkins en PR (CAL-89) ==="
echo "[1/4] Commit objetivo: ${SHORT_SHA} (${COMMIT_SHA})"

# 2. Verificar Jenkinsfile
JENKINSFILE_PATH="$(dirname "$0")/../Jenkinsfile"
if [[ -f "$JENKINSFILE_PATH" ]]; then
    if grep -q "buildDiscarder" "$JENKINSFILE_PATH" && grep -q "local-agent" "$JENKINSFILE_PATH" && grep -q "success" "$JENKINSFILE_PATH"; then
        echo "[2/4] Jenkinsfile: PASS (buildDiscarder, agente local y notificaciones post configurados)"
    else
        echo "[2/4] Jenkinsfile: WARN (revisar configuración en Jenkinsfile)"
    fi
else
    echo "[2/4] Jenkinsfile no encontrado"
fi

# 3. Verificar JCasC
CASC_PATH="$(dirname "$0")/../infra/jenkins/casc/jenkins.yaml"
if [[ -f "$CASC_PATH" ]]; then
    if grep -q "githubpluginconfig" "$CASC_PATH" && grep -q "github-token" "$CASC_PATH"; then
        echo "[3/4] JCasC (jenkins.yaml): PASS (githubpluginconfig con credentialsId github-token presente)"
    else
        echo "[3/4] JCasC (jenkins.yaml): WARN (githubpluginconfig no encontrado)"
    fi
else
    echo "[3/4] casc/jenkins.yaml no encontrado"
fi

# 4. Consultar API de GitHub para commit statuses
echo "[4/4] Consultando estados de commit en GitHub (${OWNER_REPO})..."
if command -v gh >/dev/null 2>&1; then
    STATUS_JSON=$(gh api "repos/${OWNER_REPO}/commits/${COMMIT_SHA}/statuses" 2>/dev/null || echo "[]")
    COUNT=$(echo "$STATUS_JSON" | grep -c '"context":' || true)
    if [[ "$COUNT" -gt 0 ]]; then
        echo "  -> Estados de commit encontrados: $COUNT"
        echo "$STATUS_JSON" | grep -E '"context"|"state"|"description"' || true
    else
        echo "  -> Ningún check registrado aún para ${SHORT_SHA}."
        echo "     Nota: El job Multibranch de Jenkins registrará 'continuous-integration/jenkins/pr-merge' al compilar en Azure."
    fi
else
    echo "  -> gh CLI no instalado en el entorno actual."
fi

echo ""
echo "Activación en Azure Key Vault:"
echo "  az keyvault secret set --vault-name <vault> --name github-token --value <token> --tags type=secret-text"
echo "Protección de rama en GitHub:"
echo "  Settings > Branches > main > Require status check: continuous-integration/jenkins/pr-merge"
