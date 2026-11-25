@echo off
cd /d "%~dp0"
if exist out rmdir /s /b out
mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
  del sources.txt
  pause
  exit /b 1
)
del sources.txt
jar cfe GradeTrack.jar gradetrack.Main -C out .
pause
