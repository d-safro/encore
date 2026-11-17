#!/usr/bin/env bash
# encore-usb: copy music to Encore over a USB cable.
#
# Run it on the computer that runs your Navidrome server and leave it running. Whenever an Android phone
# with USB debugging on is plugged in, it connects the phone's port 4533 to the server (adb reverse), and
# Encore talks to the server through the cable: fast, no Wi-Fi, internet or VPN needed. Unplug any time; Encore carries on
# over the network, from where it stopped.
#
#   tools/encore-usb.sh                 # Navidrome on this computer's port 4533
#   tools/encore-usb.sh 127.0.0.1:4540  # or wherever this computer reaches it (host:port). Behind a proxy that
#                                       # adds things to Navidrome (podcasts, extras), give the proxy's plain-http
#                                       # address with the same routes, so those work through the cable too
#
# Needs adb (Android platform-tools: `sudo dnf install android-tools`, `sudo apt install adb`, or Android
# Studio's SDK). Nothing is changed on the phone apart from that port forward, which ends when it's unplugged.
set -euo pipefail

target="${1:-127.0.0.1:4533}"
host="${target%:*}"
port="${target##*:}"
[[ "$target" == *:* ]] || { host="$target"; port=4533; }
PHONE_PORT=4533 # what Encore looks for (UsbLinkNotifier.port)

adb="$(command -v adb || true)"
for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
  [[ -z "$adb" && -n "$c" && -x "$c/platform-tools/adb" ]] && adb="$c/platform-tools/adb"
done
[[ -n "$adb" ]] || { echo "encore-usb: adb not found. Install Android platform-tools (package android-tools or adb)." >&2; exit 1; }

# adb can only forward to a port on this computer. A server elsewhere (another machine, a container address)
# gets a small relay on a local port first.
relay_pid=""
local_port="$port"
if [[ "$host" != "127.0.0.1" && "$host" != "localhost" ]]; then
  command -v socat >/dev/null || { echo "encore-usb: the server isn't on this computer's 127.0.0.1; install socat to relay to $target" >&2; exit 1; }
  local_port=14533
  socat "TCP-LISTEN:$local_port,bind=127.0.0.1,reuseaddr,fork" "TCP:$host:$port" &
  relay_pid=$!
fi
cleanup() {
  [[ -n "$relay_pid" ]] && kill "$relay_pid" 2>/dev/null
  for s in $("$adb" devices | awk 'NR>1 && $2=="device" {print $1}'); do
    "$adb" -s "$s" reverse --remove "tcp:$PHONE_PORT" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT
trap 'exit 0' INT TERM

if ! curl -fsS -m 5 "http://127.0.0.1:$local_port/ping" >/dev/null 2>&1; then
  echo "encore-usb: warning: no server answering at $target yet (it's checked again for each phone)" >&2
fi

"$adb" start-server >/dev/null 2>&1 || true
echo "encore-usb: waiting for a phone (USB debugging on). Ctrl+C to stop."
declare -A seen=()
while true; do
  declare -A now=()
  while read -r serial state _; do
    [[ -z "$serial" ]] && continue
    now[$serial]=1
    key="$serial:$state"
    if [[ "$state" == "unauthorized" && -z "${seen[$key]:-}" ]]; then
      echo "encore-usb: $serial: tap Allow on the phone (\"Allow USB debugging?\")"
    elif [[ "$state" == "device" ]]; then
      if ! "$adb" -s "$serial" reverse --list 2>/dev/null | grep -q "tcp:$PHONE_PORT tcp:$local_port"; then
        if "$adb" -s "$serial" reverse "tcp:$PHONE_PORT" "tcp:$local_port" >/dev/null; then
          name="$("$adb" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
          echo "encore-usb: ${name:-$serial} connected: Encore uses the cable now (Settings > Downloads)"
        fi
      fi
    fi
    seen[$key]=1
  done < <("$adb" devices | awk 'NR>1 && NF>=2 {print $1, $2}')
  for key in "${!seen[@]}"; do
    s="${key%:*}"
    if [[ -z "${now[$s]:-}" ]]; then
      [[ "$key" == *:device ]] && echo "encore-usb: $s unplugged"
      unset "seen[$key]"
    fi
  done
  unset now
  sleep 2
done
