@echo off
rem Windows wrapper: runs scripts/orca.sh with Git Bash
set "ORCA_BASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%ORCA_BASH%" set "ORCA_BASH=bash"
"%ORCA_BASH%" "%~dp0orca.sh" %*
