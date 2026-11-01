#!/usr/bin/env bash
#
# Visualise launch doctor (macOS).
#
# Checks whether Visualise's own launch agent (injector/build/libvisualise.dylib) can load into Lunar or
# Badlion's bundled java via the JVM's supported -agentpath mechanism. It does NOT touch either client: it
# inspects their java and test-launches it with the agent in `probe` mode, which only prints a marker.
#
# Build the agent first:  injector/fetch-asm.sh && injector/build.sh
#
# Usage:
#   injector/doctor.sh                 # find Lunar/Badlion automatically
#   injector/doctor.sh /path/to/java   # check one java you point it at
#
set -u

bold=$(tput bold 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
dim=$(tput setaf 8 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)

ok()   { echo "  ${green}✓${reset} $*"; }
warn() { echo "  ${yellow}!${reset} $*"; }
bad()  { echo "  ${red}✗${reset} $*"; }
note() { echo "  ${dim}$*${reset}"; }

MARKER="VISUALISE_PROBE_AGENT_LOADED"

# ---- locate Visualise's own agent -------------------------------------------
# The real agent is built by injector/build.sh and ad-hoc signed there. A properly installed copy in
# ~/.visualise is checked first, then the build output.

locate_agent() {
    local here; here=$(cd "$(dirname "$0")" && pwd)
    local ext=dylib; [ "$(uname -s)" = Linux ] && ext=so
    for cand in "${VISUALISE_HOME:-$HOME/.visualise}/libvisualise.$ext" "$here/build/libvisualise.$ext"; do
        if [ -f "$cand" ]; then agent=$cand; return 0; fi
    done
    bad "libvisualise.$ext not found. Build it with:  injector/fetch-asm.sh && injector/build.sh"
    note "(needs a C compiler: xcode-select --install)"
    return 1
}

# ---- find the clients' bundled java -----------------------------------------

find_javas() {
    local roots=(
        "$HOME/.lunarclient"
        "$HOME/Library/Application Support/Badlion Client"
        "/Applications/Badlion Client.app"
    )
    for root in "${roots[@]}"; do
        [ -d "$root" ] || continue
        find "$root" -type f -path '*/bin/java' 2>/dev/null
    done | sort -u
}

label_for() {
    case "$1" in
        *[Ll]unar*)  echo "Lunar Client" ;;
        *[Bb]adlion*) echo "Badlion Client" ;;
        *) echo "Java runtime" ;;
    esac
}

# ---- the three checks per java ----------------------------------------------

check_entitlements() {
    local java=$1 ents
    ents=$(codesign -d --entitlements - "$java" 2>/dev/null)
    if [ -z "$ents" ]; then
        warn "No entitlements reported (unsigned or ad-hoc). The launch test below is what matters."
        return
    fi
    if grep -q 'disable-library-validation' <<<"$ents"; then
        ok "Allows libraries from another developer (disable-library-validation)"
    else
        warn "No disable-library-validation — an agent from another signer may be refused"
    fi
    if grep -q 'allow-dyld-environment-variables' <<<"$ents"; then
        ok "Honors launch environment variables (allow-dyld-environment-variables)"
    else
        warn "No allow-dyld-environment-variables — the JAVA_TOOL_OPTIONS route may not apply"
    fi
}

probe_agentpath() {
    local java=$1 out
    out=$("$java" "-agentpath:$agent=probe" -version 2>&1)
    if grep -q "$MARKER" <<<"$out"; then
        ok "${bold}Loads at launch via -agentpath${reset}  →  a launcher JVM argument will work"
        return 0
    fi
    bad "The agent did NOT load via -agentpath"
    note "$(grep -i 'error\|warning\|fatal\|libjvm\|validation\|denied' <<<"$out" | head -4)"
    return 1
}

probe_env() {
    local java=$1 out
    out=$(JAVA_TOOL_OPTIONS="-agentpath:$agent=probe,viaenv" "$java" -version 2>&1)
    if grep -q "$MARKER" <<<"$out"; then
        ok "Loads via JAVA_TOOL_OPTIONS  →  a relauncher that sets it will work (if the launcher forwards its environment to the game)"
    else
        warn "JAVA_TOOL_OPTIONS did not load the agent — prefer the -agentpath argument route"
    fi
}

# ---- run ---------------------------------------------------------------------

echo
echo "${bold}Visualise launch doctor${reset}"
echo "${dim}Supported JVM -agentpath mechanism only, probe mode. Nothing in the clients is modified.${reset}"
echo

agent=""
locate_agent || exit 1
note "agent: $agent"
echo

javas=()
if [ $# -ge 1 ]; then
    javas=("$@")
else
    while IFS= read -r line; do
        [ -n "$line" ] && javas+=("$line")
    done < <(find_javas)
fi

if [ ${#javas[@]} -eq 0 ]; then
    echo "No Lunar or Badlion java found automatically."
    echo "Launch the client once so its runtime is installed, or pass the path yourself:"
    echo "    injector/doctor.sh \"\$HOME/.lunarclient/.../Contents/Home/bin/java\""
    exit 1
fi

any_agentpath=1
for java in "${javas[@]}"; do
    [ -x "$java" ] || continue
    ver=$("$java" -version 2>&1 | head -1 | tr -d '"')
    echo "${bold}$(label_for "$java")${reset}  ${dim}$ver${reset}"
    note "$java"
    check_entitlements "$java"
    if probe_agentpath "$java"; then any_agentpath=0; fi
    probe_env "$java"
    echo
done

# ---- where each launcher keeps its settings (read-only) ---------------------
# This shows where a launcher keeps its JVM arguments, so you know where to paste the
# one -agentpath argument. Nothing here writes anything — it only looks and reports.

show_config() {
    local name=$1 dir=$2 found=0 f rel w
    [ -d "$dir" ] || return
    echo "${bold}$name settings${reset}  ${dim}$dir${reset}"
    while IFS= read -r f; do
        found=1
        rel=${f#"$dir"/}
        w="${yellow}read-only${reset}"; [ -w "$f" ] && w="${green}writable${reset}"
        echo "  • $rel   ($w)"
        if grep -liE 'jvm|arguments' "$f" >/dev/null 2>&1; then
            note "    mentions a JVM/arguments key — a launch argument would live here"
        fi
    done < <(find "$dir" -maxdepth 3 -type f \( -name '*.json' -o -name '*.config' \) 2>/dev/null | head -20)
    [ $found -eq 0 ] && note "no settings files yet — launch the client once"
    echo
}

echo "${bold}Launcher settings (read-only — nothing is written)${reset}"
show_config "Lunar Client" "$HOME/.lunarclient/settings"
show_config "Badlion Client" "$HOME/Library/Application Support/Badlion Client"
note "Badlion syncs settings to your account, so its launch argument may need to be set at relaunch instead of written to a file."
echo

echo "${bold}Summary${reset}"
if [ $any_agentpath -eq 0 ]; then
    ok "At least one client accepts the Visualise agent at launch. The launch-load approach is viable here."
    echo "  Next: run injector/install.sh - it prints the one JVM argument to paste into the launcher."
else
    bad "No client accepted the agent at launch on this Mac."
    echo "  Launch-load isn't viable as-is here; share this output and we'll dig into the specific refusal."
fi
echo
