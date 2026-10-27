# Envía varias altas iguales por el proxy del frontend publicado (CAL-134, C02 paso 2)
# y guarda cada respuesta literal de curl -i con su hora.
#
# La contraseña solo se lee de la variable de entorno C9_SIT_ALTA_PASSWORD; el
# cuerpo JSON se escribe en un archivo temporal fuera del repositorio y se borra
# al terminar. En el archivo de salida la contraseña aparece como <redactado>.
#
# Uso (Windows PowerShell 5.1 o posterior):
#   $env:C9_SIT_ALTA_PASSWORD = "<contraseña del administrador de C01>"
#   .\altas-repetidas.ps1 -Slug sit-alta-10211930 -Email admin@sit-alta-10211930.test -Plan FREE -Salida respuestas\C02-altas-2-a-5.txt

param(
    [Parameter(Mandatory = $true)][string]$Slug,
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$Restaurante = "SIT Alta Braulio",
    [string]$Administrador = "Admin SIT",
    [string]$Plan = "FREE",
    [int]$Veces = 4,
    [int]$PrimeraAlta = 2,
    [string]$Url = "https://restohub.azurewebsites.net/api/v1/signup",
    [Parameter(Mandatory = $true)][string]$Salida
)

# curl.exe escribe UTF-8; sin esto, Windows PowerShell 5.1 lo decodifica con la página de códigos de la consola.
[Console]::OutputEncoding = New-Object Text.UTF8Encoding $false

$password = $env:C9_SIT_ALTA_PASSWORD
if (-not $password) { throw "Falta la variable de entorno C9_SIT_ALTA_PASSWORD." }

$body = [ordered]@{
    restaurantName = $Restaurante
    slug           = $Slug
    adminEmail     = $Email
    adminFullName  = $Administrador
    password       = $password
    planCode       = $Plan
}
$shown = [ordered]@{}
foreach ($k in $body.Keys) { $shown[$k] = $body[$k] }
$shown.password = "<redactado>"
# Windows PowerShell 5.1 escapa < y > como < y >; se restauran solo en la línea mostrada.
$shownJson = ($shown | ConvertTo-Json -Compress) -replace '\\u003c', '<' -replace '\\u003e', '>'

$tmp = Join-Path $env:TEMP ("alta-" + [guid]::NewGuid().ToString("N") + ".json")
[IO.File]::WriteAllText($tmp, ($body | ConvertTo-Json -Compress), (New-Object Text.UTF8Encoding $false))

$lines = New-Object System.Collections.Generic.List[string]
try {
    for ($i = 0; $i -lt $Veces; $i++) {
        $utc = [DateTime]::UtcNow
        $mx = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId($utc, 'Central Standard Time (Mexico)')
        if ($i -gt 0) { $lines.Add("") }
        $lines.Add("# Alta $($PrimeraAlta + $i) - $($mx.ToString('yyyy-MM-dd HH:mm:ss')) America/Mexico_City ($($utc.ToString('HH:mm:ss')) UTC)")
        $lines.Add("`$ curl -i -X POST $Url -H `"Content-Type: application/json`" -d $shownJson")
        $out = curl.exe -s -i -X POST $Url -H "Content-Type: application/json" -d "@$tmp"
        foreach ($l in $out) { $lines.Add($l.TrimEnd("`r")) }
        $status = ($out | Select-Object -First 1)
        Write-Host "Alta $($PrimeraAlta + $i) - $($mx.ToString('HH:mm:ss')) - $status"
    }
}
finally {
    Remove-Item $tmp -ErrorAction SilentlyContinue
}

[IO.File]::WriteAllText((Resolve-Path -LiteralPath (Split-Path $Salida -Parent)).Path + "\" + (Split-Path $Salida -Leaf),
    ($lines -join "`n") + "`n", (New-Object Text.UTF8Encoding $false))
Write-Host "Guardado en $Salida"
