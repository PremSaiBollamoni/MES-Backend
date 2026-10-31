-- V37 added factory_id to daily_stock_positions but never updated the uniqueness
-- constraint - it was still (zone_code, position_date) globally, meaning a second
-- factory could never log a stock position for a zone+date any other factory had
-- already used (e.g. two factories both logging 'FGS' for the same day).
--
-- Add the replacement index BEFORE dropping the old one: the old unique index also
-- backs the zone_code foreign key (fk_stock_position_zone), and MySQL refuses to drop
-- an index still supporting one. zone_code stays the leftmost column here specifically
-- so this new index can take over that role once the old one is gone.
ALTER TABLE daily_stock_positions ADD CONSTRAINT uq_stock_position_factory_zone_date
    UNIQUE (zone_code, factory_id, position_date);
ALTER TABLE daily_stock_positions DROP INDEX `uq_stock_position_zone_date`;
