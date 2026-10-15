<#
.SYNOPSIS
    Escaneo unificado de seguridad de dependencias, SAST y secretos para RestoHub.
.DESCRIPTION
    Ejecuta tres compuertas de seguridad:
    1. Secret Scan: Deteccion de claves privadas, tokens, contraseñas y credenciales en archivos rastreados.
    2. Frontend SCA: Auditoria de dependencias de produccion en frontend (pnpm audit --prod --audit-level=high).
    3. Backend SAST: Analisis de seguridad en codigo Java con SpotBugs y FindSecBugs (mvn spotbugs:check).
.PARAMETER SkipSecrets
    Omite la etapa de escaneo de secretos.
.PARAMETER SkipFrontend
    Omite la etapa de dependencias frontend.
.PARAMETER SkipBackend
    Omite la etapa de SAST backend.
.EXAMPLE
    .\scripts\security-scan.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipSecrets,
    [switch]$SkipFrontend,
    [switch]$SkipBackend
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " [RestoHub] Escaneo Unificado de Seguridad (CAL-14)" -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

$failedStages = @()

# -----------------------------------------------------------------------------
# ETAPA 1: ESCANEO DE SECRETOS Y CREDENCIALES
# -----------------------------------------------------------------------------
if (-not $SkipSecrets) {
    Write-Host "[1/3] Ejecutando escaneo de secretos en archivos rastreados..." -ForegroundColor Yellow

    $secretPatterns = @(
        @{ Name = "Private Key Block"; Pattern = "-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----" },
        @{ Name = "AWS Access Key ID"; Pattern = "\bAKIA[0-9A-Z]{16}\b" },
        @{ Name = "GitHub Personal Access Token"; Pattern = "\b(ghp_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9_]{82})\b" },
        @{ Name = "Tailscale Auth Key"; Pattern = "\btskey-auth-[a-zA-Z0-9_-]{20,}\b" },
        @{ Name = "Slack Token"; Pattern = "\bxox[baprs]-[0-9a-zA-Z]{10,48}\b" },
        @{ Name = "Stripe Live Secret Key"; Pattern = "\brk_live_[0-9a-zA-Z]{24,}\b|\bsk_live_[0-9a-zA-Z]{24,}\b" }
    )

    $trackedFiles = & git -C $repoRoot ls-files
    $ignoredPaths = @(
        "pnpm-lock.yaml",
        ".terraform.lock.hcl",
        "docs/cal-14-escaneo-dependencias-codigo.md",
        "scripts/security-scan.ps1",
        "scripts/security-scan.sh"
    )

    $foundSecrets = 0

    foreach ($file in $trackedFiles) {
        if ($ignoredPaths -contains $file) { continue }
        $fullPath = Join-Path $repoRoot $file
        if (-not (Test-Path $fullPath -PathType Leaf)) { continue }

        $content = Get-Content -Path $fullPath -Raw -ErrorAction SilentlyContinue
        if ([string]::IsNullOrEmpty($content)) { continue }

        foreach ($rule in $secretPatterns) {
            if ($content -match $rule.Pattern) {
                Write-Host "  [ALERTA] Secreto detectado ($($rule.Name)) en: $file" -ForegroundColor Red
                $foundSecrets++
            }
        }
    }

    if ($foundSecrets -eq 0) {
        Write-Host "  [OK] 0 secretos detectados en archivos rastreados.`n" -ForegroundColor Green
    } else {
        Write-Host "  [FALLO] Se detectaron $foundSecrets posibles secretos en el repositorio.`n" -ForegroundColor Red
        $failedStages += "Secret Scanning"
    }
} else {
    Write-Host "[1/3] Escaneo de secretos omitido por parametro.`n" -ForegroundColor DarkGray
}

# -----------------------------------------------------------------------------
# ETAPA 2: FRONTEND SCA (DEPENDENCIAS)
# -----------------------------------------------------------------------------
if (-not $SkipFrontend) {
    Write-Host "[2/3] Ejecutando auditoria de dependencias en frontend (pnpm audit --prod)..." -ForegroundColor Yellow
    $frontendDir = Join-Path $repoRoot "frontend"

    try {
        $auditOutput = & pnpm --dir $frontendDir audit --prod --audit-level=high 2>&1
        $auditExit = $LASTEXITCODE

        if ($auditExit -eq 0) {
            Write-Host "  [OK] 0 vulnerabilidades de severidad alta/critica en dependencias de produccion.`n" -ForegroundColor Green
        } else {
            Write-Host $auditOutput
            Write-Host "  [FALLO] pnpm audit encontro vulnerabilidades de severidad alta/critica.`n" -ForegroundColor Red
            $failedStages += "Frontend SCA"
        }
    } catch {
        Write-Host "  [FALLO] Error al ejecutar pnpm audit: $_`n" -ForegroundColor Red
        $failedStages += "Frontend SCA"
    }
} else {
    Write-Host "[2/3] Auditoria de dependencias frontend omitida por parametro.`n" -ForegroundColor DarkGray
}

# -----------------------------------------------------------------------------
# ETAPA 3: BACKEND SAST (SPOTBUGS + FINDSECBUGS)
# -----------------------------------------------------------------------------
if (-not $SkipBackend) {
    Write-Host "[3/3] Ejecutando analisis SAST en backend (SpotBugs + FindSecBugs)..." -ForegroundColor Yellow
    $backendDir = Join-Path $repoRoot "backend"
    $mvnCmd = if ($IsWindows -or ($env:OS -like "*Windows*")) { ".\mvnw.cmd" } else { "./mvnw" }

    try {
        & git -C $repoRoot rev-parse --is-inside-work-tree > $null
        $cmdLine = "$mvnCmd compile spotbugs:check"
        $sastProcess = Start-Process -FilePath (Join-Path $backendDir $mvnCmd) -ArgumentList "compile", "spotbugs:check" -WorkingDirectory $backendDir -NoNewWindow -Wait -PassThru

        if ($sastProcess.ExitCode -eq 0) {
            Write-Host "  [OK] 0 vulnerabilidades detectadas por SpotBugs + FindSecBugs.`n" -ForegroundColor Green
        } else {
            Write-Host "  [FALLO] SpotBugs encontro defectos o vulnerabilidades en el backend.`n" -ForegroundColor Red
            $failedStages += "Backend SAST"
        }
    } catch {
        Write-Host "  [FALLO] Error al ejecutar SpotBugs: $_`n" -ForegroundColor Red
        $failedStages += "Backend SAST"
    }
} else {
    Write-Host "[3/3] Analisis SAST backend omitido por parametro.`n" -ForegroundColor DarkGray
}

# -----------------------------------------------------------------------------
# RESUMEN FINAL
# -----------------------------------------------------------------------------
Write-Host "========================================================" -ForegroundColor Cyan
if ($failedStages.Count -eq 0) {
    Write-Host " [EXITO] Todas las compuertas de seguridad pasaron limpiamente." -ForegroundColor Green
    Write-Host "========================================================`n" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host " [FALLO] Las siguientes compuertas de seguridad fallaron:" -ForegroundColor Red
    foreach ($stage in $failedStages) {
        Write-Host "   - $stage" -ForegroundColor Red
    }
    Write-Host "========================================================`n" -ForegroundColor Cyan
    exit 1
}
