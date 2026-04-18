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

$javac = Get-RequiredCommand -Name "javac"
$jar = Get-RequiredCommand -Name "jar"

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
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()

$artifactsXml = @"
<?xml version='1.0' encoding='UTF-8'?>
<?artifactRepository version='1.1.0'?>
<repository name='AI Helper Update Site' type='org.eclipse.equinox.p2.artifact.repository.simpleRepository' version='1'>
  <properties size='2'>
    <property name='p2.timestamp' value='$timestamp'/>
    <property name='p2.compressed' value='true'/>
  </properties>
  <mappings size='3'>
    <rule filter='(&amp; (classifier=osgi.bundle))' output='`${repoUrl}/plugins/`${id}_`${version}.jar'/>
    <rule filter='(&amp; (classifier=binary))' output='`${repoUrl}/binary/`${id}_`${version}'/>
    <rule filter='(&amp; (classifier=org.eclipse.update.feature))' output='`${repoUrl}/features/`${id}_`${version}.jar'/>
  </mappings>
  <artifacts size='2'>
    <artifact classifier='osgi.bundle' id='com.aihelper' version='$bundleVersion'>
      <properties size='1'>
        <property name='download.size' value='$pluginSize'/>
      </properties>
    </artifact>
    <artifact classifier='org.eclipse.update.feature' id='com.aihelper.feature' version='$bundleVersion'>
      <properties size='2'>
        <property name='download.contentType' value='application/zip'/>
        <property name='download.size' value='$featureSize'/>
      </properties>
    </artifact>
  </artifacts>
</repository>
"@

$tmpDir = Join-Path $env:TEMP ("aihelper-artifacts-" + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $tmpDir | Out-Null
[System.IO.File]::WriteAllText((Join-Path $tmpDir "artifacts.xml"), $artifactsXml, (New-Object System.Text.UTF8Encoding($false)))
& $jar --create --file (Join-Path $releaseRoot "artifacts.jar") -C $tmpDir artifacts.xml | Out-Null
Remove-Item -Recurse -Force $tmpDir

$contentJar = Join-Path $releaseRoot "content.jar"
$siteXml = Join-Path $releaseRoot "site.xml"

if (Test-Path $contentJar) {
    Copy-Item $contentJar (Join-Path $docsRoot "content.jar") -Force
}
else {
    Write-Warning "No existe $contentJar; no se copio docs\\content.jar"
}

if (Test-Path $siteXml) {
    Copy-Item $siteXml (Join-Path $docsRoot "site.xml") -Force
}
else {
    Write-Warning "No existe $siteXml; no se copio docs\\site.xml"
}

Copy-Item (Join-Path $releaseRoot "artifacts.jar") (Join-Path $docsRoot "artifacts.jar") -Force

$compiledClasses = @(Get-ChildItem $outDir -Recurse -Filter "*.class").Count

Write-Output ("Version: " + $bundleVersion)
Write-Output ("Compiled classes: " + $compiledClasses)
Write-Output ("Plugin jar size: " + $pluginSize)
Write-Output ("Feature jar size: " + $featureSize)
Write-Output ("Release plugin jar: " + $pluginJar)
Write-Output ("Docs plugin jar: " + $docsPluginJar)