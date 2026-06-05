package com.bezkoder.spring.jpa.postgresql.event;

import org.springframework.context.ApplicationEvent;
import java.math.BigDecimal;

public class OvertimeSettledEvent extends ApplicationEvent {

    private final Long workerId;
    private final String month;
    private final BigDecimal totalAmount;
    private final int entriesCount;

    public OvertimeSettledEvent(Object source, Long workerId,
                                String month, BigDecimal totalAmount,
                                int entriesCount) {
        super(source);
        this.workerId = workerId;
        this.month = month;
        this.totalAmount = totalAmount;
        this.entriesCount = entriesCount;
    }

    public Long getWorkerId() { return workerId; }
    public String getMonth() { return month; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getEntriesCount() { return entriesCount; }
}