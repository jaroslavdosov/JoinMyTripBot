CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    description TEXT,
    user_name VARCHAR(255),
    age INT,
    last_notified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    state VARCHAR(50) DEFAULT 'START'

);

CREATE INDEX idx_users_search ON users(gender, age) WHERE is_active = TRUE;