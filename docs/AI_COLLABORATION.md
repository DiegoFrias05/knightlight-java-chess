# AI collaboration record

## Purpose

This project was requested for a 422C Software II Java bonus assignment about developing and launching a project with AI. The student proposed a basic chess engine and asked the AI to turn the idea into a completed repository with instructions for running it. The student selected a public GitHub repository for submission.

## Who did what

**Student input so far:** the project idea, Java/class context, permission to develop the project, and the public repository preference.

**AI work in this development session:** the proposed scope and architecture; Java chess rules, search engine, and Swing interface; build and run scripts; automated tests; documentation; and preparation of the repository. The assistant also used parallel AI agents for separate implementation and documentation tasks.

The application itself does not call an AI model. The engine uses ordinary Java search and evaluation code, and the game works offline.

**AI verification on October 1, 2026:** the assistant ran the complete suite on Windows using both JDK 17 and JDK 21. Each run passed 219 rules checks, 57 engine checks, and 47 interface checks (323 total). It also generated and visually inspected the interface screenshot. These are assistant-performed checks, separate from the student's personal review below.

**Student review and reflection:** not yet completed when this record was created. This document does not claim that the student independently wrote the generated code, reviewed every line, or personally ran the tests. Repository publication and any final verification should be checked against the actual delivered repository and test output.

## Development choices

- Keep the application small enough to read and explain: a desktop game, local engine, and a few focused classes.
- Use the JDK's Swing library so the project has no third-party runtime dependencies.
- Separate immutable positions from the current game's history to make search safer and easier to test.
- Use negamax and alpha-beta pruning to demonstrate recursion and algorithmic optimization.
- Add special-move and move-generation tests because visually plausible chess behavior can still contain subtle rule bugs.
- Document limits openly, including automatic claim-based draws and modest engine strength.

AI-generated code should be treated as a proposal that needs review. Automated checks provide evidence for specific behaviors; they do not prove that every chess position or desktop configuration works correctly.

## Student review to complete before submission

Replace the prompts below with your own observations after running and reading the project. Do not fill them with experiences you did not have.

- **Run and play:** Which operating system and Java version did you use? Did the game start using the README? What happened in a game you played?
- **Tests:** Which command did you run, and what result did you observe?
- **Code understanding:** Explain one method in `Position` and one part of `Engine` in your own words.
- **AI collaboration:** Which AI choices did you accept, question, or change? What did you verify yourself?
- **Limitations:** Describe a weakness or missing feature you observed, and a realistic improvement.
- **Contribution:** If you changed anything, describe your change and how you checked it.

Follow the course's own requirements for attribution, collaboration, and the final submission. This record is intended to make the actual division of work clear.
