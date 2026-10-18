#Requires -Version 5.1
<#
.SYNOPSIS
    Apaga, enciende o consulta el backend de Azure para no gastar credito cuando nadie lo usa.

.DESCRIPTION
    Stop   detiene la Web App, desactiva Always On, baja el plan de App Service a F1
           (gratuito) y detiene PostgreSQL. El plan B1 se cobra mientras exista, aunque
           la Web App este detenida: solo bajarlo a F1 deja de cobrarlo.
    Start  enciende PostgreSQL, sube el plan al SKU indicado (B1), reactiva Always On,
           arranca la Web App y espera a que /actuator/health responda UP.
    Status muestra el SKU del plan, el estado de la Web App y el de PostgreSQL.

    No borra nada: la base, los secretos de Key Vault y la configuracion se conservan.
    Azure vuelve a encender solo un PostgreSQL detenido a los 7 dias.

    Mientras el backend esta apagado, `terraform plan` muestra el plan en F1 y Always On
    desactivado. Corre `-Action Start` antes de cualquier `terraform apply`.
    Ver infra/terraform/azure-backend/README.md.

.EXAMPLE
    .\scripts\azure-backend-power.ps1 -Action Status

.EXAMPLE
    .\scripts\azure-backend-power.ps1 -Action Stop
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Status', 'Stop', 'Start')]
    [string]$Action,

    [string]$ResourceGroup = 'rg-callejon9-prod-mxc-001',

    # SKU al que vuelve el plan con Start. Debe coincidir con app_service_sku de Terraform.
    [string]$Sku = 'B1',

    [int]$HealthTimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'

function Invoke-Az {
    param([string[]]$Arguments)
    # Windows PowerShell turns redirected stderr into terminating errors; the exit code decides.
    $ErrorActionPreference = 'Continue'
    $output = & az @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "az $($Arguments[0..1] -join ' ') fallo con codigo $LASTEXITCODE."
    }
    $output
}

function Get-Resources {
    if ((Invoke-Az @('group', 'exists', '-n', $ResourceGroup)) -ne 'true') {
        throw "No existe el resource group $ResourceGroup en la suscripcion activa. Revisa 'az account show' y cambia con 'az account set'."
    }
    $web = Invoke-Az @('webapp', 'list', '-g', $ResourceGroup, '--query',
        '[0].{name:name, host:defaultHostName, state:state, planId:serverFarmId}', '-o', 'json') | ConvertFrom-Json
    $db = Invoke-Az @('postgres', 'flexible-server', 'list', '-g', $ResourceGroup, '--query',
        '[0].{name:name, state:state}', '-o', 'json') | ConvertFrom-Json
    if (-not $web -or -not $db) {
        throw "No se encontro la Web App o el servidor PostgreSQL en $ResourceGroup."
    }
    $plan = Invoke-Az @('appservice', 'plan', 'show', '--ids', $web.planId, '--query',
        '{name:name, sku:sku.name}', '-o', 'json') | ConvertFrom-Json
    $alwaysOn = Invoke-Az @('webapp', 'config', 'show', '-g', $ResourceGroup, '-n', $web.name,
        '--query', 'alwaysOn', '-o', 'tsv')
    [pscustomobject]@{ Web = $web; Db = $db; Plan = $plan; AlwaysOn = $alwaysOn }
}

function Show-Status {
    param($r)
    Write-Host "Plan de App Service  $($r.Plan.name): $($r.Plan.sku)"
    Write-Host "Web App              $($r.Web.name): $($r.Web.state) (Always On: $($r.AlwaysOn))"
    Write-Host "PostgreSQL           $($r.Db.name): $($r.Db.state)"
    Write-Host "API                  https://$($r.Web.host)"
}

