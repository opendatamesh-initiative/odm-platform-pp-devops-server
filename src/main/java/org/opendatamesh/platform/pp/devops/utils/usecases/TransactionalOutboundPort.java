package org.opendatamesh.platform.pp.devops.utils.usecases;

import java.util.function.Function;

public interface TransactionalOutboundPort {

    void doInTransaction(Runnable runnable);

    <T, R> R doInTransactionWithResults(Function<T, R> function, T arg);

    /**
     * Runs the function in a short read-committed, read-only transaction of its own.
     * Use it for reads that must see other transactions' commits while a use case waits,
     * without taking part in serializable conflict detection.
     */
    default <T, R> R doInReadCommittedTransaction(Function<T, R> function, T arg) {
        return function.apply(arg);
    }

    /**
     * Runs the action once the current transaction has committed, or at once when no transaction is active.
     * Use it for side effects that must not be seen before the change they describe is stored, such as events.
     * An exception thrown by the action reaches the caller of the transaction.
     */
    default void afterCommit(Runnable action) {
        action.run();
    }
}
