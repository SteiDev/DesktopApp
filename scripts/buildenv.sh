#!/usr/bin/env bash

################### Prevent executing the file - this file must be sourced #######################
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'This file should only be sourced, not run!\n'
    printf 'Use source scripts/buildenv.sh instead\n'
    printf 'If the shell prompt changes, the build environment has been activated successfully\n'
    exit 1
fi

# Purpose: Enforce a specific Java version
# Description:
#   Make sure java is set to the given version in case there are multiple versions on the system
# Params:
#   $1 -> java version (number)
set_java_version() {
    local java_version=$1

    export JAVA_HOME="/usr/lib/jvm/openjdk${java_version}"
    export PATH="$JAVA_HOME/bin:$PATH"
}

# Purpose: Change user prompt
# Description:
#   Change the user prompt so the user knows that he has entered the build environment
# Params:
#   $1 -> prompt message (a space is automatically inserted between the prompt message and the PS1)
change_prompt() {
    local venv_prompt="${1:-}"

    export PS1="${venv_prompt} ${PS1:-}"
}

# Purpose: Setup environment variables
# Description:
#   Export environment variables that may be useful to the project
# Note:
#   Environment variables will be correctly loaded with both bash and zsh
setup_env_vars() {
    if [[ -n "${ZSH_VERSION:-}" ]]; then
        local _src="${(%):-%x}"             # zsh: path of the file being sourced
    elif [[ -n "${BASH_VERSION:-}" ]]; then
        local _src="${BASH_SOURCE[0]}"      # bash: path of the file being sourced
    else
        printf "Unsupported shell\n"
        exit 1
    fi

    local script_path="$(cd "$(dirname "$_src")" && pwd -P)/$(basename "$_src")"
    local script_dir="$(dirname "$script_path")"
    export DESKTOPAPP_ROOT="$(cd "$script_dir/.." && pwd -P)"
}

set_java_version 25
change_prompt '(venv)'
setup_env_vars