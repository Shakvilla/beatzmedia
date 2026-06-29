-- V802__search_document_tsv_trigger.sql
-- Search index lifecycle (WU-SRCH-1): tsvector maintenance trigger (INV-SRCH-4).
-- Weights: title=A, subtitle=B, search_text=C. Uses 'simple' dictionary for broad language support.

CREATE FUNCTION search_document_tsv_update() RETURNS trigger AS $$
BEGIN
  NEW.tsv :=
      setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
      setweight(to_tsvector('simple', coalesce(NEW.subtitle, '')), 'B') ||
      setweight(to_tsvector('simple', coalesce(NEW.search_text, '')), 'C');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_search_document_tsv
  BEFORE INSERT OR UPDATE OF title, subtitle, search_text
  ON search_document
  FOR EACH ROW EXECUTE FUNCTION search_document_tsv_update();
