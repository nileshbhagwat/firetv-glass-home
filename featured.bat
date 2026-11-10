@echo off
setlocal
rem One click: find this week's trending titles on your apps, cut out the characters and send
rem them to the TV. Needs TMDB_TOKEN in tv.env. "featured.bat demo" uses generated artwork.
cd /d "%~dp0"
set "IMAGE=glasshome-featured:1"

if not exist tv.env copy tv.env.example tv.env >nul
set "TMDB_TOKEN="
for /f "tokens=1,* delims==" %%a in ('findstr /b "TMDB_TOKEN=" tv.env') do set "TMDB_TOKEN=%%b"
set "MODE="
if /i "%~1"=="demo" set "MODE=--demo"
if not defined MODE if not defined TMDB_TOKEN (
    echo Add your TMDB token to tv.env first: TMDB_TOKEN=...
    echo See "Featured titles" in README.md, or run "featured.bat demo" to try the styles.
    goto fail
)

docker info >nul 2>&1
if not errorlevel 1 goto docker_ready
echo Starting Docker Desktop...
start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
for /l %%i in (1,1,60) do (
    timeout /t 3 /nobreak >nul
    docker info >nul 2>&1 && goto docker_ready
)
echo Docker Desktop did not start.
goto fail

:docker_ready
docker image inspect %IMAGE% >nul 2>&1
if not errorlevel 1 goto image_ready
echo Building the artwork image (first run only, about 400 MB to download)...
docker build -t %IMAGE% docker\featured || goto fail

:image_ready
echo.
echo Preparing featured titles...
docker run --rm -e TMDB_TOKEN -v "%CD%:/work" %IMAGE% python /work/docker/featured/featured.py --out /work/featured/out %MODE% || goto fail

if not exist tools\platform-tools\adb.exe (
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\get-adb.ps1 || goto fail
)
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\push-featured.ps1 || goto fail

echo.
echo Done. On the TV, press the Menu button (three lines) on the home screen to pick a style.
pause
exit /b 0

:fail
echo.
echo Failed. See the messages above.
pause
exit /b 1
