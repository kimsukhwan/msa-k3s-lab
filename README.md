# msa-k3s-lab

k8s/MSA를 처음 배우기 위한 **연습용** 프로젝트입니다. next.msa의 실제 코드와는
무관하고, "gateway → order-service → product-service"라는 아주 작은 3개
서비스로 next.msa의 핵심 개념(Layer C/B, port-out 호출, Service DNS, 배포)만
직접 손으로 만져보는 게 목적입니다.

```
클라이언트 → gateway(:8093, Layer C 역할) → order-service(:8092, Layer B 역할)
                                                    → product-service(:8091, Layer B 역할)
```

- `order-service`가 `product-service`를 호출하는 방식이 next.msa의 **port-out**과
  같은 개념입니다 — IP를 모른 채 k8s Service 이름(`product-service`)만으로 호출.
- `gateway`는 실제 Envoy Gateway/HTTPRoute가 하는 "경로별로 알맞은 서비스로
  라우팅"을 최소 형태로 흉내낸 것입니다.

## 0. 사전 준비

이미 설치됨: `k3d`, `kubectl`, `helm`, `gh` (Homebrew), Java 25.
(Kotlin 2.3.0 / Spring Boot 3.5.16 / Gradle 9.7.0부터 JDK 25 빌드·실행을 지원한다 —
그 이전 버전 조합에서는 `Unsupported class file major version 69` 에러가 난다.)

**Docker Desktop만 직접 설치해주세요** — 설치 중 관리자 암호 입력이 필요해서
자동화할 수 없었습니다.

```bash
brew install --cask docker
```

설치 후 `/Applications/Docker.app`을 한 번 직접 열어서 초기 설정(권한 요청 등)을
완료해주세요. 메뉴바에 고래 아이콘이 뜨고 안정되면 준비 끝입니다.

## 1. Docker 없이 먼저 로컬에서 3개 다 띄워보기

가장 빠른 피드백 루프입니다. 터미널 3개를 열고:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./gradlew :product-service:bootRun   # 터미널 1
./gradlew :order-service:bootRun     # 터미널 2
./gradlew :gateway:bootRun           # 터미널 3
```

확인:

```bash
curl localhost:8093/api/orders/2
# {"orderId":..,"product":{"id":2,"name":"MSA 입문 마우스",...},"orderedBy":"local"}
```

## 2. Docker 이미지 빌드

Docker Desktop이 켜진 상태에서, **저장소 루트**에서 실행합니다(각 Dockerfile이
3개 모듈을 함께 봐야 해서 build context가 루트여야 합니다).

```bash
docker build -f product-service/Dockerfile -t msa-lab/product-service:local .
docker build -f order-service/Dockerfile   -t msa-lab/order-service:local   .
docker build -f gateway/Dockerfile         -t msa-lab/gateway:local         .
```

## 3. k3d로 로컬 k3s 클러스터 만들기

```bash
k3d cluster create msa-lab --port "8888:80@loadbalancer"
kubectl get nodes    # 클러스터가 뜬 걸 확인
```

`--port 8888:80@loadbalancer`는 클러스터 안의 Ingress(80번)를 내 맥의 8888번
포트로 뚫어주는 설정입니다 — 이게 있어야 `curl localhost:8888`으로 클러스터
안까지 도달합니다. (8080은 이 맥에서 이미 다른 프로세스가 쓰고 있어서 8888로
바꿨습니다 — 겹치면 다른 빈 포트로 바꿔서 쓰세요.)

로컬에서 빌드한 이미지를 레지스트리 없이 바로 클러스터 안으로 넣습니다:

```bash
k3d image import msa-lab/product-service:local msa-lab/order-service:local msa-lab/gateway:local -c msa-lab
```

## 4. 배포

```bash
kubectl apply -f k8s/product-service.yaml
kubectl apply -f k8s/order-service.yaml
kubectl apply -f k8s/gateway.yaml

