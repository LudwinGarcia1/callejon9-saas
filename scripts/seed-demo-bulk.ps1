# Datos ficticios para una demostracion local, mediante la API y sus reglas.
# Requiere un slug nuevo. Un error detiene la carga y conserva lo ya creado.
param(
    [string] $BaseUrl = 'http://localhost:8080',
    [ValidatePattern('^[a-z0-9-]+$')] [string] $Slug = 'demo10',
    [string] $Password = $env:DEMO_PASSWORD,
    [ValidateRange(3, 60)] [int] $Count = 10
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw 'Define DEMO_PASSWORD o proporciona -Password antes de ejecutar.'
}
if (-not ([uri]$BaseUrl).IsLoopback) {
    throw 'Este script solo permite una API local.'
}
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Invoke-Api {
    param([string] $Method, [string] $Path, [object] $Body)
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        WebSession = $session
        ContentType = 'application/json; charset=utf-8'
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress))
    }
    try { Invoke-RestMethod @parameters }
    catch { throw "Fallo $Method $Path. La carga se detuvo; revisa el estado del restaurante antes de repetir. $($_.Exception.Message)" }
}

$adminEmail = "admin@$Slug.example"
$planCode = if ($Count -le 15) { 'PRO' } else { 'PREMIUM' }
Write-Host "Creando restaurante $Slug con plan $planCode..."
# No se captura el error de alta: un slug existente no autoriza modificar sus datos.
Invoke-Api POST '/api/v1/signup' @{
    restaurantName = "Restaurante de prueba $Slug"; slug = $Slug
    adminEmail = $adminEmail; adminFullName = 'Administrador de prueba'
    password = $Password; planCode = $planCode
} | Out-Null
Invoke-Api POST '/api/v1/auth/login' @{
    slug = $Slug; email = $adminEmail; password = $Password
} | Out-Null

Write-Host "Creando $Count usuarios, categorias, productos, mesas e insumos..."
$roles = @('WAITER', 'KITCHEN', 'CASHIER', 'ADMIN')
for ($i = 1; $i -lt $Count; $i++) {
    Invoke-Api POST '/api/v1/users' @{
        email = "personal$i@$Slug.example"; fullName = "Personal de prueba $i"
        role = $roles[($i - 1) % $roles.Count]; password = $Password
    } | Out-Null
}
$categoryNames = @('Entradas', 'Sopas', 'Ensaladas', 'Tacos', 'Antojitos', 'Enchiladas',
    'Carnes', 'Pollo', 'Pescados', 'Mariscos', 'Pastas', 'Arroces', 'Vegetarianos',
    'Veganos', 'Desayunos', 'Huevos', 'Chilaquiles', 'Tortas', 'Hamburguesas',
    'Pizzas', 'Guarniciones', 'Salsas', 'Panaderia', 'Postres', 'Helados',
    'Aguas frescas', 'Refrescos', 'Jugos', 'Cafe', 'Te')
$productNames = @('Guacamole', 'Sopa de tortilla', 'Ensalada de nopales', 'Tacos al pastor',
    'Sopes', 'Enchiladas verdes', 'Arrachera', 'Pollo al limon', 'Pescado a la plancha',
    'Camarones al ajo', 'Pasta al pesto', 'Arroz rojo', 'Chile relleno', 'Bowl de garbanzos',
    'Hot cakes', 'Huevos rancheros', 'Chilaquiles rojos', 'Torta de milanesa',
    'Hamburguesa clasica', 'Pizza margarita', 'Papas al horno', 'Salsa de habanero',
    'Pan de elote', 'Flan', 'Helado de vainilla', 'Agua de jamaica', 'Refresco de naranja',
    'Jugo de naranja', 'Cafe de olla', 'Te de manzanilla')
$ingredientNames = @('Aguacate', 'Tortilla', 'Nopal', 'Cerdo', 'Masa', 'Tomatillo',
    'Res', 'Pollo', 'Pescado', 'Camaron', 'Pasta', 'Arroz', 'Chile poblano', 'Garbanzo',
    'Harina', 'Huevo', 'Jitomate', 'Pan para torta', 'Pan para hamburguesa', 'Queso',
    'Papa', 'Habanero', 'Elote', 'Leche', 'Vainilla', 'Jamaica', 'Refresco', 'Naranja',
    'Cafe molido', 'Manzanilla')
