-- Email Notification System Database Schema
-- PostgreSQL 14+

-- Email delivery tracking table
CREATE TABLE IF NOT EXISTS email_deliveries (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(36) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    recipient_name VARCHAR(255),
    template_id VARCHAR(100),
    priority VARCHAR(20) DEFAULT 'normal',
    locale VARCHAR(10) DEFAULT 'et',
    status VARCHAR(50) NOT NULL,
    retry_count INT DEFAULT 0,
    provider VARCHAR(50),
    provider_message_id VARCHAR(255),
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    failed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for email_deliveries
CREATE INDEX idx_email_deliveries_event_id ON email_deliveries(event_id);
CREATE INDEX idx_email_deliveries_status ON email_deliveries(status);
CREATE INDEX idx_email_deliveries_recipient ON email_deliveries(recipient_email);
CREATE INDEX idx_email_deliveries_event_type ON email_deliveries(event_type);
CREATE INDEX idx_email_deliveries_created_at ON email_deliveries(created_at);
CREATE INDEX idx_email_deliveries_priority ON email_deliveries(priority);

-- Email templates table
CREATE TABLE IF NOT EXISTS email_templates (
    id VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    subject VARCHAR(500),
    html_body TEXT,
    text_body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (id, locale)
);

-- Insert default templates
INSERT INTO email_templates (id, locale, subject, html_body, text_body) VALUES
('welcome-email', 'et',
 'Tere tulemast!',
 '<html><body><h1>Tere {{name}}</h1><p>Teretulemast Bürokraati!</p></body></html>',
 'Tere {{name}}! Teretulemast Bürokraati!'),

('welcome-email', 'en',
 'Welcome!',
 '<html><body><h1>Hello {{name}}</h1><p>Welcome to Bürokraatt!</p></body></html>',
 'Hello {{name}}! Welcome to Bürokraatt!'),

('password-reset', 'et',
 'Parooli lähtestamine',
 '<html><body><h1>Parooli lähtestamine</h1><p>Klõpsa lingil: {{resetUrl}}</p></body></html>',
 'Parooli lähtestamine. Klõpsa lingil: {{resetUrl}}'),

('password-reset', 'en',
 'Password Reset',
 '<html><body><h1>Password Reset</h1><p>Click the link: {{resetUrl}}</p></body></html>',
 'Password Reset. Click the link: {{resetUrl}}'),

('chat-transfer-notification', 'et',
 'Vestlus edastatud',
 '<html><body><h1>Vestlus on edastatud</h1><p>Vestlus ID: {{chatId}}</p></body></html>',
 'Vestlus on edastatud. Vestlus ID: {{chatId}}'),

('chat-transfer-notification', 'en',
 'Chat Transferred',
 '<html><body><h1>Chat has been transferred</h1><p>Chat ID: {{chatId}}</p></body></html>',
 'Chat has been transferred. Chat ID: {{chatId}}')

ON CONFLICT (id, locale) DO NOTHING;

-- Email preferences table
CREATE TABLE IF NOT EXISTS email_preferences (
    user_id VARCHAR(36) PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT TRUE,
    marketing_emails_enabled BOOLEAN DEFAULT FALSE,
    digest_enabled BOOLEAN DEFAULT TRUE,
    locale VARCHAR(10) DEFAULT 'et',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Email delivery log (for analytics)
CREATE TABLE IF NOT EXISTS email_delivery_log (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider VARCHAR(50),
    duration_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_delivery_log_event_id ON email_delivery_log(event_id);
CREATE INDEX idx_email_delivery_log_created_at ON email_delivery_log(created_at);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for updated_at
CREATE TRIGGER update_email_deliveries_updated_at BEFORE UPDATE ON email_deliveries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_email_preferences_updated_at BEFORE UPDATE ON email_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_email_templates_updated_at BEFORE UPDATE ON email_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Resql queries as views/stored procedures

-- Query: log-email-request
CREATE OR REPLACE FUNCTION log_email_request(
    p_event_id VARCHAR,
    p_event_type VARCHAR,
    p_recipient_email VARCHAR,
    p_template_id VARCHAR,
    p_priority VARCHAR,
    p_status VARCHAR
) RETURNS VOID AS $$
BEGIN
    INSERT INTO email_deliveries (
        event_id, event_type, recipient_email, template_id, priority, status
    ) VALUES (
        p_event_id, p_event_type, p_recipient_email, p_template_id, p_priority, p_status
    );
END;
$$ LANGUAGE plpgsql;

-- Query: get-email-status
CREATE OR REPLACE FUNCTION get_email_status(p_event_id VARCHAR)
RETURNS TABLE (
    event_id VARCHAR,
    event_type VARCHAR,
    recipient_email VARCHAR,
    status VARCHAR,
    provider VARCHAR,
    provider_message_id VARCHAR,
    retry_count INT,
    error_message TEXT,
    created_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    failed_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        e.event_id,
        e.event_type,
        e.recipient_email,
        e.status,
        e.provider,
        e.provider_message_id,
        e.retry_count,
        e.error_message,
        e.created_at,
        e.sent_at,
        e.delivered_at,
        e.failed_at
    FROM email_deliveries e
    WHERE e.event_id = p_event_id;
END;
$$ LANGUAGE plpgsql;

-- Query: get-email-template
CREATE OR REPLACE FUNCTION get_email_template(
    p_template_id VARCHAR,
    p_locale VARCHAR
) RETURNS TABLE (
    id VARCHAR,
    locale VARCHAR,
    subject VARCHAR,
    html_body TEXT,
    text_body TEXT,
    version INT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        t.id,
        t.locale,
        t.subject,
        t.html_body,
        t.text_body,
        t.version
    FROM email_templates t
    WHERE t.id = p_template_id
      AND t.locale = p_locale
      AND t.is_active = TRUE
    LIMIT 1;
END;
$$ LANGUAGE plpgsql;

-- Grant permissions (adjust as needed)
-- GRANT USAGE ON SCHEMA public TO your_app_user;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO your_app_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO your_app_user;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO your_app_user;
