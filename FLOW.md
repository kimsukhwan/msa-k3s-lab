# 흐름 설명 — msa-k3s-lab이 실제로 어떻게 도는가

이 문서는 지금까지 만든 걸 "위에서 아래로 하나의 요청이 흘러가는 순서"로 설명합니다.
코드/설정 자체보다 **왜 이렇게 연결되는지**에 집중합니다.

## 1. 전체 그림

```mermaid
flowchart TB
    subgraph Browser["브라우저 (localhost:5173)"]
        React["React 앱"]
    end

    subgraph Cluster["k3d 클러스터 (msa-lab)"]
        GW["gateway :8093"]
        ORD["order-service :8092 (replica 2)"]
        PRD["product-service :8091 (replica 2)"]

        subgraph LGTM["관측 스택"]
            Promtail --> Loki
            GW & ORD & PRD -.OTLP 트레이스.-> Tempo
            PromAgent["Prometheus agent"] -->|remote_write| Mimir
            PromAgent -->|scrape /actuator/prometheus| GW & ORD & PRD
            Promtail -->|stdout 로그 수집| GW & ORD & PRD
        end

        Grafana --> Loki
        Grafana --> Tempo
        Grafana --> Mimir
    end

    React -->|"① fetch + X-Action-Id 헤더"| GW
    GW -->|"② X-Action-Id 그대로 전달"| ORD
    ORD -->|"③ X-Action-Id 그대로 전달"| PRD
```

## 2. 요청 하나가 흘러가는 순서 (예: "주문하기" 버튼)

1. **React**가 버튼 클릭을 감지하고 `crypto.randomUUID()`로 actionId를 하나 만든다.
   (예: `b1ba2f5c-e3b7-4f24-b055-243dd7c99e42`)
2. React가 `fetch("http://localhost:8888/api/orders/2", { headers: { "X-Action-Id": actionId } })`를 호출한다.
   `localhost:8888`은 k3d가 클러스터 안의 Ingress(Traefik)로 뚫어준 포트다.
3. **gateway**가 요청을 받는다. `CorrelationFilter`가:
   - `X-Action-Id` 헤더를 읽는다 (React가 이미 넣어줬으니 그대로 씀)
   - 이 hop 전용 `requestId`를 새로 만든다
   - 둘 다 로그용 MDC에 넣는다
   - `/api/orders/{id}` 컨트롤러가 `order-service`를 호출할 때, `CorrelationPropagationInterceptor`가
     MDC에 있는 `X-Action-Id`를 **그대로** 아웃바운드 요청 헤더에 실어 보낸다
4. **order-service**가 요청을 받는다. 똑같이 자기 `CorrelationFilter`가 실행되고,
   자기 `requestId`를 새로 만든 뒤 `product-service`를 호출할 때 또 `X-Action-Id`를 그대로 전달한다.
5. **product-service**가 요청을 받아 상품 정보를 응답한다. 이때 실제로 어떤 Pod가 응답했는지도
   `servedBy` 필드로 같이 내려준다(replica가 2개라 매번 다른 Pod가 응답할 수 있음).
6. 응답이 order-service → gateway → React 순으로 되돌아오고, React는 결과와 함께
   자신이 만든 actionId, 그리고 "Grafana에서 이 클릭의 로그 보기" 링크를 화면에 띄운다.

**핵심 — actionId와 requestId의 차이**

| | actionId | requestId |
|---|---|---|
| 언제 만드나 | 맨 처음 (React 버튼 클릭) 딱 한 번 | 각 서비스가 요청을 받을 때마다 |
| 값이 바뀌나 | 전체 흐름 동안 **고정** | hop마다 **매번 새로 생성** |
| 용도 | "이 버튼 클릭 하나"를 서비스 3개에 걸쳐 묶는 열쇠 | 그 서비스 안에서 이 요청 하나를 가리키는 이름 |

## 3. 관측 데이터가 흘러가는 순서

같은 요청이 처리되는 동안, 3개 종류의 신호가 각자 다른 파이프라인을 탄다.

### 3-1. 로그 (Loki)

```
서비스 로그 (JSON, stdout) → Promtail(DaemonSet, 각 Pod의 stdout을 긁음) → Loki → Grafana
```

- 각 서비스는 `logback-spring.xml`의 `LogstashEncoder`로 로그를 JSON 한 줄로 찍는다.
  `{"message":"...", "actionId":"...", "requestId":"...", "service":"gateway", ...}`
- Promtail은 코드를 전혀 모른다 — 그냥 컨테이너의 stdout을 그대로 긁어서 Loki에 넣을 뿐이다.
  JSON 파싱은 Grafana에서 쿼리할 때(`| json`) 또는 그냥 문자열 검색(`|= "actionId"`)으로 한다.
