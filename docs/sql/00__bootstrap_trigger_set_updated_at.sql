-- Fresh database only. Invoke psql with ON_ERROR_STOP=1, --single-transaction,
-- and -v expected_db=<the exact new Content Builder database name>.
-- No existing migration is changed. The function below is copied from V4.
SELECT set_config('dts.bootstrap_expected_database', :'expected_db', true);

DO $bootstrap_guard$
BEGIN
    IF current_database() <> current_setting('dts.bootstrap_expected_database')
       OR current_setting('dts.bootstrap_expected_database') = '' THEN
        RAISE EXCEPTION 'Refusing bootstrap: unexpected database';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    ) THEN
        RAISE EXCEPTION 'Refusing bootstrap: public already contains tables';
    END IF;
END;
$bootstrap_guard$;

CREATE OR REPLACE FUNCTION trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
