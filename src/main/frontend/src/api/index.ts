import type { Conversation, ConversationDetail } from '../types';

const BASE = '/api/conversations';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => 'Unknown error');
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.json();
}

export function createConversation(): Promise<{ id: string; title: string }> {
  return request('', { method: 'POST' });
}

export function listConversations(): Promise<Conversation[]> {
  return request('');
}

export function getConversation(id: string): Promise<ConversationDetail> {
  return request(`/${id}`);
}

export function deleteConversation(id: string): Promise<void> {
  return request(`/${id}`, { method: 'DELETE' });
}

export function sendMessage(
  conversationId: string,
  content: string,
  onToken: (token: string) => void,
  onDone: () => void,
  onError: (err: Error) => void,
): AbortController {
  const controller = new AbortController();

  fetch(`${BASE}/${conversationId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) {
        const text = await res.text().catch(() => 'Unknown error');
        throw new Error(text || `HTTP ${res.status}`);
      }
      const reader = res.body?.getReader();
      if (!reader) {
        onDone();
        return;
      }
      const decoder = new TextDecoder();
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          if (line.trim().length > 0) {
            console.log('[SSE] raw line:', JSON.stringify(line));
          }
          if (line.startsWith('data:')) {
            const data = line.slice(5).replace(/^ ?/, '');
            if (data === '[DONE]') {
              onDone();
              return;
            }
            onToken(data);
          }
        }
      }
      onDone();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err instanceof Error ? err : new Error(String(err)));
      }
    });

  return controller;
}
