$ErrorActionPreference = 'Stop'
$ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Deps = Join-Path $ProjectRoot '.deps'
$Output = Join-Path $ProjectRoot 'output'
$SqliteVersion = '3.53.2.1'
$SqliteSha256 = 'f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1'
$SqliteJar = Join-Path $Deps "sqlite-jdbc-$SqliteVersion.jar"
$SqliteUrl = "https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/$SqliteVersion/sqlite-jdbc-$SqliteVersion.jar"
$GsonVersion = '2.14.0'
$GsonSha256 = '2cbd119bf1961c28788310963dc80ba65f58cdeec1dd139c8bdb1240faa2c36f'
$GsonJar = Join-Path $Deps "gson-$GsonVersion.jar"
$GsonUrl = "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/$GsonVersion/gson-$GsonVersion.jar"
$ReleaseProperties = Get-Content -LiteralPath (Join-Path $ProjectRoot 'release.properties')
$VersionLine = @($ReleaseProperties | Where-Object { $_ -match '^VERSION=' })
if ($VersionLine.Count -ne 1) { throw 'release.properties must contain exactly one VERSION' }
$Version = $VersionLine[0].Substring('VERSION='.Length).Trim()
if ($Version -notmatch '^\d+(?:\.\d+){1,3}$') {
  throw "SageTV plugin VERSION must be dotted numeric: $Version"
}

New-Item -ItemType Directory -Force -Path $Deps | Out-Null
$DependencySpecs = @(
  @($SqliteJar,$SqliteUrl,$SqliteSha256),
  @($GsonJar,$GsonUrl,$GsonSha256)
)
foreach ($Spec in $DependencySpecs) {
  if (!(Test-Path -LiteralPath $Spec[0])) {
    Invoke-WebRequest -UseBasicParsing -Uri $Spec[1] -OutFile $Spec[0]
  }
  $Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Spec[0]).Hash.ToLowerInvariant()
  if ($Hash -ne $Spec[2]) { throw "Dependency checksum mismatch for $($Spec[0]): $Hash" }
}
$ActualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $SqliteJar).Hash.ToLowerInvariant()
if ($ActualHash -ne $SqliteSha256) {
  throw "sqlite-jdbc checksum mismatch: $ActualHash"
}

