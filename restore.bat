@echo off
setlocal
rem Undo the debloat and remove Glass Home (re-enables packages, restores Amazon's home).
cd /d "%~dp0"
if not exist tools\platform-tools\adb.exe (
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\get-adb.ps1 || goto fail
)
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\restore.ps1 %* || goto fail
pause
exit /b 0

:fail
echo Restore failed. See the messages above.
pause
exit /b 1
