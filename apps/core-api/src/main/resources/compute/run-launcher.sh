#!/bin/sh
# written by core-api into each run directory (claude/specs/archive/0008-add-compute-wes-tes.md, Task 7). starts the
# workflow engine for one run, records its pid, and writes its exit code when it finishes, so core-api can follow the
# run across its own restarts. files are written to a temporary name and renamed, so a reader never sees half a file
#
# usage: sh run-launcher.sh <run directory> <engine command...>
#
# on the host, core-api signals the engine's pid directly. in a kubernetes Job (spec 0011, Task 4) this script is the
# pod's main process, which gets SIGTERM when the run is cancelled, and as process 1 it would ignore it: so it passes
# SIGTERM and SIGINT on to the engine, which cancels its tasks and exits, and still records the exit code
run_dir="$1"
shift
"$@" > "$run_dir/engine.stdout.log" 2> "$run_dir/engine.stderr.log" < /dev/null &
pid=$!
trap 'kill -TERM "$pid" 2> /dev/null' TERM INT
echo "$pid" > "$run_dir/engine.pid.tmp" && mv "$run_dir/engine.pid.tmp" "$run_dir/engine.pid"
wait "$pid"
code=$?
# a trapped signal interrupts wait (with a status above 128): keep waiting until the engine itself has exited. 127
# means the pid is no longer this script's child, so the status already in hand is the engine's
while [ "$code" -gt 128 ] && kill -0 "$pid" 2> /dev/null; do
  wait "$pid"
  next=$?
  [ "$next" -eq 127 ] && break
  code=$next
done
echo "$code" > "$run_dir/engine.exitcode.tmp" && mv "$run_dir/engine.exitcode.tmp" "$run_dir/engine.exitcode"
