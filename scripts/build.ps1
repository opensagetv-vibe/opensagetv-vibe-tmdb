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
$TestClasses = Join-Path $Output 'test-classes'
$Packages = Join-Path $Output 'packages'
New-Item -ItemType Directory -Force -Path $Classes,$TestClasses,$Packages | Out-Null

$MainSources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'source/main/java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$TestSources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'source/test/java') -Filter '*.java' -Recurse | ForEach-Object FullName)
$Classpath = "$SqliteJar;$GsonJar"
& javac -encoding UTF-8 '-Xlint:all,-classfile' --release 8 -classpath $Classpath -d $Classes @MainSources
if ($LASTEXITCODE) { throw 'TMDB production compilation failed' }
& javac -encoding UTF-8 '-Xlint:all,-classfile' --release 8 -classpath "$Classes;$Classpath" -d $TestClasses @TestSources
if ($LASTEXITCODE) { throw 'TMDB test compilation failed' }
& java -classpath "$TestClasses;$Classes;$Classpath" org.opensagetv.vibe.tmdb.TestRunner
if ($LASTEXITCODE) { throw 'TMDB tests failed' }

$PluginJar = Join-Path $Packages 'OpenSageTVVibeTMDB.jar'
& jar --create --file $PluginJar -C $Classes .
if ($LASTEXITCODE) { throw 'TMDB JAR packaging failed' }
Copy-Item -LiteralPath $SqliteJar -Destination $Packages
Copy-Item -LiteralPath $GsonJar -Destination $Packages
Write-Output "PASS: $PluginJar"
