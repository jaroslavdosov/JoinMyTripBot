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

CREATE TABLE trips (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    destination VARCHAR(255) NOT NULL,
    travel_start DATE NOT NULL,
    travel_end DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_trips_destination ON trips(destination);