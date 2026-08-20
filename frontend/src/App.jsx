import { useState } from "react";
import "./App.css";
import FlowDoc from "./FlowDoc.jsx";

const GATEWAY_URL = "http://localhost:8888";
const GRAFANA_URL = "http://localhost:3000";
const LOKI_DATASOURCE_UID = "P8E80F9AEF21F6940";

function grafanaExploreUrl(actionId) {
  const query = `{namespace="default"} |= "${actionId}"`;
  const panes = {
    explore: {
      datasource: LOKI_DATASOURCE_UID,
      queries: [
        {
          refId: "A",
          expr: query,
          queryType: "range",
          datasource: { type: "loki", uid: LOKI_DATASOURCE_UID },
        },
      ],
      range: { from: "now-15m", to: "now" },
    },
  };
  return `${GRAFANA_URL}/explore?schemaVersion=1&panes=${encodeURIComponent(JSON.stringify(panes))}&orgId=1`;
}

// 로그인으로 받은 액세스 토큰 — 메모리에만 둔다(랩: 새로고침하면 다시 로그인).
let currentToken = null;

// 버튼 하나 = actionId 하나. 이 값으로 gateway/order-service/product-service
// 3개 서비스의 로그를 Grafana(Loki)에서 한 번에 묶어 볼 수 있다.
function useAction() {
  const [state, setState] = useState({ status: "idle", actionId: null, result: null, error: null });

  async function run(path) {
    const actionId = crypto.randomUUID();
    setState({ status: "loading", actionId, result: null, error: null });
    try {
      const res = await fetch(GATEWAY_URL + path, {
        headers: {
          "X-Action-Id": actionId,
          ...(currentToken ? { Authorization: `Bearer ${currentToken}` } : {}),
        },
      });
      if (!res.ok) {
        throw new Error("HTTP " + res.status + (res.status === 401 ? " — 로그인이 필요합니다 (gateway가 JWT를 요구)" : ""));
      }
      const data = await res.json();
      setState({ status: "done", actionId, result: data, error: null });
    } catch (e) {
      setState({ status: "error", actionId, result: null, error: e.message });
    }
  }

  return { state, run };
}

// JWT 는 base64url 3조각 — 서명 검증은 gateway 몫이고, 화면은 payload 의 sub/exp 만 읽어 표시한다.
function decodeJwtPayload(token) {
  const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(atob(base64));
}

// 실서비스의 "슈퍼앱 → BFF → 인증" 흐름을 웹 페이지로 축소한 것 —
// 로그인하면 auth-service 가 RS256 JWT 를 발급하고, 이후 모든 호출에 Bearer 로 실린다.
function LoginCard({ auth, setAuth }) {
  const [username, setUsername] = useState("demo");
  const [password, setPassword] = useState("demo1234");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function login() {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(GATEWAY_URL + "/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Action-Id": crypto.randomUUID() },
        body: JSON.stringify({ username, password }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "HTTP " + res.status);
      currentToken = data.accessToken;
      const payload = decodeJwtPayload(data.accessToken);
      setAuth({ sub: payload.sub, custKey: payload.custKey, aud: payload.aud, expiresAt: new Date(payload.exp * 1000) });
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    // 클라이언트가 메모리에서 지우는 것과 별개로, 서버(gateway)에 이 토큰의 jti 를 폐기해달라고
    // 요청한다 — 이게 없으면 "로그아웃"은 눈속임일 뿐, 유출된 토큰은 만료 전까지 계속 유효하다.
    const oldToken = currentToken;
    currentToken = null;
    setAuth(null);
    if (oldToken) {
      fetch(GATEWAY_URL + "/api/auth/logout", {
        method: "POST",
        headers: { Authorization: `Bearer ${oldToken}` },
      }).catch(() => {});
    }
  }

  if (auth) {
    return (
      <div className="card">
        <h3>🔐 인증 — auth-service (JWKS)</h3>
        <div className="actionRow">
          <span className="badge">로그인됨: {auth.sub} · 만료 {auth.expiresAt.toLocaleTimeString()}</span>
          <button onClick={logout}>로그아웃</button>
        </div>
        <p className="muted">
          토큰 클레임 — custKey(슈퍼앱 고객키): <code>{auth.custKey}</code> · aud(수신자): <code>{auth.aud}</code>
          <br />
          이후 모든 호출에 Authorization: Bearer 토큰이 실리고, gateway가 JWKS로 서명을, aud로 "우리 시스템용인지"를 검증합니다.
          검증을 통과하면 gateway가 custKey를 내부 고유키(custId)로 변환해 하위 서비스로 넘깁니다 — custKey 자체는 여기서 끝입니다.
        </p>
      </div>
    );
  }

  return (
    <div className="card">
      <h3>🔐 인증 — auth-service (JWKS)</h3>
      <div className="row">
        <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="아이디" />
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" />
        <button onClick={login} disabled={loading}>{loading ? "로그인 중..." : "로그인"}</button>
      </div>
      {error && <p className="err">{error}</p>}
      <p className="muted">
        로그인 없이 아래 버튼을 누르면 gateway가 401을 돌려줍니다 — 인증 경계가 gateway 하나라는 것을 보는 실험.
        <br />
        데모 계정: <code>demo/demo1234</code>, <code>kim/kim1234</code>(둘 다 내부 매핑 있음) ·{" "}
        <code>guest/guest1234</code>(내부 매핑 <strong>없음</strong> — 로그인 후 주문/조회하면 403, fail-closed 시연)
      </p>
    </div>
  );
}

