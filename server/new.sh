#!/usr/bin/env bash
# Oracle Cloud Shell: create a free "todo-list-server" VM that installs the To Do List server by itself.
set -u
export OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING=True
echo "== start v4"
T="${OCI_TENANCY:-$(oci iam compartment list --all --query 'data[0]."compartment-id"' --raw-output)}"
ZIPURL=https://raw.githubusercontent.com/gs07md/ORDERFLOW/todo-android/server/todo-list-oracle.zip
say(){ echo "== $*"; }
mkdir -p ~/.ssh; chmod 700 ~/.ssh
[ -f ~/.ssh/todo_key.pub ] || { rm -f ~/.ssh/todo_key; ssh-keygen -q -t rsa -b 4096 -N "" -f ~/.ssh/todo_key -C todo-list </dev/null; }
[ -f ~/.ssh/todo_key.pub ] && say "key ready" || { say "KEY_FAILED"; exit 1; }
isid(){ case "$1" in ocid1.*) return 0;; *) return 1;; esac; }
say "tenancy ok: $(isid "$T" && echo yes || echo no)"
say "looking for servers ..."
EX=$(oci compute instance list --compartment-id "$T" --lifecycle-state RUNNING --query "data[?\"display-name\"=='todo-list-server'].id|[0]" --raw-output 2>/dev/null)
if isid "$EX"; then NID=$EX; say "server already exists"; else
  REF=$(oci compute instance list --compartment-id "$T" --lifecycle-state RUNNING --query 'data[0].id' --raw-output)
  AD=$(oci compute instance get --instance-id "$REF" --query 'data."availability-domain"' --raw-output)
  SUB=$(oci compute vnic-attachment list --compartment-id "$T" --instance-id "$REF" --query 'data[0]."subnet-id"' --raw-output)
  IMG=$(oci compute image list --compartment-id "$T" --operating-system "Canonical Ubuntu" --operating-system-version "22.04" --shape VM.Standard.E2.1.Micro --sort-by TIMECREATED --sort-order DESC --limit 1 --query 'data[0].id' --raw-output)
  say "ref=$(isid "$REF" && echo ok) AD=$AD subnet=$(isid "$SUB" && echo ok) image=$(isid "$IMG" && echo ok)"
  isid "$SUB" && isid "$IMG" || { say "LOOKUP_FAILED"; exit 1; }
  cat > /tmp/ud.sh <<UD
#!/bin/bash
cd /root && curl -fsSL $ZIPURL -o t.zip && python3 -m zipfile -e t.zip /root && bash /root/todo-list/install.sh > /var/log/todo-install.log 2>&1
UD
  say "creating server (2-5 min) ..."
  NID=$(oci compute instance launch --compartment-id "$T" --availability-domain "$AD" --shape VM.Standard.E2.1.Micro \
    --subnet-id "$SUB" --image-id "$IMG" --display-name todo-list-server --assign-public-ip true \
    --ssh-authorized-keys-file ~/.ssh/todo_key.pub --user-data-file /tmp/ud.sh \
    --wait-for-state RUNNING --max-wait-seconds 600 --query 'data.id' --raw-output 2>/tmp/launch.err)
  if ! isid "$NID"; then say "LAUNCH_FAILED"; echo "$NID" | tail -3; grep -E '"(code|message)"' /tmp/launch.err | head -4; exit 1; fi
fi
PIP=$(oci compute instance list-vnics --instance-id "$NID" --query 'data[0]."public-ip"' --raw-output)
say "TODO_SERVER_IP=$PIP"
SUB2=$(oci compute vnic-attachment list --compartment-id "$T" --instance-id "$NID" --query 'data[0]."subnet-id"' --raw-output)
for SL in $(oci network subnet get --subnet-id "$SUB2" --query 'data."security-list-ids"[]' --raw-output | tr -d '[]",'); do
  say "security list $SL open TCP ports:"
  oci network security-list get --security-list-id "$SL" --query 'data."ingress-security-rules"[?protocol==`"6"`].["tcp-options"."destination-port-range".min,"tcp-options"."destination-port-range".max,source]' --output table
done
say "TODO_DONE"