kubectl get pods -w   # 전부 Running/Ready 될 때까지 지켜보기 (Ctrl+C로 중단)
```

확인:

```bash
curl localhost:8888/api/orders/2
```

Pod 이름이 바뀌는 걸 보고 싶다면 replicas=2인 product-service를 반복 호출해보세요:

```bash
for i in 1 2 3 4; do curl -s localhost:8888/api/products/1 | jq .servedBy; done
```

## 5. GitHub 연결 — 비밀번호 대신 PAT로

**비밀번호는 저에게 알려주지 마세요.** 대신 아래 둘 중 하나로 진행해주세요.

**방법 A (추천) — 사용자님이 직접 로그인**

터미널에서 아래 명령을 직접 실행해 브라우저로 로그인해주세요(이 단계는 제가
대신 못 합니다):

```bash
gh auth login
```

완료되면 저에게 "로그인했다"고만 알려주세요. 그 다음부터는 `gh repo create`,
`git push` 등을 제가 이어서 실행할 수 있습니다.

**방법 B — Personal Access Token(PAT) 발급**

1. https://github.com/settings/tokens → "Generate new token (classic)"
2. 권한(scope): `repo`, `workflow` 체크
3. 발급된 토큰 문자열을 저에게 붙여넣어 주세요(토큰은 나중에 언제든 revoke 가능 — 비밀번호보다 훨씬 안전합니다)

## 6. 저장소 생성 + 푸시 (인증 완료 후 제가 진행)

```bash
gh repo create msa-k3s-lab --public --source=. --remote=origin
git add -A
git commit -m "chore: init msa-k3s-lab"
git push -u origin main
```

## 7. GitHub Actions 확인

푸시하면 `.github/workflows/ci-cd.yaml`이 자동 실행됩니다:
1. `build-and-test` — Gradle 빌드 + 테스트
2. `build-and-push-images` — 3개 서비스 이미지를 각각 빌드해
   `ghcr.io/<내계정>/msa-k3s-lab-{서비스명}:latest`로 push

`GITHUB_TOKEN`은 Actions가 실행마다 자동 발급하는 임시 토큰이라 별도로
비밀번호나 PAT을 등록할 필요가 없습니다.

```bash
gh run watch   # 실행 상태 실시간으로 보기
```

## 8. "CD"는 왜 자동으로 안 되는가 — next.msa와 비교

GitHub Actions는 GitHub의 클라우드에서 도는 러너라, **내 맥에 떠 있는 k3d
클러스터에 직접 접근할 방법이 없습니다.** 그래서 이 랩에서는 이미지가
ghcr에 올라간 뒤 아래처럼 **수동으로** 최신 이미지를 당겨와 배포합니다:

```bash
kubectl set image deployment/product-service \
  product-service=ghcr.io/<내계정>/msa-k3s-lab-product-service:latest
kubectl rollout status deployment/product-service
```

next.msa는 이 간극을 **ArgoCD(GitOps)**로 메웁니다 — 클러스터 안에 떠 있는
ArgoCD가 git 저장소를 스스로 지켜보다가 `newTag`가 바뀌면 클러스터가 스스로
당겨와 반영합니다(사람이 직접 `kubectl set image`를 안 침). 이 랩에서 그
차이를 직접 겪어보는 게 오히려 학습 포인트입니다.

## 9. LGTM 스택 (로그·트레이스·메트릭)

`k8s/lgtm/`에 매니페스트/values가 있다. 이미 이 클러스터에는 설치되어 있고,
다시 만들 때는 아래 순서대로:

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install tempo grafana/tempo                                     # 트레이스, 기본값 그대로 OTLP(4317/4318) 수신
kubectl apply -f k8s/lgtm/mimir.yaml                                  # 메트릭 저장 (단일 프로세스, filesystem)
kubectl apply -f k8s/lgtm/prometheus-agent.yaml                       # 3개 서비스 스크레이프 -> mimir remote_write
helm install loki grafana/loki-stack -f k8s/lgtm/loki-stack-values.yaml  # 로그(Loki+Promtail) + Grafana(+datasource 3개)
```

Grafana 접속:

```bash
kubectl port-forward svc/loki-grafana 3000:80
# http://localhost:3000  (admin / admin)
```

**대시보드**: `k8s/lgtm/grafana-dashboard.json`을 아래처럼 넣으면 서비스 상태·요청량·
실시간 로그·최근 트레이스를 한 화면에서 보는 대시보드가 생긴다.

```bash
curl -s -u admin:admin -X POST -H "Content-Type: application/json" \
  -d @k8s/lgtm/grafana-dashboard.json \
  http://localhost:3000/api/dashboards/db
# http://localhost:3000/d/msa-lgtm-overview
```

전체 요청·관측 흐름을 그림과 함께 설명한 문서는 [FLOW.md](./FLOW.md) 참고.

**각 조각이 하는 일**

| | 역할 | 비고 |
|---|---|---|
| Promtail | 각 Pod의 stdout을 긁어서 Loki로 전송 | DaemonSet, loki-stack이 자동 설치 |
| Loki | 로그 저장/검색 (LogQL) | filesystem 저장, 단일 replica |
| Tempo | 분산 트레이싱 저장/검색 | OTel Java agent가 각 서비스에 이미 붙어 있어 코드 변경 없이 자동 계측됨 |
| Mimir | 메트릭 저장 (Prometheus 호환) | mimir-distributed 헬름차트는 단일 프로세스 모드가 없어서 직접 최소 구성함 |
| Prometheus agent | 3개 서비스 `/actuator/prometheus`를 스크레이프해 Mimir로 push | agent 모드라 자체 조회 기능 없음 |
| Grafana | Loki/Tempo/Mimir 3개 데이터소스로 조회 | `k8s/lgtm/loki-stack-values.yaml`이 자동 프로비저닝 |