if (Test-Path -LiteralPath $Output) {
  $ResolvedRoot = [System.IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\') + '\'
  $ResolvedOutput = [System.IO.Path]::GetFullPath($Output)
  if (!$ResolvedOutput.StartsWith($ResolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean output outside project: $ResolvedOutput"
  }
  Remove-Item -LiteralPath $ResolvedOutput -Recurse -Force
}
$Classes = Join-Path $Output 'classes'
$ApiClasses = Join-Path $Output 'api-classes'
$TestClasses = Join-Path $Output 'test-classes'
$Packages = Join-Path $Output 'packages'
New-Item -ItemType Directory -Force -Path $ApiClasses,$Classes,$TestClasses,$Packages | Out-Null

$ApiSources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'source/compileOnly/java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$MainSources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'source/main/java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$TestSources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'source/test/java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$Classpath = "$SqliteJar;$GsonJar"
& javac -encoding UTF-8 '-Xlint:all' --release 8 -d $ApiClasses @ApiSources
if ($LASTEXITCODE) { throw 'SageTV compile-only API compilation failed' }
& javac -encoding UTF-8 '-Xlint:all,-classfile' --release 8 -classpath "$ApiClasses;$Classpath" -d $Classes @MainSources
if ($LASTEXITCODE) { throw 'TMDB production compilation failed' }
& javac -encoding UTF-8 '-Xlint:all,-classfile' --release 8 -classpath "$ApiClasses;$Classes;$Classpath" -d $TestClasses @TestSources
if ($LASTEXITCODE) { throw 'TMDB test compilation failed' }
& java -classpath "$TestClasses;$ApiClasses;$Classes;$Classpath" org.opensagetv.vibe.tmdb.TestRunner
if ($LASTEXITCODE) { throw 'TMDB tests failed' }
$SageJar = $env:SAGETV_COMPILE_JAR
if (!$SageJar) {
  $Candidate = Join-Path (Split-Path -Parent $ProjectRoot) 'opensagetv-vibe-core/build/release/Sage.jar'
  if (Test-Path -LiteralPath $Candidate) { $SageJar = $Candidate }
}
if ($SageJar) {
  if (!(Test-Path -LiteralPath $SageJar)) { throw "SAGETV_COMPILE_JAR does not exist: $SageJar" }
  & java -classpath "$TestClasses;$Classes;$SageJar;$Classpath" org.opensagetv.vibe.tmdb.SageTvBinaryCompatibilityProbe
  if ($LASTEXITCODE) { throw 'Actual Sage.jar binary-compatibility probe failed' }
} else {
  Write-Output 'SKIPPED: actual Sage.jar binary-compatibility probe (set SAGETV_COMPILE_JAR)'
}

$PluginJar = Join-Path $Packages 'OpenSageTVVibeTMDB.jar'
& jar --create --file $PluginJar -C $Classes .
if ($LASTEXITCODE) { throw 'TMDB JAR packaging failed' }
$JarEntries = @(& jar tf $PluginJar)
if ($LASTEXITCODE) { throw 'TMDB JAR inspection failed' }
if ($JarEntries | Where-Object { $_ -like 'sage/*' }) {
  throw 'Compile-only SageTV API classes leaked into the plugin JAR'
}
Copy-Item -LiteralPath $SqliteJar -Destination $Packages
Copy-Item -LiteralPath $GsonJar -Destination $Packages
$PluginStage = Join-Path $Output 'plugin-stage'
$PluginJars = Join-Path $PluginStage 'JARs'
$PluginConfig = Join-Path $PluginStage 'plugins/opensagetv-vibe-tmdb'
$PluginDocs = Join-Path $PluginStage 'docs/opensagetv-vibe-tmdb'
New-Item -ItemType Directory -Force -Path $PluginJars,$PluginConfig,$PluginDocs | Out-Null
Copy-Item -LiteralPath $PluginJar,$SqliteJar,$GsonJar -Destination $PluginJars
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'tmdb_config.example.toml'),(Join-Path $ProjectRoot 'plugin.properties') -Destination $PluginConfig
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'LICENSE'),(Join-Path $ProjectRoot 'THIRD_PARTY_NOTICES.md'),(Join-Path $ProjectRoot 'docs/TMDB_ATTRIBUTION.md') -Destination $PluginDocs
$PluginZip = Join-Path $Packages 'OpenSageTVVibeTMDB-plugin.zip'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Archive = [IO.Compression.ZipFile]::Open($PluginZip,[IO.Compression.ZipArchiveMode]::Create)
try {
  Get-ChildItem -LiteralPath $PluginStage -File -Recurse | Sort-Object FullName | ForEach-Object {
    $Relative = $_.FullName.Substring($PluginStage.Length + 1).Replace('\','/')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
      $Archive,$_.FullName,$Relative,[IO.Compression.CompressionLevel]::Optimal)
  }
} finally { $Archive.Dispose() }
$VersionedPluginZip = Join-Path $Packages "OpenSageTVVibeTMDB-plugin-$Version.zip"
Copy-Item -LiteralPath $PluginZip -Destination $VersionedPluginZip
$PluginManifest = Join-Path $Packages 'opensagetv-vibe-tmdb.plugin.xml'
& python (Join-Path $ProjectRoot 'scripts/generate-plugin-manifest.py') --version $Version --package $VersionedPluginZip --output $PluginManifest
if ($LASTEXITCODE) { throw 'SageTV plugin repository manifest generation failed' }
$Checksums = Get-ChildItem -LiteralPath $Packages -File | Where-Object Name -ne 'SHA256SUMS' | Sort-Object Name | ForEach-Object {
  '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant(),$_.Name
}
[IO.File]::WriteAllLines((Join-Path $Packages 'SHA256SUMS'),$Checksums,(New-Object Text.UTF8Encoding($false)))
Write-Output "PASS: $PluginJar"
Write-Output "PASS: $PluginZip"
Write-Output "PASS: $VersionedPluginZip"
Write-Output "PASS: $PluginManifest"
