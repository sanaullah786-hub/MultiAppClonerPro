#!/usr/bin/env bash
# Compiles and runs the plain-JVM engine tests. Needs only a JDK (keytool + javac).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
keytool -genkeypair -keystore "$OUT/test.p12" -storetype PKCS12 -storepass testpass -keypass testpass \
  -alias t -keyalg RSA -keysize 2048 -validity 30 -dname "CN=Test" >/dev/null 2>&1
javac -encoding UTF-8 -d "$OUT/classes" app/src/main/java/com/multiappcloner/pro/engine/*.java engine-test/EngineTest.java
java -Dfile.encoding=UTF-8 -cp "$OUT/classes" com.multiappcloner.pro.engine.EngineTest "$OUT/test.p12" testpass
javac -encoding UTF-8 -d "$OUT/diag" app/src/main/java/com/multiappcloner/pro/diag/*.java engine-test/DiagTest.java
java -Dfile.encoding=UTF-8 -cp "$OUT/diag" DiagTest
