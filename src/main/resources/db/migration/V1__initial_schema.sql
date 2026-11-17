CREATE TABLE commands (
 id UUID PRIMARY KEY, status VARCHAR(255) NOT NULL, source VARCHAR(10) NOT NULL,
 transcript VARCHAR(4000), answer VARCHAR(6000), proposal_json VARCHAR(2000),
 transaction_id UUID, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE financial_transactions (
 id UUID PRIMARY KEY, type VARCHAR(255) NOT NULL CHECK(type IN ('RECEITA','DESPESA')),
 amount NUMERIC(12,2) NOT NULL CHECK(amount > 0), description VARCHAR(160) NOT NULL,
 category VARCHAR(60) NOT NULL, transaction_date DATE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 command_id UUID UNIQUE REFERENCES commands(id)
);
ALTER TABLE commands ADD CONSTRAINT fk_command_transaction FOREIGN KEY(transaction_id) REFERENCES financial_transactions(id);
CREATE TABLE audit_events (
 id UUID PRIMARY KEY, command_id UUID NOT NULL REFERENCES commands(id),
 action VARCHAR(50) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_transaction_date ON financial_transactions(transaction_date);
CREATE INDEX idx_audit_command ON audit_events(command_id,occurred_at);
