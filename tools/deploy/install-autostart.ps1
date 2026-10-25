<#
.SYNOPSIS
    Настраивает автозапуск LikeTime при включении сервера (планировщик заданий Windows).

.DESCRIPTION
    Запускать от имени администратора. Задание стартует при загрузке, работает от SYSTEM и
    перезапускается при сбое (до 3 раз с интервалом в минуту). Отмена: .\install-autostart.ps1 -Remove
#>
param(
    [switch]$Remove,
    [string]$TaskName = 'LikeTime'
)

$ErrorActionPreference = 'Stop'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { throw 'Запустите PowerShell от имени администратора.' }

if ($Remove) {
    if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Host "Задание $TaskName удалено."
    } else {
        Write-Host "Задания $TaskName нет."
    }
    return
}

$start = Join-Path $PSScriptRoot 'start.ps1'
if (-not (Test-Path $start)) { throw "Не найден $start. Запускайте скрипт из папки release." }

$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$start`"" -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings `
    -Description 'LikeTime: учёт посещаемости и зарплаты' -Force | Out-Null

Write-Host "Автозапуск настроен (задание $TaskName). Запустить сейчас: Start-ScheduledTask -TaskName $TaskName"
