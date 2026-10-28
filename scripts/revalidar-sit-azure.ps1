# scripts/revalidar-sit-azure.ps1
# Script para re-ejecutar y capturar las respuestas reales con curl.exe contra Azure
# para las 7 pruebas SIT devueltas a In Progress (CAL-145, 136, 137, 141, 142, 143, 144).

[Console]::OutputEncoding = New-Object Text.UTF8Encoding $false
$ErrorActionPreference = "Stop"

$FrontUrl  = "https://restohub.azurewebsites.net"
$ApiUrl    = "https://app-callejon9-api-prod-3878c6.azurewebsites.net"
$Slug      = "sit-val-10281430"
$AdminPass = "Password123!Val"
$RepoRoot  = "C:\Users\pingu\callejon9-saas"

$RunSuffix = (Get-Date).ToString("yyyyMMddHHmmss")

$TempDir = Join-Path $env:TEMP "sit-azure-run"
if (Test-Path $TempDir) { Remove-Item -Recurse -Force $TempDir }
New-Item -ItemType Directory -Path $TempDir | Out-Null

$JarAdmin   = Join-Path $TempDir "jar-admin.txt"
$JarMesero  = Join-Path $TempDir "jar-mesero.txt"
$JarCaja    = Join-Path $TempDir "jar-caja.txt"

