<#
.SYNOPSIS
    Ежедневная резервная копия базы по расписанию (планировщик заданий Windows).

.DESCRIPTION
    Запускать от имени администратора. Перед этим создайте файл с логином и паролем MySQL
    (см. docs/deployment.md, раздел «Резервные копии») и передайте путь в -DefaultsFile.
    Отмена: .\install-backup-task.ps1 -Remove

.EXAMPLE
    .\install-backup-task.ps1 -DefaultsFile C:\ProgramData\LikeTime\backup.cnf -OutDir D:\Backups\LikeTime
#>
param(
    [string]$DefaultsFile,
    [string]$OutDir,
    [string]$Database = 'liketime',
    [string]$Time = '02:00',
    [int]$KeepDays = 30,
    [switch]$Remove,
    [string]$TaskName = 'LikeTime-Backup'
)

$ErrorActionPreference = 'Stop'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { throw 'Запустите PowerShell от имени администратора.' }

if ($Remove) {
    if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Host "Задание $TaskName удалено."
    }
    return
}

if (-not $DefaultsFile -or -not (Test-Path $DefaultsFile)) { throw 'Укажите существующий файл настроек MySQL через -DefaultsFile.' }
if (-not $OutDir) { throw 'Укажите папку для копий через -OutDir.' }

$script = Join-Path $PSScriptRoot 'backup-db.ps1'
$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$script`" -Database $Database -OutDir `"$OutDir`" -KeepDays $KeepDays -DefaultsFile `"$DefaultsFile`""
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $argument
$trigger = New-ScheduledTaskTrigger -Daily -At $Time
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings `
    -Description 'LikeTime: ежедневная резервная копия базы' -Force | Out-Null
Write-Host "Резервное копирование настроено: каждый день в $Time, копии в $OutDir, хранятся $KeepDays дн."
