#!/bin/sh
# Replays a recorded harness session: prints the lines of the transcript ($1) and, at an approval request, waits for
# the client's answer. Everything the client writes is appended to $2; the process id goes to $2.pid.
echo $$ > "$2.pid"
: > "$2"
IFS= read -r request
printf '%s\n' "$request" >> "$2"
case $request in
  *'"type":"set_model"'*)
    id=$(printf '%s' "$request" | sed -n 's/.*"id":"\([^" ]*\)".*/\1/p')
    echo "{\"id\":\"$id\",\"type\":\"response\",\"command\":\"set_model\",\"success\":true}"
    IFS= read -r request
    printf '%s\n' "$request" >> "$2"
    ;;
esac
while IFS= read -r line <&3; do
  printf '%s\n' "$line"
  case $line in
    *'"method":"select"'* | *'"method":"confirm"'*)
      while IFS= read -r reply; do
        printf '%s\n' "$reply" >> "$2"
        case $reply in *extension_ui_response*) break ;; esac
      done
      ;;
  esac
done 3< "$1"
# The harness stays alive for the next command until the client ends it.
read -r _
