#!/usr/bin/env bash

# This script is unfinished
# it should remove either logs or db files depending on the given argument ($1)

# Remove log files
readonly LOGPATH="$DESKTOPAPP_ROOT/logs"
rm -v $LOGPATH/*.log

# Remove database files
readonly DB_PATH="$DESKTOPAPP_ROOT/data"

rm -v $DB_PATH/*.db