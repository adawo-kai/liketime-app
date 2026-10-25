<#
.SYNOPSIS
    Резервная копия базы LikeTime (mysqldump -> .sql.zip) с удалением старых копий.

.DESCRIPTION
    Копия делается согласованно (--single-transaction), без блокировки работы системы.
    Пароль никогда не передаётся в командной строке: используется файл настроек клиента MySQL
    (-DefaultsFile) или переменные окружения LIKETIME_DB_USER и LIKETIME_DB_PASSWORD.

    В копии есть персональные данные учеников и хеши паролей, поэтому храните её в защищённой папке
    и регулярно копируйте на другой компьютер или носитель.

.PARAMETER Database
    Имя базы. По умолчанию liketime.

.PARAMETER OutDir
    Куда складывать копии. По умолчанию папка backups в корне проекта (она в .gitignore).

.PARAMETER KeepDays
    Копии старше этого числа дней удаляются (только файлы этого скрипта: <база>-*.sql.zip).

.PARAMETER DefaultsFile
    Файл вида [client] user=... password=... (рекомендуется, см. docs/deployment.md).

.EXAMPLE
    .\backup-db.ps1 -DefaultsFile C:\ProgramData\LikeTime\backup.cnf -OutDir D:\Backups\LikeTime
#>
param(
    [string]$Database = 'liketime',
    [string]$OutDir = (Join-Path $PSScriptRoot '..\..\backups'),
    [int]$KeepDays = 30,
    [string]$DefaultsFile,
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$MysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
)

$ErrorActionPreference = 'Stop'

$dump = Join-Path $MysqlBin 'mysqldump.exe'
if (-not (Test-Path $dump)) { throw "Не найден mysqldump: $dump. Укажите папку MySQL через -MysqlBin." }

$authArgs = @()
if ($DefaultsFile) {
    if (-not (Test-Path $DefaultsFile)) { throw "Не найден файл настроек: $DefaultsFile" }
    # --defaults-extra-file должен идти первым аргументом
    $authArgs = @("--defaults-extra-file=$DefaultsFile")
} elseif ($env:LIKETIME_DB_USER -and $env:LIKETIME_DB_PASSWORD) {
    $authArgs = @("--user=$($env:LIKETIME_DB_USER)")
    $env:MYSQL_PWD = $env:LIKETIME_DB_PASSWORD
} else {
    throw 'Укажите -DefaultsFile или задайте переменные окружения LIKETIME_DB_USER и LIKETIME_DB_PASSWORD.'
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$OutDir = (Resolve-Path $OutDir).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sqlFile = Join-Path $OutDir "$Database-$stamp.sql"
$zipFile = "$sqlFile.zip"

try {
    # --no-tablespaces: не требует привилегии PROCESS; для InnoDB-дампа табличные пространства не нужны
    & $dump @authArgs --host=$DbHost --port=$Port --protocol=tcp `
        --single-transaction --triggers --no-tablespaces --default-character-set=utf8mb4 `
        --result-file=$sqlFile $Database
    if ($LASTEXITCODE -ne 0) { throw "mysqldump завершился с ошибкой (код $LASTEXITCODE)." }

    $size = (Get-Item $sqlFile).Length
    if ($size -lt 1024) { throw "Копия подозрительно мала ($size байт). Проверьте имя базы и права пользователя." }

    Compress-Archive -Path $sqlFile -DestinationPath $zipFile -CompressionLevel Optimal
    Remove-Item $sqlFile -Force
}
catch {
    # Неудавшаяся копия не должна выглядеть как хорошая
    foreach ($partial in @($sqlFile, $zipFile)) {
        if (Test-Path $partial) { Remove-Item $partial -Force }
    }
    throw
}
finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}

# Удаляем только собственные старые копии по маске
$limit = (Get-Date).AddDays(-$KeepDays)
Get-ChildItem -Path $OutDir -Filter "$Database-*.sql.zip" -File |
    Where-Object { $_.LastWriteTime -lt $limit } |
    ForEach-Object { Remove-Item $_.FullName -Force; Write-Host "Удалена старая копия: $($_.Name)" }

$info = Get-Item $zipFile
Write-Host ("Готово: {0} ({1:N0} КБ)" -f $info.FullName, ($info.Length / 1KB))
