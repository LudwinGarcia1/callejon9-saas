# Extrae cada coleccion en alcance del volcado a JSON por lineas.
# Solo extrae lo que la migracion usa: sacar las 20 colecciones daria la
# impresion de que todas se migran.
param(
    [string] $DumpPath = 'C:\Users\pingu\OneDrive\Escritorio\backups\callejon9',
    [string] $OutPath  = "$PSScriptRoot\out",
    [string] $Bsondump = 'C:\Program Files\MongoDB\Tools\100\bin\bsondump.exe'
)

$ErrorActionPreference = 'Stop'

$collections = @('usuarios','productos','mesas','comandas','insumos','movimientos_inventario')

if (-not (Test-Path $Bsondump)) { throw "No se encontro bsondump en $Bsondump" }
New-Item -ItemType Directory -Force -Path $OutPath | Out-Null

# UTF8Encoding($false) evita el BOM: Set-Content -Encoding utf8 en Windows
# PowerShell 5.1 siempre lo agrega, y psql rechaza esa marca como JSON invalido
# en la primera linea del archivo.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

foreach ($c in $collections) {
    $src = Join-Path $DumpPath "$c.bson"
    if (-not (Test-Path $src)) { throw "Falta la coleccion $c en $DumpPath" }

    $dst = Join-Path $OutPath "$c.jsonl"
    $lines = & $Bsondump --quiet $src
    [System.IO.File]::WriteAllLines($dst, $lines, $utf8NoBom)
    $n = (Get-Content $dst | Measure-Object -Line).Lines
    Write-Host ("{0,-24} {1,5} documentos" -f $c, $n)
}
