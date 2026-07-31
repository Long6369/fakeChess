# ♟️ Agent Specification Reactive PvP fakeChess Backend

## 1. System Overview & Architecture
This is a high-concurrency, event-driven, non-blocking Chess Backend built with Quarkus (Reactive Stack) and PostgreSQL. 

### Architectural Core Principles
 Asynchronous Execution No thread-blocking calls (`Thread.sleep()`, `.await().indefinitely()`) are allowed. Use SmallRye Mutiny reactive streams (`Uni` and `Multi`).
 Authoritative Server Pattern The backend owns and validates the complete game state using `chesslib`. Frontend structures are treated as purely visual and untrusted.
 Hybrid Communication Topology
   REST Endpoints Stateless, short-lived HTTP requests for Player Profiles and Match Histories.
   WebSocket Channels Stateful, duplex, persistent connection pipelines for Matchmaking, Move Relaying, and Real-time Status Synchronization.

---

## 2. Component Blueprint & Class Mapping

Agents must generate and implement code strictly adhering to the following directory package structure

```text
srcmainjavacomyournamechess
├── entity                 # Database Schema Mapping (Hibernate Reactive Panache)
│   ├── Player.java         # 'players' table
│   └── GameHistory.java    # 'game_histories' table
├── model                  # Data Transfer Objects  Network Payloads
│   ├── MoveRequest.java    # Deserialized inbound WebSocket payload
│   └── GameStateEvent.java # Serialized outbound WebSocket events
├── service                # In-Memory State Coordinators
│   ├── GameManager.java    # Singleton orchestrator for connections & matches
│   └── GameSession.java    # Instance tracking a single live board state
├── rest                   # HTTP API Routers
│   └── PlayerResource.java # REST endpoints for profiles
└── websocket              # WebSocket Frame Interceptors
    └── ChessWebSocket.java # Reactive WebSocket router