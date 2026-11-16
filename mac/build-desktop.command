#!/usr/bin/env bash
cd "$(dirname "$0")" || exit 1
bash ./build-desktop.sh --debug
status=$?
echo ""
if [ "$status" -ne 0 ]; then echo "构建失败（exit $status），请查看上方错误。"; fi
read -n 1 -s -r -p "按任意键关闭这个窗口…"
echo ""
exit "$status"
