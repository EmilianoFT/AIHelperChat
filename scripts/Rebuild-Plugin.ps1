[CmdletBinding()]
param(
    [string]$RepoRoot,
    [string]$EclipsePluginsPath = $env:AIHELPER_ECLIPSE_PLUGINS_PATH,
    [string]$JavaRelease = "17"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $RepoRoot) {
    $scriptPath = $PSCommandPath
    if (-not $scriptPath) {
        $scriptPath = $MyInvocation.MyCommand.Path
    }
    if (-not $scriptPath) {
        throw "No pude resolver la ruta del script para calcular RepoRoot."
    }
    $RepoRoot = (Resolve-Path (Join-Path (Split-Path -Parent $scriptPath) "..")).Path
}

function Get-RequiredCommand {
    param([Parameter(Mandatory = $true)][string]$Name)

    $command = Get-Command $Name -ErrorAction Stop
    return $command.Source
}

function Get-BundleVersion {
    param([Parameter(Mandatory = $true)][string]$ManifestPath)

    $match = Select-String -Path $ManifestPath -Pattern '^Bundle-Version:\s*(.+)$' | Select-Object -First 1
    if (-not $match) {
        throw "No pude leer Bundle-Version desde $ManifestPath"
    }

    return $match.Matches[0].Groups[1].Value.Trim()
}

function Ensure-Directory {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-EclipsePluginsDirectory {
    param([Parameter(Mandatory = $true)][string]$PluginsPath)

    if ($PluginsPath.EndsWith("*")) {
        return (Resolve-Path ($PluginsPath.Substring(0, $PluginsPath.Length - 1))).Path
    }

    return (Resolve-Path $PluginsPath).Path
}

function Get-EclipseLauncherJar {
    param([Parameter(Mandatory = $true)][string]$PluginsDirectory)

    $launcher = Get-ChildItem -Path $PluginsDirectory -Filter "org.eclipse.equinox.launcher_*.jar" | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $launcher) {
        throw "No encontre org.eclipse.equinox.launcher_*.jar en $PluginsDirectory"
    }

    return $launcher.FullName
}

$javac = Get-RequiredCommand -Name "javac"
$jar = Get-RequiredCommand -Name "jar"
$java = Get-RequiredCommand -Name "java"

if (-not $EclipsePluginsPath) {
    $defaultPlugins = "C:\Users\Emiliano\eclipse\plugins\*"
    if (Test-Path ($defaultPlugins -replace '\\*$', '')) {
        $EclipsePluginsPath = $defaultPlugins
    }
}

if (-not $EclipsePluginsPath) {
    throw "Define -EclipsePluginsPath o la variable AIHELPER_ECLIPSE_PLUGINS_PATH con la ruta a eclipse\\plugins\\*"
}

$pluginRoot = Join-Path $RepoRoot "com.aihelper"
$featureRoot = Join-Path $RepoRoot "com.aihelper.feature"
$releaseRoot = Join-Path $RepoRoot "release"
$docsRoot = Join-Path $RepoRoot "docs"
$manifestPath = Join-Path $pluginRoot "META-INF\MANIFEST.MF"
$bundleVersion = Get-BundleVersion -ManifestPath $manifestPath

$outDir = Join-Path $env:TEMP "aihelper-build-clean"
if (Test-Path $outDir) {
    Remove-Item -Recurse -Force $outDir
}
New-Item -ItemType Directory -Path $outDir | Out-Null

$sources = Get-ChildItem (Join-Path $pluginRoot "src") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
if (-not $sources -or $sources.Count -eq 0) {
    throw "No encontre fuentes Java en $pluginRoot\src"
}

& $javac --release $JavaRelease -encoding UTF-8 -cp $EclipsePluginsPath -d $outDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac finalizo con codigo $LASTEXITCODE"
}

$binComPath = Join-Path $pluginRoot "bin\com"
if (Test-Path $binComPath) {
    Remove-Item -Recurse -Force $binComPath
}
Copy-Item (Join-Path $outDir "com") $binComPath -Recurse

Ensure-Directory -Path (Join-Path $releaseRoot "plugins")
Ensure-Directory -Path (Join-Path $releaseRoot "features")
Ensure-Directory -Path (Join-Path $docsRoot "plugins")
Ensure-Directory -Path (Join-Path $docsRoot "features")

