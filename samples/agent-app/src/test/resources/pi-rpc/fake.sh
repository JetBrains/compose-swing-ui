#!/bin/sh
# A stand-in harness. $1 is a log file that gets a "start" line with the process id and arguments, and a "prompt"
# line for every prompt. $2 is the mode: "echo" answers each prompt with its number, "handled" reports every prompt as
# handled, "quit" writes half an answer and exits with status 0, "chatty" fills stderr before it lists a model.
log=$1
mode=$2
shift 2
if [ "$mode" = stubborn ]; then trap '' TERM; fi
echo "start $$ $*" >> "$log"
n=0
while IFS= read -r line; do
  case $line in
    *'"type":"get_available_models"'*)
      if [ "$mode" = chatty ]; then
        i=0
        while [ $i -lt 3000 ]; do
          echo "diagnostic line $i padded to fill the pipe buffer of the error stream quickly........" >&2
          i=$((i+1))
        done
      fi
      if [ "$mode" = discovery-failed ]; then
        echo '{"type":"response","command":"get_available_models","success":false,"error":"Discovery refused"}'
        continue
      fi
      if [ "$mode" = discovery-empty ]; then
        echo '{"type":"response","command":"get_available_models","success":true,"data":{"models":[]}}'
        continue
      fi
      if [ "$mode" = discovery-quit ]; then
        echo 'Discovery process quit' >&2
        exit 1
      fi
      echo '{"type":"response","command":"get_available_models","success":true,"data":{"models":[{"id":"m","provider":"p","contextWindow":1000}]}}'
      ;;
    *'"type":"set_model"'*)
      id=$(printf '%s' "$line" | sed -n 's/.*"id":"\([^" ]*\)".*/\1/p')
      if [ "$mode" = reject-model ] && echo "$line" | grep -q '"modelId":"rejected"'; then
        echo "{\"id\":\"$id\",\"type\":\"response\",\"command\":\"set_model\",\"success\":false,\"error\":\"Model refused\"}"
      else
        echo "{\"id\":\"$id\",\"type\":\"response\",\"command\":\"set_model\",\"success\":true}"
      fi
      ;;
    *'"type":"prompt"'*)
      n=$((n+1))
      echo "prompt $n" >> "$log"
      case $mode in
        handled)
          echo '{"type":"response","command":"prompt","success":true,"data":{"disposition":"handled"}}'
          ;;
        plan)
          echo '{"type":"tool_execution_start","toolCallId":"plan-1","toolName":"plan","args":{"steps":[{"text":"Read the code","done":true},{"text":"Run the tests","done":false}]}}'
          echo '{"type":"tool_execution_end","toolCallId":"plan-1","toolName":"plan","isError":false,"result":{"content":[{"type":"text","text":"[x] Read the code\n[ ] Run the tests"}]}}'
          echo '{"type":"agent_settled"}'
          ;;
        quit)
          echo '{"type":"message_update","assistantMessageEvent":{"type":"text_delta","delta":"half"}}'
          exit 0
          ;;
        *)
          echo '{"type":"response","command":"prompt","success":true,"data":{"disposition":"started"}}'
          echo "{\"type\":\"message_update\",\"assistantMessageEvent\":{\"type\":\"text_delta\",\"delta\":\"turn $n\"}}"
          echo '{"type":"turn_end","message":{"role":"assistant","usage":{"input":10,"output":2},"stopReason":"stop"}}'
          echo '{"type":"agent_settled"}'
          ;;
      esac
      ;;
  esac
done
