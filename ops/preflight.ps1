param(
    [Parameter(Mandatory = $false)]
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnv = Join-Path $repoRoot $EnvFile
$productionCompose = Join-Path $repoRoot "docker-compose.production.yml"

if (-not (Test-Path -LiteralPath $resolvedEnv -PathType Leaf)) {
    throw "Environment file not found: $resolvedEnv"
}
if (-not (Test-Path -LiteralPath $productionCompose -PathType Leaf)) {
    throw "Production application compose file not found: $productionCompose"
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $resolvedEnv) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
        continue
    }
    $separator = $trimmed.IndexOf("=")
    if ($separator -le 0) {
        throw "Invalid environment line: $trimmed"
    }
    $key = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()
    $values[$key] = $value
}

$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function Require-Value {
    param([string]$Name, [int]$MinimumLength = 1)
    if (-not $values.ContainsKey($Name) -or $values[$Name].Length -lt $MinimumLength) {
        $errors.Add("$Name is missing or shorter than $MinimumLength characters.")
        return
    }
    $lower = $values[$Name].ToLowerInvariant()
    if ($lower.Contains("replace-with") -or $lower.Contains("change-me") -or $lower -eq "admin") {
        $errors.Add("$Name still contains a placeholder value.")
    }
}

foreach ($tag in @(
    "CHRONO_IMAGE_TAG",
    "MYSQL_IMAGE_TAG",
    "PROMETHEUS_IMAGE_TAG",
    "GRAFANA_IMAGE_TAG",
    "ALERTMANAGER_IMAGE_TAG",
    "RESTIC_IMAGE_TAG"
)) {
    Require-Value $tag
    if ($values.ContainsKey($tag) -and $values[$tag].ToLowerInvariant() -in @("latest", "main", "local")) {
        $errors.Add("$tag must be an immutable release tag, not '$($values[$tag])'.")
    }
}

foreach ($image in @(
    "CHRONO_BACKEND_IMAGE",
    "CHRONO_FRONTEND_IMAGE"
)) {
    Require-Value $image
    if ($values.ContainsKey($image) -and
        ($values[$image].Contains(":") -or $values[$image].EndsWith("/"))) {
        $errors.Add("$image must be an untagged repository name; CHRONO_IMAGE_TAG owns the immutable tag.")
    }
}

foreach ($image in @(
    "NGINX_PROXY_MANAGER_IMAGE",
    "OPEN_WEBUI_IMAGE",
    "OLLAMA_IMAGE"
)) {
    Require-Value $image
    if ($values.ContainsKey($image) -and
        $values[$image] -notmatch "^[^@\s]+@sha256:[a-f0-9]{64}$") {
        $errors.Add("$image must be pinned to an immutable sha256 image digest.")
    }
}

foreach ($secret in @(
    "MYSQL_PASSWORD",
    "MYSQL_ROOT_PASSWORD",
    "RESTORE_TEST_ROOT_PASSWORD",
    "JWT_SECRET",
    "NFC_AGENT_TOKEN",
    "REPORT_ICS_FEED_TOKEN",
    "GRAFANA_ADMIN_PASSWORD"
)) {
    Require-Value $secret 24
}

if ($values.ContainsKey("MYSQL_USER") -and $values["MYSQL_USER"].ToLowerInvariant() -eq "root") {
    $errors.Add("MYSQL_USER must be a dedicated application account, not root.")
}

