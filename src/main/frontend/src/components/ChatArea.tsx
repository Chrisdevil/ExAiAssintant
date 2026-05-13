import { useEffect, useRef } from 'react';
import type { Message } from '../types';
import MessageItem from './MessageItem';
import ChatInput from './ChatInput';

interface Props {
  messages: Message[];
  streaming: boolean;
  error: string | null;
  onSend: (content: string) => void;
  onAbort: () => void;
}

export default function ChatArea({ messages, streaming, error, onSend, onAbort }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="chat-area">
      <div className="message-list">
        {messages.length === 0 && (
          <div className="welcome">
            <h2>ExAi Assistant</h2>
            <p>Start a conversation — create a new chat or select one from the sidebar.</p>
          </div>
        )}
        {messages.map((m) => (
          <MessageItem key={m.id} message={m} />
        ))}
        {error && <div className="chat-error">{error}</div>}
        <div ref={bottomRef} />
      </div>
      <ChatInput onSend={onSend} onStop={onAbort} streaming={streaming} />
    </div>
  );
}
