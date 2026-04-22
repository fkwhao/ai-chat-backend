CREATE TABLE IF NOT EXISTS chat_session (
                                            id TEXT PRIMARY KEY,
                                            title TEXT NOT NULL,
                                            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                            update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
                                            id TEXT PRIMARY KEY,
                                            session_id TEXT NOT NULL,
                                            role TEXT NOT NULL,
                                            content TEXT,
                                            reasoning_content TEXT,
                                            create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_document (
                                            id TEXT PRIMARY KEY,
                                            title TEXT NOT NULL,
                                            source TEXT,
                                            content TEXT NOT NULL,
                                            create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_chunk (
                                         id TEXT PRIMARY KEY,
                                         document_id TEXT NOT NULL,
                                         chunk_index INTEGER NOT NULL,
                                         content TEXT NOT NULL,
                                         token_vector_json TEXT NOT NULL,
                                         token_count INTEGER NOT NULL,
                                         create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_chunk_document_id ON rag_chunk(document_id);

CREATE TABLE IF NOT EXISTS rag_token_stats (
                                               token TEXT PRIMARY KEY,
                                               doc_freq INTEGER NOT NULL DEFAULT 0
);
