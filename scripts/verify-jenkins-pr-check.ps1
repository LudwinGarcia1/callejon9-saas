<#
.SYNOPSIS
    Verifica la configuración e integración de checks de Jenkins en Pull Requests (CAL-89).
.DESCRIPTION
    Inspecciona la configuración como código (JCasC), el pipeline (Jenkinsfile) y consulta
    la API de GitHub para verificar si existen checks de commit emitidos por Jenkins.
.PARAMETER CommitSha
    SHA del commit a verificar. Por defecto el HEAD actual.
.PARAMETER OwnerRepo
    Repositorio de GitHub en formato 'owner/repo'. Por defecto 'LudwinGarcia1/callejon9-saas'.
#>
[CmdletBinding()]
param(
    [string]$CommitSha,
    [string]$OwnerRepo = "LudwinGarcia1/callejon9-saas"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Verificación de Checks de Jenkins en PR (CAL-89) ===" -ForegroundColor Cyan

# 1. Resolver commit objetivo
if (-not $CommitSha) {
    $CommitSha = (git rev-parse HEAD).Trim()
}
$ShortSha = $CommitSha.Substring(0, [Math]::Min(7, $CommitSha.Length))
Write-Host "[1/4] Commit objetivo: $ShortSha ($CommitSha)" -ForegroundColor Gray

# 2. Verificar Jenkinsfile
$JenkinsfilePath = Join-Path $PSScriptRoot "..\Jenkinsfile"
if (Test-Path $JenkinsfilePath) {
    $content = Get-Content $JenkinsfilePath -Raw
    $hasDiscarder = $content -match "buildDiscarder"
    $hasAgent = $content -match "label\s+'local-agent'"
    $hasPost = $content -match "success\s*\{" -and $content -match "failure\s*\{"
    
    if ($hasDiscarder -and $hasAgent -and $hasPost) {
        Write-Host "[2/4] Jenkinsfile: PASS (buildDiscarder, agente local y notificaciones post configurados)" -ForegroundColor Green
    } else {
        Write-Host "[2/4] Jenkinsfile: WARN (revisar configuración de buildDiscarder o post blocks)" -ForegroundColor Yellow
    }
} else {
    Write-Host "[2/4] Jenkinsfile no encontrado en la raíz" -ForegroundColor Red
}

# 3. Verificar configuración JCasC
$CascPath = Join-Path $PSScriptRoot "..\infra\jenkins\casc\jenkins.yaml"
if (Test-Path $CascPath) {
    $cascContent = Get-Content $CascPath -Raw
    if ($cascContent -match "githubpluginconfig" -and $cascContent -match "credentialsId:\s*`"github-token`"") {
        Write-Host "[3/4] JCasC (jenkins.yaml): PASS (githubpluginconfig con credentialsId github-token presente)" -ForegroundColor Green
    } else {
        Write-Host "[3/4] JCasC (jenkins.yaml): WARN (githubpluginconfig no encontrado)" -ForegroundColor Yellow
    }
} else {
    Write-Host "[3/4] casc/jenkins.yaml no encontrado" -ForegroundColor Red
}

# 4. Consultar API de GitHub para commit statuses
Write-Host "[4/4] Consultando estados de commit en GitHub ($OwnerRepo)..." -ForegroundColor Gray
try {
    $ghCli = Get-Command "gh" -ErrorAction SilentlyContinue
    if ($ghCli) {
        $statusJson = & gh api "repos/$OwnerRepo/commits/$CommitSha/statuses" 2>&1
        if ($LASTEXITCODE -eq 0) {
            $statuses = $statusJson | ConvertFrom-Json
            $jenkinsStatuses = $statuses | Where-Object { $_.context -match "jenkins" }
            
            if ($jenkinsStatuses -and $jenkinsStatuses.Count -gt 0) {
                Write-Host "  -> Encontrados $($jenkinsStatuses.Count) checks emitidos por Jenkins:" -ForegroundColor Green
                foreach ($st in $jenkinsStatuses) {
                    $color = if ($st.state -eq "success") { "Green" } elseif ($st.state -eq "pending") { "Yellow" } else { "Red" }
                    Write-Host "     - [$($st.state.ToUpper())] $($st.context): $($st.description) ($($st.target_url))" -ForegroundColor $color
                }
            } else {
                Write-Host "  -> Ningún check con contexto 'jenkins' registrado aún para $ShortSha." -ForegroundColor Yellow
                Write-Host "     Nota: El job Multibranch de Jenkins registrará 'continuous-integration/jenkins/pr-merge'" -ForegroundColor Gray
                Write-Host "     una vez que ejecute el build sobre la rama o PR correspondiente en Azure." -ForegroundColor Gray
            }
        } else {
            Write-Host "  -> Error consultando la API con gh: $statusJson" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  -> gh CLI no disponible en PATH para consulta directa." -ForegroundColor Yellow
    }
} catch {
    Write-Host "  -> Advertencia consultando GitHub API: $_" -ForegroundColor Yellow
}

Write-Host "`nGuía de activación en Azure Key Vault:" -ForegroundColor Cyan
Write-Host "  az keyvault secret set --vault-name <vault> --name github-token --value <token> --tags type=secret-text" -ForegroundColor White
Write-Host "Protección de rama en GitHub:" -ForegroundColor Cyan
Write-Host "  Settings > Branches > main > Require status check: continuous-integration/jenkins/pr-merge`n" -ForegroundColor White