$databaseSecrets = @("MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "RESTORE_TEST_ROOT_PASSWORD")
$distinctDatabaseSecrets = $databaseSecrets |
    Where-Object { $values.ContainsKey($_) } |
    ForEach-Object { $values[$_] } |
    Select-Object -Unique
if ($distinctDatabaseSecrets.Count -ne $databaseSecrets.Count) {
    $errors.Add("Application, root and restore-test database passwords must be different.")
}

foreach ($flag in @(
    "APP_DEMO_LOGIN_ENABLED",
    "APP_DEMO_LOGIN_ALLOW_PRODUCTION",
    "APP_PMS_TEST_ACCOUNT_ENABLED",
    "APP_PMS_DEMO_DATA_ENABLED",
    "APP_INITIALIZE_ADMIN",
    "APP_PMS_PAYMENTS_SIMULATED_ENABLED"
)) {
    if (-not $values.ContainsKey($flag) -or $values[$flag].ToLowerInvariant() -ne "false") {
        $errors.Add("$flag must be explicitly false for production.")
    }
}

Require-Value "APP_SECURITY_ALLOWED_ORIGINS"
if ($values.ContainsKey("APP_SECURITY_ALLOWED_ORIGINS")) {
    foreach ($origin in $values["APP_SECURITY_ALLOWED_ORIGINS"].Split(",")) {
        $candidate = $origin.Trim().ToLowerInvariant()
        if (-not $candidate.StartsWith("https://") -or
            $candidate.Contains("localhost") -or
            $candidate.Contains("*")) {
            $errors.Add("Production CORS origin is not an explicit public HTTPS URL: $origin")
        }
    }
}

if ($values.Get_Item("APP_PMS_PROVIDER_GATEWAY_ENABLED").ToLowerInvariant() -eq "true") {
    Require-Value "APP_PMS_PROVIDER_GATEWAY_ENDPOINT"
    Require-Value "APP_PMS_PROVIDER_GATEWAY_SECRET" 32
    if (-not $values["APP_PMS_PROVIDER_GATEWAY_ENDPOINT"].ToLowerInvariant().StartsWith("https://")) {
        $errors.Add("APP_PMS_PROVIDER_GATEWAY_ENDPOINT must use HTTPS.")
    }
} else {
    $warnings.Add("PMS provider gateway is disabled; OTA/channel and external message delivery remain offline.")
}

if ($values.Get_Item("APP_PMS_PAYMENTS_STRIPE_ENABLED").ToLowerInvariant() -eq "true") {
    Require-Value "STRIPE_SECRET_KEY" 20
} else {
    $warnings.Add("Stripe PMS payments are disabled; card payments will be rejected.")
}

if (-not $values.ContainsKey("RESTIC_REPOSITORY") -or [string]::IsNullOrWhiteSpace($values["RESTIC_REPOSITORY"])) {
    $warnings.Add("No encrypted off-site restic repository is configured.")
}

foreach ($mailSetting in @(
    "ALERT_EMAIL_TO",
    "SPRING_MAIL_HOST",
    "SPRING_MAIL_PORT",
    "SPRING_MAIL_USERNAME",
    "SPRING_MAIL_PASSWORD"
)) {
    Require-Value $mailSetting
}
if ($values.ContainsKey("ALERT_EMAIL_TO") -and $values["ALERT_EMAIL_TO"] -notmatch "^[^@\s]+@[^@\s]+$") {
    $errors.Add("ALERT_EMAIL_TO must be a valid e-mail address.")
}

$previousEnvFile = $env:CHRONO_ENV_FILE
try {
    $env:CHRONO_ENV_FILE = $EnvFile
    & docker compose `
        --project-directory $repoRoot `
        --file $productionCompose `
        --env-file $resolvedEnv `
        config --quiet
    if ($LASTEXITCODE -ne 0) {
        $errors.Add("Production application compose validation failed.")
    } else {
        $services = @(
            & docker compose `
                --project-directory $repoRoot `
                --file $productionCompose `
                --env-file $resolvedEnv `
                config --services
        ) | Sort-Object
        if ($LASTEXITCODE -ne 0 -or
            ($services -join ",") -ne "backend,frontend") {
            $errors.Add(
                "Production deployment must contain exactly backend and frontend; " +
                "database and infrastructure services are forbidden."
            )
        }
    }
} finally {
    $env:CHRONO_ENV_FILE = $previousEnvFile
}

foreach ($warning in $warnings) {
    Write-Warning $warning
}

if ($errors.Count -gt 0) {
    Write-Host "Chrono production preflight failed:" -ForegroundColor Red
    foreach ($message in $errors) {
        Write-Host " - $message" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Chrono production preflight passed." -ForegroundColor Green
Write-Host "Warnings: $($warnings.Count)"
