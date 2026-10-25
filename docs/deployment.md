# Установка на сервер

Система запускается одним файлом `liketime.jar`: в нём и сервер, и сайт. Нужны только Java и MySQL.

## Что нужно на сервере

- Windows (инструкция для Windows; на Linux те же шаги, только запуск `java -jar`)
- Java (JDK или JRE) **21 или новее**
- MySQL 8.0
- Порт 8080 открыт для компьютеров школы (или другой, см. ниже)

## 1. Сборка (на компьютере разработчика)

```powershell
cd tools\deploy
.\build-release.ps1
```

Скрипт собирает сайт, кладёт его внутрь приложения, собирает jar и **проверяет, что в нём нет локальных
секретов**. Результат лежит в папке `release`: `liketime.jar`, `start.ps1`, `install-autostart.ps1`
и шаблон `config\application-local.properties.example`. Ключ `-SkipTests` пропускает тесты (по умолчанию они идут).

Папку `release` целиком скопируйте на сервер, например в `C:\LikeTime`.

## 2. База данных

Создайте базу и отдельного пользователя только с правами на неё (под администратором MySQL):

```sql
CREATE DATABASE liketime CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'liketime_app'@'localhost' IDENTIFIED BY 'ПРИДУМАЙТЕ_ДЛИННЫЙ_ПАРОЛЬ';
GRANT ALL PRIVILEGES ON liketime.* TO 'liketime_app'@'localhost';
```

Таблицы создаёт само приложение при первом запуске (Flyway). Ничего вручную загружать не нужно.

## 3. Настройки и секреты

```powershell
cd C:\LikeTime
Copy-Item config\application-local.properties.example config\application-local.properties
notepad config\application-local.properties
```

Заполните пароль базы, ключ подписи входа, пароль первого администратора и ключ устройства.
Подсказки по каждой строке есть в самом файле. **Этот файл нельзя пересылать и коммитить в git.**
Ограничьте к нему доступ:

```powershell
icacls C:\LikeTime\config /inheritance:r /grant:r "SYSTEM:(OI)(CI)F" "Administrators:(OI)(CI)F"
```

## 4. Запуск

```powershell
cd C:\LikeTime
.\start.ps1
```

Откройте `http://<адрес сервера>:8080`, войдите под `admin` с паролем из настроек.
**После первого входа смените пароль** (Пользователи → Сбросить пароль) и удалите из файла настроек
строки `liketime.bootstrap.admin.*`: первый администратор создаётся один раз, пока в системе нет пользователей.

Логи пишутся в `logs\liketime.log` (хранится 30 дней).

Другой порт: переменная окружения `LIKETIME_PORT`. Другая база: `LIKETIME_DB_URL` или строка
`spring.datasource.url` в файле настроек.

### Автозапуск при включении сервера

От имени администратора:

```powershell
cd C:\LikeTime
.\install-autostart.ps1          # включить
.\install-autostart.ps1 -Remove  # отключить
Start-ScheduledTask -TaskName LikeTime
```

## 5. Резервные копии

В копии есть персональные данные учеников и хеши паролей: храните их в защищённой папке и периодически
копируйте на другой компьютер или внешний диск.

Создайте файл с доступом к MySQL **вне** папки проекта и закройте его от посторонних:

```powershell
New-Item -ItemType Directory -Force C:\ProgramData\LikeTime | Out-Null
Set-Content C:\ProgramData\LikeTime\backup.cnf "[client]`nuser=liketime_app`npassword=ПАРОЛЬ_БАЗЫ" -Encoding ASCII
icacls C:\ProgramData\LikeTime\backup.cnf /inheritance:r /grant:r "SYSTEM:R" "Administrators:R"
```

Разовая копия:

```powershell
.\tools\backup\backup-db.ps1 -DefaultsFile C:\ProgramData\LikeTime\backup.cnf -OutDir D:\Backups\LikeTime
```

Ежедневно в 02:00 (от имени администратора):

```powershell
.\tools\backup\install-backup-task.ps1 -DefaultsFile C:\ProgramData\LikeTime\backup.cnf -OutDir D:\Backups\LikeTime
```

Копии старше 30 дней удаляются (`-KeepDays`).

### Проверка и восстановление

Копию стоит иногда проверять: восстановить **в отдельную пустую базу**, а не в рабочую.

```powershell
# распаковать архив, затем (под администратором MySQL):
mysql -u root -p -e "CREATE DATABASE liketime_check CHARACTER SET utf8mb4"
mysql -u root -p liketime_check < liketime-20261025-020000.sql
```

Восстановление в рабочую базу затирает текущие данные: сначала остановите приложение и сделайте свежую копию.

## 6. Безопасность

- В локальной сети достаточно порта 8080. Если сайт нужен вне школы, поставьте перед ним HTTPS
  (например, Caddy или nginx) и не открывайте порт 8080 наружу. Приложение понимает заголовки прокси.
- Вход ограничен: 5 неудачных попыток подряд блокируют вход на 15 минут (`liketime.security.*`).
- Устройства подключаются не по паролю пользователя, а по серийному номеру и ключу устройства.
- Ключи и пароли не хранятся в базе и не попадают в журнал действий.

## 7. Обновление

1. Сделайте резервную копию.
2. Остановите задание: `Stop-ScheduledTask -TaskName LikeTime` (и завершите процесс `java`, если он остался).
3. Замените `liketime.jar` новым. Папку `config` не трогайте.
4. Запустите снова. Новые миграции базы применятся сами.