// "다른 제휴사(partner-mall)용으로 발급된, 서명은 진짜인 토큰"을 우리 API에 재사용하는 공격 시연.
// aud 검증이 없으면 이 토큰도 통과해버린다 — SecurityConfig의 audienceValidator가 막는 지점이 이것.
function AudienceAttackCard() {
  const [username, setUsername] = useState("demo");
  const [password, setPassword] = useState("demo1234");
  const [result, setResult] = useState(null);

  async function tryReuseAttack() {
    setResult({ status: "loading" });
    try {
      const tokenRes = await fetch(GATEWAY_URL + "/api/auth/demo-partner-token", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const tokenData = await tokenRes.json();
      if (!tokenRes.ok) throw new Error(tokenData.error || "토큰 발급 실패");

      const payload = decodeJwtPayload(tokenData.accessToken);
      const apiRes = await fetch(GATEWAY_URL + "/api/products/1", {
        headers: { Authorization: `Bearer ${tokenData.accessToken}` },
      });
      setResult({
        status: "done",
        aud: payload.aud,
        httpStatus: apiRes.status,
        blocked: !apiRes.ok,
      });
    } catch (e) {
      setResult({ status: "error", error: e.message });
    }
  }

  return (
    <div className="card">
      <h3>🧪 실험 — 다른 제휴사용 토큰 재사용 공격</h3>
      <p className="muted">
        같은 슈퍼앱 IdP가 <code>partner-mall</code>(가상의 다른 제휴사)용으로 서명한 토큰을 받아, 우리 은행 API(
        <code>/api/products/1</code>)에 그대로 재사용해봅니다. 서명은 진짜입니다 — <strong>aud 검증이 있어야만</strong> 막힙니다.
      </p>
      <div className="row">
        <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="아이디" />
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" />
        <button onClick={tryReuseAttack}>제휴사용 토큰으로 공격 시도</button>
      </div>
      {result?.status === "loading" && <p className="muted">시도 중...</p>}
      {result?.status === "error" && <p className="err">{result.error}</p>}
      {result?.status === "done" && (
        <p className={result.blocked ? "ok" : "err"}>
          토큰의 aud=<code>{result.aud}</code> (은행이 아님) → 응답 HTTP {result.httpStatus} —{" "}
          {result.blocked ? "✅ 차단됨 (gateway의 audience 검증이 정상 동작)" : "🚨 통과됨 — aud 검증이 없다면 이렇게 뚫립니다"}
        </p>
      )}
    </div>
  );
}

// JWT는 원래 무상태라 "발급 후 취소"가 안 된다 — 로그아웃해도 만료 전까지 토큰이 계속 유효할 수
// 있다는 뜻이다. gateway의 jti 폐기 목록(Redis)이 이 빈틈을 메우는지 실측으로 보여준다.
function RevocationAttackCard() {
  const [username, setUsername] = useState("demo");
  const [password, setPassword] = useState("demo1234");
  const [result, setResult] = useState(null);

  async function tryReplayAfterLogout() {
    setResult({ status: "loading" });
    try {
      const loginRes = await fetch(GATEWAY_URL + "/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const loginData = await loginRes.json();
      if (!loginRes.ok) throw new Error(loginData.error || "로그인 실패");
      const token = loginData.accessToken;

      const beforeRes = await fetch(GATEWAY_URL + "/api/products/1", { headers: { Authorization: `Bearer ${token}` } });
      const logoutRes = await fetch(GATEWAY_URL + "/api/auth/logout", {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      // 로그아웃 후에도 "같은" 토큰을 그대로 들고 재사용 시도 — 폐기 목록이 있어야만 막힌다
      const afterRes = await fetch(GATEWAY_URL + "/api/products/1", { headers: { Authorization: `Bearer ${token}` } });

      setResult({
        status: "done",
        beforeStatus: beforeRes.status,
        logoutStatus: logoutRes.status,
        afterStatus: afterRes.status,
        blocked: !afterRes.ok,
      });
    } catch (e) {
      setResult({ status: "error", error: e.message });
    }
  }

  return (
    <div className="card">
      <h3>🧪 실험 — 로그아웃 후 같은 토큰 재사용</h3>
      <p className="muted">
        로그인 → 정상 호출 → 로그아웃 → <strong>같은 토큰</strong>으로 다시 호출을 시도합니다. 서명·만료는
        여전히 유효한 토큰이라, jti 폐기 목록이 없다면 로그아웃은 클라이언트만 잊어버리는 눈속임에
        불과합니다.
      </p>
      <div className="row">
        <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="아이디" />
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" />
        <button onClick={tryReplayAfterLogout}>로그인 → 로그아웃 → 재사용 시도</button>
      </div>
      {result?.status === "loading" && <p className="muted">시도 중...</p>}
      {result?.status === "error" && <p className="err">{result.error}</p>}
      {result?.status === "done" && (
        <p className={result.blocked ? "ok" : "err"}>
          로그아웃 전 호출 {result.beforeStatus} · 로그아웃 {result.logoutStatus} · 로그아웃 후 같은 토큰 재호출{" "}
          {result.afterStatus} —{" "}
          {result.blocked ? "✅ 차단됨 (jti 폐기 목록 정상 동작)" : "🚨 통과됨 — 폐기 목록이 없다면 이렇게 뚫립니다"}
        </p>
      )}
    </div>
  );
}

function ActionCard({ title, children }) {
  return (
    <div className="card">
      <h3>{title}</h3>
      {children}
    </div>
  );
}

function ResultBlock({ state }) {
  if (state.status === "idle") return <p className="muted">아직 호출 안 함</p>;
  return (
    <div>
      {state.status === "loading" && <p className="muted">호출 중...</p>}
      {state.status === "error" && <p className="err">실패: {state.error}</p>}
      {state.result && <pre>{JSON.stringify(state.result, null, 2)}</pre>}
      {state.actionId && (
        <div className="actionRow">
          <span className="badge">actionId: {state.actionId}</span>
          <a href={grafanaExploreUrl(state.actionId)} target="_blank" rel="noreferrer">
            Grafana에서 이 클릭의 로그 보기 →
          </a>
        </div>
      )}
    </div>
  );
}

export default function App() {
  const productAction = useAction();
  const orderAction = useAction();

  const [productId, setProductId] = useState(1);
  const [orderProductId, setOrderProductId] = useState(2);
  const [view, setView] = useState("demo");
  const [auth, setAuth] = useState(null);

  return (
    <div className="page">
      <h1>msa-k3s-lab — React + LGTM 데모</h1>

      <nav className="tabs">
        <button className={view === "demo" ? "tabOn" : ""} onClick={() => setView("demo")}>
          데모
        </button>
        <button className={view === "flow" ? "tabOn" : ""} onClick={() => setView("flow")}>
          흐름 설명 (FLOW.md)
        </button>
        <button className={view === "argocd" ? "tabOn" : ""} onClick={() => setView("argocd")}>
          ArgoCD 화면 읽는 법
        </button>
        <button className={view === "mtls" ? "tabOn" : ""} onClick={() => setView("mtls")}>
          mTLS 검토안
        </button>
        <button className={view === "arch" ? "tabOn" : ""} onClick={() => setView("arch")}>
          실서비스 아키텍처
        </button>
        <button className={view === "pt" ? "tabOn" : ""} onClick={() => setView("pt")}>
          발표 대본
        </button>
      </nav>

      {view === "flow" && <FlowDoc file="/FLOW.md" />}
      {view === "argocd" && <FlowDoc file="/ARGOCD.md" />}
      {view === "mtls" && <FlowDoc file="/MTLS-REVIEW.md" />}
      {view === "arch" && <FlowDoc file="/ARCHITECTURE.md" />}
      {view === "pt" && <FlowDoc file="/PRESENTATION.md" />}

      {view === "demo" && (
        <>
      <LoginCard auth={auth} setAuth={setAuth} />
      <AudienceAttackCard />
      <RevocationAttackCard />
      <p className="muted">
        버튼을 누를 때마다 브라우저가 actionId(UUID)를 새로 만들어 <code>X-Action-Id</code> 헤더로 보냅니다.
        gateway → order-service → product-service가 이 값을 그대로 전파하며 로그에 남기므로,
        버튼 클릭 하나 = 3개 서비스 로그를 하나로 묶는 열쇠가 됩니다.
      </p>

      <ActionCard title="① 상품 조회 — product-service">
        <div className="row">
          <input type="number" min={1} max={3} value={productId} onChange={(e) => setProductId(e.target.value)} />
          <button onClick={() => productAction.run(`/api/products/${productId}`)}>조회</button>
        </div>
        <ResultBlock state={productAction.state} />
      </ActionCard>

      <ActionCard title="② 주문하기 — gateway → order-service → product-service">
        <div className="row">
          <input
            type="number"
            min={1}
            max={3}
            value={orderProductId}
            onChange={(e) => setOrderProductId(e.target.value)}
          />
          <button onClick={() => orderAction.run(`/api/orders/${orderProductId}`)}>주문</button>
        </div>
        <ResultBlock state={orderAction.state} />
      </ActionCard>
        </>
      )}
    </div>
  );
}
