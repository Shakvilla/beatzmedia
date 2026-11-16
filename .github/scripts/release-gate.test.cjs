"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const { evaluateWorkflowGate } = require("./release-gate.cjs");

const SHA = "0123456789abcdef0123456789abcdef01234567";

function run(name, overrides = {}) {
  return {
    name,
    head_sha: SHA,
    head_branch: "master",
    event: "push",
    status: "completed",
    conclusion: "success",
    updated_at: "2026-11-01T12:00:00Z",
    ...overrides,
  };
}

test("succeeds only when every required workflow passed for the exact commit", () => {
  const result = evaluateWorkflowGate([run("ci"), run("security")], ["ci", "security"], SHA, "master");
  assert.equal(result.state, "success");
});

test("fails closed when a non-build workflow fails", () => {
  const result = evaluateWorkflowGate(
    [run("ci"), run("security", { conclusion: "failure" })],
    ["ci", "security"],
    SHA,
    "master",
  );
  assert.equal(result.state, "failure");
  assert.match(result.message, /security=failure/);
});

test("waits when required evidence is missing or still running", () => {
  const result = evaluateWorkflowGate(
    [run("ci"), run("security", { status: "in_progress", conclusion: null })],
    ["ci", "security"],
    SHA,
    "master",
  );
  assert.equal(result.state, "pending");
});

test("rejects successful evidence from a pull request or different commit", () => {
  const result = evaluateWorkflowGate(
    [run("ci", { event: "pull_request" }), run("security", { head_sha: "other" })],
    ["ci", "security"],
    SHA,
    "master",
  );
  assert.equal(result.state, "pending");
});
