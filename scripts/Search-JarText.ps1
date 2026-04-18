[CmdletBinding()]
param(
    [string]$RepoRoot,
    [string]$JarPath,
    [Parameter(Mandatory = $true)][string[]]$SearchText,
    [string]$EntryFilter = "*.class"
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

if (-not $JarPath) {
    $bundleVersion = Get-BundleVersion -ManifestPath (Join-Path $RepoRoot "com.aihelper\META-INF\MANIFEST.MF")
    $JarPath = Join-Path $RepoRoot ("release\plugins\com.aihelper_{0}.jar" -f $bundleVersion)
}

if (-not (Test-Path $JarPath)) {
    throw "No existe el JAR: $JarPath"
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
$encoding = [System.Text.Encoding]::GetEncoding(28591)
$matches = New-Object System.Collections.Generic.List[object]

try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.EndsWith("/")) {
            continue
        }
        if ($entry.FullName -notlike $EntryFilter) {
            continue
        }

        $stream = $entry.Open()
        try {
            $buffer = New-Object byte[] $entry.Length
            $read = $stream.Read($buffer, 0, $buffer.Length)
            if ($read -le 0) {
                continue
            }
            $text = $encoding.GetString($buffer, 0, $read)
            foreach ($needle in $SearchText) {
                if ($text.Contains($needle)) {
                    $matches.Add([PSCustomObject]@{
                        Entry = $entry.FullName
                        Text = $needle
                    })
                }
            }
        }
        finally {
            $stream.Dispose()
        }
    }
}
finally {
    $zip.Dispose()
}

if ($matches.Count -eq 0) {
    Write-Output "No se encontraron coincidencias."
    exit 1
}

$matches | Sort-Object Entry, Text | Format-Table -AutoSize