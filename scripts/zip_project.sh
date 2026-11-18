#!/usr/bin/env bash
bash "$DESKTOPAPP_ROOT/scripts/cleanup.sh"
mvn clean
readonly zip_path="$(dirname "$DESKTOPAPP_ROOT")"
tar czvf "${zip_path}/${PROJECT_NAME}.tar.gz" "${zip_path}/${PROJECT_NAME}"