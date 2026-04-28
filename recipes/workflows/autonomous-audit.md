# Autonomous Audit Recipe

## 1. autonomous-loop
tool: agent
operation: loop
prompt: |
  Perform an audit of the last 3 git commits.
  1. Use git.log to get the last 3 commit hashes and timestamps.
  2. For each commit, use datetime.convert to convert the timestamp to a human-readable format.
  3. Use git.diff to see what changed in the most recent commit.
  4. Use json.parse if there are any JSON files modified to see their structure.
  5. Summarize the findings in the context.
tools:
  - git
  - datetime
  - json
  - interactive
