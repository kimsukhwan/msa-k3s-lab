#!/usr/bin/env bash
# mTLS 랩용 인증서 생성 — 실제 CA가 아니라 이 랩 전용 자체서명 CA다.
# 생성물은 ./certs/ 아래(gitignore 대상)에만 남고, 절대 커밋하지 않는다.
# 개인키를 커밋하지 않는 이유는 실제 비밀값과 동일하다 — 이 랩이 진짜 CA를 흉내내고 있어서다.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf certs && mkdir certs && cd certs

DAYS=3650
PASS=$(openssl rand -hex 16)
echo "$PASS" > keystore-password.txt
echo "생성된 keystore 비밀번호를 certs/keystore-password.txt 에 저장했다(gitignore 대상)."

# ── 1. CA (이 랩의 mTLS 신뢰 루트) ──────────────────────────────
openssl genrsa -out ca.key 4096 2>/dev/null
openssl req -x509 -new -nodes -key ca.key -sha256 -days $DAYS \
  -subj "/CN=msa-k3s-lab-mtls-lab-ca/O=msa-k3s-lab" -out ca.crt

# ── 2. gateway 서버 인증서 (TLS 서버 신원 증명) ──────────────────
openssl genrsa -out gateway-server.key 2048 2>/dev/null
openssl req -new -key gateway-server.key -subj "/CN=gateway" -out gateway-server.csr
cat > gateway-server.ext << 'EOF'
subjectAltName = DNS:gateway, DNS:gateway.default.svc.cluster.local, DNS:localhost
extendedKeyUsage = serverAuth
EOF
openssl x509 -req -in gateway-server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days $DAYS -sha256 -extfile gateway-server.ext -out gateway-server.crt

# ── 3. superapp-proxy 클라이언트 인증서 (슈퍼앱 백엔드의 신원 증명) ──
openssl genrsa -out superapp-proxy-client.key 2048 2>/dev/null
openssl req -new -key superapp-proxy-client.key -subj "/CN=superapp-proxy" -out superapp-proxy-client.csr
cat > superapp-proxy-client.ext << 'EOF'
extendedKeyUsage = clientAuth
EOF
openssl x509 -req -in superapp-proxy-client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days $DAYS -sha256 -extfile superapp-proxy-client.ext -out superapp-proxy-client.crt

# ── 4. gateway 용 keystore(자기 서버 인증서) + truststore(CA만 — 클라이언트 검증용) ──
openssl pkcs12 -export -name gateway \
  -inkey gateway-server.key -in gateway-server.crt -certfile ca.crt \
  -out gateway-keystore.p12 -passout pass:"$PASS"
keytool -importcert -noprompt -alias mtls-lab-ca -file ca.crt \
  -keystore gateway-truststore.p12 -storetype PKCS12 -storepass "$PASS"

# ── 5. superapp-proxy 용 keystore(자기 클라이언트 인증서) + truststore(CA만 — gateway 서버 검증용) ──
openssl pkcs12 -export -name superapp-proxy \
  -inkey superapp-proxy-client.key -in superapp-proxy-client.crt -certfile ca.crt \
  -out superapp-proxy-keystore.p12 -passout pass:"$PASS"
cp gateway-truststore.p12 superapp-proxy-truststore.p12

echo "완료 — certs/ 에 keystore/truststore 6개 파일 생성됨"
ls -la
