<#
.SYNOPSIS
    Собирает LikeTime одним файлом liketime.jar (сайт + сервер) в папку release.

.DESCRIPTION
    1. Собирает сайт (npm ci, проверка типов, тесты, vite build).
    2. Кладёт собранный сайт внутрь приложения, чтобы сервер сам его раздавал.
    3. Собирает jar профилем release: локальные секреты разработчика в него не попадают.
    4. Проверяет jar и раскладывает в release/: liketime.jar, start.ps1, шаблон настроек.

    Тесты бэкенда используют базу liketime_test; пропустить их можно ключом -SkipTests.

.EXAMPLE
    .\build-release.ps1
    .\build-release.ps1 -SkipTests
#>
param(
    [string]$JavaHome,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$frontend = Join-Path $root 'frontend'
$backend = Join-Path $root 'backend'
$static = Join-Path $backend 'src\main\resources\static'
$release = Join-Path $root 'release'

function Find-JavaHome {
    if ($JavaHome) { return $JavaHome }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        $v = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Select-Object -First 1
        if ("$v" -match '"(\d+)') { if ([int]$Matches[1] -ge 21) { return $env:JAVA_HOME } }
    }
    $found = Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-2*' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'jdk-(\d+)' -and [int]$Matches[1] -ge 21 } | Sort-Object Name -Descending | Select-Object -First 1
    if ($found) { return $found.FullName }
    throw 'Не найден JDK 21 или новее. Установите его или укажите -JavaHome.'
}

function Step($text) { Write-Host "`n=== $text" -ForegroundColor Cyan }
# Успех определяем по коду возврата: предупреждения npm в stderr ошибкой не считаются
function Run($file, $arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $file @arguments } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) { throw "Команда завершилась с ошибкой ($LASTEXITCODE): $file $arguments" }
}

$env:JAVA_HOME = Find-JavaHome
Write-Host "JDK: $env:JAVA_HOME"

Step 'Сайт: установка зависимостей и проверка'
Push-Location $frontend
try {
    Run 'npm' @('ci')
    if (-not $SkipTests) { Run 'npm' @('test') }
    Step 'Сайт: сборка'
    Run 'npm' @('run', 'build')
} finally { Pop-Location }

Step 'Сайт кладётся внутрь приложения'
# static - полностью собираемая папка (в .gitignore), поэтому её можно пересоздавать
if (Test-Path $static) { Remove-Item $static -Recurse -Force }
New-Item -ItemType Directory -Force -Path $static | Out-Null
Copy-Item (Join-Path $frontend 'dist\*') $static -Recurse

try {
    Step 'Сервер: сборка jar (профиль release)'
    Push-Location $backend
    try {
        $mvnArgs = @('-B', '-Prelease', 'clean', 'package')
        if ($SkipTests) { $mvnArgs += '-DskipTests' }
        Run '.\mvnw.cmd' $mvnArgs
    } finally { Pop-Location }
} finally {
    # Чтобы обычная разработка не раздавала старую сборку сайта
    if (Test-Path $static) { Remove-Item $static -Recurse -Force }
}

Step 'Проверка jar'
$jar = Get-ChildItem (Join-Path $backend 'target') -Filter 'backend-*.jar' | Where-Object { $_.Name -notlike '*original*' } | Select-Object -First 1
if (-not $jar) { throw 'jar не найден в backend\target' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $names = $zip.Entries | ForEach-Object { $_.FullName }
    if ($names -contains 'BOOT-INF/classes/application-local.properties') {
        throw 'В jar попал application-local.properties с секретами! Сборка остановлена.'
    }
    if (-not ($names -contains 'BOOT-INF/classes/static/index.html')) {
        throw 'В jar нет сайта (static/index.html).'
    }
    if (-not ($names | Where-Object { $_ -like 'BOOT-INF/classes/db/migration/V*.sql' })) {
        throw 'В jar нет миграций базы данных.'
    }
} finally { $zip.Dispose() }
Write-Host 'Проверено: сайт и миграции на месте, секретов в jar нет.'

Step 'Раскладка в release'
New-Item -ItemType Directory -Force -Path (Join-Path $release 'config') | Out-Null
Copy-Item $jar.FullName (Join-Path $release 'liketime.jar') -Force
Copy-Item (Join-Path $PSScriptRoot 'start.ps1') $release -Force
Copy-Item (Join-Path $PSScriptRoot 'install-autostart.ps1') $release -Force
Copy-Item (Join-Path $PSScriptRoot 'application-local.properties.example') (Join-Path $release 'config') -Force

$size = (Get-Item (Join-Path $release 'liketime.jar')).Length / 1MB
Write-Host ("`nГотово: {0} ({1:N1} МБ)" -f (Join-Path $release 'liketime.jar'), $size) -ForegroundColor Green
Write-Host 'Дальше: скопируйте папку release на сервер и следуйте docs/deployment.md.'
