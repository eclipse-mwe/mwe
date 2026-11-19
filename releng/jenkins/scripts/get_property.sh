#!/bin/bash

# $1 path to .properties file
# $2 property name

if [ -z "$1" ] || [ ! -f "$1" ]; then
    echo "File not found: $1" >&2
    exit 1
fi
if [ -z "$2" ]; then
    echo "Error: No property name provided" >&2
    exit 1
fi

# Literal match on "name=" at the start of a line, first match wins, and keep
# everything after the first '=' so values that themselves contain '=' survive.
VALUE=$(awk -v key="$2" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' "$1")
if [ -z "$VALUE" ]; then
    echo "Could not get property value" >&2
    exit 1
fi
# printf, not echo: the value is passed through byte-for-byte, so backslash
# sequences from the properties file cannot be reinterpreted by echo in
# shells such as dash.
printf '%s\n' "$VALUE"
