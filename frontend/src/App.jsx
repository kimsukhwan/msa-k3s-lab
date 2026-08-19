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
      setAuth({ sub: payload.sub, expiresAt: new Date(payload.exp * 1000) });
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    currentToken = null;
    setAuth(null);
  }

  if (auth) {
    return (
      <div className="card">
        <h3>🔐 인증 — auth-service (JWKS)</h3>
        <div className="actionRow">
          <span className="badge">로그인됨: {auth.sub} · 만료 {auth.expiresAt.toLocaleTimeString()}</span>
          <button onClick={logout}>로그아웃</button>
        </div>
        <p className="muted">이후 모든 호출에 Authorization: Bearer 토큰이 실리고, gateway가 JWKS 공개키로 서명을 검증합니다.</p>
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
        (데모 계정: demo / demo1234)
      </p>
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
      </nav>

      {view === "flow" && <FlowDoc file="/FLOW.md" />}
      {view === "argocd" && <FlowDoc file="/ARGOCD.md" />}

      {view === "demo" && (
        <>
      <LoginCard auth={auth} setAuth={setAuth} />
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
