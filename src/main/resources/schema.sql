CREATE TABLE IF NOT EXISTS memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL,
    embedding JSON NOT NULL,
    conversation_id VARCHAR(36),
    created_at DATETIME NOT NULL,
    INDEX idx_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
