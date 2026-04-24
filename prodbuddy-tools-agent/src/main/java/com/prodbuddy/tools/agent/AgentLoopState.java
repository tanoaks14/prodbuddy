package com.prodbuddy.tools.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tracks the state of an autonomous agent loop.
 */
public final class AgentLoopState {

    public enum Status {
        RUNNING,
        FINISHED,
        FAILED,
        ABORTED
    }

    private final List<Task> tasks = new ArrayList<>();
    private final List<StepLog> history = new ArrayList<>();
    private Status status = Status.RUNNING;
    private String goal;
    private int iterations = 0;

    public record Task(String description, boolean completed) { }

    public record StepLog(String thought, String tool,
                          Map<String, Object> params, String result) { }

    public AgentLoopState(final String goal) {
        this.goal = goal;
    }

    public void addTask(final String description) {
        tasks.add(new Task(description, false));
    }

    public void logStep(final String thought, final String tool,
                        final Map<String, Object> params, final String result) {
        history.add(new StepLog(thought, tool, params, result));
        iterations++;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<StepLog> getHistory() {
        return history;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    public int getIterations() {
        return iterations;
    }

    public String getGoal() {
        return goal;
    }
}
