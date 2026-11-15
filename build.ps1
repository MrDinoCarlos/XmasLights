[CmdletBinding()]
param([string]$JdkHome)

$ErrorActionPreference = 'Stop'
$candidates = [System.Collections.Generic.List[string]]::new()
if ($JdkHome) { $candidates.Add($JdkHome) }
elseif ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
if (-not $JdkHome) {
    foreach ($root in @((Join-Path $env:USERPROFILE '.jdks'), (Join-Path $env:USERPROFILE '.gradle/jdks'))) {
        if (Test-Path -LiteralPath $root) {
            Get-ChildItem -LiteralPath $root -Directory -Filter '*21*' | ForEach-Object { $candidates.Add($_.FullName) }
        }
    }
    $compiler = Get-Command javac -ErrorAction SilentlyContinue
    if ($compiler) { $candidates.Add((Split-Path (Split-Path $compiler.Source -Parent) -Parent)) }
}
$selected = $null
foreach ($candidate in $candidates) {
    $java = Join-Path $candidate 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe'))) { continue }
    $version = (& $java --version | Select-Object -First 1)
    if ($version -match '(?:openjdk|java) 21(?:\.|\s|$)') { $selected = $candidate; break }
}
if (-not $selected) { throw 'Instala un JDK 21 o ejecuta .\build.ps1 -JdkHome "C:\ruta\al\jdk-21".' }

$previousJava = $env:JAVA_HOME
$previousOptions = $env:MAVEN_OPTS
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $selected
    # Use Windows' trusted certificate store, including local enterprise roots.
    # This preserves certificate validation; it never disables TLS verification.
    if ($env:OS -eq 'Windows_NT' -and $env:MAVEN_OPTS -notmatch 'javax.net.ssl.trustStore') {
        $env:MAVEN_OPTS = "$($env:MAVEN_OPTS) -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE"
    }
    Write-Host "Compilando y verificando XmasLights con JDK 21: $selected"
    & (Join-Path $PSScriptRoot 'mvnw.cmd') -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw "Maven ha terminado con el código $LASTEXITCODE." }
    $pluginVersion = ([xml](Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pom.xml') -Raw)).project.version
    Write-Host "JAR listo: target/XmasLights-$pluginVersion.jar"
} finally {
    $env:JAVA_HOME = $previousJava
    $env:MAVEN_OPTS = $previousOptions
    Pop-Location
}
