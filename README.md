# GradeTrack - website with a Java backend (OOP101 Final Project)

GradeTrack tracks scores per course, finds the grade needed on the remaining work, and computes the GPA.

- **Website** (`index.html`, `sw.js`, `manifest.webmanifest`, `icons/`): the screens the user sees.
- **Java backend** (`backend/`): validates inputs, calculates grades and GPA, searches and sorts, saves data
  to a file, and serves the website. Built with classes, inheritance, polymorphism, collections, exceptions and
  file handling. Only the JDK is needed; there are no libraries and no internet is needed.

Documentation for the project sheet: `DOCUMENTATION.md` (title, overview, objectives, scope and limitations, flowchart).

---

## Run it with the Java backend (for the OOP101 demo)

You need a **JDK 8 or newer** (`javac -version` must work).

1. Open a terminal in the `backend` folder.
2. Start it:
   - Windows: double-click `run.bat`
   - Mac / Linux: `./run.sh`  (once: `chmod +x run.sh`)
   - or with the jar: `java -jar GradeTrack.jar`
3. The browser opens at **http://localhost:8080**. The badge at the top right says **"Java backend · saved to file"**.
   If it does not open, type that address yourself.

Options: `run.bat --port 9090`, `--data other-file.txt`, `--no-browser`.
Your data is saved automatically in `backend/data/gradetrack-data.txt`. Delete the `data` folder to get the sample courses back.
Stop the program with `Ctrl+C`.

Run the automatic checks: `backend/run-tests.sh` (or `run-tests.bat`). Expected last line: `45 passed, 0 failed`.

## Publish only the website (GitHub Pages)

The old way still works: upload every file to a GitHub repository and turn on Pages (Settings, Pages, branch `main`, folder `/ (root)`).
GitHub cannot run Java, so there the website uses its built-in fallback (JavaScript and browser storage), exactly like before.
When you change the site, edit `VERSION` in `sw.js` so visitors get the update.

## What is new compared with the old site

| | |
|---|---|
| Overview | Search courses and sort by order added, name, or grade |
| Courses | Search and sort the scores of a course |
| Everything else | Same screens. Calculations, validation, duplicate-type pop-up, target pop-up and GPA now come from Java |

## How the pieces talk

The website calls `/api/...` on the Java server (JSON). Every change returns the updated courses, and the website redraws.
The list of API calls is at the top of `backend/src/gradetrack/server/ApiServer.java`.

```
backend/src/gradetrack/
  Main.java, SelfTest.java
  model/      Assessment (abstract) + Quiz, Test, Examination, Laboratory, CustomActivity, Course, GradeBook
  logic/      GradeCalculator, GradeScale, Validator, TextUtil
  service/    GradeService
  server/     ApiServer, Json
  storage/    FileManager
  exception/  InvalidInputException, DuplicateEntryException, TargetTooLowException, NotFoundException
```

## Submission reminders (from the project sheet)

- Due **December 4, 2026 (Friday)**, black sliding folder, 8.5" x 13", Times New Roman 12 pt, double spacing.
- Be ready to explain every part of the code. The user interface section of the sheet names Console and Java Swing;
  ask your instructor to approve a website with a Java backend.
