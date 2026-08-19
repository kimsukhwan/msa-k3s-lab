import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import mermaid from "mermaid";

mermaid.initialize({ startOnLoad: false, theme: "neutral" });

let mermaidSeq = 0;

// ```mermaid 코드블록을 SVG 다이어그램으로 렌더링한다.
function Mermaid({ code }) {
  const [svg, setSvg] = useState("");
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    mermaidSeq += 1;
    mermaid
      .render(`flow-mermaid-${mermaidSeq}`, code)
      .then((r) => alive && setSvg(r.svg))
      .catch((e) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, [code]);

  if (error) return <pre className="err">mermaid 렌더링 실패: {error}</pre>;
  return <div className="mermaidBlock" dangerouslySetInnerHTML={{ __html: svg }} />;
}

function isMermaid(children) {
  const child = Array.isArray(children) ? children[0] : children;
  return typeof child?.props?.className === "string" && child.props.className.includes("language-mermaid");
}

// 저장소 루트의 md 문서(predev 훅이 public/ 으로 복사)를 읽어 보여주는 문서 화면.
export default function FlowDoc({ file = "/FLOW.md" }) {
  const [flowMd, setFlowMd] = useState(null);
  const [loadError, setLoadError] = useState(null);

  useEffect(() => {
    setFlowMd(null);
    setLoadError(null);
    fetch(file)
      .then((res) => {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.text();
      })
      .then(setFlowMd)
      .catch((e) => setLoadError(e.message));
  }, [file]);

  if (loadError) return <p className="err">{file} 로드 실패: {loadError}</p>;
  if (flowMd === null) return <p className="muted">{file} 읽는 중...</p>;

  return (
    <div className="doc">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          pre: ({ children, ...props }) => (isMermaid(children) ? <>{children}</> : <pre {...props}>{children}</pre>),
          code: ({ className, children, ...props }) => {
            if (className?.includes("language-mermaid")) return <Mermaid code={String(children)} />;
            return (
              <code className={className} {...props}>
                {children}
              </code>
            );
          },
        }}
      >
        {flowMd}
      </ReactMarkdown>
    </div>
  );
}
