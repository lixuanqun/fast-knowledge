export const queryKeys = {
  kbs: {
    all: ['kbs'] as const,
    detail: (id: number) => ['kbs', id] as const,
    documents: (id: number) => ['kbs', id, 'documents'] as const,
    members: (id: number) => ['kbs', id, 'members'] as const,
    failedTasks: (id: number) => ['kbs', id, 'failed-tasks'] as const,
    document: (kbId: number, docId: number) => ['kbs', kbId, 'documents', docId] as const,
    documentPreview: (kbId: number, docId: number) =>
      ['kbs', kbId, 'documents', docId, 'preview'] as const,
    documentChunks: (kbId: number, docId: number) =>
      ['kbs', kbId, 'documents', docId, 'chunks'] as const
  },
  dashboard: {
    stats: ['dashboard', 'stats'] as const,
    audits: (limit: number) => ['dashboard', 'audits', limit] as const
  },
  system: {
    config: ['system', 'config'] as const,
    llmProviders: ['system', 'llm-providers'] as const
  },
  workspaces: {
    all: ['workspaces'] as const
  },
  users: {
    all: ['users'] as const
  },
  chat: {
    sessions: ['chat', 'sessions'] as const,
    messages: (sessionId: number) => ['chat', 'sessions', sessionId, 'messages'] as const
  }
}
