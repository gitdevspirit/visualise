#!/usr/bin/env bash
# Copies the built injector into ~/.visualise and prints the one JVM argument to give your launcher.
#   1. injector/fetch-asm.sh && injector/build.sh        (native agent + engine)
#   2. ./gradlew payloadJar                               (payload, MCP names)
#   3. java tools/MakeMcpMappings.java --obf-map mappings/obf-1.8.9.map     (MCP -> obfuscated names)
set -euo pipefail
cd "$(dirname "$0")/.."
DEST=${VISUALISE_HOME:-$HOME/.visualise}
B=injector/build
case "$(uname -s)" in Darwin) LIB=libvisualise.dylib;; *) LIB=libvisualise.so;; esac

need() { [ -e "$1" ] || { echo "missing $1 - $2"; exit 1; }; }
need "$B/$LIB" "run injector/build.sh"
need build/injector/visualise-payload.jar "run ./gradlew payloadJar"
need mappings/obf-1.8.9.map "run: java tools/MakeMcpMappings.java --obf-map mappings/obf-1.8.9.map"

mkdir -p "$DEST/lib"
cp "$B/$LIB" "$B/visualise-agent.jar" "$B/visualise-engine.jar" "$DEST/"
cp "$B"/lib/*.jar "$DEST/lib/"
cp build/injector/visualise-payload.jar "$DEST/"
cp mappings/obf-1.8.9.map "$DEST/obf.map"
[ "$(uname -s)" = Darwin ] && codesign --force -s - "$DEST/$LIB" >/dev/null 2>&1 || true

echo "Installed to $DEST"
echo
echo "Add this JVM argument in your launcher (Lunar: Settings > Game > JVM arguments, Badlion: Settings > Java):"
echo
echo "  -agentpath:$DEST/$LIB=dir=$DEST"
echo
echo "Check the result in the game log: look for '[visualise] all 10 hooks were applied' on exit,"
echo "or '[visualise] hook NOT APPLIED ...' lines for names this client doesn't match."
