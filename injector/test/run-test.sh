#!/usr/bin/env bash
# End-to-end check against a fake MCP-named "game": agent loads, hooks apply, accessors are rewritten,
# the payload is defined in the game's classloader. Run after ./build.sh.
set -euo pipefail
cd "$(dirname "$0")"
B=../build; T=$(mktemp -d); V=$T/install; mkdir -p $V/lib
case "$(uname -s)" in Darwin) LIB=$B/libvisualise.dylib;; *) LIB=$B/libvisualise.so;; esac

javac --release 8 -nowarn -d $T/game $(find game -name '*.java') 2>/dev/null
javac --release 8 -nowarn -d $T/stubs $(find stubs -name '*.java') 2>/dev/null
javac --release 8 -nowarn -cp $T/game:$T/stubs:$B/visualise-agent.jar -d $T/payload $(find payload -name '*.java') 2>/dev/null
rm -rf $T/payload/org; jar cf $V/visualise-payload.jar -C $T/payload .
cp $B/visualise-agent.jar $B/visualise-engine.jar $V/; cp $B/lib/*.jar $V/lib/
[ -n "${OBF_MAP:-}" ] && cp "$OBF_MAP" $V/obf.map

echo "== run with agent"; java -agentpath:$LIB=dir=$V -cp $T/game net.minecraft.client.Minecraft
echo "== run without agent (control)"; java -cp $T/game net.minecraft.client.Minecraft

# Obfuscated variant: same game renamed to short names, payload stays MCP-named and is remapped at define time.
echo; echo "== obfuscated game + obf.map"
ASMCP=$(ls $B/lib/asm*.jar | tr '\n' ':')
javac -nowarn -cp "$ASMCP" -d $T/tool MakeObfFixture.java 2>/dev/null
java -cp "$ASMCP$T/tool" MakeObfFixture $T/game $T/obfgame $V/obf.map
MAIN=$(grep -P "^C\tnet/minecraft/client/Minecraft\t" $V/obf.map | cut -f3)
java -agentpath:$LIB=dir=$V -cp $T/obfgame $MAIN
