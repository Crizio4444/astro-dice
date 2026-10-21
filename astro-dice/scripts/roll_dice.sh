#!/bin/sh
# astro-dice 入口包装：以任意工作目录调用掷骰脚本，参数原样透传。
DIR="$(cd "$(dirname "$0")" && pwd)"
exec node "$DIR/roll.js" "$@"
