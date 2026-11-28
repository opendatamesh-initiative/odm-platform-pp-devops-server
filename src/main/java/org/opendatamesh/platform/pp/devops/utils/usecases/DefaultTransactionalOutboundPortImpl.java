package org.opendatamesh.platform.pp.devops.utils.usecases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Serializable transactions for the use cases.
 * <p>
 * PostgreSQL aborts one of two serializable transactions that conflict (SQL state 40001), even when they
 * touch different activities. The aborted transaction is retried here, a few times with a short backoff,
 * so a conflict does not leave a background flow (task execution, approvals) half done.
 * A retry only happens for an outermost transaction: inside another transaction the conflict is the
 * caller's to retry. The function must therefore read the state it changes inside the transaction.
 */
@Component
public class DefaultTransactionalOutboundPortImpl implements TransactionalOutboundPort {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTransactionalOutboundPortImpl.class);

    static final int MAX_ATTEMPTS = 5;
    private static final long BACKOFF_MILLIS = 50;

    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readCommittedTemplate;

    public DefaultTransactionalOutboundPortImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.readCommittedTemplate = new TransactionTemplate(transactionManager);
        this.readCommittedTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readCommittedTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readCommittedTemplate.setReadOnly(true);
    }

    @Override
    public void doInTransaction(Runnable runnable) {
        withRetry(() -> {
            transactionTemplate.executeWithoutResult(status -> runnable.run());
            return null;
        });
    }

    @Override
    public <T, R> R doInTransactionWithResults(Function<T, R> function, T arg) {
        return withRetry(() -> transactionTemplate.execute(status -> function.apply(arg)));
    }

    @Override
    public <T, R> R doInReadCommittedTransaction(Function<T, R> function, T arg) {
        return readCommittedTemplate.execute(status -> function.apply(arg));
    }

    @Override
    public void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private <R> R withRetry(Supplier<R> transaction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return transaction.get();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.get();
            } catch (ConcurrencyFailureException conflict) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw conflict;
                }
                logger.info("Transaction conflict on attempt {} of {}, retrying: {}", attempt, MAX_ATTEMPTS,
                        conflict.getMessage());
                pause(attempt);
            }
        }
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(BACKOFF_MILLIS * attempt + (long) (Math.random() * BACKOFF_MILLIS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying a transaction", interrupted);
        }
    }
}