$pluginJar = Join-Path $releaseRoot ("plugins\com.aihelper_{0}.jar" -f $bundleVersion)
$featureJar = Join-Path $releaseRoot ("features\com.aihelper.feature_{0}.jar" -f $bundleVersion)
$docsPluginJar = Join-Path $docsRoot ("plugins\com.aihelper_{0}.jar" -f $bundleVersion)
$docsFeatureJar = Join-Path $docsRoot ("features\com.aihelper.feature_{0}.jar" -f $bundleVersion)

& $jar --create --file $pluginJar --manifest $manifestPath -C (Join-Path $pluginRoot "bin") . -C $pluginRoot plugin.xml -C $pluginRoot icons | Out-Null
& $jar --create --file $featureJar -C $featureRoot feature.xml | Out-Null

Copy-Item $pluginJar $docsPluginJar -Force
Copy-Item $featureJar $docsFeatureJar -Force

$pluginSize = (Get-Item $pluginJar).Length
$featureSize = (Get-Item $featureJar).Length

$releaseSiteXml = Join-Path $releaseRoot "site.xml"
$docsSiteXml = Join-Path $docsRoot "site.xml"

$siteXmlContent = @"
<?xml version="1.0" encoding="UTF-8"?>
<site>
    <category-def name="AI Helper" label="AI Helper">
        <description>AI Helper plugins</description>
    </category-def>
    <feature
        url="features/com.aihelper.feature_$bundleVersion.jar"
        id="com.aihelper.feature"
        version="$bundleVersion">
        <category name="AI Helper"/>
    </feature>
</site>
"@

[System.IO.File]::WriteAllText($releaseSiteXml, $siteXmlContent, (New-Object System.Text.UTF8Encoding($false)))
Copy-Item $releaseSiteXml $docsSiteXml -Force

$eclipsePluginsDirectory = Get-EclipsePluginsDirectory -PluginsPath $EclipsePluginsPath
$launcherJar = Get-EclipseLauncherJar -PluginsDirectory $eclipsePluginsDirectory

$publishSource = Join-Path $env:TEMP ("aihelper-p2source-" + [guid]::NewGuid().ToString())
Ensure-Directory -Path $publishSource
Ensure-Directory -Path (Join-Path $publishSource "plugins")
Ensure-Directory -Path (Join-Path $publishSource "features")

Copy-Item $pluginJar (Join-Path $publishSource ("plugins\com.aihelper_{0}.jar" -f $bundleVersion)) -Force
Copy-Item $featureJar (Join-Path $publishSource ("features\com.aihelper.feature_{0}.jar" -f $bundleVersion)) -Force
Copy-Item $releaseSiteXml (Join-Path $publishSource "site.xml") -Force

$releaseArtifactsJar = Join-Path $releaseRoot "artifacts.jar"
$releaseContentJar = Join-Path $releaseRoot "content.jar"
$docsArtifactsJar = Join-Path $docsRoot "artifacts.jar"
$docsContentJar = Join-Path $docsRoot "content.jar"

if (Test-Path $releaseArtifactsJar) {
        Remove-Item $releaseArtifactsJar -Force
}

if (Test-Path $releaseContentJar) {
        Remove-Item $releaseContentJar -Force
}

$releaseUri = ([System.Uri]$releaseRoot).AbsoluteUri.TrimEnd('/')
& $java -jar $launcherJar -application org.eclipse.equinox.p2.publisher.UpdateSitePublisher -metadataRepository $releaseUri -artifactRepository $releaseUri -source $publishSource -compress
if ($LASTEXITCODE -ne 0) {
        throw "La publicacion p2 fallo con codigo $LASTEXITCODE"
}

if (-not (Test-Path $releaseArtifactsJar)) {
        Remove-Item -Recurse -Force $publishSource
    throw "No se genero $releaseArtifactsJar"
}

if (-not (Test-Path $releaseContentJar)) {
        Remove-Item -Recurse -Force $publishSource
    throw "No se genero $releaseContentJar"
}

Copy-Item $releaseArtifactsJar $docsArtifactsJar -Force
Copy-Item $releaseContentJar $docsContentJar -Force
Remove-Item -Recurse -Force $publishSource

$compiledClasses = @(Get-ChildItem $outDir -Recurse -Filter "*.class").Count

Write-Output ("Version: " + $bundleVersion)
Write-Output ("Compiled classes: " + $compiledClasses)
Write-Output ("Plugin jar size: " + $pluginSize)
Write-Output ("Feature jar size: " + $featureSize)
Write-Output ("Generated metadata: " + $releaseArtifactsJar)
Write-Output ("Generated metadata: " + $releaseContentJar)
Write-Output ("Release plugin jar: " + $pluginJar)
Write-Output ("Docs plugin jar: " + $docsPluginJar)