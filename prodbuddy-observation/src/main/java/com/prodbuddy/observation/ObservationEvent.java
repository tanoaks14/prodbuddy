package com.prodbuddy.observation;

/**
 * Data object representing a sequenced event for logging.
 * Adheres to SOLID Single Responsibility Principle.
 */
public class ObservationEvent {
    private final String sender;
    private final String receiver;
    private final String method;
    private final String action;

    public ObservationEvent(String sender, String receiver, String method, String action) {
        this.sender = sender;
        this.receiver = receiver;
        this.method = method;
        this.action = action;
    }

    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getMethod() { return method; }
    public String getAction() { return action; }

    /**
     * Formats the event into a standard log parsable format.
     */
    public String formatLog() {
        return String.format("[SEQUENCE_TRACE] sender=\"%s\" receiver=\"%s\" method=\"%s\" action=\"%s\"",
                sender, receiver, method, action);
    }
}
