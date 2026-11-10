@echo off
setlocal
rem One-click build and install of Glass Home using Docker Desktop.
rem The TV address is read from tv.env (TV_IP=...).
cd /d "%~dp0"
set "IMAGE=glasshome-builder:2"

if not exist tv.env copy tv.env.example tv.env >nul
set "TV_IP="
for /f "tokens=1,* delims==" %%a in ('findstr /b "TV_IP=" tv.env') do set "TV_IP=%%b"
if "%TV_IP%"=="" (
    echo Set TV_IP in tv.env first.
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
echo Building the toolchain image (first run only, about 1 GB to download)...
docker build -t %IMAGE% docker || goto fail

:image_ready
echo.
echo Building Glass Home...
docker run --rm -v "%CD%:/work" -v glasshome-gradle:/cache/gradle %IMAGE% sh /work/docker/build.sh || goto fail

echo.
echo Installing on %TV_IP% (accept "Allow USB debugging?" on the TV if asked)...
if not exist "%USERPROFILE%\.android" mkdir "%USERPROFILE%\.android"
docker run --rm -v "%CD%:/work" -v "%USERPROFILE%\.android:/root/.android" %IMAGE% sh /work/docker/install.sh %TV_IP% || goto fail

echo.
echo Glass Home is installed.
pause
exit /b 0

:fail
echo.
echo Failed. See the messages above.
pause
exit /b 1