function Wait-Healthy {
    param([string]$HostName)
    $url = "https://$HostName/actuator/health"
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    Write-Host "== Esperando a que $url responda UP (el arranque tarda un par de minutos)"
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30
            # Actuator answers application/vnd.spring-boot.actuator.v3+json, which
            # Windows PowerShell does not recognize as text and returns as bytes.
            $content = $response.Content
            if ($content -is [byte[]]) {
                $content = [Text.Encoding]::UTF8.GetString($content)
            }
            # A 200 with HTML is the App Service welcome page, not the API.
            if ($content -match '"status"\s*:\s*"UP"') {
                Write-Host "Backend listo: $content"
                return
            }
        } catch {
            # Expected while the JVM and Flyway start.
        }
        Start-Sleep -Seconds 15
    }
    throw "El backend no respondio UP en $HealthTimeoutSeconds s. Revisa el log con 'az webapp log tail -g $ResourceGroup -n <web_app>'."
}

$r = Get-Resources

switch ($Action) {
    'Status' {
        Show-Status $r
    }

    'Stop' {
        if ($r.Web.state -ne 'Stopped') {
            Write-Host "== Deteniendo la Web App $($r.Web.name)"
            Invoke-Az @('webapp', 'stop', '-g', $ResourceGroup, '-n', $r.Web.name) | Out-Null
        }
        # F1 does not allow Always On: the plan update fails while it is enabled.
        if ($r.AlwaysOn -eq 'true') {
            Write-Host '== Desactivando Always On'
            Invoke-Az @('webapp', 'config', 'set', '-g', $ResourceGroup, '-n', $r.Web.name,
                '--always-on', 'false') | Out-Null
        }
        if ($r.Plan.sku -ne 'F1') {
            Write-Host "== Bajando el plan $($r.Plan.name) de $($r.Plan.sku) a F1"
            Invoke-Az @('appservice', 'plan', 'update', '--ids', $r.Web.planId, '--sku', 'F1') | Out-Null
        }
        if ($r.Db.state -eq 'Ready') {
            Write-Host "== Deteniendo PostgreSQL $($r.Db.name) (tarda unos minutos)"
            Invoke-Az @('postgres', 'flexible-server', 'stop', '-g', $ResourceGroup, '-n', $r.Db.name) | Out-Null
        } elseif ($r.Db.state -ne 'Stopped') {
            Write-Warning "PostgreSQL esta en estado $($r.Db.state); no se detiene. Repite el comando cuando quede Ready."
        }
        Write-Host ''
        Show-Status (Get-Resources)
    }

    'Start' {
        if ($r.Db.state -eq 'Stopped') {
            Write-Host "== Encendiendo PostgreSQL $($r.Db.name) (tarda unos minutos)"
            Invoke-Az @('postgres', 'flexible-server', 'start', '-g', $ResourceGroup, '-n', $r.Db.name) | Out-Null
        } elseif ($r.Db.state -ne 'Ready') {
            Write-Warning "PostgreSQL esta en estado $($r.Db.state); el backend no estara listo hasta que quede Ready."
        }
        if ($r.Plan.sku -ne $Sku) {
            Write-Host "== Subiendo el plan $($r.Plan.name) de $($r.Plan.sku) a $Sku"
            Invoke-Az @('appservice', 'plan', 'update', '--ids', $r.Web.planId, '--sku', $Sku) | Out-Null
        }
        if ($r.AlwaysOn -ne 'true') {
            Write-Host '== Reactivando Always On'
            Invoke-Az @('webapp', 'config', 'set', '-g', $ResourceGroup, '-n', $r.Web.name,
                '--always-on', 'true') | Out-Null
        }
        if ($r.Web.state -ne 'Running') {
            Write-Host "== Arrancando la Web App $($r.Web.name)"
            Invoke-Az @('webapp', 'start', '-g', $ResourceGroup, '-n', $r.Web.name) | Out-Null
        }
        Wait-Healthy $r.Web.host
        Write-Host ''
        Show-Status (Get-Resources)
    }
}
