import type { Conversation } from '../types';

interface Props {
  conversations: Conversation[];
  activeId: string | null;
  loading: boolean;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  onNew: () => void;
}

export default function Sidebar({ conversations, activeId, loading, onSelect, onDelete, onNew }: Props) {
  return (
    <aside className="sidebar">
      <button className="new-chat-btn" onClick={onNew}>
        + New Chat
      </button>
      <div className="conversation-list">
        {loading ? (
          <div className="sidebar-loading">Loading...</div>
        ) : conversations.length === 0 ? (
          <div className="sidebar-empty">No conversations yet</div>
        ) : (
          conversations.map((c) => (
            <div
              key={c.id}
              className={`conversation-item ${c.id === activeId ? 'active' : ''}`}
              onClick={() => onSelect(c.id)}
            >
              <span className="conversation-title">{c.title || 'Untitled'}</span>
              <button
                className="delete-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(c.id);
                }}
                title="Delete conversation"
              >
                &times;
              </button>
            </div>
          ))
        )}
      </div>
    </aside>
  );
}
