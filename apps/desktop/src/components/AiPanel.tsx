// AI 대화창(오른쪽 패널). 플러그인 설치·키·대화·고치기 제안 검토를 한곳에서 한다(ADR 0009).
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { ask } from "@tauri-apps/plugin-dialog";
import {
  aiInstall,
  aiStatus,
  aiUninstall,
  applyProposal,
  cancelChat,
  deleteKey,
  isAgent,
  listModels,
  mcpConfig,
  newConversationId,
  rejectProposal,
  resetChat,
  sendChat,
  setKey,
  type AiStatus,
  type ChatEventEnvelope,
  type ModelInfo,
  type Proposal,
  type ProviderId,
  type Target,
  type Usage,
} from "../ai";
import { Icon } from "./Icon";
import { locale, t } from "../i18n";

interface Props {
  doc: { id: number; fileName: string; canEdit: boolean } | null;
  onApplied(doc: number): void;
  onClose(): void;
}

type Item =
  | { kind: "user"; text: string }
  | { kind: "assistant"; text: string; done: boolean }
  | { kind: "tool"; id: string; name: string; state: "running" | "ok" | "fail"; error?: string }
  | { kind: "notice"; text: string }
  | { kind: "error"; text: string }
  | { kind: "proposal"; proposal: Proposal; state: "pending" | "applied" | "rejected" | "failed"; error?: string };

const KEYED: ProviderId[] = ["anthropic", "openai", "gemini"];
const TARGETS: Target[] = ["anthropic", "openai", "gemini", "ollama", "acp:claude-code", "acp:codex", "acp:gemini"];
const KEY_SITES: Record<string, string> = {
  anthropic: "console.anthropic.com",
  openai: "platform.openai.com",
  gemini: "aistudio.google.com",
};

const store = {
  get(key: string): string | null {
    try {
      return localStorage.getItem(`papy.ai.${key}`);
    } catch {
      return null;
    }
  },
  set(key: string, value: string) {
    try {
      localStorage.setItem(`papy.ai.${key}`, value);
    } catch {
      // 저장하지 못하면 이번 창에서만 쓴다.
    }
  },
};

