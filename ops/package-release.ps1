[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$")]
    [string]$Version,

    [Parameter(Mandatory = $false)]
    [ValidatePattern("^[0-9A-Za-z][0-9A-Za-z._/-]{0,127}$")]
    [string]$Registry = "chrisubuntu1",

    [Parameter(Mandatory = $false)]
    [string]$OutputDirectory = "release",

    [switch]$SkipTests,
    [switch]$SkipImageBuild,
    [switch]$PushImages,
    [switch]$ExportImages
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$repoRootFull = [System.IO.Path]::GetFullPath($repoRoot).TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
)
$outputRoot = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRootFull $OutputDirectory))
}
$repoPrefix = $repoRootFull + [System.IO.Path]::DirectorySeparatorChar

if (-not $outputRoot.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputDirectory must stay inside the repository: $outputRoot"
}

$backendImage = "$Registry/chrono-backend:$Version"
$frontendImage = "$Registry/chrono-frontend:$Version"
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$releaseName = "chrono-pms-$Version-$timestamp"
$stageDirectory = Join-Path $outputRoot $releaseName
$archivePath = Join-Path $outputRoot "$releaseName.zip"

New-Item -ItemType Directory -Path $stageDirectory -Force | Out-Null

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory = $true)]
        [string]$Executable,
        [Parameter(Mandatory = $false)]
        [string[]]$Arguments = @()
    )

    Push-Location $WorkingDirectory
    try {
        & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Executable failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

if (-not $SkipTests) {
    $npmInstallArguments = @("ci", "--legacy-peer-deps")
    if ($env:OS -eq "Windows_NT") {
        # The web release does not load the optional desktop Smartcard module.
        # Its legacy node-gyp script is incompatible with Python 3.14 on
        # Windows; the Linux production image still performs the full install.
        $npmInstallArguments += "--ignore-scripts"
    }
    Invoke-Checked `
        -WorkingDirectory (Join-Path $repoRootFull "Chrono-frontend") `
        -Executable "npm" `
        -Arguments $npmInstallArguments
    Invoke-Checked `
        -WorkingDirectory (Join-Path $repoRootFull "Chrono-frontend") `
        -Executable "npm" `
        -Arguments @("run", "audit:prod")
    Invoke-Checked `
        -WorkingDirectory (Join-Path $repoRootFull "Chrono-frontend") `
        -Executable "npm" `
        -Arguments @("test")
    Invoke-Checked `
        -WorkingDirectory (Join-Path $repoRootFull "Chrono-frontend") `
        -Executable "npm" `
        -Arguments @("run", "build:prod")
    Invoke-Checked `
        -WorkingDirectory (Join-Path $repoRootFull "Chrono-backend") `
        -Executable ".\mvnw.cmd" `
        -Arguments @("-B", "clean", "verify")
}

if (-not $SkipImageBuild) {
    Invoke-Checked `
        -WorkingDirectory $repoRootFull `
        -Executable "docker" `
        -Arguments @("build", "--tag", $backendImage, (Join-Path $repoRootFull "Chrono-backend"))
    Invoke-Checked `
        -WorkingDirectory $repoRootFull `
        -Executable "docker" `
        -Arguments @("build", "--tag", $frontendImage, (Join-Path $repoRootFull "Chrono-frontend"))
}

if ($PushImages) {
    Invoke-Checked -WorkingDirectory $repoRootFull -Executable "docker" -Arguments @("push", $backendImage)
    Invoke-Checked -WorkingDirectory $repoRootFull -Executable "docker" -Arguments @("push", $frontendImage)
}

Copy-Item -LiteralPath (Join-Path $repoRootFull "docker-compose.yml") -Destination $stageDirectory
Copy-Item -LiteralPath (Join-Path $repoRootFull "prometheus.yml") -Destination $stageDirectory
Copy-Item -LiteralPath (Join-Path $repoRootFull "SECURITY.md") -Destination $stageDirectory
Copy-Item -LiteralPath (Join-Path $repoRootFull "ops") -Destination $stageDirectory -Recurse
New-Item -ItemType Directory -Path (Join-Path $stageDirectory "docs") -Force | Out-Null
Copy-Item `
    -LiteralPath (Join-Path $repoRootFull "docs\operations") `
    -Destination (Join-Path $stageDirectory "docs") `
    -Recurse

$environmentTemplate = Get-Content -LiteralPath (Join-Path $repoRootFull ".env.example")
$environmentTemplate = $environmentTemplate `
    -replace "^CHRONO_IMAGE_TAG=.*$", "CHRONO_IMAGE_TAG=$Version" `
    -replace "^CHRONO_BACKEND_IMAGE=.*$", "CHRONO_BACKEND_IMAGE=$Registry/chrono-backend" `
    -replace "^CHRONO_FRONTEND_IMAGE=.*$", "CHRONO_FRONTEND_IMAGE=$Registry/chrono-frontend"
$environmentTemplate |
    Set-Content -LiteralPath (Join-Path $stageDirectory ".env.production.template") -Encoding utf8

$gitCommit = "unavailable"
$gitStatus = @()
try {
    $gitSafeDirectory = $repoRootFull.Replace("\", "/")
    $gitCommitOutput = & git -c "safe.directory=$gitSafeDirectory" -C $repoRootFull rev-parse HEAD
    if ($LASTEXITCODE -ne 0) {
        throw "Git commit lookup failed."
    }
    $gitCommit = ([string]$gitCommitOutput).Trim()
    $gitStatus = @(& git -c "safe.directory=$gitSafeDirectory" -C $repoRootFull status --short)
    if ($LASTEXITCODE -ne 0) {
        throw "Git status lookup failed."
    }
} catch {
    $gitStatus = @("Git metadata could not be read.")
}

$manifest = [ordered]@{
    release = $releaseName
    version = $Version
    createdAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    sourceCommit = $gitCommit
    sourceDirty = $gitStatus.Count -gt 0
    sourceChanges = $gitStatus
    backendImage = $backendImage
    frontendImage = $frontendImage
    testsExecuted = -not $SkipTests
    imagesBuilt = -not $SkipImageBuild
    imagesPushed = [bool]$PushImages
    imagesExported = [bool]$ExportImages
}
$manifest |
    ConvertTo-Json -Depth 5 |
    Set-Content -LiteralPath (Join-Path $stageDirectory "RELEASE_MANIFEST.json") -Encoding utf8

if ($ExportImages) {
    Invoke-Checked `
        -WorkingDirectory $repoRootFull `
        -Executable "docker" `
        -Arguments @(
            "save",
            "--output",
            (Join-Path $stageDirectory "chrono-pms-images.tar"),
            $backendImage,
            $frontendImage
        )
}

$checksumLines = Get-ChildItem -LiteralPath $stageDirectory -File -Recurse |
    Sort-Object FullName |
    ForEach-Object {
        $relativePath = $_.FullName.Substring($stageDirectory.Length).TrimStart("\", "/").Replace("\", "/")
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relativePath"
    }
$checksumLines |
    Set-Content -LiteralPath (Join-Path $stageDirectory "SHA256SUMS.txt") -Encoding ascii

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipArchive = [System.IO.Compression.ZipFile]::Open(
    $archivePath,
    [System.IO.Compression.ZipArchiveMode]::Create
)
try {
    Get-ChildItem -LiteralPath $stageDirectory -File -Recurse |
        Sort-Object FullName |
        ForEach-Object {
            $entryName = $_.FullName.Substring($stageDirectory.Length).TrimStart("\", "/").Replace("\", "/")
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zipArchive,
                $_.FullName,
                $entryName,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
} finally {
    $zipArchive.Dispose()
}
$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()

Write-Host "Chrono PMS release package created." -ForegroundColor Green
Write-Host "Archive: $archivePath"
Write-Host "SHA-256: $archiveHash"
Write-Host "Backend image: $backendImage"
Write-Host "Frontend image: $frontendImage"
if (-not $PushImages) {
    Write-Host "Images were not pushed. Re-run with -PushImages after Docker registry login."
}
