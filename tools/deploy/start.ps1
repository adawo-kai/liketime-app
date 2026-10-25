<#
.SYNOPSIS
    Запускает LikeTime на сервере (профиль prod). Лежит рядом с liketime.jar.

.DESCRIPTION
    Настройки и секреты читаются из config\application-local.properties рядом с этим файлом.
    Рабочая папка = папка скрипта, поэтому логи пишутся в logs\ рядом.
#>
param(
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Find-Java {
    if ($JavaHome) { return (Join-Path $JavaHome 'bin\java.exe') }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $found = Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?:jdk|jre)-?(\d+)' -and [int]$Matches[1] -ge 21 } |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($found) { return (Join-Path $found.FullName 'bin\java.exe') }
    return 'java'
}

if (-not (Test-Path 'config\application-local.properties')) {
    throw 'Нет файла config\application-local.properties. Скопируйте config\application-local.properties.example и заполните (см. docs/deployment.md).'
}

$java = Find-Java
Write-Host "Запуск LikeTime: $java"
& $java -Xms256m -Xmx768m '-Dfile.encoding=UTF-8' -jar 'liketime.jar' '--spring.profiles.active=prod'
exit $LASTEXITCODE
