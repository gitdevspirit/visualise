#!/usr/bin/env bash
# Builds the injector: native agent + bootstrap jar + engine jar.  Output: injector/build/
#   Needs: a C compiler (macOS: xcode-select --install), a JDK (javac), and ASM jars in $ASM_DIR
#   (asm, asm-commons, asm-tree; run injector/fetch-asm.sh to download them).
set -euo pipefail
cd "$(dirname "$0")"
OUT=build; rm -rf "$OUT"; mkdir -p "$OUT/classes/loader" "$OUT/classes/engine" "$OUT/lib"
ASM_DIR=${ASM_DIR:-lib}

# --- native agent -----------------------------------------------------------
case "$(uname -s)" in
  Darwin)
    cc -dynamiclib -O2 -fvisibility=hidden -arch arm64 -arch x86_64 -Inative/include -Inative/include/darwin \
       -o "$OUT/libvisualise.dylib" native/visualise_agent.c
    # Apple Silicon refuses unsigned dylibs; an ad-hoc signature is enough for -agentpath.
    codesign --force -s - "$OUT/libvisualise.dylib"
    ;;
  Linux)
    cc -shared -fPIC -O2 -fvisibility=hidden -Inative/include -Inative/include/linux \
       -o "$OUT/libvisualise.so" native/visualise_agent.c
    ;;
  *) echo "unsupported OS"; exit 1 ;;
esac

# --- java: bootstrap jar + engine jar ---------------------------------------
javac --release 8 -d "$OUT/classes/loader" $(find loader/src -name '*.java')
jar cf "$OUT/visualise-agent.jar" -C "$OUT/classes/loader" .

CP=$(ls "$ASM_DIR"/asm*.jar | tr '\n' ':')"$OUT/visualise-agent.jar"
javac --release 8 -cp "$CP" -d "$OUT/classes/engine" $(find engine/src -name '*.java')
jar cf "$OUT/visualise-engine.jar" -C "$OUT/classes/engine" .
cp "$ASM_DIR"/asm*.jar "$OUT/lib/"
echo "built -> injector/$OUT"
