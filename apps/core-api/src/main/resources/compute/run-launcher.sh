#!/bin/sh
# written by core-api into each run directory (claude/specs/active/0008-add-compute-wes-tes.md, Task 7). starts the
# workflow engine for one run, records its pid, and writes its exit code when it finishes, so core-api can follow the
# run across its own restarts. files are written to a temporary name and renamed, so a reader never sees half a file
#
# usage: sh run-launcher.sh <run directory> <engine command...>
run_dir="$1"
shift
"$@" > "$run_dir/engine.stdout.log" 2> "$run_dir/engine.stderr.log" < /dev/null &
pid=$!
echo "$pid" > "$run_dir/engine.pid.tmp" && mv "$run_dir/engine.pid.tmp" "$run_dir/engine.pid"
wait "$pid"
code=$?
echo "$code" > "$run_dir/engine.exitcode.tmp" && mv "$run_dir/engine.exitcode.tmp" "$run_dir/engine.exitcode"
