#!/usr/bin/env bash
# Oracle Cloud Shell: update the To Do List server (data is kept).
URL=https://raw.githubusercontent.com/gs07md/ORDERFLOW/todo-android/server/todo-list-oracle.zip
ssh -i ~/.ssh/todo_key -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20 ubuntu@137.23.39.242 \
  "curl -fsSL $URL -o t.zip && rm -rf todo-list && python3 -m zipfile -e t.zip . && sudo bash todo-list/install.sh >/tmp/upd.log 2>&1; tail -n 4 /tmp/upd.log; curl -s https://137-23-39-242.sslip.io/api/config"
echo; echo "== TODO_UPDATE_DONE"
