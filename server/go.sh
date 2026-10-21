#!/usr/bin/env bash
# Runs from Oracle Cloud Shell: installs/updates the To Do List server on the Oracle VM.
IP=129.154.227.77
URL=https://raw.githubusercontent.com/gs07md/ORDERFLOW/todo-android/server/todo-list-oracle.zip
for u in ubuntu opc; do
  echo "== trying $u@$IP"
  if ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 $u@$IP true 2>/dev/null; then
    ssh -o BatchMode=yes $u@$IP "curl -fsSL $URL -o todo-list-oracle.zip && rm -rf todo-list && python3 -m zipfile -e todo-list-oracle.zip . && sudo bash todo-list/install.sh"
    echo "== TODO_INSTALL_FINISHED exit=$?"
    exit 0
  fi
done
echo "== TODO_NO_SSH: Cloud Shell has no key for this server"
