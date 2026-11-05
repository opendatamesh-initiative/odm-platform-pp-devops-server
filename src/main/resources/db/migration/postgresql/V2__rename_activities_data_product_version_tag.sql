-- BDMD-5647: the column holds the descriptor's info.version (Registry V2 versionNumber), not the Git tag.
ALTER TABLE activities RENAME COLUMN data_product_version_tag TO data_product_version_number;
