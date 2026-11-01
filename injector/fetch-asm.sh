#!/usr/bin/env bash
# Downloads the ASM jars the engine needs into injector/lib (Maven Central).
set -euo pipefail
cd "$(dirname "$0")"; mkdir -p lib
V=9.7
for a in asm asm-commons asm-tree; do
  [ -f "lib/$a-$V.jar" ] || curl -fsSL -o "lib/$a-$V.jar" "https://repo1.maven.org/maven2/org/ow2/asm/$a/$V/$a-$V.jar"
done
echo "ASM $V ready in injector/lib"
