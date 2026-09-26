#!/usr/bin/env bash
set -e

echo "🔍 Verificando recursos duplicados en XML..."

# Buscar definiciones de estilos y temas
DUPLICATES=$(grep -R "<style name=" app/src/main/res/values/*.xml \
  | sed -E 's/.*<style name="([^"]+)".*/\1/' \
  | sort | uniq -d)

if [ -n "$DUPLICATES" ]; then
  echo "❌ Se encontraron estilos/temas duplicados:"
  echo "$DUPLICATES"
  exit 1
else
  echo "✅ No se encontraron duplicados."
fi