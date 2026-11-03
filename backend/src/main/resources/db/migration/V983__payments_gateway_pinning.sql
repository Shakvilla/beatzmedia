-- WU-EXT-2: pin the gateway vendor on every charge and payout operation.
-- Legacy active rows cannot be attributed reliably because PSP_REDDE was mutable and did not
-- record the vendor per operation. Keep them explicitly unknown so automated reconciliation fails
-- closed until an operator resolves them using provider evidence. Completed history is sandbox
-- because terminal operations are never re-queried or routed.

ALTER TABLE payment_intent ADD COLUMN gateway_vendor TEXT;
UPDATE payment_intent
SET gateway_vendor = CASE
    WHEN status = 'pending' THEN 'unknown'
    ELSE 'sandbox'
END;
ALTER TABLE payment_intent ALTER COLUMN gateway_vendor SET NOT NULL;
ALTER TABLE payment_intent ADD CONSTRAINT payment_intent_gateway_vendor_check
    CHECK (gateway_vendor IN ('sandbox', 'redde', 'disabled', 'unknown'));

ALTER TABLE payout_txn ADD COLUMN gateway_vendor TEXT;
UPDATE payout_txn
SET gateway_vendor = CASE
    WHEN status = 'sent' THEN 'unknown'
    ELSE 'sandbox'
END;
ALTER TABLE payout_txn ALTER COLUMN gateway_vendor SET NOT NULL;
ALTER TABLE payout_txn ADD CONSTRAINT payout_txn_gateway_vendor_check
    CHECK (gateway_vendor IN ('sandbox', 'redde', 'disabled', 'unknown'));

-- Transfer the old routing choice into the new provider registry without overwriting a selection
-- already changed by an operator. Production bootstrap changes only this migration seed to disabled.
UPDATE provider_selection
SET provider_code = CASE WHEN COALESCE((
        SELECT is_enabled FROM feature_flag WHERE key = 'PSP_REDDE'
    ), false) THEN 'redde' ELSE 'sandbox' END,
    updated_at = now(),
    updated_by = 'system:migration'
WHERE capability = 'payment'
  AND updated_by = 'system:migration'
  AND version = 1;
