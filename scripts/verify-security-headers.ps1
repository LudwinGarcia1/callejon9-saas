param(
    [string] $FrontendOrigin = 'http://localhost:3001',
    [string] $BackendOrigin = 'http://localhost:8080',
    [switch] $ExpectFrontendHsts
)

$ErrorActionPreference = 'Stop'

function Assert-Header {
    param($Response, [string] $Name, [string] $Expected)
    if ($Response.Headers[$Name] -ne $Expected) {
        throw "Cabecera $Name incorrecta: se esperaba $Expected."
    }
}

function Assert-CommonHeaders {
    param($Response)
    Assert-Header $Response 'X-Frame-Options' 'DENY'
    Assert-Header $Response 'X-Content-Type-Options' 'nosniff'
    Assert-Header $Response 'Referrer-Policy' 'strict-origin-when-cross-origin'
    if ($Response.Headers['Content-Security-Policy'] -notmatch "frame-ancestors 'none'") {
        throw 'CSP no bloquea la inclusion de la pagina en iframes.'
    }
}

$previousNonce = $null
foreach ($route in @('/login', '/signup', '/login')) {
    $response = Invoke-WebRequest -Uri ($FrontendOrigin.TrimEnd('/') + $route) -UseBasicParsing
    if ($response.StatusCode -ne 200) { throw "La ruta $route no devuelve HTTP 200." }
    Assert-CommonHeaders $response
    $csp = [string] $response.Headers['Content-Security-Policy']
    if ($csp -notmatch "'nonce-([A-Za-z0-9+/=]+)'") { throw "Falta nonce en $route." }
    $pageNonce = $Matches[1]
    if ($pageNonce -eq $previousNonce) { throw 'El nonce se repitio entre respuestas.' }
    $previousNonce = $pageNonce
    if ($csp -match "script-src[^;]*'unsafe-inline'") { throw 'CSP permite scripts inline sin nonce.' }
    $scriptTags = [regex]::Matches($response.Content, '<script\b[^>]*>', 'IgnoreCase')
    if ($scriptTags.Count -eq 0) { throw 'No se encontraron scripts de Next para verificar.' }
    foreach ($scriptTag in $scriptTags) {
        if ($scriptTag.Value -notmatch ('nonce="' + [regex]::Escape($pageNonce) + '"')) {
            throw "Un script en $route no tiene el nonce de su CSP."
        }
    }
    if ($ExpectFrontendHsts) {
        Assert-Header $response 'Strict-Transport-Security' 'max-age=31536000'
    }
    Write-Output "PASS frontend $route : HTTP 200, cabeceras y nonce de scripts"
}

# Comprueba el origen API y el mismo recurso a traves del rewrite de Next.
foreach ($origin in @($BackendOrigin, $FrontendOrigin)) {
    try {
        $response = Invoke-WebRequest -Uri ($origin.TrimEnd('/') + '/api/v1/auth/me') -UseBasicParsing
        throw 'La consulta de sesion sin cookie deberia responder 401.'
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if (-not $response -or [int] $response.StatusCode -ne 401) { throw }
        Assert-CommonHeaders $response
        if ($origin.StartsWith('https://')) {
            Assert-Header $response 'Strict-Transport-Security' 'max-age=31536000'
        }
        Write-Output "PASS API $origin : HTTP 401 protegido"
    }
}
