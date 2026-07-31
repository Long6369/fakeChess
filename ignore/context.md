# Project Context & Roadmap

## Current Implementation State
- [x] Phase 1: Database Schema Design & PostgreSQL Trigger creation.
- [x] Phase 2: Quarkus Reactive Entities mapping (Account, User, Game, Friendship).
- [x] Phase 3: Core Memory Game Engine + Endgame Pipeline (GameSession & GameManager logic with chesslib, User stats update, Game entity persistence).
- [x] Phase 4: Reactive Chess Clock & Timeout Scheduler implementation.
- [x] Phase 5: WebSocket Route Handling & Live Presence Status broadcast.
- [x] Phase 6: REST API for Social Features (Friends, Invitations).
- [x] Professional Frontend (index.html) with full BE coverage:
  - Chess game with WebSocket integration
  - Players list viewing
  - Friends management (send request, accept, view friends)
  - Clean, modern UI with Tailwind CSS

## Active Task
- Tất cả các phase và frontend đã hoàn thành!

## Known Blockers / Notes
- Remember that the User profile is automatically created via a PostgreSQL database trigger whenever an Account is inserted. Do not write duplicate user insertion logic in Java.
- Always respond in Vietnamese
