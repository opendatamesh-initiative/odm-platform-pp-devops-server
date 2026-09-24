package org.opendatamesh.platform.pp.devops.activity.entities;

import org.opendatamesh.platform.pp.devops.utils.entities.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.sql.Timestamp;

@Entity
@Table(name = "activities_tasks_results")
public class TaskResult extends VersionedEntity {

    @Id
    @Column(name = "uuid", length = 36)
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "generated_at")
    private Timestamp generatedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_uuid", nullable = false)
    private Task task;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Timestamp getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Timestamp generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
