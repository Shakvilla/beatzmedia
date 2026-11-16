"use strict";

function newestFirst(left, right) {
  return new Date(right.updated_at ?? right.created_at) - new Date(left.updated_at ?? left.created_at);
}

function evaluateWorkflowGate(runs, requiredNames, sha, branch) {
  const pending = [];
  const failed = [];

  for (const name of requiredNames) {
    const run = runs
      .filter(candidate =>
        candidate.name === name
        && candidate.head_sha === sha
        && candidate.head_branch === branch
        && candidate.event === "push")
      .sort(newestFirst)[0];

    if (!run || run.status !== "completed") {
      pending.push(name);
    } else if (run.conclusion !== "success") {
      failed.push(`${name}=${run.conclusion ?? "unknown"}`);
    }
  }

  if (failed.length > 0) {
    return {
      state: "failure",
      message: `Required workflow failed for ${sha}: ${failed.join(", ")}`,
    };
  }
  if (pending.length > 0) {
    return {
      state: "pending",
      message: `Waiting for required workflows on ${sha}: ${pending.join(", ")}`,
    };
  }
  return {
    state: "success",
    message: `Required workflows succeeded for exact commit ${sha}: ${requiredNames.join(", ")}`,
  };
}

module.exports = { evaluateWorkflowGate };
