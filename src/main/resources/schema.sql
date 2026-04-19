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