# WorthIt — A White Paper

## Abstract

Career decisions are among the most consequential choices people make, yet they are routinely made with incomplete and poorly comparable information. Compensation figures circulate as isolated anecdotes, workplace culture is described in polished marketing language, and the true tradeoff between pay, workload, and wellbeing is left for candidates to infer on their own. WorthIt is a community-driven platform that addresses this gap by collecting structured, employee-submitted experiences and transforming them into transparent, comparable insight. This paper describes the motivation behind WorthIt, the problem it solves, the approach it takes, and the system that brings it to life.

## Introduction

The modern labor market produces an abundance of information about jobs, but very little of it is trustworthy, structured, or directly comparable. A candidate weighing an offer must reconcile fragmented salary rumors, subjective online reviews, and their own guesswork about what daily life at a company will actually feel like. WorthIt was created on a simple premise: the people best positioned to describe what a job is really like are the people who have lived it. By capturing those lived experiences in a consistent format and pairing compensation with the context that gives it meaning, WorthIt helps individuals answer the question at the heart of every career move — is this actually worth it?

## The Problem

Existing tools tend to fail candidates in one of two ways. Salary aggregators report numbers without the context of role, level, location, workload, or personal sentiment, leaving the reader unable to judge whether a figure represents a good deal or a grueling one. Review sites, on the other hand, capture sentiment but rarely tie it to concrete compensation or a clear, comparable signal of overall satisfaction. The result is that two of the most important dimensions of a job — what you are paid and what it costs you in stress and time — are almost never presented together in a way that supports a confident decision. Compounding this, much of the available data is noisy, unverified, or diluted by companies that have little meaningful information behind them.

## The WorthIt Approach

WorthIt reframes the problem around a single, structured unit of truth: the experience. Each submitted experience combines compensation with the context that makes it interpretable — the role and level, the location, the hours and stress involved, and an explicit answer to whether the contributor would make the same choice again. This final signal, expressed as a worth score, distills a complex tradeoff into something people can compare across companies and roles.

Because experiences share a consistent structure, WorthIt can aggregate them into explainable, company- and role-level metrics: how many experiences exist, the average worth score, the typical stress, and related measures. These aggregates are intentionally simple to reason about, favoring bounded scales and straightforward averages over opaque scoring. The platform also prioritizes signal quality by default, keeping the primary browsing experience focused on companies with real data while still allowing users to search directly for any company when they know what they are looking for. This creates a virtuous cycle: as more people contribute, the aggregates grow stronger, and stronger aggregates make the platform more valuable to the next person facing a decision.

## How WorthIt Works

WorthIt is delivered as two cooperating applications. The frontend, `worthit`, is the interface through which people browse companies, search and filter, drill into role- and level-specific detail, read published experiences, and contribute their own. The backend, `worthit-backend`, is the source of truth for the underlying data and business rules, exposing a versioned HTTP JSON API under `/api/v1`.

A typical journey begins on the companies page, where the interface requests a paginated list of companies from the backend. As the user refines their search, applies an industry filter, or changes the sort order, the interface re-queries the backend, which returns results using cursor-based pagination so that large datasets can be loaded incrementally and predictably. Selecting a company reveals its detail view along with the roles and levels available there, and opening a role surfaces the individual published experiences behind the aggregate numbers. Should the user choose to contribute, their submission enters a lifecycle governed by the backend, which distinguishes published experiences from those awaiting review and enforces consistent validation and response conventions throughout.

Beneath this flow, the backend centralizes the rules that keep the data trustworthy: it distinguishes active from inactive entities, separates published experiences from unpublished ones, applies consistent pagination and sorting semantics, and computes the aggregate metrics that power the browsing experience. Its relational domain model — companies, roles, the company-role relationships between them, company-specific level ladders, locations, and experiences — reflects the reality that expectations, level naming, and outcomes genuinely differ from one company and place to another.

## Design Philosophy

Several convictions shape WorthIt. The first is that real-world context is inseparable from compensation; a number without role, level, location, and workload attached is at best incomplete and at worst misleading. The second is that metrics should be explainable, so that users can trust and interpret what they see rather than defer to a black box. The third is that the default experience should surface meaningful signal, protecting users from being overwhelmed by companies with little behind them while still honoring intentional, specific searches. Finally, WorthIt currently keeps its read APIs openly accessible so that insight is easy to reach, even as the authentication infrastructure needed for future tightening is already in place.

## Current State and Direction

WorthIt is in an active build phase in which backend contracts and frontend integration are being progressively hardened. The present emphasis is on completing the API surface for every major screen, refining pagination, filtering, and sorting semantics, and maintaining clear, shared documentation across the two applications. Looking ahead, the platform is oriented toward stronger authentication and authorization, schema migrations and production hardening, richer analytics and aggregates, and expanded quality and moderation workflows for contributed experiences.

## Conclusion

WorthIt exists to make one of life's harder decisions a little clearer. By treating lived experience as structured, comparable data and pairing compensation with the context that gives it meaning, it replaces guesswork and marketing with transparent, community-sourced signal. The more people share, the sharper that signal becomes — turning individual stories into collective insight that helps each new candidate judge, with confidence, whether a job is truly worth it.
