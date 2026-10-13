package org.opendatamesh.platform.pp.devops.rest.v2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * Sets created_at when a scenario is about which historical row wins.
 */
@Component
public class ActivityHistoryFixture {

    private final JdbcTemplate jdbcTemplate;
    private final String schema;

    public ActivityHistoryFixture(JdbcTemplate jdbcTemplate,
                                  @Value("${spring.jpa.properties.hibernate.default_schema}") String schema) {
        this.jdbcTemplate = jdbcTemplate;
        this.schema = schema;
    }

    public void setActivityCreatedAt(String uuid, String timestamp) {
        jdbcTemplate.update(
                "update " + schema + ".activities set created_at = ? where uuid = ?",
                Timestamp.valueOf(timestamp),
                uuid
        );
    }

    public void setTaskCreatedAt(String uuid, String timestamp) {
        jdbcTemplate.update(
                "update " + schema + ".activities_tasks set created_at = ? where uuid = ?",
                Timestamp.valueOf(timestamp),
                uuid
        );
    }
}
