param([string]$Database='trailersys',[string]$Server='localhost',[int]$Port=5432,
      [string]$Username='postgres',[string]$Destination=(Join-Path $PSScriptRoot 'backups'))
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$backupPath = Join-Path $Destination "$Database`_$(Get-Date -Format 'yyyyMMdd_HHmmss').dump"
# La clave se obtiene de pgpass o PGPASSWORD; nunca se escribe en este script.
& pg_dump --host=$Server --port=$Port --username=$Username --format=custom --compress=9 --blobs --verbose --file=$backupPath $Database
if ($LASTEXITCODE -ne 0) { throw "pg_dump termino con codigo $LASTEXITCODE" }
& pg_restore --list $backupPath | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'El respaldo no supera la verificacion.' }
Get-FileHash -Algorithm SHA256 $backupPath | Format-List
Write-Host "Respaldo verificado: $backupPath"
