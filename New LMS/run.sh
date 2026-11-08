#!/bin/bash
# ===== Library Management System - compile and run (Linux / macOS) =====
cd "$(dirname "$0")"
mkdir -p out

if ! ls lib/mysql-connector-j*.jar >/dev/null 2>&1; then
  echo "[ERROR] MySQL driver jar not found in the lib folder."
  echo "Download mysql-connector-j-8.x.x.jar and put it inside lib/. See README.md step 3."
  exit 1
fi

echo "Compiling..."
javac -cp "lib/*" -d out src/lms/*.java || exit 1

echo "Starting server..."
java -cp "out:lib/*" lms.Main
