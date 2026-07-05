package com.streamtube.infrastructure.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers a side effect until the surrounding transaction commits, so external systems (queue,
 * SMTP) never observe state that may still roll back. Outside a transaction the action runs
 * immediately. On rollback the action is discarded.
 */
@Component
public class AfterCommitExecutor {

  public void run(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
