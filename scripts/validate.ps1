$ErrorActionPreference = 'Stop'
$ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$TrackedCandidates = Get-ChildItem -LiteralPath $ProjectRoot -File -Recurse | Where-Object {
  $_.FullName -notmatch '[\\/](\.git|\.deps|output|artifacts[\\/]downloads)[\\/]'
}
foreach ($File in $TrackedCandidates) {
  if ($File.Name -eq 'tmdb_config.toml' -or $File.Extension -match '^\.sqlite3(?:-wal|-shm)?$') {
    throw "Private TMDB runtime data is present in publishable source: $($File.FullName)"
  }
}
$SecretPatterns = 'api_read_access_token\s*=\s*"[^"\s]+"','api_key\s*=\s*"[^"\s]+"'
foreach ($Pattern in $SecretPatterns) {
  $Matches = Select-String -Path ($TrackedCandidates.FullName) -Pattern $Pattern -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -notlike '*tmdb_config.example.toml' }
  if ($Matches) { throw "Possible TMDB credential in source: $($Matches.Path)" }
}
Write-Output 'PASS: no private TMDB credentials or cache databases in publishable source'
