param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'release-config.json'),
    [ValidateSet('patch','minor','major')]
    [string]$Bump = 'patch',
    [string]$Version,
    [switch]$NoPush
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Resolve-AgainstRoot([string]$Root, [string]$PathValue) {
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }
    return [System.IO.Path]::GetFullPath((Join-Path $Root $PathValue))
}

function Ensure-Path([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label not found: $Path"
    }
}

function Get-ConfigValue($Config, [string]$Name) {
    $property = $Config.PSObject.Properties[$Name]
    if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$property.Value)) {
        throw "Missing required config value '$Name' in $ConfigPath"
    }
    return [string]$property.Value
}

function Get-OptionalConfigValue($Config, [string]$Name) {
    $property = $Config.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    $value = [string]$property.Value
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $null
    }
    return $value
}

function Get-NextVersion([string]$Current, [string]$BumpType) {
    if ($Current -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
        throw "Current version '$Current' is not semver x.y.z"
    }

    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = [int]$Matches[3]

    switch ($BumpType) {
        'major' { $major += 1; $minor = 0; $patch = 0 }
        'minor' { $minor += 1; $patch = 0 }
        default { $patch += 1 }
    }

    return "$major.$minor.$patch"
}

function Test-IsGitRepo([string]$Repo) {
    $process = Start-Process -FilePath git -ArgumentList @('-C', $Repo, 'rev-parse', '--is-inside-work-tree') -NoNewWindow -Wait -PassThru -RedirectStandardOutput ([System.IO.Path]::GetTempFileName()) -RedirectStandardError ([System.IO.Path]::GetTempFileName())
    return ($process.ExitCode -eq 0)
}

function Invoke-GitIfRepo([string]$Repo, [string[]]$GitArgs) {
    if (-not (Test-IsGitRepo $Repo)) {
        Write-Host "Skipping git command outside a repository: $Repo" -ForegroundColor Yellow
        return
    }

    & git -C $Repo @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed in ${Repo}: git -C ${Repo} $(($GitArgs) -join ' ')"
    }
}

function Commit-IfNeeded([string]$Repo, [string[]]$Paths, [string]$Message) {
    if (-not (Test-IsGitRepo $Repo)) {
        Write-Host "Skipping git commit outside a repository: $Repo" -ForegroundColor Yellow
        return
    }

    & git -C $Repo add -- @Paths
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to stage files in $Repo"
    }

    $staged = & git -C $Repo diff --cached --name-only
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect staged files in $Repo"
    }

    if ($staged) {
        & git -C $Repo commit -m $Message
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to commit changes in $Repo"
        }
    } else {
        Write-Host "No staged changes to commit in $Repo" -ForegroundColor Yellow
    }
}

function Copy-DirectoryContents([string]$SourceDir, [string]$DestinationDir) {
    if (Test-Path -LiteralPath $DestinationDir) {
        Get-ChildItem -LiteralPath $DestinationDir -Force | ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }
    } else {
        New-Item -ItemType Directory -Path $DestinationDir | Out-Null
    }

    Get-ChildItem -LiteralPath $SourceDir -Force | ForEach-Object {
        $target = Join-Path $DestinationDir $_.Name
        if ($_.PSIsContainer) {
            Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force
        } else {
            Copy-Item -LiteralPath $_.FullName -Destination $target -Force
        }
    }
}

Ensure-Path $ConfigPath 'Release config'
$configRoot = Split-Path -Parent $ConfigPath
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json

$appName = Get-ConfigValue $config 'appName'
$sourceRepo = Resolve-AgainstRoot $configRoot (Get-ConfigValue $config 'sourceRepo')
$publishRepo = Resolve-AgainstRoot $configRoot (Get-ConfigValue $config 'publishRepo')
$manifestRelativePath = Get-ConfigValue $config 'manifestPath'
$releaseDirectoryName = Get-ConfigValue $config 'releaseDirectory'
$publishLauncherDirectoryName = Get-OptionalConfigValue $config 'publishLauncherDirectory'
$assetFileNameTemplate = Get-ConfigValue $config 'assetFileNameTemplate'
$rawBaseUrl = (Get-ConfigValue $config 'rawBaseUrl').TrimEnd('/')
$branch = Get-ConfigValue $config 'branch'
$gradleTask = Get-ConfigValue $config 'gradleTask'
$buildOutputRelative = Get-ConfigValue $config 'buildOutput'
$launcherBuildScriptRelative = Get-OptionalConfigValue $config 'launcherBuildScript'
$launcherDistRelative = Get-OptionalConfigValue $config 'launcherDist'

Ensure-Path $sourceRepo 'Source repo'
Ensure-Path $publishRepo 'Publish repo'

