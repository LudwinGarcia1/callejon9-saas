# Levanta el backend contra el PostgreSQL local.
# Uso: .\scripts\run-dev.ps1
$ErrorActionPreference = 'Stop'

# Cargar variables locales desde .env si existe
$envFiles = @(
    (Join-Path $PSScriptRoot "..\.env"),
    (Join-Path $PSScriptRoot "..\backend\.env")
)
foreach ($f in $envFiles) {
    if (Test-Path $f) {
        Get-Content $f | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
            $parts = $_ -split '=', 2
            $varName = $parts[0].Trim()
            $varVal = $parts[1].Trim()
            if (-not [System.Environment]::GetEnvironmentVariable($varName, 'Process')) {
                [System.Environment]::SetEnvironmentVariable($varName, $varVal, 'Process')
            }
        }
    }
}

if (-not $env:DB_APP_PASSWORD)   { $env:DB_APP_PASSWORD   = 'app_dev_pwd' }
if (-not $env:DB_OWNER_PASSWORD) { $env:DB_OWNER_PASSWORD = 'owner_dev_pwd' }
if (-not $env:DB_PORT)           { $env:DB_PORT           = '5433' }
# En local no hay HTTPS: sin esto el navegador podria descartar las cookies Secure.
if (-not $env:AUTH_COOKIE_SECURE) { $env:AUTH_COOKIE_SECURE = 'false' }

Write-Host 'Iniciando backend en http://localhost:8080 ...' -ForegroundColor Cyan
Set-Location "$PSScriptRoot\..\backend"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
