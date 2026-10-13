package org.opendatamesh.platform.pp.devops.utils.services;

import org.hibernate.Hibernate;
import org.opendatamesh.platform.pp.devops.exceptions.InternalException;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Initializes a persistent graph so it can be used after the persistence context closes,
 * and reloads a graph from the database after another transaction has written to it.
 */
@Component
public class EntityInitAndDetachService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Initializes the entity and every nested collection, then detaches the root,
     * so the graph can be read after the persistence context closes.
     */
    public void initializeEntityAndDetach(Object entity) {
        initializeEntity(entity);
        entityManager.detach(entity);
    }

    /**
     * Reloads the entity from the database, then each element of each collection field,
     * so the graph includes rows another transaction has committed.
     * This method does not take a database lock.
     */
    public void refresh(Object entity) {
        if (entity == null) {
            return;
        }
        entityManager.refresh(entity);
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (!Collection.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Collection<?> collection = (Collection<?>) field.get(entity);
                if (collection == null) {
                    continue;
                }
                Hibernate.initialize(collection);
                for (Object element : collection) {
                    refresh(element);
                }
            } catch (IllegalAccessException exception) {
                throw new InternalException(exception.getMessage(), exception);
            }
        }
    }

    private void initializeEntity(Object entity) {
        if (entity == null) return;
        Hibernate.initialize(entity);
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (Collection.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    Collection<?> collection = (Collection<?>) field.get(entity);
                    if (collection != null) {
                        collection.forEach(this::initializeEntity);
                    }
                } catch (IllegalAccessException e) {
                    throw new InternalException(e.getMessage(), e);
                }
            }
        }
    }
}
