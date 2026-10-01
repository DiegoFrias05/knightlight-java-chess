#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh build.sh
mkdir -p build/test-classes
find src/test/java -name '*.java' -print > build/tests.txt
javac --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes @build/tests.txt
for test in chess.RulesTest chess.EngineTest chess.UiTest; do
    java -Djava.awt.headless=true -cp build/classes:build/test-classes "$test"
done
echo 'All tests passed.'
