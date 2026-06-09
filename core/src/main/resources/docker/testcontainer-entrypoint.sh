#!/bin/bash

set -eo pipefail

# logging functions
tce_log() {
	local type="$1"
	shift
	# accept argument string or stdin
	local text="$*"
	if [ "$#" -eq 0 ]; then text="$(cat)"; fi
	local dt
	dt="$(date --rfc-3339=ns)"
	printf '%s [%s] [Entrypoint]: %s\n' "$dt" "$type" "$text"
}
tce_note() {
	tce_log Note "$@"
}
tce_warn() {
	tce_log Warn "$@" >&2
}
tce_error() {
	tce_log ERROR "$@" >&2
	exit 1
}

# Restore a pre-baked data snapshot (under *_temp) into the live data directory.
# Args: <snapshot_temp_dir> <live_data_dir>
tce_restore_temp_to_live() {
	local snapshot_temp="$1"
	local live_data="$2"
	tce_note "copying files to ${live_data} started"
	cp -a "${snapshot_temp}"/. "${live_data}"/
	tce_note "copying files to ${live_data} ended"
}

# Copy live data directory into a temp dir (e.g. before docker commit when TCE_TEMP_MODE is set).
# Args: <live_data_dir> <temp_dir>
tce_persist_data_dir_to_temp() {
	local live_data="$1"
	local temp_dir="$2"
	tce_note "copying files to ${temp_dir} started"
	mkdir -p "${temp_dir}"
	cp -a "${live_data}"/. "${temp_dir}"/
	tce_note "copying files to ${temp_dir} ended"
}

# Restore all snapshot/live pairs populated by tce_read_tmpfs_pairs_from_env.
tce_restore_all_snapshots() {
	local i=0
	while [ "$i" -lt "$TCE_PAIR_COUNT" ]; do
		tce_restore_temp_to_live "${TCE_SNAPSHOT_TEMPS[$i]}" "${TCE_LIVE_DATAS[$i]}"
		i=$((i + 1))
	done
}

# Persist all live/snapshot pairs populated by tce_read_tmpfs_pairs_from_env.
tce_persist_all_to_temp() {
	local i=0
	while [ "$i" -lt "$TCE_PAIR_COUNT" ]; do
		tce_persist_data_dir_to_temp "${TCE_LIVE_DATAS[$i]}" "${TCE_SNAPSHOT_TEMPS[$i]}"
		i=$((i + 1))
	done
}

# Split colon-separated argv with backslash escaping (\: and \\). Populates nameref array.
tce_split_colon_escaped() {
	local encoded="$1"
	local -n _out=$2
	_out=()
	local token=""
	local i=0
	local len=${#encoded}
	while [ "$i" -lt "$len" ]; do
		local c="${encoded:$i:1}"
		if [ "$c" = '\' ]; then
			i=$((i + 1))
			if [ "$i" -ge "$len" ]; then
				tce_error "Trailing backslash in colon-separated argv"
			fi
			token+="${encoded:$i:1}"
			i=$((i + 1))
		elif [ "$c" = ':' ]; then
			_out+=("$token")
			token=""
			i=$((i + 1))
		else
			token+="$c"
			i=$((i + 1))
		fi
	done
	_out+=("$token")
}

# Populates TCE_PAIR_COUNT, TCE_LIVE_DATAS, and TCE_SNAPSHOT_TEMPS from container env
# (colon-separated paths; literal ':' as \:, literal '\' as \\).
tce_read_tmpfs_pairs_from_env() {
	TCE_PAIR_COUNT="${TCE_PAIR_COUNT:-0}"
	TCE_LIVE_DATAS=()
	TCE_SNAPSHOT_TEMPS=()
	if [ "$TCE_PAIR_COUNT" -eq 0 ]; then
		return
	fi
	if [ -z "${TCE_LIVE_DATA_PATHS:-}" ] || [ -z "${TCE_SNAPSHOT_TEMP_PATHS:-}" ]; then
		tce_error "TCE_PAIR_COUNT is ${TCE_PAIR_COUNT} but TCE_LIVE_DATA_PATHS or TCE_SNAPSHOT_TEMP_PATHS is missing or empty"
	fi
	tce_split_colon_escaped "${TCE_LIVE_DATA_PATHS}" TCE_LIVE_DATAS
	tce_split_colon_escaped "${TCE_SNAPSHOT_TEMP_PATHS}" TCE_SNAPSHOT_TEMPS
	if [ "${#TCE_LIVE_DATAS[@]}" -ne "$TCE_PAIR_COUNT" ] || [ "${#TCE_SNAPSHOT_TEMPS[@]}" -ne "$TCE_PAIR_COUNT" ]; then
		tce_error "TCE_PAIR_COUNT=${TCE_PAIR_COUNT} but got ${#TCE_LIVE_DATAS[@]} live paths and ${#TCE_SNAPSHOT_TEMPS[@]} snapshot paths"
	fi
}

# Populates TCE_UPSTREAM_CMD from TCE_UPSTREAM_ENTRYPOINT (colon-separated argv; \: and \\ escaping).
# Image/default CMD arrives as $@ from Docker create.
tce_read_upstream_from_env() {
	TCE_UPSTREAM_CMD=()
	if [ -n "${TCE_UPSTREAM_ENTRYPOINT:-}" ]; then
		tce_split_colon_escaped "${TCE_UPSTREAM_ENTRYPOINT}" TCE_UPSTREAM_CMD
	fi
}

tce_invoke_upstream() {
	"${TCE_UPSTREAM_CMD[@]}" "$@"
}

# Uses TCE_UPSTREAM_CMD + $@ (runtime CMD)
tce_temp_wrap_argv() {
	TCE_SIGTERM_MSG="$1"
	shift
	CHILD_PID=
	_tce_on_term() {
		tce_note "$TCE_SIGTERM_MSG"
		if [ -n "$CHILD_PID" ]; then
			kill -TERM "$CHILD_PID" 2>/dev/null || true
			wait "$CHILD_PID" 2>/dev/null || true
		fi
		tce_persist_all_to_temp
	}
	trap _tce_on_term TERM INT
	echo "$@"
	"${TCE_UPSTREAM_CMD[@]}" "$@" &
	CHILD_PID=$!
	wait "$CHILD_PID"
}

_tce_main() {
	tce_read_tmpfs_pairs_from_env
	tce_read_upstream_from_env
	case "${TCE_TEMP_MODE:-}" in
	1|true|yes)
		tce_temp_wrap_argv "SIGTERM received - shutting down service and copying data" "$@"
		;;
	*)
		tce_restore_all_snapshots
		echo "$@"
		tce_invoke_upstream "$@"
		;;
	esac
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
	_tce_main "$@"
fi
