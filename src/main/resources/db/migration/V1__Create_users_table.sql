CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    description VARCHAR(255),
    user_name VARCHAR(255),
    age INT,
    last_notified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE

);