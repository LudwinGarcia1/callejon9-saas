#Requires -Version 5.1
<#
.SYNOPSIS
    Regenera el espejo .claude/ a partir del arbol fuente .agents/.

.DESCRIPTION
    Claude Code descubre skills en .claude/skills/ y subagentes en .claude/agents/.
    La fuente de verdad de ambos es .agents/, que es lo que el repositorio versiona
    y lo que Codex tambien lee. Este script copia una direccion: .agents -> .claude.
    Cualquier edicion hecha dentro de .claude/skills o .claude/agents se pierde.

    Los archivos .agents/agents/<nombre>.agent.md se copian como
    .claude/agents/<nombre>.md, que es el nombre que Claude Code espera.

    .claude/settings.json y .claude/settings.local.json no se tocan.

.PARAMETER Check
    No escribe nada: solo compara y devuelve codigo 1 si el espejo esta desalineado.

.EXAMPLE
    .\scripts\sync-claude-harness.ps1
    .\scripts\sync-claude-harness.ps1 -Check
#>
[CmdletBinding()]
param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$source   = Join-Path $repoRoot '.agents'
$target   = Join-Path $repoRoot '.claude'

if (-not (Test-Path $source)) {
    throw "No existe $source. Este script se corre desde la raiz del repositorio."
}

function Get-FileHashOrNull {
    param([string]$Path)
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    }
    return $null
}

# Pares origen -> destino que el espejo debe contener.
$pairs = @()

$skillsSource = Join-Path $source 'skills'
if (Test-Path $skillsSource) {
    foreach ($file in Get-ChildItem -LiteralPath $skillsSource -Recurse -File) {
        $relative = $file.FullName.Substring($skillsSource.Length).TrimStart('\', '/')
        $pairs += [pscustomobject]@{
            Source = $file.FullName
            Target = Join-Path (Join-Path $target 'skills') $relative
        }
    }
}

$agentsSource = Join-Path $source 'agents'
if (Test-Path $agentsSource) {
    foreach ($file in Get-ChildItem -LiteralPath $agentsSource -File -Filter '*.agent.md') {
        $name = $file.Name -replace '\.agent\.md$', '.md'
        $pairs += [pscustomobject]@{
            Source = $file.FullName
            Target = Join-Path (Join-Path $target 'agents') $name
        }
    }
}

if ($pairs.Count -eq 0) {
    throw "No se encontro nada que espejar en $source (skills/ y agents/ estan vacios)."
}

$expected = @{}
foreach ($pair in $pairs) { $expected[$pair.Target] = $true }

# Archivos que sobran en el espejo: existen en .claude pero ya no en .agents.
$stale = @()
foreach ($dir in @('skills', 'agents')) {
    $mirrorDir = Join-Path $target $dir
    if (Test-Path $mirrorDir) {
        foreach ($file in Get-ChildItem -LiteralPath $mirrorDir -Recurse -File) {
            if (-not $expected.ContainsKey($file.FullName)) { $stale += $file.FullName }
        }
    }
}

$missing = @()
$changed = @()
foreach ($pair in $pairs) {
    $targetHash = Get-FileHashOrNull -Path $pair.Target
    if ($null -eq $targetHash) {
        $missing += $pair
    } elseif ($targetHash -ne (Get-FileHash -LiteralPath $pair.Source -Algorithm SHA256).Hash) {
        $changed += $pair
    }
}

if ($Check) {
    $drift = $missing.Count + $changed.Count + $stale.Count
    if ($drift -eq 0) {
        Write-Host "Espejo alineado: $($pairs.Count) archivos." -ForegroundColor Green
        exit 0
    }
    Write-Host "Espejo desalineado." -ForegroundColor Yellow
    foreach ($item in $missing) { Write-Host "  falta:   $($item.Target)" }
    foreach ($item in $changed) { Write-Host "  difiere: $($item.Target)" }
    foreach ($item in $stale)   { Write-Host "  sobra:   $item" }
    Write-Host "Corre el script sin -Check para regenerarlo."
    exit 1
}

foreach ($item in $stale) {
    Remove-Item -LiteralPath $item -Force
    Write-Host "eliminado $item"
}

foreach ($pair in ($missing + $changed)) {
    $dir = Split-Path -Parent $pair.Target
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Copy-Item -LiteralPath $pair.Source -Destination $pair.Target -Force
    Write-Host "copiado  $($pair.Target)"
}

# Directorios que quedaron vacios tras eliminar archivos sobrantes.
foreach ($dir in @('skills', 'agents')) {
    $mirrorDir = Join-Path $target $dir
    if (Test-Path $mirrorDir) {
        Get-ChildItem -LiteralPath $mirrorDir -Recurse -Directory |
            Sort-Object { $_.FullName.Length } -Descending |
            Where-Object { -not (Get-ChildItem -LiteralPath $_.FullName -Recurse -File) } |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force }
    }
}

$sinCambios = $pairs.Count - $missing.Count - $changed.Count
Write-Host ""
Write-Host "Espejo regenerado: $($pairs.Count) archivos ($sinCambios sin cambios, $($missing.Count) nuevos, $($changed.Count) actualizados, $($stale.Count) eliminados)." -ForegroundColor Green
