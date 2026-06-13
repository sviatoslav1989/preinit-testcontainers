@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat publishArtifacts %*
exit /b %ERRORLEVEL%
