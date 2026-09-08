param([ValidateSet('test','validate','build','install','all')][string]$Command='all')
$ErrorActionPreference = 'Stop'
$Root = [System.IO.Path]::GetFullPath($PSScriptRoot)
switch ($Command) {
  'test' { & (Join-Path $Root 'scripts/build.ps1') }
  'validate' { & (Join-Path $Root 'scripts/validate.ps1') }
  'build' { & (Join-Path $Root 'scripts/build.ps1') }
  'install' { Write-Output 'SKIPPED: installation will be owned by the container component-update workflow' }
  'all' {
    & (Join-Path $Root 'scripts/validate.ps1')
    & (Join-Path $Root 'scripts/build.ps1')
    Write-Output 'SKIPPED: installation will be owned by the container component-update workflow'
  }
}
