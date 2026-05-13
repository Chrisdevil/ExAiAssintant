import { useState, useCallback } from 'react';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import { useConversations } from './hooks/useConversations';
import { useChat } from './hooks/useChat';

export default function App() {
  const { conversations, loading, create, remove, refetch } = useConversations();
  const { messages, streaming, error, loadConversation, send, abort, clear } = useChat();
  const [activeId, setActiveId] = useState<string | null>(null);

  const handleSelect = useCallback(
    (id: string) => {
      setActiveId(id);
      loadConversation(id);
    },
    [loadConversation],
  );

  const handleNew = useCallback(async () => {
    const conv = await create();
    if (conv) {
      clear();
      setActiveId(conv.id);
    }
  }, [create, clear]);

  const handleDelete = useCallback(
    async (id: string) => {
      await remove(id);
      if (id === activeId) {
        setActiveId(null);
        clear();
      }
      refetch();
    },
    [remove, activeId, clear, refetch],
  );

  const handleSend = useCallback(
    async (content: string) => {
      let targetId = activeId;
      if (!targetId) {
        const conv = await create();
        if (!conv) return;
        targetId = conv.id;
        setActiveId(targetId);
      }
      send(targetId, content);
      refetch();
    },
    [activeId, create, send, refetch],
  );

  return (
    <div className="app">
      <Sidebar
        conversations={conversations}
        activeId={activeId}
        loading={loading}
        onSelect={handleSelect}
        onDelete={handleDelete}
        onNew={handleNew}
      />
      <ChatArea
        messages={messages}
        streaming={streaming}
        error={error}
        onSend={handleSend}
        onAbort={abort}
      />
    </div>
  );
}
