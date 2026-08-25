param([Parameter(Mandatory=$true)][string]$Backup,[string]$Database='trailersys_restore',
      [string]$Server='localhost',[int]$Port=5432,[string]$Username='postgres',[switch]$Replace)
$ErrorActionPreference = 'Stop'
$resolvedBackup = (Resolve-Path -LiteralPath $Backup).Path
if ($Replace) {
    & dropdb --host=$Server --port=$Port --username=$Username --if-exists $Database
    if ($LASTEXITCODE -ne 0) { throw "dropdb termino con codigo $LASTEXITCODE" }
}
& createdb --host=$Server --port=$Port --username=$Username $Database
if ($LASTEXITCODE -ne 0) { throw "createdb termino con codigo $LASTEXITCODE" }
& pg_restore --host=$Server --port=$Port --username=$Username --dbname=$Database --clean --if-exists --no-owner --exit-on-error --verbose $resolvedBackup
if ($LASTEXITCODE -ne 0) { throw "pg_restore termino con codigo $LASTEXITCODE" }
Write-Host "Restauracion completada en: $Database"
