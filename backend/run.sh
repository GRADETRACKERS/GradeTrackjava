#!/bin/sh
# Compile and start GradeTrack (needs a JDK, version 8 or newer). Then open http://localhost:8080
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java") && java -cp out gradetrack.Main "$@"
