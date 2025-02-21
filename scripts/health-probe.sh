#!/usr/bin/env zsh

n1=$(cat aws/nodes.txt | head -n 1)
n2=$(cat aws/nodes.txt | head -n 2 | tail -n 1)
n3=$(cat aws/nodes.txt | head -n 3 | tail -n 1)

req_path="metadata/jepsen-set" # mds
#req_path="Set/jepsen-set/get" # set vo

echo "n1 n2 n3"
while sleep 1; do
  curl --silent -m 0.5 http://${n1}:8080/${req_path} -o /dev/null && echo -n "✅ " || echo -n "❌ "
  curl --silent -m 0.5 http://${n2}:8080/${req_path} -o /dev/null && echo -n "✅ " || echo -n "❌ "
  curl --silent -m 0.5 http://${n3}:8080/${req_path} -o /dev/null && echo -n "✅ " || echo -n "❌ "
  echo
done
