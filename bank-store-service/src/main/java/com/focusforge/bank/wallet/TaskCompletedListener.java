package com.focusforge.bank.wallet;

import com.focusforge.events.TaskCompletedEvent;
import com.focusforge.events.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskCompletedListener {

    private final WalletService wallets;

    public TaskCompletedListener(WalletService wallets) {
        this.wallets = wallets;
    }

    /** Offset is committed only after this returns, i.e. after the DB transaction commits. */
    @KafkaListener(topics = Topics.TASK_COMPLETED)
    public void on(TaskCompletedEvent event) {
        wallets.creditForTask(event);
    }
}