$manifestPath = Resolve-AgainstRoot $publishRepo $manifestRelativePath
Ensure-Path $manifestPath 'Manifest file'

Step 'Reading manifest and resolving next version'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$currentVersion = [string]$manifest.version
if ([string]::IsNullOrWhiteSpace($currentVersion)) {
    throw "Manifest version missing in $manifestPath"
}

$newVersion = if ([string]::IsNullOrWhiteSpace($Version)) { Get-NextVersion -Current $currentVersion -BumpType $Bump } else { $Version }
$assetFileName = $assetFileNameTemplate.Replace('{version}', $newVersion)

Write-Host "App:            $appName"
Write-Host "Current version: $currentVersion"
Write-Host "Next version:    $newVersion"

Step "Building package via gradlew.bat $gradleTask"
Push-Location $sourceRepo
try {
    & .\gradlew.bat @($gradleTask -split ' ')
    if ($LASTEXITCODE -ne 0) {
        throw 'Gradle build failed'
    }
}
finally {
    Pop-Location
}

if ($launcherBuildScriptRelative -and $launcherDistRelative -and $publishLauncherDirectoryName) {
    $launcherBuildScriptPath = Resolve-AgainstRoot $sourceRepo $launcherBuildScriptRelative
    $launcherDistPath = Resolve-AgainstRoot $sourceRepo $launcherDistRelative
    Ensure-Path $launcherBuildScriptPath 'Launcher build script'

    Step 'Building launcher executable'
    & powershell -NoProfile -ExecutionPolicy Bypass -File $launcherBuildScriptPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Launcher build failed'
    }

    Ensure-Path $launcherDistPath 'Launcher dist'
}

$buildOutputPath = Resolve-AgainstRoot $sourceRepo $buildOutputRelative
Ensure-Path $buildOutputPath 'Built package zip'

$releaseDirectoryPath = Resolve-AgainstRoot $publishRepo $releaseDirectoryName
if (-not (Test-Path -LiteralPath $releaseDirectoryPath)) {
    New-Item -ItemType Directory -Path $releaseDirectoryPath | Out-Null
}

$assetPath = Join-Path $releaseDirectoryPath $assetFileName
$publishLauncherDirectoryPath = if ($publishLauncherDirectoryName) { Resolve-AgainstRoot $publishRepo $publishLauncherDirectoryName } else { $null }

Step "Copying package to publish target: $assetFileName"
Copy-Item -LiteralPath $buildOutputPath -Destination $assetPath -Force
$zipHash = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()

if ($launcherBuildScriptRelative -and $launcherDistRelative -and $publishLauncherDirectoryName) {
    $launcherDistPath = Resolve-AgainstRoot $sourceRepo $launcherDistRelative
    Step 'Publishing launcher files'
    Copy-DirectoryContents -SourceDir $launcherDistPath -DestinationDir $publishLauncherDirectoryPath
}

Step 'Updating manifest.json'
$releaseRelativePath = ($assetPath.Substring($publishRepo.Length).TrimStart('\')).Replace('\','/')
$manifest.version = $newVersion
$manifest.zipUrl = "$rawBaseUrl/$releaseRelativePath"
$manifest.zipSha256 = $zipHash
$manifest.notes = "Published on $([DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss'))"
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$releaseMessage = "$appName $newVersion"

Step 'Committing source repo changes if needed'
Commit-IfNeeded -Repo $sourceRepo -Paths @('.') -Message $releaseMessage

Step 'Committing publish repo changes'
$manifestRelativeForGit = ($manifestPath.Substring($publishRepo.Length).TrimStart('\')).Replace('\','/')
$assetRelativeForGit = ($assetPath.Substring($publishRepo.Length).TrimStart('\')).Replace('\','/')
$publishPaths = @($manifestRelativeForGit, $assetRelativeForGit)
if ($publishLauncherDirectoryName) {
    $publishPaths += $publishLauncherDirectoryName.Replace('\','/')
}
Commit-IfNeeded -Repo $publishRepo -Paths $publishPaths -Message $releaseMessage

if (-not $NoPush) {
    Step 'Pushing git repositories'
    Invoke-GitIfRepo -Repo $sourceRepo -GitArgs @('push', 'origin', $branch)
    if ($publishRepo -ne $sourceRepo) {
        Invoke-GitIfRepo -Repo $publishRepo -GitArgs @('push', 'origin', $branch)
    }
} else {
    Step 'NoPush enabled: skipped git push'
}

Write-Host "`nRelease complete." -ForegroundColor Green
Write-Host "Version: $newVersion"
Write-Host "Zip:     $assetPath"
Write-Host "SHA256:  $zipHash"
