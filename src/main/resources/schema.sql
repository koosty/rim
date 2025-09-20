-- XMPP Server Database Schema
-- PostgreSQL schema for user management and authentication

-- Users table for XMPP authentication
CREATE TABLE IF NOT EXISTS xmpp_users (
    jid VARCHAR(255) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    password_salt VARCHAR(255) NOT NULL,
    hash_algorithm VARCHAR(32) NOT NULL DEFAULT 'SCRAM-SHA-256',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Index for faster username lookups
CREATE INDEX IF NOT EXISTS idx_xmpp_users_username ON xmpp_users(username);

-- Index for active users
CREATE INDEX IF NOT EXISTS idx_xmpp_users_active ON xmpp_users(active) WHERE active = TRUE;

-- Sessions table for tracking active connections
CREATE TABLE IF NOT EXISTS xmpp_sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_jid VARCHAR(255) NOT NULL REFERENCES xmpp_users(jid),
    resource VARCHAR(64),
    full_jid VARCHAR(320) NOT NULL, -- user@domain/resource
    connection_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_activity TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    presence VARCHAR(32) NOT NULL DEFAULT 'unavailable'
);

-- Index for user sessions lookup
CREATE INDEX IF NOT EXISTS idx_xmpp_sessions_user_jid ON xmpp_sessions(user_jid);

-- Index for full JID lookups
CREATE INDEX IF NOT EXISTS idx_xmpp_sessions_full_jid ON xmpp_sessions(full_jid);

-- Index for active sessions
CREATE INDEX IF NOT EXISTS idx_xmpp_sessions_activity ON xmpp_sessions(last_activity DESC);

-- Insert demo user for testing (password: "test123")
-- This uses a simplified hash for demo purposes
INSERT INTO xmpp_users (jid, username, password_hash, password_salt, hash_algorithm, created_at, active)
VALUES (
    'testuser@localhost',
    'testuser',
    'tDVJ/CqM8paFACTeEk/dRx8JzF6yCnQCJMQQg3+LF1E=', -- Base64 SHA-256 hash of "test123" + salt
    'ZGVtb3NhbHQ=', -- Base64 encoded "demosalt"
    'SHA-256',
    NOW(),
    TRUE
) ON CONFLICT (jid) DO NOTHING;

-- Insert admin user for testing (password: "admin123")
INSERT INTO xmpp_users (jid, username, password_hash, password_salt, hash_algorithm, created_at, active)
VALUES (
    'admin@localhost',
    'admin',
    'K8J+m1FQzFKTz3J8FqH9D5Y3CrZp4K/GjRyTsE3+PlA=', -- Base64 SHA-256 hash of "admin123" + salt
    'YWRtaW5zYWx0', -- Base64 encoded "adminsalt"
    'SHA-256',
    NOW(),
    TRUE
) ON CONFLICT (jid) DO NOTHING;