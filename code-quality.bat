@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat spotlessApply checkstyleMain checkstyleTest %*
exit /b %ERRORLEVEL%
