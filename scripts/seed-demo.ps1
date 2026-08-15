# Llena la base con un restaurante de prueba, su personal, su carta y
# comandas en todos los estados del ciclo de servicio.
#
# Siembra contra la API y no contra la base a proposito: asi los folios, el
# congelado de precios y los limites de plan los aplica el sistema, no el
# script. Sembrar con INSERT produciria datos que el sistema nunca habria
# aceptado.
#
# Uso:
#   .\scripts\seed-demo.ps1
#   .\scripts\seed-demo.ps1 -Slug otro -BaseUrl http://localhost:8080

param(
    [string] $BaseUrl  = 'http://localhost:8080',
    [string] $Slug     = 'centro',
    [string] $Password = 'Callejon9Demo!',
    [string] $PlanCode = 'PRO'
)

$ErrorActionPreference = 'Stop'
$session = $null

function Invoke-Api {
    param(
        [Parameter(Mandatory)] [string] $Method,
        [Parameter(Mandatory)] [string] $Path,
        [object] $Body
    )

    $params = @{
        Method      = $Method
        Uri         = "$BaseUrl$Path"
        ContentType = 'application/json; charset=utf-8'
    }

    if ($Body) {
        $json = $Body | ConvertTo-Json -Depth 8 -Compress
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    if ($session) { $params.WebSession = $session } else { $params.SessionVariable = 'newSession' }

    $result = Invoke-RestMethod @params
    if (-not $session) { $script:session = $newSession }
    return $result
}

function Step($text) { Write-Host "  $text" -ForegroundColor DarkGray }

Write-Host "Sembrando '$Slug' en $BaseUrl" -ForegroundColor Cyan

# --- Restaurante y administrador -------------------------------------------
Step 'alta del restaurante'
$admin = "admin@$Slug.com"
try {
    Invoke-Api POST '/api/v1/signup' @{
        restaurantName = "Callejon 9 $Slug"
        slug           = $Slug
        adminEmail     = $admin
        adminFullName  = 'Administrador de sucursal'
        password       = $Password
        planCode       = $PlanCode
    } | Out-Null
} catch {
    Write-Host "    ya existia, se reutiliza" -ForegroundColor DarkYellow
}

Step 'sesion como administrador'
Invoke-Api POST '/api/v1/auth/login' @{ slug = $Slug; email = $admin; password = $Password } | Out-Null

# --- Personal ---------------------------------------------------------------
Step 'personal por rol'
$staff = @(
    @{ email = "mesero@$Slug.com";  fullName = 'Mesero de piso';   role = 'WAITER'  },
    @{ email = "cocina@$Slug.com";  fullName = 'Jefe de cocina';   role = 'KITCHEN' },
    @{ email = "caja@$Slug.com";    fullName = 'Cajero de turno';  role = 'CASHIER' }
)
foreach ($s in $staff) {
    try { Invoke-Api POST '/api/v1/users' ($s + @{ password = $Password }) | Out-Null } catch { }
}

# --- Carta ------------------------------------------------------------------
Step 'categorias y productos'
$carta = [ordered]@{
    'Entradas'       = @(@{n='Guacamole con totopos'; p=95},  @{n='Queso fundido'; p=110}, @{n='Sopa de tortilla'; p=85})
    'Platos fuertes' = @(@{n='Arrachera 300 g'; p=320}, @{n='Enchiladas de mole'; p=185}, @{n='Pescado a la talla'; p=290}, @{n='Chiles en nogada'; p=265})
    'Bebidas'        = @(@{n='Agua de horchata'; p=45}, @{n='Cerveza artesanal'; p=85}, @{n='Margarita'; p=135})
    'Postres'        = @(@{n='Flan napolitano'; p=75}, @{n='Pastel de tres leches'; p=90})
}

$productos = @()
$orden = 0
foreach ($nombre in $carta.Keys) {
    $cat = Invoke-Api POST '/api/v1/categories' @{ name = $nombre; sortOrder = $orden }
    $orden++
    foreach ($p in $carta[$nombre]) {
        $productos += Invoke-Api POST '/api/v1/products' @{
            name        = $p.n
            description = "$nombre - $($p.n)"
            price       = $p.p
            categoryId  = $cat.id
        }
    }
}
Step "  $($productos.Count) productos en $($carta.Keys.Count) categorias"

# --- Mesas ------------------------------------------------------------------
Step 'mesas'
$mesas = @()
foreach ($n in 1..12) {
    $capacidad = if ($n -le 6) { 4 } elseif ($n -le 10) { 6 } else { 8 }
    $mesas += Invoke-Api POST '/api/v1/tables' @{ number = $n; capacity = $capacidad }
}

# --- Comandas ---------------------------------------------------------------
function New-Comanda {
    param($mesa, [int] $comensales, [int] $cuantosProductos)

    $orden = Invoke-Api POST '/api/v1/orders' @{ tableId = $mesa.id; guestCount = $comensales }
    $elegidos = $productos | Get-Random -Count $cuantosProductos
    $items = @($elegidos | ForEach-Object {
        @{ productId = $_.id; quantity = (Get-Random -Minimum 1 -Maximum 3); notes = $null }
    })
    Invoke-Api POST "/api/v1/orders/$($orden.id)/items" @{ items = $items } | Out-Null
    return $orden
}

Step 'comandas cobradas (historial de ventas)'
$pagos = @('CASH','CARD','TRANSFER')
foreach ($i in 0..4) {
    $o = New-Comanda $mesas[$i] (Get-Random -Minimum 2 -Maximum 5) (Get-Random -Minimum 2 -Maximum 5)
    Invoke-Api POST "/api/v1/orders/$($o.id)/send-to-kitchen" | Out-Null
    Invoke-Api POST "/api/v1/orders/$($o.id)/checkout" @{
        paymentMethod = $pagos[$i % $pagos.Count]
        tipPercent    = @(0, 10, 15)[$i % 3]
    } | Out-Null
}

Step 'comandas en cocina (tablero)'
foreach ($i in 5..8) {
    $o = New-Comanda $mesas[$i] (Get-Random -Minimum 2 -Maximum 6) (Get-Random -Minimum 3 -Maximum 6)
    Invoke-Api POST "/api/v1/orders/$($o.id)/send-to-kitchen" | Out-Null
}

Step 'avance parcial en cocina'
$enCocina = Invoke-Api GET '/api/v1/kitchen/orders'
$avanzados = 0
foreach ($o in $enCocina) {
    $items = @($o.items)
    for ($j = 0; $j -lt $items.Count; $j++) {
        # Deja cada comanda con una mezcla de estados: el tablero se ve real
        # solo si conviven pendientes, en preparacion y listos.
        if ($j % 3 -eq 1) {
            Invoke-Api POST "/api/v1/kitchen/items/$($items[$j].id)/status" @{ status = 'IN_PREPARATION' } | Out-Null
            $avanzados++
        } elseif ($j % 3 -eq 2) {
            Invoke-Api POST "/api/v1/kitchen/items/$($items[$j].id)/status" @{ status = 'IN_PREPARATION' } | Out-Null
            Invoke-Api POST "/api/v1/kitchen/items/$($items[$j].id)/status" @{ status = 'READY' } | Out-Null
            $avanzados++
        }
    }
}
Step "  $avanzados productos avanzados"

Step 'comandas abiertas (pantalla de mesero)'
foreach ($i in 9..10) {
    New-Comanda $mesas[$i] (Get-Random -Minimum 2 -Maximum 4) (Get-Random -Minimum 1 -Maximum 3) | Out-Null
}

Step 'una comanda cancelada'
$cancelada = New-Comanda $mesas[11] 2 2
Invoke-Api POST "/api/v1/orders/$($cancelada.id)/cancel" | Out-Null

# --- Resumen ----------------------------------------------------------------
# /sales responde {"sales":[...]}, no un arreglo suelto: hay que entrar al
# campo o se cuenta el objeto envolvente como un solo elemento.
$ventas   = (Invoke-Api GET '/api/v1/sales').sales
$ordenes  = Invoke-Api GET '/api/v1/orders'
$cocina   = Invoke-Api GET '/api/v1/kitchen/orders'
$porEstado = $ordenes | Group-Object status | ForEach-Object { "$($_.Name)=$($_.Count)" }

Write-Host ""
Write-Host "Listo." -ForegroundColor Green
Write-Host "  restaurante : $Slug"
Write-Host "  admin       : $admin / $Password"
Write-Host "  mesas       : $($mesas.Count)"
Write-Host "  productos   : $($productos.Count)"
Write-Host "  comandas    : $(@($ordenes).Count)  ($($porEstado -join ', '))"
Write-Host "  en cocina   : $(@($cocina).Count)"
Write-Host "  ventas      : $(@($ventas).Count)"
Write-Host ""
Write-Host "El semaforo de cocina nace en verde: mide contra sentToKitchenAt y" -ForegroundColor DarkYellow
Write-Host "estas comandas acaban de enviarse. Para verlo en ambar y rojo hay que" -ForegroundColor DarkYellow
Write-Host "envejecerlas en la base; ver scripts\age-kitchen-orders.sql." -ForegroundColor DarkYellow
