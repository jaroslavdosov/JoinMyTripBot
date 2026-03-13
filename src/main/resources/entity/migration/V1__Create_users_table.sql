CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    bio TEXT, -- переименовал для соответствия коду
    user_name VARCHAR(255),
    age INT,
    language_code VARCHAR(10) DEFAULT 'ru',
    is_active BOOLEAN DEFAULT TRUE,
    state VARCHAR(50) DEFAULT 'START',
    -- Поля для "прошлого поиска" (шаблона)
    search_city_id BIGINT,
    search_country_id BIGINT,
    search_age_min INT,
    search_age_max INT,
    search_gender VARCHAR(10)
);

CREATE TABLE trips (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    city_id BIGINT,
    country_id BIGINT,
    travel_start DATE NOT NULL,
    travel_end DATE NOT NULL,

    -- Новые поля для уведомлений
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notifications_enabled BOOLEAN DEFAULT FALSE,
    last_seen_trip_id BIGINT,
    pref_gender VARCHAR(10) DEFAULT 'ALL',
    pref_age_min INT DEFAULT 18,
    pref_age_max INT DEFAULT 99,

    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Индексы для ускорения поиска уведомлений
CREATE INDEX idx_trips_notif_lookup ON trips(notifications_enabled, last_seen_trip_id)
WHERE notifications_enabled = TRUE;

-- Индекс для быстрого поиска новых поездок по локации
CREATE INDEX idx_trips_geo_lookup ON trips(city_id, country_id, id);