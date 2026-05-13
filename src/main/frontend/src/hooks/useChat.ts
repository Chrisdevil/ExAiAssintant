import { useState, useCallback, useRef } from 'react';
import type { Message, ConversationDetail } from '../types';
import * as api from '../api';

export function useChat() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const loadConversation = useCallback(async (id: string) => {
    setError(null);
    try {
      const detail: ConversationDetail = await api.getConversation(id);
      setMessages(detail.messages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load conversation');
    }
  }, []);

  const send = useCallback((conversationId: string, content: string) => {
    setError(null);
    const userMsg: Message = {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    };
    const assistantMsg: Message = {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMsg, assistantMsg]);
    setStreaming(true);

    const controller = api.sendMessage(
      conversationId,
      content,
      (token) => {
        console.log('[SSE] onToken:', token);
        setMessages((prev) => {
          const updated = [...prev];
          const last = updated[updated.length - 1];
          if (last && last.role === 'assistant') {
            updated[updated.length - 1] = { ...last, content: last.content + token };
          }
          return updated;
        });
      },
      () => {
        console.log('[SSE] onDone');
        setStreaming(false);
        abortRef.current = null;
      },
      (err) => {
        console.log('[SSE] onError:', err.message);
        setError(err.message);
        setStreaming(false);
        abortRef.current = null;
      },
    );
    abortRef.current = controller;
  }, []);

  const abort = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreaming(false);
  }, []);

  const clear = useCallback(() => {
    setMessages([]);
    setError(null);
  }, []);

  return { messages, streaming, error, loadConversation, send, abort, clear };
}
