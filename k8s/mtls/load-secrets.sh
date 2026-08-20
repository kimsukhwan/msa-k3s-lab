#!/usr/bin/env bash
# mTLS 인증서를 k8s Secret 으로 클러스터에 반영한다.
# 다른 서비스들과 달리 이 값들은 ArgoCD(Git) 로 관리하지 않는다 — next.msa 의 정책과 같은
# 이유다: "비밀값은 Git 에 두지 않고 kubectl 로 직접 적용한다."
# generate-certs.sh 를 먼저 실행해 ./certs/ 를 만들어둔 뒤 이 스크립트를 돌린다.
set -euo pipefail
cd "$(dirname "$0")/certs"

if [ ! -f keystore-password.txt ]; then
  echo "certs/ 가 없다 — 먼저 ./generate-certs.sh 를 실행하라" >&2
  exit 1
fi
PASS=$(cat keystore-password.txt)

kubectl create secret generic gateway-mtls \
  --from-file=keystore.p12=gateway-keystore.p12 \
  --from-file=truststore.p12=gateway-truststore.p12 \
  --from-literal=password="$PASS" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic superapp-proxy-mtls \
  --from-file=keystore.p12=superapp-proxy-keystore.p12 \
  --from-file=truststore.p12=superapp-proxy-truststore.p12 \
  --from-literal=password="$PASS" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Secret gateway-mtls / superapp-proxy-mtls 적용 완료"
