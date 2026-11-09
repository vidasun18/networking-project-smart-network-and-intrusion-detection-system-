$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build.ps1')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Set-Location (Join-Path $PSScriptRoot '..')
& java -cp build/main com.demo.netguard.NetGuardApp @args
exit $LASTEXITCODE
