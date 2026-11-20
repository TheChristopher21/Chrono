# Synthetic BMF program tests only; uses Windows TLS trust and never reads employee data.
$ErrorActionPreference = 'Stop'
$chronoBase = [ordered]@{ LZZ=2; STKL=1; KVZ='2.9'; PVZ=1; PVS=0; PVA=0; ZKF=0; R=0; af=0; f=1 }
$chronoCases = @(@{ RE4=500000 }, @{ RE4=550000 }, @{ RE4=500000; JRE4=6000000; SONSTB=100000 }, @{ RE4=0; JRE4=500000; SONSTB=50000 }, @{ LZZ=4; RE4=0; JRE4=250000; SONSTB=50000 })
$chronoRows = foreach ($chronoCase in $chronoCases) {
    $chronoInputs = [ordered]@{}
    foreach ($chronoKey in $chronoBase.Keys) { $chronoInputs[$chronoKey] = $chronoBase[$chronoKey] }
    foreach ($chronoKey in $chronoCase.Keys) { $chronoInputs[$chronoKey] = $chronoCase[$chronoKey] }
    $chronoQuery = @('code=LSt2026ext') + @($chronoInputs.Keys | ForEach-Object { [Uri]::EscapeDataString($_) + '=' + [Uri]::EscapeDataString([string]$chronoInputs[$_]) })
    $chronoUri = 'https://www.bmf-steuerrechner.de/interface/2025Version1.xhtml?' + ($chronoQuery -join '&')
    [xml]$chronoXml = (Invoke-WebRequest -Uri $chronoUri -TimeoutSec 25).Content
    $chronoOutputs = [ordered]@{}
    foreach ($chronoNode in $chronoXml.lohnsteuer.ausgaben.ausgabe) { $chronoOutputs[$chronoNode.name] = $chronoNode.value }
    foreach ($chronoKey in @('LSTLZZ','SOLZLZZ','BK','STS','SOLZS','BKS')) { if (-not $chronoOutputs.Contains($chronoKey)) { throw 'Incomplete BMF program-test result' } }
    @{ inputs=$chronoInputs; outputs=$chronoOutputs }
}
$chronoTarget = Join-Path $PSScriptRoot '../../Chrono-backend/src/test/resources/payroll/bmf-2025-payments-reference.json'
@{ source='Official BMF 2025Version1 program-test interface'; retrieved='2026-11-06'; cases=@($chronoRows) } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $chronoTarget -Encoding utf8
Write-Output "$($chronoRows.Count) independent synthetic BMF payment fixtures written."