/** 에이전트가 붙인 이름(`mcp__papy__find_text`)에서 도구 이름만. */
const toolName = (name: string) => name.split(/__|\//).pop() ?? name;
const clip = (text: string, max = 280) => (text.length > max ? `${text.slice(0, max)}…` : text);
const number = (n: number) => n.toLocaleString(locale);

export function AiPanel({ doc, onApplied, onClose }: Props) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [view, setView] = useState<"chat" | "settings">("chat");
  const [installing, setInstalling] = useState<string | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [target, setTarget] = useState<Target>(() => (store.get("target") as Target | null) ?? "anthropic");
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [model, setModel] = useState("");
  const [ollamaBase, setOllamaBase] = useState(() => store.get("ollamaBase") ?? "");
  const [conversation, setConversation] = useState(newConversationId);
  const [items, setItems] = useState<Item[]>([]);
  const [running, setRunning] = useState(false);
  const [draft, setDraft] = useState("");
  const [usage, setUsage] = useState<{ total: Usage; cost: number | null } | null>(null);
  const [consent, setConsent] = useState<{ text: string } | null>(null);
  const consented = useRef(new Set<string>());
  const conversationRef = useRef(conversation);
  conversationRef.current = conversation;
  const list = useRef<HTMLDivElement>(null);

  const refresh = useCallback(async () => {
    try {
      setStatus(await aiStatus());
    } catch (caught) {
      setProblem(String(caught));
    }
  }, []);
  useEffect(() => void refresh(), [refresh]);

  const ready = !!status?.installed && !status.outdated && !!status.info;
  const provider = isAgent(target) ? null : target;
  const providerInfo = status?.info?.providers.find((p) => p.id === provider);
  const hasKey = !provider || provider === "ollama" || !!status?.keys?.[provider];

  // 모델 목록: 고른 회사가 바뀌거나 키를 넣었을 때.
  useEffect(() => {
    if (!ready || !provider || !hasKey) {
      setModels([]);
      return;
    }
    setModel(store.get(`model.${provider}`) ?? providerInfo?.defaultModel ?? "");
    let stale = false;
    listModels(provider, provider === "ollama" ? ollamaBase || undefined : undefined)
      .then((found) => {
        if (stale) return;
        setModels(found);
        if (provider === "ollama" && !store.get("model.ollama") && found[0]) setModel(found[0].id);
      })
      .catch(() => !stale && setModels([]));
    return () => {
      stale = true;
    };
  }, [ready, provider, hasKey, ollamaBase, providerInfo?.defaultModel]);

  // 플러그인 알림.
  useEffect(() => {
    const stops = [
      listen<ChatEventEnvelope>("ai-event", ({ payload }) => {
        const { event } = payload;
        if (event.type === "pluginStopped") {
          setRunning(false);
          void refresh();
          return;
        }
        if (payload.conversation !== conversationRef.current) return;
        setItems((old) => reduce(old, event));
        if (event.type === "usage") setUsage((u) => ({ total: event.total, cost: u?.cost ?? null }));
        if (event.type === "done") {
          const { outcome } = event;
          if (outcome.total) setUsage({ total: outcome.total, cost: outcome.totalCost ?? null });
        }
        if (event.type === "done" || event.type === "error" || event.type === "cancelled") setRunning(false);
      }),
      listen<Proposal>("ai-proposal", ({ payload }) => {
        if (payload.conversation !== conversationRef.current) return;
        setItems((old) => [...old, { kind: "proposal", proposal: payload, state: "pending" }]);
      }),
      listen<{ stage: string }>("ai-install", ({ payload }) => setInstalling(payload.stage)),
    ];
    return () => stops.forEach((stop) => void stop.then((unlisten) => unlisten()));
  }, [refresh]);

  useEffect(() => {
    list.current?.scrollTo({ top: list.current.scrollHeight });
  }, [items]);

  const install = async () => {
    setProblem(null);
    setInstalling("download");
    try {
      setStatus(await aiInstall());
    } catch (caught) {
      setProblem(String(caught));
    } finally {
      setInstalling(null);
    }
  };

  const send = async (text: string, confirmed = false) => {
    if (!text.trim() || running) return;
    const external = isAgent(target) || !!providerInfo?.external;
    const consentKey = `${conversation}|${doc?.id ?? "-"}|${target}`;
    if (external && doc && !confirmed && !consented.current.has(consentKey)) {
      setConsent({ text });
      return;
    }
    consented.current.add(consentKey);
    setDraft("");
    setItems((old) => [...old, { kind: "user", text }]);
    setRunning(true);
    try {
      await sendChat(doc?.id ?? null, doc?.fileName ?? null, {
        conversation,
        provider: target,
        model: provider ? model : undefined,
        base: provider === "ollama" && ollamaBase ? ollamaBase : undefined,
        text,
        language: locale,
      });
    } catch (caught) {
      setRunning(false);
      setItems((old) => [...old, { kind: "error", text: String(caught) }]);
    }
  };

  const newChat = () => {
    if (running) void cancelChat(conversation);
    void resetChat(conversation);
    setConversation(newConversationId());
    setItems([]);
    setUsage(null);
    setRunning(false);
  };

  const decide = async (item: Extract<Item, { kind: "proposal" }>, apply: boolean) => {
    const update = (patch: Partial<Extract<Item, { kind: "proposal" }>>) =>
      setItems((old) =>
        old.map((other) =>
          other.kind === "proposal" && other.proposal.id === item.proposal.id ? { ...other, ...patch } : other,
        ),
      );
    if (!apply) {
      await rejectProposal(item.proposal.id);
      update({ state: "rejected" });
      return;
    }
    try {
      const changed = await applyProposal(item.proposal.id);
      update({ state: "applied" });
      onApplied(changed);
    } catch (caught) {
      const message = typeof caught === "object" && caught && "message" in caught ? String(caught.message) : String(caught);
      update({ state: "failed", error: message });
    }
  };

  const targetLabel = (value: Target) => t.ai.targets[value.replace("acp:", "acp_") as keyof typeof t.ai.targets];
  const agentInfo = isAgent(target) ? status?.info?.agents.find((a) => `acp:${a.id}` === target) : undefined;

  return (
    <aside className="ai-panel" aria-label={t.ai.title}>
      <div className="ai-head">
        <strong>{t.ai.title}</strong>
        <span className="spacer" />
        {ready && view === "chat" && (
          <button className="icon small" onClick={newChat} title={t.ai.newChat}>
            <Icon name="plus" size={16} />
          </button>
        )}
        {status?.installed && (
          <button
            className="icon small"
            aria-pressed={view === "settings"}
            onClick={() => setView(view === "chat" ? "settings" : "chat")}
            title={t.ai.settings}
          >
            <Icon name="gear" size={16} />
          </button>
        )}
        <button className="icon small" onClick={onClose} title={t.common.close}>
          <Icon name="close" size={16} />
        </button>
      </div>

      {!status ? (
        <div className="ai-empty">{t.common.loading}</div>
      ) : !status.installed || status.outdated ? (
        <div className="ai-intro">
          <div className="dialog-icon">
            <Icon name="sparkle" size={22} />
          </div>
          <h3>{status.outdated ? t.ai.updateTitle : t.ai.installTitle}</h3>
          <p>{t.ai.installBody}</p>
          <ul>
            {t.ai.installPoints.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
          {status.supported ? (
            <button className="primary" onClick={() => void install()} disabled={!!installing}>
              {installing ? (
                <>
                  <span className="spinner" /> {t.ai.installStage[installing as keyof typeof t.ai.installStage] ?? installing}
                </>
              ) : status.outdated ? (
                t.ai.update(status.appVersion)
              ) : (
                t.ai.install
              )}
            </button>
          ) : (
            <p className="error-text">{t.ai.unsupported}</p>
          )}
          {problem && <p className="error-text">{problem}</p>}
        </div>
      ) : view === "settings" || !status.info ? (
        <Settings
          status={status}
          ollamaBase={ollamaBase}
          onOllamaBase={(value) => {
            setOllamaBase(value);
            store.set("ollamaBase", value);
          }}
          onChanged={refresh}
          onUninstalled={() => {
            setView("chat");
            void refresh();
          }}
        />
      ) : (
        <>
          <div className="ai-target">
            <select
              value={target}
              aria-label={t.ai.target}
              onChange={(event) => {
                const next = event.target.value as Target;
                setTarget(next);
                store.set("target", next);
              }}
            >
              {TARGETS.map((value) => (
                <option key={value} value={value}>
                  {targetLabel(value)}
                </option>
              ))}
            </select>
            {provider && hasKey && (
              <input
                list="ai-models"
                value={model}
                aria-label={t.ai.model}
                placeholder={t.ai.model}
                onChange={(event) => {
                  setModel(event.target.value);
                  store.set(`model.${provider}`, event.target.value);
                }}
              />
            )}
            <datalist id="ai-models">
              {models.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </datalist>
          </div>
          {provider && !hasKey && (
            <div className="ai-note">
              {t.ai.needKey(targetLabel(target))}{" "}
              <button className="link" onClick={() => setView("settings")}>
                {t.ai.openSettings}
              </button>
            </div>
          )}
          {agentInfo && !agentInfo.available && <div className="ai-note">{t.ai.agentMissing(t.ai.agentSetup[agentInfo.id] ?? agentInfo.setup)}</div>}
          {agentInfo?.available && items.length === 0 && <div className="ai-note">{t.ai.agentNote}</div>}

          <div className="ai-messages" ref={list}>
            {items.length === 0 && (
              <div className="ai-empty">
                <p>{doc ? t.ai.emptyWithDoc(doc.fileName) : t.ai.emptyNoDoc}</p>
                {doc && (
                  <div className="ai-suggestions">
                    {t.ai.suggestions.map((suggestion) => (
                      <button key={suggestion} className="ghost small" onClick={() => void send(suggestion)} disabled={running}>
                        {suggestion}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {items.map((item, index) => (
              <ItemView key={index} item={item} doc={doc} onDecide={decide} />
            ))}
            {running && (
              <div className="ai-working">
                <span className="spinner" /> {t.ai.working}
              </div>
            )}
          </div>
          {usage && (
            <div className="ai-usage">
              {t.ai.usage(number(usage.total.input + usage.total.cacheRead + usage.total.cacheWrite), number(usage.total.output))}
              {usage.cost != null && ` · ${t.ai.cost(usage.cost < 0.01 ? "<$0.01" : `$${usage.cost.toFixed(2)}`)}`}
            </div>
          )}
          <form
            className="ai-input"
            onSubmit={(event) => {
              event.preventDefault();
              void send(draft);
            }}
          >
            <textarea
              value={draft}
              rows={3}
              placeholder={t.ai.placeholder}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                  event.preventDefault();
                  void send(draft);
                }
              }}
            />
            {running ? (
              <button type="button" className="icon" onClick={() => void cancelChat(conversation)} title={t.ai.stop}>
                <Icon name="stop" />
              </button>
            ) : (
              <button type="submit" className="icon primary" disabled={!draft.trim() || (!!provider && !hasKey)} title={t.ai.send}>
                <Icon name="send" />
              </button>
            )}
          </form>
        </>
      )}

      {consent && doc && (
        <div className="backdrop">
          <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="ai-consent-title">
            <h2 id="ai-consent-title">{t.ai.consentTitle}</h2>
            <p>{t.ai.consentBody(doc.fileName, targetLabel(target))}</p>
            <div className="dialog-actions">
              <button className="ghost" onClick={() => setConsent(null)}>
                {t.common.cancel}
              </button>
              <button
                className="primary"
                onClick={() => {
                  const { text } = consent;
                  setConsent(null);
                  void send(text, true);
                }}
              >
                {t.ai.consentSend}
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}

/** 알림 하나를 대화 목록에 반영한다. */
export function reduce(items: Item[], event: ChatEventEnvelope["event"]): Item[] {
  const last = items[items.length - 1];
  switch (event.type) {
    case "text":
      if (last?.kind === "assistant" && !last.done) {
        return [...items.slice(0, -1), { ...last, text: last.text + event.text }];
      }
      return [...items, { kind: "assistant", text: event.text, done: false }];
    case "toolStart": {
      const closed = last?.kind === "assistant" ? [...items.slice(0, -1), { ...last, done: true }] : items;
      if (items.some((item) => item.kind === "tool" && item.id === event.id)) return closed;
      return [...closed, { kind: "tool", id: event.id, name: toolName(String(event.name ?? "")), state: "running" }];
    }
    case "toolEnd":
      return items.map((item) =>
        item.kind === "tool" && item.id === event.id
          ? { ...item, state: event.result.ok ? "ok" : "fail", error: event.result.error }
          : item,
      );
    case "notice":
      return [...items, { kind: "notice", text: t.ai.notices[event.code]?.(event.detail ?? "") ?? event.code }];
    case "status":
      return [...items, { kind: "notice", text: t.ai.agentStarting(event.detail ?? "") }];
    case "error":
      return [...items, { kind: "error", text: event.message }];
    case "cancelled":
      return [...items, { kind: "notice", text: t.ai.cancelled }];
    case "done":
      return last?.kind === "assistant" ? [...items.slice(0, -1), { ...last, done: true }] : items;
    default:
      return items;
  }
}

function ItemView({
  item,
  doc,
  onDecide,
}: {
  item: Item;
  doc: Props["doc"];
  onDecide(item: Extract<Item, { kind: "proposal" }>, apply: boolean): void;
}) {
  switch (item.kind) {
    case "user":
      return <div className="ai-msg user">{item.text}</div>;
    case "assistant":
      return <div className="ai-msg assistant">{item.text}</div>;
    case "tool":
      return (
        <div className={`ai-tool ${item.state}`} title={item.error}>
          <span className="ai-tool-mark">{item.state === "running" ? <span className="spinner" /> : item.state === "ok" ? "✓" : "!"}</span>
          <span>
            {t.ai.tools[item.name as keyof typeof t.ai.tools] ?? item.name}
            {item.state === "fail" && ` — ${t.ai.toolFailed}`}
          </span>
        </div>
      );
    case "notice":
      return <div className="ai-notice">{item.text}</div>;
    case "error":
      return <div className="ai-notice error-text">{item.text}</div>;
    case "proposal": {
      const { proposal } = item;
      const otherDoc = !doc || doc.id !== proposal.doc;
      return (
        <div className={`ai-proposal ${item.state}`}>
          <div className="ai-proposal-title">
            <Icon name="edit" size={14} /> {t.ai.proposalTitle(t.ai.tools[proposal.tool as keyof typeof t.ai.tools] ?? proposal.tool)}
          </div>
          {proposal.changes.map((change) => (
            <div key={change.paragraph} className="ai-change">
              <span className="muted">{t.ai.paragraph(change.paragraph)}</span>
              {change.before === change.after ? (
                <div>{clip(change.after)}</div>
              ) : (
                <>
                  <del>{clip(change.before) || t.ai.emptyParagraph}</del>
                  <ins>{clip(change.after) || t.ai.emptyParagraph}</ins>
                </>
              )}
            </div>
          ))}
          {item.state === "pending" ? (
            <div className="dialog-actions">
              <button className="ghost small" onClick={() => onDecide(item, false)}>
                {t.ai.reject}
              </button>
              <button className="primary small" disabled={otherDoc || !doc?.canEdit} onClick={() => onDecide(item, true)}>
                {t.ai.apply}
              </button>
            </div>
          ) : (
            <div className={item.state === "failed" ? "error-text" : "muted"}>
              {item.state === "applied" ? t.ai.applied : item.state === "rejected" ? t.ai.rejected : t.ai.failed(item.error ?? "")}
            </div>
          )}
          {item.state === "pending" && otherDoc && <div className="muted">{t.ai.otherDocument}</div>}
        </div>
      );
    }
  }
}

function Settings({
  status,
  ollamaBase,
  onOllamaBase,
  onChanged,
  onUninstalled,
}: {
  status: AiStatus;
  ollamaBase: string;
  onOllamaBase(value: string): void;
  onChanged(): Promise<void>;
  onUninstalled(): void;
}) {
  const [keys, setKeys] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [mcp, setMcp] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    void mcpConfig().then((config) =>
      setMcp(config ? JSON.stringify({ mcpServers: { papy: config } }, null, 2) : null),
    );
  }, []);

  const run = async (label: string, job: () => Promise<unknown>) => {
    setBusy(label);
    setProblem(null);
    try {
      await job();
      await onChanged();
    } catch (caught) {
      setProblem(String(caught));
    } finally {
      setBusy(null);
    }
  };

  const agents = useMemo(() => status.info?.agents ?? [], [status.info]);

  return (
    <div className="ai-settings">
      <section>
        <h4>{t.ai.keysTitle}</h4>
        <p className="muted">{t.ai.keysBody}</p>
        {KEYED.map((id) => (
          <form
            key={id}
            className="ai-key"
            onSubmit={(event) => {
              event.preventDefault();
              const value = keys[id]?.trim();
              if (value) void run(id, () => setKey(id, value)).then(() => setKeys((old) => ({ ...old, [id]: "" })));
            }}
          >
            <label>
              <span>
                {t.ai.targets[id]} <span className={`chip ${status.keys?.[id] ? "on" : ""}`}>{status.keys?.[id] ? t.ai.keySaved : t.ai.keyMissing}</span>
              </span>
              <input
                type="password"
                autoComplete="off"
                placeholder={t.ai.keyPlaceholder(KEY_SITES[id]!)}
                value={keys[id] ?? ""}
                onChange={(event) => setKeys((old) => ({ ...old, [id]: event.target.value }))}
              />
            </label>
            <button type="submit" className="ghost small" disabled={!keys[id]?.trim() || busy === id}>
              {t.common.save}
            </button>
            {status.keys?.[id] && (
              <button type="button" className="ghost small" onClick={() => void run(id, () => deleteKey(id))} disabled={busy === id}>
                {t.ai.deleteKey}
              </button>
            )}
          </form>
        ))}
        <label className="ai-key">
          <span>{t.ai.targets.ollama}</span>
          <input value={ollamaBase} placeholder="http://127.0.0.1:11434/v1" onChange={(event) => onOllamaBase(event.target.value)} />
        </label>
      </section>

      <section>
        <h4>{t.ai.agentsTitle}</h4>
        <p className="muted">{t.ai.agentsBody}</p>
        {agents.map((agent) => (
          <div key={agent.id} className="ai-agent">
            <span>{agent.name}</span>
            <span className={`chip ${agent.available ? "on" : ""}`}>{agent.available ? t.ai.agentReady : t.ai.agentNotFound}</span>
            {!agent.available && <div className="muted">{t.ai.agentSetup[agent.id] ?? agent.setup}</div>}
          </div>
        ))}
      </section>

      <section>
        <h4>{t.ai.mcpTitle}</h4>
        <p className="muted">{t.ai.mcpBody}</p>
        {mcp && (
          <>
            <pre className="ai-code">{mcp}</pre>
            <button
              className="ghost small"
              onClick={() =>
                void navigator.clipboard.writeText(mcp).then(() => {
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                })
              }
            >
              <Icon name="copy" size={14} /> {copied ? t.ai.copied : t.ai.copy}
            </button>
          </>
        )}
      </section>

      <section>
        <h4>{t.ai.pluginTitle}</h4>
        <p className="muted">
          {t.ai.pluginVersion(status.version ?? "?")}
          {status.dev && " (dev)"}
        </p>
        {status.error && <p className="error-text">{status.error}</p>}
        <button
          className="ghost small danger"
          disabled={busy === "uninstall"}
          onClick={() =>
            void ask(t.ai.uninstallQuestion, { title: t.ai.uninstall, kind: "warning" }).then((yes) => {
              if (yes) void run("uninstall", () => aiUninstall(true)).then(onUninstalled);
            })
          }
        >
          <Icon name="trash" size={14} /> {t.ai.uninstall}
        </button>
      </section>
      {problem && <p className="error-text">{problem}</p>}
    </div>
  );
}
