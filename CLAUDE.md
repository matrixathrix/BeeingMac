# CLAUDE.md

Instructions for Claude Code when working in this repository.

## Verifying changes

Always verify changes by running:

```
./gradlew installDebug
```

## Java path issues on this Mac

If `./gradlew` fails with an error like "Unable to locate a Java Runtime", run it with `JAVA_HOME` set to Android Studio's bundled JDK instead:

```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
```