$products = @()
$tables = @()
for ($i = 0; $i -lt $Count; $i++) {
    $suffix = if ($i -ge 30) { " $($i + 1)" } else { '' }
    $category = Invoke-Api POST '/api/v1/categories' @{
        name = $categoryNames[$i % 30] + $suffix; sortOrder = $i
    }
    $products += Invoke-Api POST '/api/v1/products' @{
        name = $productNames[$i % 30] + $suffix; description = 'Producto ficticio para demostracion'
        categoryId = $category.id; price = 35 + ($i % 12) * 20
    }
    $tables += Invoke-Api POST '/api/v1/tables' @{ number = $i + 1; capacity = 4 + ($i % 3) * 2 }
    # El alta con existencias crea tambien un movimiento IN auditable.
    Invoke-Api POST '/api/v1/inventory/items' @{
        name = $ingredientNames[$i % 30] + $suffix; unit = 'kg'
        initialStock = 5 + ($i % 5) * 10; minStock = 10; unitCost = 15 + $i * 3
    } | Out-Null
}

function New-DemoOrder([object] $Table, [int] $Index) {
    $order = Invoke-Api POST '/api/v1/orders' @{ tableId = $Table.id; guestCount = 2 }
    $items = @(0..2 | ForEach-Object {
        @{ productId = $products[($Index + $_) % $Count].id; quantity = 1 + ($_ % 2) }
    })
    Invoke-Api POST "/api/v1/orders/$($order.id)/items" @{ items = $items } | Out-Null
    return $order
}

Write-Host "Creando $Count ventas y tickets..."
$ticketIds = @()
for ($i = 0; $i -lt $Count; $i++) {
    $order = New-DemoOrder $tables[$i] $i
    Invoke-Api POST "/api/v1/orders/$($order.id)/send-to-kitchen" | Out-Null
    $ticket = Invoke-Api POST "/api/v1/orders/$($order.id)/checkout" @{
        paymentMethod = @('CASH', 'CARD', 'TRANSFER')[$i % 3]
        tipPercent = @(0, 10, 15)[$i % 3]
    }
    $ticketIds += $ticket.id
}
# Una cancelacion permite revisar ese estado sin ocupar una mesa adicional.
$canceledOrder = New-DemoOrder $tables[0] 0
Invoke-Api POST "/api/v1/orders/$($canceledOrder.id)/cancel" | Out-Null

Write-Host "Creando $Count comandas para cocina..."
for ($i = 0; $i -lt $Count; $i++) {
    $order = New-DemoOrder $tables[$i] $i
    Invoke-Api POST "/api/v1/orders/$($order.id)/send-to-kitchen" | Out-Null
    $detail = Invoke-Api GET "/api/v1/orders/$($order.id)"
    Invoke-Api POST "/api/v1/kitchen/items/$($detail.items[1].id)/status" @{ status = 'IN_PREPARATION' } | Out-Null
    Invoke-Api POST "/api/v1/kitchen/items/$($detail.items[2].id)/status" @{ status = 'IN_PREPARATION' } | Out-Null
    Invoke-Api POST "/api/v1/kitchen/items/$($detail.items[2].id)/status" @{ status = 'READY' } | Out-Null
}

Write-Host 'Verificando los registros desde la API...'
$summary = [ordered]@{ restaurant = $Slug; adminEmail = $adminEmail }
foreach ($section in @('users', 'categories', 'products', 'tables', 'inventory/items', 'inventory/movements', 'kitchen/orders')) {
    $actual = @(Invoke-Api GET "/api/v1/$section").Count
    if ($actual -ne $Count) { throw "Verificacion de ${section}: esperados $Count, encontrados $actual." }
    $summary[$section] = $actual
}
$sales = (Invoke-Api GET '/api/v1/sales').sales
if (@($sales).Count -ne $Count) { throw 'La cantidad de ventas no coincide.' }
$summary['sales'] = @($sales).Count
foreach ($ticketId in $ticketIds) {
    $verifiedTicket = Invoke-Api GET "/api/v1/tickets/$ticketId"
    if ($verifiedTicket.id -ne $ticketId) { throw 'No se pudo verificar un ticket.' }
}
$summary['tickets'] = $ticketIds.Count
$orders = @(Invoke-Api GET '/api/v1/orders')
if ($orders.Count -ne (2 * $Count + 1)) { throw 'La cantidad de comandas no coincide.' }
$summary['orders'] = $orders.Count
$summary | ConvertTo-Json
