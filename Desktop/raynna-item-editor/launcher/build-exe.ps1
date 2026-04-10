Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$launcherRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir = Join-Path $launcherRoot "tools"
$dotnetDir = Join-Path $toolsDir "dotnet"
$dotnet = Join-Path $dotnetDir "dotnet.exe"
$cliHome = Join-Path $launcherRoot ".dotnet-cli-home"

if (-not (Test-Path -LiteralPath $dotnet))
{
    if (-not (Test-Path -LiteralPath $toolsDir))
    {
        New-Item -ItemType Directory -Path $toolsDir | Out-Null
    }

    $installScript = Join-Path $toolsDir "dotnet-install.ps1"
    Invoke-WebRequest -Uri https://dot.net/v1/dotnet-install.ps1 -OutFile $installScript
    & $installScript -Version 8.0.300 -InstallDir $dotnetDir -NoPath
}

$env:DOTNET_CLI_HOME = $cliHome
$env:HOME = $cliHome
$env:DOTNET_CLI_TELEMETRY_OPTOUT = "1"
if (-not (Test-Path -LiteralPath $cliHome))
{
    New-Item -ItemType Directory -Path $cliHome | Out-Null
}

$project = Join-Path $launcherRoot "ItemEditorLauncherExe\ItemEditorLauncherExe.csproj"
$dist = Join-Path $launcherRoot "dist"
$distFallbackRoot = Join-Path $launcherRoot "dist-build"
$publishOutput = $dist

if (Test-Path -LiteralPath $dist)
{
    try
    {
        Remove-Item -LiteralPath $dist -Recurse -Force
    }
    catch
    {
        $publishOutput = $distFallbackRoot
        if (Test-Path -LiteralPath $publishOutput)
        {
            try
            {
                Remove-Item -LiteralPath $publishOutput -Recurse -Force
            }
            catch
            {
                $publishOutput = Join-Path $launcherRoot ("dist-build-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
            }
        }
    }
}

if (Test-Path -LiteralPath $publishOutput)
{
    Remove-Item -LiteralPath $publishOutput -Recurse -Force
}
New-Item -ItemType Directory -Path $publishOutput | Out-Null

& $dotnet publish $project `
    -c Release `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:EnableCompressionInSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:InvariantGlobalization=true `
	-p:DebugSymbols=false `
	-p:DebugType=None `
	-p:PublishTrimmed=false `
	--source https://api.nuget.org/v3/index.json `
	-o $publishOutput

$pdb = Join-Path $publishOutput "ItemEditorLauncher.pdb"
if (Test-Path -LiteralPath $pdb)
{
	Remove-Item -LiteralPath $pdb -Force
}

Copy-Item -LiteralPath (Join-Path $launcherRoot "launcher-config.json") -Destination (Join-Path $publishOutput "launcher-config.json") -Force
Copy-Item -LiteralPath (Join-Path $launcherRoot "launch-item-editor.bat") -Destination (Join-Path $publishOutput "launch-item-editor.bat") -Force
Copy-Item -LiteralPath (Join-Path $launcherRoot "README.md") -Destination (Join-Path $publishOutput "README.md") -Force

Write-Host "Launcher build complete: $publishOutput\ItemEditorLauncher.exe"
