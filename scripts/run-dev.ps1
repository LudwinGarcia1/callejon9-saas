# Levanta el backend contra el PostgreSQL local.
# Uso: .\scripts\run-dev.ps1
$ErrorActionPreference = 'Stop'

if (-not $env:DB_APP_PASSWORD)   { $env:DB_APP_PASSWORD   = 'app_dev_pwd' }
if (-not $env:DB_OWNER_PASSWORD) { $env:DB_OWNER_PASSWORD = 'owner_dev_pwd' }
# En local no hay HTTPS: sin esto el navegador podria descartar las cookies Secure.
if (-not $env:AUTH_COOKIE_SECURE) { $env:AUTH_COOKIE_SECURE = 'false' }

Write-Host 'Iniciando backend en http://localhost:8080 ...' -ForegroundColor Cyan
Set-Location "$PSScriptRoot\..\backend"
.\mvnw.cmd spring-boot:run
