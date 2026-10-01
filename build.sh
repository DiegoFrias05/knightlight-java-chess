#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
for tool in java javac jar; do
    command -v "$tool" >/dev/null 2>&1 || { echo "Missing $tool: install JDK 17 or later." >&2; exit 1; }
done
mkdir -p build/classes
find src/main/java -name '*.java' -print > build/sources.txt
javac --release 17 -encoding UTF-8 -d build/classes @build/sources.txt
jar --create --file build/knightlight.jar --main-class chess.ChessApp -C build/classes .
echo 'Built build/knightlight.jar'
