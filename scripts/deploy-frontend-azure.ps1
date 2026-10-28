#Requires -Version 5.1
<#
.SYNOPSIS
    Compila el frontend en salida standalone y lo publica en la Web App de Azure.

.DESCRIPTION
    1. Compila con la etapa `builder` de frontend/Dockerfile, en Linux, igual que la
       imagen de produccion. En Windows `next build` no puede copiar los enlaces de
       pnpm a la salida standalone, y los binarios nativos deben ser los de Linux.
    2. Dentro de esa imagen reinstala con node_modules plano (node-linker=hoisted) y
       recompila, porque el despliegue zip de App Service no conserva los enlaces
       simbolicos de pnpm. Copia server.js, .next/static y public.
    3. Empaqueta en zip y lo sube con `az webapp deploy`, autenticado con Entra ID.
    4. Espera a que /login responda 200.

    BACKEND_ORIGIN queda fijo en el rewrite de /api/v1/* al compilar: cambiar la API
    exige volver a correr este script.

    Los valores por defecto salen de `terraform output` en infra/terraform/azure-frontend.

.EXAMPLE
    .\scripts\deploy-frontend-azure.ps1

.EXAMPLE
    .\scripts\deploy-frontend-azure.ps1 -BackendOrigin https://app-callejon9-api-prod-xxxxxx.azurewebsites.net
#>
[CmdletBinding()]
param(
    [string]$ResourceGroup,
    [string]$WebAppName,
    [string]$BackendOrigin,
    [int]$HealthTimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'

$repoRoot     = Split-Path -Parent $PSScriptRoot
$frontendDir  = Join-Path $repoRoot 'frontend'
$terraformDir = Join-Path $repoRoot 'infra\terraform\azure-frontend'
$image        = 'callejon9-frontend-azure-build'

function Invoke-Native {
    param([string]$Command, [string[]]$Arguments)
    # Windows PowerShell turns redirected stderr into terminating errors; the exit code decides.
    $ErrorActionPreference = 'Continue'
    $output = & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command $($Arguments[0]) fallo con codigo $LASTEXITCODE."
    }
    $output
}

function Get-TerraformOutput {
    param([string]$Name)
    Invoke-Native 'terraform' @("-chdir=$terraformDir", 'output', '-raw', $Name)
}

if (-not $ResourceGroup) { $ResourceGroup = Get-TerraformOutput 'resource_group_name' }
if (-not $WebAppName)    { $WebAppName    = Get-TerraformOutput 'web_app_name' }
if (-not $BackendOrigin) {
    $BackendOrigin = Invoke-Native 'az' @('webapp', 'config', 'appsettings', 'list', '-g', $ResourceGroup,
        '-n', $WebAppName, '--query', "[?name=='BACKEND_ORIGIN'].value | [0]", '-o', 'tsv')
}
if ($BackendOrigin -notmatch '^https://[a-z0-9.-]+(:[0-9]+)?$') {
    throw "BackendOrigin debe ser un origen https:// sin ruta ni diagonal final. Recibido: '$BackendOrigin'."
}

# CAL-165: Validacion estricta de cuotas y limites de Azure App Service Plan F1
try {
    # Obtener el plan exacto asociado a la Web App
    $planId = Invoke-Native 'az' @('webapp', 'show', '-g', $ResourceGroup, '-n', $WebAppName, '--query', 'appServicePlanId', '-o', 'tsv') 2>$null
    if ($planId) {
        $planJson = Invoke-Native 'az' @('appservice', 'plan', 'show', '--ids', $planId, '-o', 'json') | ConvertFrom-Json
        $planSku = $planJson.sku.name

        if ($planSku -eq 'F1') {
            Write-Warning "El Frontend se encuentra en Azure App Service Plan F1 (Gratuito: 60 min CPU/dia, WPStopRequests = 15/dia)."
            
            # Consultar estado de cuota y usages del App Service Plan
            $usagesJson = Invoke-Native 'az' @('rest', '--method', 'get', '--uri', "$planId/usages?api-version=2022-03-01") 2>$null
            if ($usagesJson) {
                $usages = ($usagesJson | ConvertFrom-Json).value
                $stopReqUsage = $usages | Where-Object { $_.name.value -eq 'WPStopRequests' -or $_.name.localizedValue -match 'Stop' }
                if ($stopReqUsage) {
                    $curr = [int]$stopReqUsage.currentValue
                    $limit = [int]$stopReqUsage.limit
                    Write-Host "Cuota de reinicios de aplicacion (WPStopRequests): $curr / $limit consumidos hoy."
                    if ($curr -ge $limit) {
                        throw "CUOTA AGOTADA (CAL-165): Se alcanzo el limite de $limit reinicios diarios (WPStopRequests) en el plan F1. La aplicacion respondera 403 QuotaExceeded. La cuota se reinicia a las 04:00 UTC. Para continuar de inmediato, escale el plan a B1: en infra/terraform/azure-frontend/terraform.tfvars configure app_service_sku = 'B1' y ejecute terraform apply."
                    }
                }
            }

            # Consultar si el sitio se encuentra reportando QuotaExceeded
            $appState = Invoke-Native 'az' @('webapp', 'show', '-g', $ResourceGroup, '-n', $WebAppName, '--query', 'state', '-o', 'tsv') 2>$null
            $appAvailability = Invoke-Native 'az' @('webapp', 'show', '-g', $ResourceGroup, '-n', $WebAppName, '--query', 'availabilityState', '-o', 'tsv') 2>$null
            if ($appState -eq 'QuotaExceeded' -or $appAvailability -eq 'QuotaExceeded') {
                throw "SITIO BLOQUEADO POR CUOTA (CAL-165): La Web App '$WebAppName' se encuentra en estado QuotaExceeded. La cuota diaria se reinicia a las 04:00 UTC. Para desbloquearla ahora, escale a SKU B1 con Terraform."
            }
        }
    }
} catch {
    if ($_.Exception.Message -match 'CUOTA AGOTADA' -or $_.Exception.Message -match 'SITIO BLOQUEADO') {
        throw $_
    }
    Write-Warning "No fue posible consultar las metricas de cuota en Azure: $($_.Exception.Message)"
}

$work = Join-Path ([IO.Path]::GetTempPath()) "callejon9-frontend-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$siteDir   = Join-Path $work 'site'
$zipPath   = Join-Path $work 'package.zip'
$container = "callejon9-frontend-package-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
New-Item -ItemType Directory -Force $work | Out-Null

try {
    $revision = (Invoke-Native 'git' @('-C', $repoRoot, 'rev-parse', '--short', 'HEAD'))
    Write-Host "== Compilando el frontend ($revision) contra $BackendOrigin"
    Invoke-Native 'docker' @('build', '--target', 'builder', '--build-arg', "BACKEND_ORIGIN=$BackendOrigin",
        '-t', $image, $frontendDir) | Out-Host

    # Se reinstala con node_modules plano y se recompila: con los enlaces de pnpm,
    # copiar la salida resolviendolos separa a next de sus dependencias y server.js
    # no arranca. El script falla si queda algun enlace en la salida.
    Write-Host '== Recompilando con node_modules plano'
    $package = 'set -e; rm -rf node_modules .next; pnpm install --frozen-lockfile --config.node-linker=hoisted; ' +
               'pnpm build; [ $(find .next/standalone -type l | wc -l) -eq 0 ]; mkdir /out; ' +
               'cp -r .next/standalone/. /out/; mkdir -p /out/.next; cp -r .next/static /out/.next/static; ' +
               'cp -r public /out/public'
    Invoke-Native 'docker' @('create', '--name', $container, '--entrypoint', 'sh', $image, '-c', $package) | Out-Null
    Invoke-Native 'docker' @('start', '--attach', $container) | Out-Host
    Invoke-Native 'docker' @('cp', "${container}:/out", $siteDir) | Out-Null

    if (-not (Test-Path (Join-Path $siteDir 'server.js'))) {
        throw 'La salida no contiene server.js: revisa que la compilacion use NEXT_OUTPUT=standalone.'
    }
    # tar.exe de Windows (bsdtar) escribe zip con -a; incluye .next, que Compress-Archive omite.
    $systemTar = Join-Path $env:SystemRoot 'System32\tar.exe'
    Invoke-Native $systemTar @('-a', '-cf', $zipPath, '-C', $siteDir, '.')
    $sizeMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)

    Write-Host "== Publicando $sizeMb MB en $WebAppName"
    Invoke-Native 'az' @('webapp', 'deploy', '-g', $ResourceGroup, '-n', $WebAppName, '--src-path', $zipPath,
        '--type', 'zip', '--clean', 'true', '--restart', 'true', '--track-status', 'false') | Out-Null

    $hostName = Invoke-Native 'az' @('webapp', 'show', '-g', $ResourceGroup, '-n', $WebAppName,
        '--query', 'defaultHostName', '-o', 'tsv')
    $url = "https://$hostName/login"
    Write-Host "== Esperando a que $url responda 200"
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 60
            # La pagina de bienvenida de App Service tambien responde 200, pero no trae la CSP de Next.
            if ($response.StatusCode -eq 200 -and $response.Headers['Content-Security-Policy']) {
                Write-Host "Frontend listo: https://$hostName"
                return
            }
        } catch {
            # Esperado mientras App Service arranca Node.
        }
        Start-Sleep -Seconds 15
    }
    throw "El frontend no respondio en $HealthTimeoutSeconds s. Revisa 'az webapp log tail -g $ResourceGroup -n $WebAppName'."
} finally {
    $ErrorActionPreference = 'Continue'
    & docker rm -f $container 2>$null | Out-Null
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}