- 그래서 `{namespace="default"} |= "<actionId>"` 한 줄이면 3개 서비스 로그가 다 걸린다 —
  actionId라는 "값"이 세 서비스 로그 문자열 안에 똑같이 박혀있기 때문이다.

### 3-2. 트레이스 (Tempo)

```
OTel Java agent(각 Pod에 -javaagent로 붙어 있음, 코드 변경 없음)
  → OTLP/HTTP로 Tempo(:4318)에 직접 전송
```

- 이건 코드 한 줄도 안 건드렸다 — Dockerfile에서 JVM 실행할 때
  `-javaagent:/app/otel-javaagent.jar`만 붙였다.
- 이 agent가 Spring MVC/RestClient 호출을 자동으로 가로채서 "누가 누구를 호출했는지"를
  트레이스(span)로 기록하고, Tempo로 직접 전송한다.
- **actionId/requestId와는 별개의 상관관계 체계다** — 트레이스는 자체적인 `traceID`로
  같은 요청 체인을 하나로 묶는다. (이 랩에서는 둘을 아직 서로 연결하지 않았다 —
  "다음 단계로 해볼만한 것" 참고)

### 3-3. 메트릭 (Mimir)

```
Spring Boot Actuator(/actuator/prometheus 엔드포인트로 지표 노출)
  ← Prometheus agent가 15초마다 스크레이프
  → remote_write로 Mimir에 push
  → Grafana가 Mimir를 Prometheus 호환 데이터소스로 조회
```

- Prometheus agent는 "에이전트 모드"라서 자기 자신은 조회 기능이 없다 —
  긁어서 밀어넣기만 한다. 실제 조회는 Grafana가 Mimir에 대고 한다.
- `up{job="gateway"}` 같은 지표가 방금 만든 대시보드의 상단 3개 상태 타일이다.

## 4. 실제로 확인해보는 법

1. React 앱(`http://localhost:5173`)에서 버튼을 누른다.
2. 결과 아래 actionId와 "Grafana에서 이 클릭의 로그 보기" 링크가 뜬다 — 클릭하면
   Grafana Explore가 그 actionId로 필터링된 로그만 보여준다(3줄, 서비스 3개).
3. Grafana 대시보드(`http://localhost:3000/d/msa-lgtm-overview`)에서는:
   - 상단 3개 타일 — 서비스가 살아있는지(Mimir 기반)
   - 요청 처리 건수 그래프(Mimir)
   - 최근 로그 스트림(Loki, 실시간)
   - 최근 트레이스 테이블(Tempo) — Trace ID를 클릭하면 실제 span waterfall(어느 서비스가
     얼마나 걸렸는지)까지 볼 수 있다

## 5. 배포 흐름 (ArgoCD) — 코드가 어떻게 클러스터까지 도착하는가

```mermaid
flowchart LR
    Dev["git push"] --> CI["GitHub Actions"]
    CI -->|"① 빌드+테스트"| CI
    CI -->|"② 이미지 push (amd64+arm64)"| GHCR["ghcr.io"]
    CI -->|"③ k8s/*.yaml 이미지 태그를 커밋SHA로 갱신 후 push"| Git["GitHub 저장소"]
    ArgoCD["ArgoCD (클러스터 안)"] -.주기적으로 감시.-> Git
    ArgoCD -->|"④ 변경 감지 시 자동 apply"| K8s["k3d 클러스터"]
    K8s -->|이미지 pull| GHCR
```

- ①②는 지금까지의 CI와 같다.
- **③이 핵심이다** — ArgoCD는 git만 본다. 이미지가 ghcr에 새로 올라간 것 자체는
  ArgoCD에게 아무 의미가 없다. `k8s/*.yaml`에 적힌 텍스트(이미지 태그)가 바뀌어야
  "spec이 바뀌었다"고 인식한다. 그래서 CI가 `:latest`가 아니라 매번 새 커밋 SHA로
  태그를 고쳐 쓰고 git에 커밋한다.
- ④에서 사람이 `kubectl`을 칠 일이 없다 — ArgoCD가 스스로 git과 클러스터 상태를
  비교하다가 다르면 동기화(`syncPolicy.automated`)한다.

## 6. 다음 단계로 해볼만한 것

- **트레이스와 로그 연결**: OTel agent가 남긴 `traceID`를 MDC에도 넣으면, 로그 한 줄 →
  버튼을 눌러 그 요청의 정확한 span waterfall로 바로 이동하는 것도 가능해진다
  (Grafana의 "Loki → Tempo derived field" 기능).
