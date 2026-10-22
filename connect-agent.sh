#!/bin/sh
# Hermes Companion: teaches your AI assistant how to use it. Run it on the computer that runs the assistant.
# It copies one instruction file (a "skill") into the assistant's skills folder, if it finds one.
set -eu

DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$DIR/skill/hermes-companion/SKILL.md"
[ -f "$SRC" ] || { echo "Stopped: $SRC is missing." >&2; exit 1; }
[ -x "$DIR/hc" ] || chmod +x "$DIR/hc"

install_into() {  # $1 = folder that will hold hermes-companion/
  mkdir -p "$1/hermes-companion"
  sed "s|{{HC}}|$DIR/hc|g" "$SRC" > "$1/hermes-companion/SKILL.md"
  printf '  Taught: %s\n' "$1/hermes-companion/SKILL.md"
}

found=0
echo "Looking for your assistant ..."
if [ -d "$HOME/.hermes/skills" ]; then install_into "$HOME/.hermes/skills/productivity"; found=1; fi
if [ -d "$HOME/.claude/skills" ]; then install_into "$HOME/.claude/skills"; found=1; fi
if [ -d "$HOME/.codex/skills" ]; then install_into "$HOME/.codex/skills"; found=1; fi

if [ "$found" -eq 1 ]; then
  echo
  echo "Done. Your assistant now knows about Hermes Companion. Try: \"remind me in 10 minutes to stretch\"."
else
  mkdir -p "$DIR/agent"
  sed "s|{{HC}}|$DIR/hc|g" "$SRC" > "$DIR/agent/INSTRUCTIONS.md"
  cat <<MSG

I could not find a skills folder for a known assistant.
Tell your assistant (in its chat):

  Read the file $DIR/agent/INSTRUCTIONS.md and follow it whenever I ask for a reminder.

MSG
fi
