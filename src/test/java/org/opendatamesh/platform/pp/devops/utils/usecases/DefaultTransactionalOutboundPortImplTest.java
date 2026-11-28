package org.opendatamesh.platform.pp.devops.utils.usecases;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serializable use-case transactions: a conflict PostgreSQL resolves by aborting one transaction is retried.
 */
class DefaultTransactionalOutboundPortImplTest {

    /**
     * Feature: Use-case transactions
     *
     * Scenario: A transaction aborted by a serialization conflict is run again
     *   Given a use-case transaction that fails twice with a concurrency conflict, then succeeds
     *   When it runs through the transactional port
     *   Then it runs three times and its result is returned
     */
    @Test
    void whenConflictThenRetriedUntilItCommits() {
        AtomicInteger attempts = new AtomicInteger();
        DefaultTransactionalOutboundPortImpl port = new DefaultTransactionalOutboundPortImpl(new FakeTransactionManager());

        String result = port.doInTransactionWithResults(ignored -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotAcquireLockException("could not serialize access");
            }
            return "committed";
        }, null);

        assertThat(result).isEqualTo("committed");
        assertThat(attempts).hasValue(3);
    }

    /**
     * Feature: Use-case transactions
     *
     * Scenario: A conflict that keeps coming back is reported after the last attempt
     */
    @Test
    void whenConflictPersistsThenItIsThrownAfterTheLastAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        DefaultTransactionalOutboundPortImpl port = new DefaultTransactionalOutboundPortImpl(new FakeTransactionManager());

        assertThatThrownBy(() -> port.doInTransaction(() -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("could not serialize access");
        })).isInstanceOf(CannotAcquireLockException.class);
        assertThat(attempts).hasValue(DefaultTransactionalOutboundPortImpl.MAX_ATTEMPTS);
    }

    /**
     * Feature: Use-case transactions
     *
     * Scenario: Any other failure is not retried
     */
    @Test
    void whenOtherFailureThenNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        DefaultTransactionalOutboundPortImpl port = new DefaultTransactionalOutboundPortImpl(new FakeTransactionManager());

        assertThatThrownBy(() -> port.doInTransaction(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("bad input");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(attempts).hasValue(1);
    }

    private static final class FakeTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
