$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build.ps1')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Set-Location (Join-Path $PSScriptRoot '..')
& java -cp 'build/main;build/test' com.demo.netguard.SmokeTest
exit $LASTEXITCODE
