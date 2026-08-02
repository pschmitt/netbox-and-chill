#!/usr/bin/env bash

set -u

wait_for_android() {
  local attempt boot_completed device_provisioned package_service package_path

  adb wait-for-device

  for attempt in $(seq 1 60)
  do
    boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    device_provisioned="$(adb shell settings get global device_provisioned 2>/dev/null | tr -d '\r' || true)"
    package_service="$(adb shell service check package 2>/dev/null | tr -d '\r' || true)"
    package_path="$(adb shell cmd package path android 2>/dev/null | tr -d '\r' || true)"

    if [[ "$boot_completed" == "1" && "$device_provisioned" == "1" && "$package_service" == *found* && "$package_path" == package:* ]]
    then
      return 0
    fi

    if [[ "$attempt" == "60" ]]
    then
      printf 'Android system providers did not become ready\n' >&2
      adb shell getprop sys.boot_completed || true
      adb shell settings get global device_provisioned || true
      adb shell service check package || true
      adb shell cmd package path android || true
      return 1
    fi

    sleep 5
  done
}

main() {
  wait_for_android
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]
then
  main "$@"
fi

# vim: set ft=sh et ts=2 sw=2 :
