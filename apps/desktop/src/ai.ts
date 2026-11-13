// AI 플러그인 command 래퍼(src-tauri/src/ai.rs, lib.rs 의 ai_*). 연결·키·대화는 플러그인이 맡는다.
import { invoke } from "@tauri-apps/api/core";

export type ProviderId = "anthropic" | "openai" | "gemini" | "ollama";
export type AgentId = "claude-code" | "codex" | "gemini";
/** 대화 상대: API(`anthropic` 등) 또는 ACP 에이전트(`acp:claude-code` 등) */
export type Target = ProviderId | `acp:${AgentId}`;

export interface ProviderInfo {
  id: ProviderId;
  needsKey: boolean;
  external: boolean;
  defaultModel: string;
  defaultBase: string;
}

export interface AgentInfo {
  id: AgentId;
  name: string;
  command: string;
  setup: string;
  available: boolean;
}

export interface AiStatus {
  installed: boolean;
  version: string | null;
  appVersion: string;
  supported: boolean;
  dev: boolean;
  path: string | null;
  outdated?: boolean;
  error?: string;
  info?: { version: string; providers: ProviderInfo[]; agents: AgentInfo[] };
  keys?: Partial<Record<ProviderId, boolean>>;
}

export interface ModelInfo {
  id: string;
  name: string;
}

export interface Usage {
  input: number;
  output: number;
  cacheRead: number;
  cacheWrite: number;
}

export interface Change {
  paragraph: string;
  before: string;
  after: string;
}

export interface Proposal {
  conversation: string;
  id: number;
  doc: number;
  tool: string;
  summary: string;
  changes: Change[];
}

/** 플러그인이 보내는 대화 알림(`chat/event`). */
export type ChatEvent =
  | { type: "text"; text: string }
  | { type: "thinking"; text: string }
  | { type: "toolStart"; id: string; name: string; input?: unknown }
  | { type: "toolEnd"; id: string; name: string; result: { ok: boolean; error?: string; status?: string | null } }
  | { type: "usage"; usage: Usage; total: Usage }
  | { type: "notice"; code: "refusal" | "maxTokens" | "maxSteps" | "permissionDenied"; detail?: string | null }
  | { type: "status"; code: "agentStarting"; detail?: string }
  | { type: "plan"; entries: unknown }
  | {
      type: "done";
      outcome: { stop: unknown; usage?: Usage; total?: Usage; cost?: number | null; totalCost?: number | null };
    }
  | { type: "error"; message: string }
  | { type: "cancelled" }
  | { type: "pluginStopped" };

export interface ChatEventEnvelope {
  conversation: string | null;
  event: ChatEvent;
}

export const aiStatus = () => invoke<AiStatus>("ai_status");
export const aiInstall = () => invoke<AiStatus>("ai_install");
export const aiUninstall = (forgetKeys: boolean) => invoke<void>("ai_uninstall", { forgetKeys });
const request = <T>(method: string, params: Record<string, unknown> = {}) =>
  invoke<T>("ai_request", { method, params });

export const setKey = (provider: ProviderId, key: string) =>
  request<Partial<Record<ProviderId, boolean>>>("keys/set", { provider, key });
export const deleteKey = (provider: ProviderId) =>
  request<Partial<Record<ProviderId, boolean>>>("keys/delete", { provider });
export const listModels = (provider: ProviderId, base?: string) =>
  request<{ models: ModelInfo[] }>("models/list", { provider, base }).then((reply) => reply.models);
export const detectAgents = () => request<AgentInfo[]>("agents/detect");
export const cancelChat = (conversation: string) => request<unknown>("chat/cancel", { conversation });

export interface SendOptions {
  conversation: string;
  provider: Target;
  model?: string;
  base?: string;
  text: string;
  language: string;
}

export const sendChat = (doc: number | null, name: string | null, params: SendOptions) =>
  invoke<{ started: boolean }>("ai_send", { doc, name, params });
export const resetChat = (conversation: string) => invoke<void>("ai_reset", { conversation });
export const applyProposal = (id: number) => invoke<number>("ai_apply_proposal", { id });
export const rejectProposal = (id: number) => invoke<void>("ai_reject_proposal", { id });
export const mcpConfig = () =>
  invoke<{ command: string; args: string[]; env: Record<string, string> } | null>("ai_mcp_config");

/** ACP 에이전트인가. */
export const isAgent = (target: Target): target is `acp:${AgentId}` => target.startsWith("acp:");

export const newConversationId = () =>
  `c${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