- **Pod 단위 메트릭**: 지금은 Service를 스크레이프해서 replica 2개 중 하나만 잡힌다 —
  `kubernetes_sd_configs(role: pod)`로 바꾸면 Pod별로 다 잡을 수 있다.
- **알림(Alerting)**: Mimir 지표 기준으로 "에러율 5% 초과 시 알림" 같은 룰을 Grafana에 걸어보기.

## 7. 주문 이벤트와 캐시 — Kafka·Redis가 끼어드는 지점

HTTP 요청-응답 체인(위 2절)에 두 가지 비동기/가속 장치가 추가됐습니다.

```mermaid
flowchart LR
    ORD["order-service"] -->|"① 주문 확정 후 발행<br/>topic: order-events<br/>(X-Action-Id 헤더 포함)"| K["Kafka<br/>kafka:9092"]
    K -->|"② 구독 (groupId: product-service)"| PRD["product-service<br/>재고 차감 시뮬레이션 로그"]
    PRD2["product-service<br/>상품 조회"] -->|"③ 먼저 캐시 확인 (TTL 60초)"| R["Redis<br/>redis:6379"]
    R -.->|"미스면 원장 조회 후 채움"| PRD2
```

- **주문 이벤트 (Kafka)** — order-service는 주문 응답을 돌려준 것과 별개로 `order-events` 토픽에
  주문 사실을 발행한다. product-service가 이를 구독해 재고 차감을 시뮬레이션한다.
  요청을 만든 actionId가 **Kafka 레코드 헤더(X-Action-Id)** 로도 전파되므로, Grafana에서
  같은 actionId로 검색하면 HTTP hop 로그와 **비동기 hop 로그까지 한 줄에 묶인다** —
  발행이 실패해도 주문 응답은 실패하지 않는다(경고 로그만 남음).
- **상품 캐시 (Redis)** — product-service는 상품 조회 시 `product:{id}` 키를 먼저 본다.
  적중하면 원장 조회(200ms 지연 시뮬레이션)를 건너뛰고, 미스면 원장을 읽어 60초 TTL로 채운다.
  응답의 `source` 필드가 `redis-cache`/`origin` 으로 표시되므로 화면에서 두 번 연속 조회해보면
  차이가 바로 보인다. **Redis가 죽어도 조회는 원장으로 계속 동작한다** — 캐시는 가속 장치이지
  의존성이 아니다.

## 8. 인증 — 로그인부터 401까지 (JWKS 패턴)

실서비스의 "슈퍼앱 → BFF 진입 → 토큰 검증 → 분기" 구조를 웹 페이지로 축소한 것입니다.
**인증 경계는 gateway 하나** — 통과한 요청만 하위 서비스로 내려가고, order/product는
토큰을 다시 검사하지 않습니다(클러스터 내부 신뢰).

```mermaid
sequenceDiagram
    participant R as React (웹)
    participant G as gateway :8093
    participant A as auth-service :8094
    participant O as order-service

    R->>G: ① POST /api/auth/login (demo/demo1234)
    G->>A: 그대로 전달 (인증 면제 경로)
    A-->>R: ② RS256 JWT (30분)
    Note over G,A: gateway는 A의 /.well-known/jwks.json에서<br/>공개키를 받아 캐시해 둔다
    R->>G: ③ GET /api/orders/2 + Authorization: Bearer
    G->>G: ④ JWKS 공개키로 서명·만료 검증
    G->>O: ⑤ 통과한 요청만 라우팅
    R->>G: (토큰 없이 호출하면) ⑥ 401
```

- **JWKS의 요점** — auth-service는 개인키로 서명만 하고, gateway는 `/.well-known/jwks.json`의
  **공개키로 검증만** 한다. 두 서비스가 비밀을 공유하지 않으므로 검증측이 늘어나도(모바일 BFF,
  외부 BFF…) 키 배포 문제가 없다. 실서비스에서 슈퍼앱 BFF가 같은 방식으로 검증하는 이유.
- **왜 gateway에서만 검증하나** — 토큰 검사를 서비스마다 반복하면 모든 서비스가 auth를 의존하게
  된다. 외부 진입점이 gateway 하나뿐이므로(order/product는 ClusterIP) 경계 한 곳 검증으로 충분하다.
- **랩의 제약** — auth-service의 서명키는 파드 메모리에만 있어 replica 1 고정이고, 재기동하면
  기존 토큰이 전부 무효가 된다(재로그인). 실서비스는 키를 공유 저장소에 두고 kid 기반으로 회전한다.
- 화면에서 해볼 것: 로그인 없이 주문 → **401**, 로그인 후 주문 → 정상. auth-service 파드를
  지워 재기동시킨 뒤 기존 토큰으로 호출 → 401(키 교체 체험).
