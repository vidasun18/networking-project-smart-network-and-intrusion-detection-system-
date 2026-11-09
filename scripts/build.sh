#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/main build/test
find src/main/java -name '*.java' > build/main-sources.txt
javac -encoding UTF-8 --release 17 -d build/main @build/main-sources.txt
find src/test/java -name '*.java' > build/test-sources.txt
javac -encoding UTF-8 --release 17 -cp build/main -d build/test @build/test-sources.txt