**actionId로 로그 상관관계 확인 — "버튼 단위" 로깅**

3개 서비스 모두 `CorrelationFilter`(각 서비스 소스 참고)가:
1. 들어온 요청의 `X-Action-Id` 헤더를 읽는다(없으면 새로 만듦)
2. 이 hop 전용 `requestId`를 매번 새로 만든다
3. 하위 서비스 호출 시 `X-Action-Id`를 그대로 실어 보낸다(`CorrelationPropagationInterceptor`)
4. 로그를 JSON으로 남긴다(`logback-spring.xml` — `logstash-logback-encoder`)

즉 **actionId는 요청 전체에서 안 바뀌고, requestId는 서비스를 거칠 때마다 바뀐다.**
Grafana Explore에서 다음 LogQL로 한 번의 클릭이 남긴 3개 서비스 로그를 전부 볼 수 있다:

```
{namespace="default"} |= "<actionId>"
```

## 10. React 프론트엔드

`frontend/`는 Vite + React 앱이다. 버튼을 누를 때마다 `crypto.randomUUID()`로
actionId를 새로 만들어 `X-Action-Id` 헤더로 gateway에 보내고, 응답 아래에
"Grafana에서 이 클릭의 로그 보기" 링크를 띄운다 — 클릭 한 번으로 그 클릭이 남긴
3개 서비스 로그만 걸러서 바로 보여준다.

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

Gateway(`http://localhost:8888`)와 Grafana(`http://localhost:3000`)가 이미 떠 있어야 한다.

## 11. ArgoCD — git push만으로 자동 배포

8번에서 설명한 "CD가 왜 자동으로 안 되는가"의 답이다. ArgoCD를 클러스터 안에 설치하면
`kubectl set image`를 사람이 직접 안 쳐도 된다.

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml --server-side

# 로컬 랩 전용 — 자체서명 인증서 대신 평문 HTTP로 접속하려고 설정
kubectl -n argocd patch deployment argocd-server --type='json' \
  -p='[{"op": "add", "path": "/spec/template/spec/containers/0/args/-", "value": "--insecure"}]'

kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
kubectl port-forward svc/argocd-server -n argocd 8081:80
# http://localhost:8081  (admin / 위에서 뽑은 비밀번호)

kubectl apply -f k8s/argocd/application.yaml
```

**왜 이미지 태그가 `latest`가 아니라 커밋 SHA로 바뀌는가**: ArgoCD는 git에 적힌 내용과
클러스터의 실제 상태를 비교해서 다르면 동기화한다. 매니페스트가 항상 `:latest`라고
적혀 있으면, 실제 이미지가 바뀌어도 git 쪽 텍스트는 그대로라 ArgoCD 입장에서는
"바뀐 게 없다"— 그래서 `.github/workflows/ci-cd.yaml`의 `bump-manifests` job이 이미지를
push한 뒤 `k8s/*.yaml`의 태그를 그 커밋 SHA로 고쳐서 **다시 git에 커밋**한다. 이 커밋이
있어야 ArgoCD가 "spec이 바뀌었다"고 인식해 동기화한다(next.msa의
`bump-image-tag.sh` → `newTag` 갱신과 똑같은 패턴).

**전체 흐름**: 코드 push → CI 빌드/테스트 → ghcr.io에 이미지 push(amd64+arm64 멀티아키텍처 —
GitHub 러너는 amd64, 로컬 k3d는 Apple Silicon이라 둘 다 필요하다) → CI가 `k8s/*.yaml`의
태그를 커밋 SHA로 바꿔 git에 push → **ArgoCD가 git 변경을 감지해 자동으로 클러스터에 반영**.
사람이 `kubectl`을 칠 일이 하나도 없다.

ArgoCD 대시보드에서 Application을 열면 Sync 상태(Synced/OutOfSync)와 Health 상태
(Healthy/Progressing/Degraded), 그리고 리소스 트리(Deployment→ReplicaSet→Pod)를
실시간으로 볼 수 있다.

## 12. 정리

```bash
k3d cluster delete msa-lab
```

(클러스터를 지우면 LGTM 스택도 함께 사라진다 — 위 9번 명령으로 다시 설치.)
