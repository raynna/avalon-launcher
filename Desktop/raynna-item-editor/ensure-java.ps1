param(
    [Parameter(Mandatory = $true)]
    [string]$AppRoot,

    [int]$RequiredMajor = 21
)

$ErrorActionPreference = "Stop"

function Get-JavaMajorVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaExe
    )

    $versionOutput = & $JavaExe -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    foreach ($line in $versionOutput) {
        if ($line -match 'version "(?<version>[0-9]+)(\.[0-9._+-]+)?"') {
            return [int]$Matches.version
        }
    }

    return $null
}

function Resolve-JavaHome {
    param(
        [string]$JavaHome
    )

    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        return $null
    }

    $javaExe = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExe)) {
        return $null
    }

    $majorVersion = Get-JavaMajorVersion -JavaExe $javaExe
    if ($majorVersion -ge $RequiredMajor) {
        return (Resolve-Path -LiteralPath $JavaHome).Path
    }

    return $null
}

function Resolve-JavaFromPath {
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        return $null
    }

    $javaExe = $command.Source
    $majorVersion = Get-JavaMajorVersion -JavaExe $javaExe
    if ($majorVersion -lt $RequiredMajor) {
        return $null
    }

    $javaBinDir = Split-Path -Parent $javaExe
    $javaHome = Split-Path -Parent $javaBinDir
    return (Resolve-Path -LiteralPath $javaHome).Path
}

function Install-LocalJava {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DestinationRoot,

        [Parameter(Mandatory = $true)]
        [string]$DestinationHome
    )

    $downloadUrl = "https://api.adoptium.net/v3/binary/latest/$RequiredMajor/ga/windows/x64/jre/hotspot/normal/eclipse"
    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("item-editor-java-" + [System.Guid]::NewGuid().ToString("N"))
    $zipPath = Join-Path $tempRoot "java.zip"
    $extractDir = Join-Path $tempRoot "extract"

    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    New-Item -ItemType Directory -Path $extractDir | Out-Null
    New-Item -ItemType Directory -Path $DestinationRoot -Force | Out-Null

    try {
        Write-Host "Java $RequiredMajor+ not found. Downloading a local runtime..."
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
        Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force

        $extractedJavaHome = Get-ChildItem -LiteralPath $extractDir -Directory |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\java.exe") } |
            Select-Object -First 1

        if ($null -eq $extractedJavaHome) {
            throw "Downloaded archive did not contain a Java runtime."
        }

        if (Test-Path -LiteralPath $DestinationHome) {
            Remove-Item -LiteralPath $DestinationHome -Recurse -Force
        }

        Move-Item -LiteralPath $extractedJavaHome.FullName -Destination $DestinationHome
        return (Resolve-Path -LiteralPath $DestinationHome).Path
    }
    finally {
        if (Test-Path -LiteralPath $tempRoot) {
            Remove-Item -LiteralPath $tempRoot -Recurse -Force
        }
    }
}

$resolvedAppRoot = (Resolve-Path -LiteralPath $AppRoot).Path
$localRuntimeRoot = Join-Path $resolvedAppRoot ".runtime"
$localJavaHome = Join-Path $localRuntimeRoot "jdk-$RequiredMajor"

$resolvedJavaHome = Resolve-JavaHome -JavaHome $localJavaHome
if ($null -eq $resolvedJavaHome) {
    $resolvedJavaHome = Resolve-JavaHome -JavaHome $env:JAVA_HOME
}
if ($null -eq $resolvedJavaHome) {
    $resolvedJavaHome = Resolve-JavaFromPath
}
if ($null -eq $resolvedJavaHome) {
    $resolvedJavaHome = Install-LocalJava -DestinationRoot $localRuntimeRoot -DestinationHome $localJavaHome
}

Write-Output $resolvedJavaHome
