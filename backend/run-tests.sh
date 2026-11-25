#!/bin/sh
# Compile and run the self-test (calculations, validation, file saving and the web API)
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java") && java -cp out gradetrack.SelfTest
