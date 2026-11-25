#!/bin/sh
# Build GradeTrack.jar. Start it from this folder with:  java -jar GradeTrack.jar
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java") && jar cfe GradeTrack.jar gradetrack.Main -C out .
