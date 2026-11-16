#!/usr/bin/env bash
# Compiles and runs the SoundLab frontend using the JavaFX jars from ~/.m2.
# Usage: ./run.sh          (override JavaFX location: JAVAFX_LIB=/path/to/dir ./run.sh)
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"

M2="${M2_REPO:-$HOME/.m2/repository}/org/openjfx"
OUT=out
MODULES=(javafx-base javafx-graphics javafx-controls javafx-fxml)

case "$(uname -m)" in
  aarch64|arm64) CLASSIFIER="linux-aarch64" ;;
  *)             CLASSIFIER="linux" ;;
esac

command -v java  >/dev/null || { echo "java not found (void: sudo xbps-install -S openjdk21)"; exit 1; }
command -v javac >/dev/null || { echo "javac not found - install a full JDK, not just the JRE"; exit 1; }

# ---- locate JavaFX ---------------------------------------------------------
# prints the platform jar of module $1 at version $2 (empty if none)
find_jar() {
  local m="$1" v="$2" f
  for f in "$M2/$m/$v/$m-$v-$CLASSIFIER.jar" "$M2/$m/$v/$m-$v-linux.jar" "$M2/$m/$v/$m-$v"-linux*.jar; do
    [[ -f "$f" ]] && { echo "$f"; return; }
  done
}

pick_module_path() {
  local m v j best path
  # 1) newest version for which all four modules exist
  for v in $(ls "$M2/javafx-base" 2>/dev/null | sort -rV); do
    path=""
    for m in "${MODULES[@]}"; do
      j=$(find_jar "$m" "$v"); [[ -n "$j" ]] || { path=""; break; }
      path+="$j:"
    done
    [[ -n "$path" ]] && { echo "${path%:}"; return; }
  done
  # 2) otherwise newest available jar of each module
  path=""
  for m in "${MODULES[@]}"; do
    j=""
    for v in $(ls "$M2/$m" 2>/dev/null | sort -rV); do
      j=$(find_jar "$m" "$v"); [[ -n "$j" ]] && break
    done
    [[ -n "$j" ]] || return 1
    path+="$j:"
  done
  echo "${path%:}"
}

if [[ -n "${JAVAFX_LIB:-}" ]]; then
  MODULE_PATH="$JAVAFX_LIB"
else
  MODULE_PATH=$(pick_module_path || true)
  if [[ -z "$MODULE_PATH" && -f ../pom.xml ]] && command -v mvn >/dev/null; then
    echo "JavaFX jars missing from ~/.m2 - asking Maven to download them..."
    mvn -q -f ../pom.xml dependency:resolve || true
    MODULE_PATH=$(pick_module_path || true)
  fi
  if [[ -z "$MODULE_PATH" ]]; then
    echo "Could not find JavaFX jars in $M2. What is there:"
    find "$M2" -name '*.jar' 2>/dev/null | sed 's/^/  /' || true
    echo "Fix the versions in the pom (use real ones, e.g. javafx 21.0.5) and run 'mvn dependency:resolve' in the project root,"
    echo "or run: JAVAFX_LIB=/path/to/javafx-sdk/lib ./run.sh"
    exit 1
  fi
  echo "Module path:"; echo "$MODULE_PATH" | tr ":" "\n"
fi

FX_MODS=javafx.controls,javafx.fxml

# ---- build -----------------------------------------------------------------
rm -rf "$OUT"
mkdir -p "$OUT"
javac --module-path "$MODULE_PATH" --add-modules "$FX_MODS" -d "$OUT" ./*.java

# FXML files and the font are loaded from the classpath, so copy them next to the classes
cp ./*.fxml ./*.ttf "$OUT"/ 2>/dev/null || true

# ---- run -------------------------------------------------------------------
exec java --module-path "$MODULE_PATH" --add-modules "$FX_MODS" \
  --enable-native-access=javafx.graphics \
  -cp "$OUT" PreviewApp
