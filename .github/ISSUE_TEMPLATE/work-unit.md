---
name: Work Unit (WU)
about: Track a single WU from BACKEND-PRD.md §8
title: "WU-XXX-N — <short title>"
labels: ["type:feat"]
assignees: []
---

<!--
One issue == one WU == one branch == one PR. Title must start with the literal
WU id (e.g. "WU-IDN-1 — Account model, signup/login, JWT"). Apply area:<module>,
type:<feat|fix|chore>, and a phase:<0-4> label, and set the matching milestone.
-->

## Work unit
- **WU id:** WU-XXX-N
- **Module / bounded context:** <identity | catalog | payments | commerce | platform | …>
- **Phase (milestone):** phase:0 | phase:1 | phase:2 | phase:3 | phase:4  <!-- §8.1 -->

## Description
<!-- Copy the §8 WU description: what this unit delivers. -->

## Depends on (must be merged to main first — §8.1)
<!-- List dependency WU ids, or "none". Do not start until these are merged. -->
- WU-XXX-N

## LLFRs satisfied
<!-- The LLFR ids this WU implements, for mechanical traceability. -->
- LLFR-XXX-NN.M
- LLFR-XXX-NN.M

## Acceptance criteria
<!-- Concrete, testable outcomes (the LLFR acceptance criteria). CI must prove them. -->
- [ ] <criterion 1>
- [ ] <criterion 2>

## Invariants / notes
<!-- Relevant invariants (e.g. INV-1 ownership-on-settlement, INV-11 money in minor units). -->
