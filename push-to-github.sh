#!/bin/bash
echo "Введи свой GitHub токен и нажми Enter:"
read -s TOKEN
git remote set-url github "https://salakandos-jpg:${TOKEN}@github.com/salakandos-jpg/cfvfv.git" 2>/dev/null || git remote add github "https://salakandos-jpg:${TOKEN}@github.com/salakandos-jpg/cfvfv.git"
git push github main -f
echo "Готово!"
