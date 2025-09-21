-- XMPP Server Database Schema - H2 Development Version
-- H2 in-memory database schema for user management and authentication

-- Users table for XMPP authentication
CREATE TABLE IF NOT EXISTS xmpp_users (
    jid VARCHAR(255) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    password_salt VARCHAR(255) NOT NULL,
    hash_algorithm VARCHAR(32) NOT NULL DEFAULT 'SCRAM-SHA-256',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Index for faster username lookups
CREATE INDEX IF NOT EXISTS idx_xmpp_users_username ON xmpp_users(username);

-- Index for active users (H2 doesn't support partial indexes, so we create a regular index)
CREATE INDEX IF NOT EXISTS idx_xmpp_users_active ON xmpp_users(active);

-- Sessions table for tracking active connections
CREATE TABLE IF NOT EXISTS xmpp_sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_jid VARCHAR(255) NOT NULL,
    resource VARCHAR(64),
    full_jid VARCHAR(320) NOT NULL, -- user@domain/resource
    connection_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    presence VARCHAR(32) NOT NULL DEFAULT 'unavailable',
    FOREIGN KEY (user_jid) REFERENCES xmpp_users(jid)
);

-- Index for user sessions lookup
CREATE INDEX IF NOT EXISTS idx_xmpp_sessions_user_jid ON xmpp_sessions(user_jid);

-- Index for full JID lookups
CREATE INDEX IF NOT EXISTS idx_xmpp_sessions_full_jid ON xmpp_sessions(full_jid);

-- Index for active sessions
CREATE INDEX IF NOT EXISTS idx_xmpp_sessions_activity ON xmpp_sessions(last_activity DESC);

-- Insert demo user for testing (password: "test123")
-- This uses a simplified hash for demo purposes
MERGE INTO xmpp_users (jid, username, password_hash, password_salt, hash_algorithm, created_at, active)
KEY (jid)
VALUES (
    'testuser@localhost',
    'testuser',
    'tDVJ/CqM8paFACTeEk/dRx8JzF6yCnQCJMQQg3+LF1E=', -- Base64 SHA-256 hash of "test123" + salt
    'ZGVtb3NhbHQ=', -- Base64 encoded "demosalt"
    'SHA-256',
    CURRENT_TIMESTAMP,
    TRUE
);

-- Insert admin user for testing (password: "admin123")
MERGE INTO xmpp_users (jid, username, password_hash, password_salt, hash_algorithm, created_at, active)
KEY (jid)
VALUES (
    'admin@localhost',
    'admin',
    'K8J+m1FQzFKTz3J8FqH9D5Y3CrZp4K/GjRyTsE3+PlA=', -- Base64 SHA-256 hash of "admin123" + salt
    'YWRtaW5zYWx0', -- Base64 encoded "adminsalt"
    'SHA-256',
    CURRENT_TIMESTAMP,
    TRUE
);

-- Insert additional test user for testing (password: "password123")
MERGE INTO xmpp_users (jid, username, password_hash, password_salt, hash_algorithm, created_at, active)
KEY (jid)
VALUES (
    'alice@localhost',
    'alice',
    'xP8QZnXhV3JYhqJw9rR8F2Y4KlM6yCnQCJMQQg3+ABC=', -- Base64 SHA-256 hash of "password123" + salt
    'YWxpY2VzYWx0', -- Base64 encoded "alicesalt"
    'SHA-256',
    CURRENT_TIMESTAMP,
    TRUE
);

-- Insert another test user (password: "secret456")
MERGE INTO xmpp_users (jid, username, password_hash, password_salt, hash_algorithm, created_at, active)
KEY (jid)
VALUES (
    'bob@localhost',
    'bob',
    'zQ7RWnX9V3JYhqJw9rR8F2Y4KlM6yCnQCJMQQg3+XYZ=', -- Base64 SHA-256 hash of "secret456" + salt
    'Ym9ic2FsdA==', -- Base64 encoded "bobsalt"
    'SHA-256',
    CURRENT_TIMESTAMP,
    TRUE
);

-- Create a simple view for active users (useful for development)
CREATE VIEW IF NOT EXISTS active_users AS
SELECT jid, username, created_at, last_login
FROM xmpp_users 
WHERE active = TRUE
ORDER BY created_at DESC;

-- Create a view for current sessions (useful for monitoring during development)
CREATE VIEW IF NOT EXISTS current_sessions AS
SELECT 
    s.session_id,
    s.full_jid,
    s.resource,
    s.presence,
    s.created_at,
    s.last_activity,
    u.username
FROM xmpp_sessions s
JOIN xmpp_users u ON s.user_jid = u.jid
ORDER BY s.last_activity DESC;