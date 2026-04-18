[CmdletBinding()]
param(
    [string]$RepoRoot
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

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-BundleVersion {
    param([Parameter(Mandatory = $true)][string]$ManifestPath)

    $match = Select-String -Path $ManifestPath -Pattern '^Bundle-Version:\s*(.+)$' | Select-Object -First 1
    if (-not $match) {
        throw "No pude leer Bundle-Version desde $ManifestPath"
    }

    return $match.Matches[0].Groups[1].Value.Trim()
}

function Get-ZipEntries {
    param([Parameter(Mandatory = $true)][string]$ZipPath)

    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        return @($zip.Entries | ForEach-Object { $_.FullName })
    }
    finally {
        $zip.Dispose()
    }
}

function Get-ZipEntryText {
    param(
        [Parameter(Mandatory = $true)][string]$ZipPath,
        [Parameter(Mandatory = $true)][string]$EntryName
    )

    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        $entry = $zip.GetEntry($EntryName)
        if (-not $entry) {
            return $null
        }

        $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
        try {
            return $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $zip.Dispose()
    }
}

$bundleVersion = Get-BundleVersion -ManifestPath (Join-Path $RepoRoot "com.aihelper\META-INF\MANIFEST.MF")
$pluginJars = @(
    (Join-Path $RepoRoot ("release\plugins\com.aihelper_{0}.jar" -f $bundleVersion)),
    (Join-Path $RepoRoot ("docs\plugins\com.aihelper_{0}.jar" -f $bundleVersion))
)
$requiredEntries = @(
    "META-INF/MANIFEST.MF",
    "plugin.xml",
    "com/aihelper/ui/ChatView.class",
    "com/aihelper/ui/chat/LocalWorkspaceRouter.class"
)

foreach ($jarPath in $pluginJars) {
    if (-not (Test-Path $jarPath)) {
        throw "Falta el JAR: $jarPath"
    }

    $entries = Get-ZipEntries -ZipPath $jarPath
    foreach ($requiredEntry in $requiredEntries) {
        if ($entries -notcontains $requiredEntry) {
            throw "El JAR $jarPath no contiene $requiredEntry"
        }
    }

    Write-Output ("OK JAR: " + $jarPath)
}

$artifactJars = @(
    (Join-Path $RepoRoot "release\artifacts.jar"),
    (Join-Path $RepoRoot "docs\artifacts.jar")
)
$expectedMappings = @(
    '${repoUrl}/plugins/${id}_${version}.jar',
    '${repoUrl}/features/${id}_${version}.jar'
)

foreach ($artifactJar in $artifactJars) {
    if (-not (Test-Path $artifactJar)) {
        throw "Falta el metadata JAR: $artifactJar"
    }

    $artifactsXml = Get-ZipEntryText -ZipPath $artifactJar -EntryName "artifacts.xml"
    if (-not $artifactsXml) {
        throw "No encontre artifacts.xml dentro de $artifactJar"
    }

    foreach ($expectedMapping in $expectedMappings) {
        if ($artifactsXml -notlike ("*" + $expectedMapping + "*")) {
            throw "El archivo $artifactJar no contiene el mapping esperado: $expectedMapping"
        }
    }

    Write-Output ("OK metadata: " + $artifactJar)
}

Write-Output "Verificacion completada correctamente."