function Write-Http-Evidence {
    param(
        [string]$FilePath,
        [string]$Description,
        [string]$Method,
        [string]$Url,
        [string]$CookieJar,
        [string]$JsonBody,
        [string]$ExtraHeader
    )

    $fullPath = if ([System.IO.Path]::IsPathRooted($FilePath)) { $FilePath } else { Join-Path $RepoRoot $FilePath }
    $parent = Split-Path $fullPath -Parent
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

    $utc = [DateTime]::UtcNow
    $mx = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId($utc, 'Central Standard Time (Mexico)')
    
    $headerLines = New-Object System.Collections.Generic.List[string]
    $headerLines.Add("# $Description")
    $headerLines.Add("# Fecha: $($mx.ToString('yyyy-MM-dd HH:mm:ss')) America/Mexico_City ($($utc.ToString('HH:mm:ss')) UTC)")
    
    $maskedBody = $JsonBody
    if ($maskedBody) {
        $maskedBody = $maskedBody -replace '"password":\s*"[^"]*"', '"password":"<redactado>"'
    }

    $curlCmd = "`$ curl.exe -i -s -X $Method $Url"
    if ($CookieJar) { $curlCmd += " -b <cookies>" }
    if ($ExtraHeader) { $curlCmd += " -H `"$ExtraHeader`"" }
    if ($maskedBody) { $curlCmd += " -H `"Content-Type: application/json`" -d '$maskedBody'" }
    $headerLines.Add($curlCmd)
    $headerLines.Add("")

    $tmpJson = $null
    $argsList = New-Object System.Collections.Generic.List[string]
    $argsList.Add("-i")
    $argsList.Add("-s")
    $argsList.Add("-X")
    $argsList.Add($Method)
    $argsList.Add($Url)

    if ($CookieJar) {
        $argsList.Add("-b")
        $argsList.Add($CookieJar)
    }

    if ($ExtraHeader) {
        $argsList.Add("-H")
        $argsList.Add($ExtraHeader)
    }

    if ($JsonBody) {
        $tmpJson = Join-Path $TempDir ("payload-" + [guid]::NewGuid().ToString("N") + ".json")
        [IO.File]::WriteAllText($tmpJson, $JsonBody, (New-Object Text.UTF8Encoding $false))
        $argsList.Add("-H")
        $argsList.Add("Content-Type: application/json")
        $argsList.Add("-d")
        $argsList.Add("@$tmpJson")
    }

    $rawOutput = & curl.exe @argsList
    if ($tmpJson -and (Test-Path $tmpJson)) { Remove-Item $tmpJson -ErrorAction SilentlyContinue }

    # Mask any session token in headers and collect body
    $cleanOutput = New-Object System.Collections.Generic.List[string]
    $inBody = $false
    $bodyLines = New-Object System.Collections.Generic.List[string]

    foreach ($line in $rawOutput) {
        $l = $line.TrimEnd("`r")
        if ($inBody) {
            $bodyLines.Add($l)
        } elseif ($l -eq "") {
            $inBody = $true
        }

        if ($l -match "Set-Cookie:\s*(access_token|refresh_token)=([^;]+)(.*)") {
            $l = "Set-Cookie: " + $Matches[1] + "=<redactado>" + $Matches[3]
        }
        $cleanOutput.Add($l)
    }

    $allLines = ($headerLines + $cleanOutput) -join "`n"
    [IO.File]::WriteAllText($fullPath, $allLines + "`n", (New-Object Text.UTF8Encoding $false))
    Write-Host "-> Guardado: $fullPath" -ForegroundColor Cyan

    $bodyText = $bodyLines -join "`n"
    $parsedObj = $null
    if ($bodyText) {
        try { $parsedObj = $bodyText | ConvertFrom-Json } catch { }
    }
    return $parsedObj
}

Write-Host "=== 1. INICIAR SESIONES EN AZURE ===" -ForegroundColor Yellow
$adminLogin = (@{ slug = $Slug; email = "admin@sitval1028.test"; password = $AdminPass } | ConvertTo-Json -Compress)
$tmp = Join-Path $TempDir "admin-login.json"
[IO.File]::WriteAllText($tmp, $adminLogin, (New-Object Text.UTF8Encoding $false))
curl.exe -s -c $JarAdmin -X POST "$FrontUrl/api/v1/auth/login" -H "Content-Type: application/json" -d "@$tmp" | Out-Null

$meseroLogin = (@{ slug = $Slug; email = "mesero@sitval1028.test"; password = $AdminPass } | ConvertTo-Json -Compress)
[IO.File]::WriteAllText($tmp, $meseroLogin, (New-Object Text.UTF8Encoding $false))
curl.exe -s -c $JarMesero -X POST "$FrontUrl/api/v1/auth/login" -H "Content-Type: application/json" -d "@$tmp" | Out-Null

$cajaLogin = (@{ slug = $Slug; email = "caja@sitval1028.test"; password = $AdminPass } | ConvertTo-Json -Compress)
[IO.File]::WriteAllText($tmp, $cajaLogin, (New-Object Text.UTF8Encoding $false))
curl.exe -s -c $JarCaja -X POST "$FrontUrl/api/v1/auth/login" -H "Content-Type: application/json" -d "@$tmp" | Out-Null
Remove-Item $tmp -ErrorAction SilentlyContinue

Write-Host "=== 2. EJECUTAR CASOS DE CAL-145 (Aislamiento y Seguridad Transversal) ===" -ForegroundColor Yellow
$p145 = "docs/pruebas/sit/CAL-145-aislamiento-seguridad-transversal/respuestas"
Write-Http-Evidence -FilePath "$p145/C01-health-backend.txt" -Description "C01: Sonda /actuator/health directa en Azure App Service" -Method "GET" -Url "$ApiUrl/actuator/health" | Out-Null
Write-Http-Evidence -FilePath "$p145/C01-health-liveness.txt" -Description "C01: Sonda /actuator/health/liveness en Azure App Service" -Method "GET" -Url "$ApiUrl/actuator/health/liveness" | Out-Null
Write-Http-Evidence -FilePath "$p145/C01-health-readiness.txt" -Description "C01: Sonda /actuator/health/readiness en Azure App Service" -Method "GET" -Url "$ApiUrl/actuator/health/readiness" | Out-Null
# C02: RLS Isolation
$foreignOrder = "00000000-0000-0000-0000-000000000999"
Write-Http-Evidence -FilePath "$p145/C02-rls-cross-tenant-order-404.txt" -Description "C02: Intento de consulta de orden ajena (404 por aislamiento RLS en PostgreSQL)" -Method "GET" -Url "$FrontUrl/api/v1/orders/$foreignOrder" -CookieJar $JarAdmin | Out-Null
# C03: Security headers
Write-Http-Evidence -FilePath "$p145/C03-security-headers-frontend.txt" -Description "C03: Cabeceras de seguridad en Frontend publicado" -Method "GET" -Url "$FrontUrl" | Out-Null
Write-Http-Evidence -FilePath "$p145/C03-security-headers-api.txt" -Description "C03: Cabeceras de seguridad en API Spring Boot" -Method "GET" -Url "$ApiUrl/actuator/health" | Out-Null
# C04: Swagger UI y OpenAPI en produccion
Write-Http-Evidence -FilePath "$p145/C04-swagger-ui-azure.txt" -Description "C04: Verificacion de endpoint /swagger-ui/index.html en Azure" -Method "GET" -Url "$ApiUrl/swagger-ui/index.html" | Out-Null
Write-Http-Evidence -FilePath "$p145/C04-openapi-docs-azure.txt" -Description "C04: Verificacion de endpoint /v3/api-docs en Azure" -Method "GET" -Url "$ApiUrl/v3/api-docs" | Out-Null

Write-Host "=== 3. EJECUTAR CASOS DE CAL-136 (Catalogo) ===" -ForegroundColor Yellow
$p136 = "docs/pruebas/sit/CAL-136-catalogo/respuestas"
# Crear categoria
$catPayload = (@{ name = "Bebidas $RunSuffix"; sortOrder = 1 } | ConvertTo-Json -Compress)
$tmpCat = Join-Path $TempDir "cat.json"
[IO.File]::WriteAllText($tmpCat, $catPayload, (New-Object Text.UTF8Encoding $false))
$resCat = curl.exe -s -b $JarAdmin -X POST "$FrontUrl/api/v1/categories" -H "Content-Type: application/json" -d "@$tmpCat" | ConvertFrom-Json
$catId = $resCat.id
Remove-Item $tmpCat -ErrorAction SilentlyContinue

# C01: Crear producto activo
$prodPayload = (@{ name = "Agua Fresca $RunSuffix"; description = "Bebida natural de temporada"; price = 45.00; categoryId = $catId } | ConvertTo-Json -Compress)
$prodRes = Write-Http-Evidence -FilePath "$p136/C01-crear-producto.txt" -Description "C01: Creacion de producto activo por Administrador" -Method "POST" -Url "$FrontUrl/api/v1/products" -CookieJar $JarAdmin -JsonBody $prodPayload
$prodId = $prodRes.id

Write-Http-Evidence -FilePath "$p136/C01-catalogo-activo.txt" -Description "C01: Consulta de catalogo activo visible para meseros" -Method "GET" -Url "$FrontUrl/api/v1/products?categoryId=$catId" -CookieJar $JarMesero | Out-Null

# C02: Desactivar producto
$patchPayload = '{"active":false}'
Write-Http-Evidence -FilePath "$p136/C02-desactivar-producto.txt" -Description "C02: Desactivacion de producto por Administrador" -Method "PATCH" -Url "$FrontUrl/api/v1/products/$prodId" -CookieJar $JarAdmin -JsonBody $patchPayload | Out-Null

# C02: Comprobar que ya no aparece en menu activo
Write-Http-Evidence -FilePath "$p136/C02-catalogo-sin-inactivo.txt" -Description "C02: El catalogo activo no incluye el producto desactivado" -Method "GET" -Url "$FrontUrl/api/v1/products?categoryId=$catId" -CookieJar $JarMesero | Out-Null

Write-Host "=== 4. EJECUTAR CASOS DE CAL-137 (Mesas) ===" -ForegroundColor Yellow
$p137 = "docs/pruebas/sit/CAL-137-mesas/respuestas"
# Crear mesa con numero aleatorio unico
$tableNum = (Get-Random -Minimum 10 -Maximum 9999)
$tablePayload = (@{ number = $tableNum; capacity = 4 } | ConvertTo-Json -Compress)
$tmpTbl = Join-Path $TempDir "tbl.json"
[IO.File]::WriteAllText($tmpTbl, $tablePayload, (New-Object Text.UTF8Encoding $false))
$resTbl = curl.exe -s -b $JarAdmin -X POST "$FrontUrl/api/v1/tables" -H "Content-Type: application/json" -d "@$tmpTbl" | ConvertFrom-Json
$tableId = $resTbl.id
Remove-Item $tmpTbl -ErrorAction SilentlyContinue

# C01: Abrir comanda en mesa
$openOrderPayload = (@{ tableId = $tableId; guestCount = 2 } | ConvertTo-Json -Compress)
$ordRes = Write-Http-Evidence -FilePath "$p137/C01-abrir-orden-mesa.txt" -Description "C01: Apertura de orden en mesa $tableNum" -Method "POST" -Url "$FrontUrl/api/v1/orders" -CookieJar $JarMesero -JsonBody $openOrderPayload
$orderId = $ordRes.id

Write-Http-Evidence -FilePath "$p137/C01-mesa-ocupada.txt" -Description "C01: Listado de mesas muestra mesa $tableNum en estado OCCUPIED" -Method "GET" -Url "$FrontUrl/api/v1/tables" -CookieJar $JarMesero | Out-Null

# C02: Conflicto 409 al intentar abrir la misma mesa ocupada
Write-Http-Evidence -FilePath "$p137/C02-conflicto-409-mesa-ocupada.txt" -Description "C02: Intento concurrente de abrir mesa ocupada recibe 409 Conflict" -Method "POST" -Url "$FrontUrl/api/v1/orders" -CookieJar $JarMesero -JsonBody $openOrderPayload | Out-Null

# C02 de CAL-136: Intento de agregar el producto inactivo a la orden abierta -> 409 Conflict
$addInactiveItemPayload = (@{ items = @(@{ productId = $prodId; quantity = 1; notes = "Sin hielo" }) } | ConvertTo-Json -Compress)
Write-Http-Evidence -FilePath "$p136/C02-rechazo-producto-inactivo-comanda.txt" -Description "C02: Rechazo con 409 al agregar producto inactivo a comanda abierta" -Method "POST" -Url "$FrontUrl/api/v1/orders/$orderId/items" -CookieJar $JarMesero -JsonBody $addInactiveItemPayload | Out-Null

# Cancelar la orden de prueba para que la mesa vuelva a libre
Write-Http-Evidence -FilePath "$p137/C03-cancelar-orden.txt" -Description "C03: Cancelacion de orden para liberar la mesa" -Method "POST" -Url "$FrontUrl/api/v1/orders/$orderId/cancel" -CookieJar $JarMesero | Out-Null
Write-Http-Evidence -FilePath "$p137/C03-mesa-liberada.txt" -Description "C03: Comprobacion de que la mesa $tableNum regresa a FREE" -Method "GET" -Url "$FrontUrl/api/v1/tables" -CookieJar $JarMesero | Out-Null

Write-Host "=== 5. EJECUTAR CASOS DE CAL-141 (Cobro y Pagos) ===" -ForegroundColor Yellow
$p141 = "docs/pruebas/sit/CAL-141-cobro-pagos/respuestas"
# Reactivar producto para la venta
$tmpPatch = Join-Path $TempDir "patch-active.json"
[IO.File]::WriteAllText($tmpPatch, '{"active":true}', (New-Object Text.UTF8Encoding $false))
curl.exe -s -b $JarAdmin -X PATCH "$FrontUrl/api/v1/products/$prodId" -H "Content-Type: application/json" -d "@$tmpPatch" | Out-Null
Remove-Item $tmpPatch -ErrorAction SilentlyContinue

# Abrir nueva orden en mesa (ya liberada)
$ordRes2 = Write-Http-Evidence -FilePath "$p141/C01-abrir-orden-checkout.txt" -Description "C01: Apertura de orden para checkout en mesa $tableNum" -Method "POST" -Url "$FrontUrl/api/v1/orders" -CookieJar $JarMesero -JsonBody $openOrderPayload
$order2Id = $ordRes2.id

# Agregar 2 aguas frescas (total = 90.00) usando archivo temporal para JSON intacto
$addItemPayload = (@{ items = @(@{ productId = $prodId; quantity = 2; notes = "Normal" }) } | ConvertTo-Json -Compress)
$tmpItemAdd = Join-Path $TempDir "add-items.json"
[IO.File]::WriteAllText($tmpItemAdd, $addItemPayload, (New-Object Text.UTF8Encoding $false))
curl.exe -s -b $JarMesero -X POST "$FrontUrl/api/v1/orders/$order2Id/items" -H "Content-Type: application/json" -d "@$tmpItemAdd" | Out-Null
Remove-Item $tmpItemAdd -ErrorAction SilentlyContinue

# Enviar comanda a cocina para completar ciclo
curl.exe -s -b $JarMesero -X POST "$FrontUrl/api/v1/orders/$order2Id/send-to-kitchen" | Out-Null

# Abrir turno de caja si no esta abierto
$openCashPayload = '{"openingFund":500.00}'
$tmpCash = Join-Path $TempDir "cash.json"
[IO.File]::WriteAllText($tmpCash, $openCashPayload, (New-Object Text.UTF8Encoding $false))
curl.exe -s -b $JarCaja -X POST "$FrontUrl/api/v1/cash-sessions" -H "Content-Type: application/json" -d "@$tmpCash" | Out-Null
Remove-Item $tmpCash -ErrorAction SilentlyContinue

# C02: Cobro insuficiente (50.00 en vez de 90.00) -> 422 Unprocessable Entity ("Los pagos no cubren el total de la cuenta.")
$insufficientPayload = (@{
    tipPercent = 0.00
    payments   = @(@{ method = "CASH"; amount = 50.00 })
} | ConvertTo-Json -Compress)
Write-Http-Evidence -FilePath "$p141/C02-rechazo-pago-insuficiente.txt" -Description "C02: Rechazo con 422 ante pagos que no cubren el total" -Method "POST" -Url "$FrontUrl/api/v1/orders/$order2Id/checkout" -CookieJar $JarCaja -JsonBody $insufficientPayload | Out-Null

# C01: Cobro con pago dividido y propina 10% (Subtotal 90.00 + propina 9.00 = 99.00 total)
# Pago: Efectivo 50.00 + Tarjeta 49.00
$splitPayload = (@{
    tipPercent = 10.00
    payments   = @(
        @{ method = "CASH"; amount = 50.00 },
        @{ method = "CARD"; amount = 49.00 }
    )
} | ConvertTo-Json -Compress)
$checkRes = Write-Http-Evidence -FilePath "$p141/C01-checkout-pago-dividido.txt" -Description "C01: Checkout exitoso con pago dividido (efectivo + tarjeta) y propina" -Method "POST" -Url "$FrontUrl/api/v1/orders/$order2Id/checkout" -CookieJar $JarCaja -JsonBody $splitPayload
$ticketId = $checkRes.id
$ticketFolio = $checkRes.folio

Write-Http-Evidence -FilePath "$p141/C01-mesa-liberada.txt" -Description "C01: Mesa liberada automaticamente tras el cobro" -Method "GET" -Url "$FrontUrl/api/v1/tables" -CookieJar $JarCaja | Out-Null

# C03: Reintento de cobro en orden ya pagada -> 409 Conflict
Write-Http-Evidence -FilePath "$p141/C03-rechazo-cobro-duplicado.txt" -Description "C03: Rechazo con 409 al intentar cobrar orden ya pagada" -Method "POST" -Url "$FrontUrl/api/v1/orders/$order2Id/checkout" -CookieJar $JarCaja -JsonBody $splitPayload | Out-Null

Write-Host "=== 6. EJECUTAR CASOS DE CAL-142 (Tickets) ===" -ForegroundColor Yellow
$p142 = "docs/pruebas/sit/CAL-142-tickets/respuestas"
Write-Http-Evidence -FilePath "$p142/C01-descarga-ticket-pdf-headers.txt" -Description "C01: Cabeceras de descarga de ticket PDF" -Method "GET" -Url "$FrontUrl/api/v1/tickets/$ticketId/pdf" -CookieJar $JarCaja | Out-Null
# Descarga directa del binario PDF
curl.exe -s -b $JarCaja "$FrontUrl/api/v1/tickets/$ticketId/pdf" -o "$RepoRoot/docs/pruebas/sit/CAL-142-tickets/ticket.pdf"
Write-Host "-> Guardado PDF real: $RepoRoot/docs/pruebas/sit/CAL-142-tickets/ticket.pdf" -ForegroundColor Green

# Consulta por folio existente
Write-Http-Evidence -FilePath "$p142/C01-consulta-ticket-folio.txt" -Description "C01: Consulta de ticket por folio" -Method "GET" -Url "$FrontUrl/api/v1/tickets?folio=$ticketFolio" -CookieJar $JarCaja | Out-Null

# C02: Consulta de ticket inexistente o ajeno -> 404 Not Found
Write-Http-Evidence -FilePath "$p142/C02-ticket-inexistente-404.txt" -Description "C02: Consulta de ticket inexistente responde 404" -Method "GET" -Url "$FrontUrl/api/v1/tickets?folio=TCK-INEXISTENTE-9999" -CookieJar $JarCaja | Out-Null

Write-Host "=== 7. EJECUTAR CASOS DE CAL-143 (Inventario) ===" -ForegroundColor Yellow
$p143 = "docs/pruebas/sit/CAL-143-inventario/respuestas"
# C01: Crear insumo
$itemPayload = (@{
    name         = "Mango Fresco $RunSuffix"
    unit         = "KG"
    minStock     = 5.0
    unitCost     = 25.00
    initialStock = 20.0
} | ConvertTo-Json -Compress)
$itemRes = Write-Http-Evidence -FilePath "$p143/C01-crear-insumo.txt" -Description "C01: Alta de insumo de inventario por Administrador" -Method "POST" -Url "$FrontUrl/api/v1/inventory/items" -CookieJar $JarAdmin -JsonBody $itemPayload
$itemId = $itemRes.id

# C01: Registrar salida de insumo (OUT) de 3.5 kg
$movPayload = (@{
    inventoryItemId = $itemId
    movementType    = "OUT"
    quantity        = 3.5
    reason          = "Preparacion de agua fresca"
} | ConvertTo-Json -Compress)
Write-Http-Evidence -FilePath "$p143/C01-registrar-salida.txt" -Description "C01: Registro de salida de insumo de inventario" -Method "POST" -Url "$FrontUrl/api/v1/inventory/movements" -CookieJar $JarAdmin -JsonBody $movPayload | Out-Null

# Listado de movimientos
Write-Http-Evidence -FilePath "$p143/C01-movimientos-registrados.txt" -Description "C01: Listado de movimientos de inventario" -Method "GET" -Url "$FrontUrl/api/v1/inventory/movements?itemId=$itemId" -CookieJar $JarAdmin | Out-Null

# C02: Desactivar insumo
Write-Http-Evidence -FilePath "$p143/C02-desactivar-insumo.txt" -Description "C02: Desactivacion de insumo de inventario" -Method "PATCH" -Url "$FrontUrl/api/v1/inventory/items/$itemId" -CookieJar $JarAdmin -JsonBody '{"active":false}' | Out-Null

# C02: Intento de movimiento sobre insumo inactivo -> 409 Conflict ("El insumo '...' esta dado de baja. Reactivalo para poder moverlo.")
$mov2Payload = (@{
    inventoryItemId = $itemId
    movementType    = "OUT"
    quantity        = 1.0
    reason          = "Intento en insumo dado de baja"
} | ConvertTo-Json -Compress)
Write-Http-Evidence -FilePath "$p143/C02-rechazo-movimiento-insumo-inactivo.txt" -Description "C02: Rechazo con 409 al registrar movimiento en insumo inactivo" -Method "POST" -Url "$FrontUrl/api/v1/inventory/movements" -CookieJar $JarAdmin -JsonBody $mov2Payload | Out-Null

Write-Host "=== 8. EJECUTAR CASOS DE CAL-144 (Ventas y Analitica) ===" -ForegroundColor Yellow
$p144 = "docs/pruebas/sit/CAL-144-ventas-analitica/respuestas"
# C01: Historial de ventas
Write-Http-Evidence -FilePath "$p144/C01-historial-ventas.txt" -Description "C01: Historial consolidado de ventas del dia" -Method "GET" -Url "$FrontUrl/api/v1/sales" -CookieJar $JarAdmin | Out-Null

# C02: Analítica ejecutiva
Write-Http-Evidence -FilePath "$p144/C02-analitica-ejecutiva.txt" -Description "C02: Analitica ejecutiva (Pareto, ventas diarias y mezcla de pago)" -Method "GET" -Url "$FrontUrl/api/v1/analytics" -CookieJar $JarAdmin | Out-Null

Write-Host "=== EJECUCION SIT FINALIZADA CON EXITO ===" -ForegroundColor Green
