package com.bezkoder.spring.jpa.postgresql.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OvertimeSettledEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOvertimeSettled(OvertimeSettledEvent event) {
        try {
            // SMS would be sent here via an SMS gateway
            // For now we log it — in production replace with actual SMS API call
            System.out.println("SMS SENT to worker " + event.getWorkerId() +
                    ": Your overtime for " + event.getMonth() +
                    " of ₹" + event.getTotalAmount() + " has been settled.");
        } catch (Exception e) {
            // SMS failure must NOT affect settlement data
            // Log and queue for retry in production
            System.err.println("SMS failed for worker " + event.getWorkerId() +
                    ": " + e.getMessage());
        }
    }
}