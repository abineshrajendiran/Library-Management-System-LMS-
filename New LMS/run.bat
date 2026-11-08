@echo off
REM ===== Library Management System - compile and run (Windows) =====
if not exist out mkdir out

dir /b lib\mysql-connector-j*.jar >nul 2>&1
if errorlevel 1 (
  echo [ERROR] MySQL driver jar not found in the lib folder.
  echo Download mysql-connector-j-8.x.x.jar and put it inside the lib folder. See README.md step 3.
  pause
  exit /b 1
)

echo Compiling...
javac -cp "lib/*" -d out src\lms\*.java
if errorlevel 1 (
  echo [ERROR] Compile failed.
  pause
  exit /b 1
)

echo Starting server...
java -cp "out;lib/*" lms.Main
pause
