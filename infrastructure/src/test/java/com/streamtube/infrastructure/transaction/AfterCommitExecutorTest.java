package com.streamtube.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitExecutorTest {

  private final AfterCommitExecutor executor = new AfterCommitExecutor();
  private final AtomicBoolean ran = new AtomicBoolean(false);

  @AfterEach
  void cleanUp() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void runsImmediatelyWithoutActiveTransaction() {
    executor.run(() -> ran.set(true));
    assertThat(ran).isTrue();
  }

  @Test
  void defersUntilAfterCommitInsideTransaction() {
    TransactionSynchronizationManager.initSynchronization();

    executor.run(() -> ran.set(true));
    assertThat(ran).isFalse();

    commit();
    assertThat(ran).isTrue();
  }

  @Test
  void discardsActionOnRollback() {
    TransactionSynchronizationManager.initSynchronization();

    executor.run(() -> ran.set(true));
    rollback();

    assertThat(ran).isFalse();
  }

  private void commit() {
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
      sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }
  }

  private void rollback() {
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }
  }
}
