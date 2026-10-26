<#
.SYNOPSIS
  Security-test helper for YOUR OWN local appointMe app.
  Copies a JWT but changes ONE claim in the payload, keeping the old signature.
  A correct API must answer 401 to the result, because the signature no longer matches.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\forge-token.ps1 -Token "<token>" -Claim tenant_id -Value 2
  powershell -ExecutionPolicy Bypass -File .\scripts\forge-token.ps1 -Token "<token>" -Claim role -Value OWNER

  Numbers are written as numbers, anything else as a string.
  The LAST line printed is the forged token. Paste it into the request.
#>
param(
    [Parameter(Mandatory = $true)][string]$Token,
    [Parameter(Mandatory = $true)][string]$Claim,
    [Parameter(Mandatory = $true)][string]$Value
)

function FromB64Url([string]$s) {
    $s = $s.Replace('-', '+').Replace('_', '/')
    switch ($s.Length % 4) { 2 { $s += '==' } 3 { $s += '=' } }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s))
}

function ToB64Url([string]$t) {
    [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($t)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$parts = $Token.Trim().Split('.')
if ($parts.Length -ne 3) {
    Write-Error "That does not look like a JWT (expected three parts separated by dots)."
    exit 1
}

$payload = FromB64Url $parts[1]

# 5 -> 5   but   OWNER -> "OWNER"
if ($Value -match '^-?\d+$') { $jsonValue = $Value } else { $jsonValue = '"' + $Value + '"' }

$pattern = '"' + [regex]::Escape($Claim) + '"\s*:\s*(?:"[^"]*"|-?\d+(?:\.\d+)?)'
$forged  = ([regex]$pattern).Replace($payload, ('"' + $Claim + '":' + $jsonValue), 1)

if ($forged -eq $payload) {
    Write-Error "Claim '$Claim' not found in the payload: $payload"
    exit 1
}

Write-Host "ORIGINAL payload: $payload"
Write-Host "FORGED   payload: $forged"
Write-Host ""
"$($parts[0]).$(ToB64Url $forged).$($parts[2])"