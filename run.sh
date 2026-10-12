#!/usr/bin/env bash
# Launches Playwright Test Studio (needs JDK 25+ and Maven on PATH)
cd "$(dirname "$0")"
mvn -q javafx:run
