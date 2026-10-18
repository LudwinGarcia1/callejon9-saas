#Requires -Version 5.1
<#
.SYNOPSIS
    Puerta de calidad de infraestructura: formato y validacion de Terraform.

.DESCRIPTION
    Corre `terraform fmt -check` sobre infra/terraform y `terraform validate` en cada
    root module. Inicializa sin backend, asi que no necesita credenciales de Azure.
    El plan contra Azure se ejecuta aparte; ver infra/terraform/azure-ci/README.md.

.EXAMPLE
    .\scripts\terraform-check.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot  = Split-Path -Parent $PSScriptRoot
$infraRoot = Join-Path $repoRoot 'infra\terraform'
$roots     = @(
    'azure-ci\bootstrap',
    'azure-ci',
    'azure-backend\bootstrap',
    'azure-backend',
    'azure-frontend'
)

function Invoke-Terraform {
    param([string[]]$Arguments)
    # Windows PowerShell turns redirected stderr into terminating errors; the exit code decides.
    $ErrorActionPreference = 'Continue'
    & terraform @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "terraform $($Arguments -join ' ') fallo con codigo $LASTEXITCODE."
    }
}

Write-Host "== terraform fmt -check ($infraRoot)"
Invoke-Terraform @('fmt', '-check', '-recursive', '-diff', $infraRoot)

foreach ($root in $roots) {
    $path = Join-Path $infraRoot $root
    Write-Host "== terraform validate ($root)"
    Invoke-Terraform @("-chdir=$path", 'init', '-backend=false', '-input=false', '-no-color')
    Invoke-Terraform @("-chdir=$path", 'validate', '-no-color')
}

Write-Host 'Terraform: formato y validacion correctos.'
