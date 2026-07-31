# ♟️ Production-Grade Agent Specification: Reactive PvP fakeChess Platform

## 1. System Architecture & Core Framework Laws
This backend is a stateful, high-concurrency Chess Engine built using the **Quarkus Reactive Stack** and **PostgreSQL**. 

### Architectural Mandates for the Agent:
1. **Absolute Non-Blocking Execution:** Never use blocking I/O, `Thread.sleep()`, or `.await().indefinitely()`. All asynchronous chaining must use SmallRye Mutiny (`Uni` and `Multi`).
2. **Authoritative Server Pattern:** The backend owns and validates the game state. Every move must be strictly validated by the `chesslib` library inside the memory layer before any state mutation or network broadcast.
3. **Hybrid Protocol Topology:**
   * **REST Endpoints:** Stateful/Stateless HTTP routers for profiles, social connections, and match ledger history.
   * **WebSocket Channels:** Duplex, persistent pipes for real-time presence, game move forwarding, and chess clock synchronization.

---

## 2. Updated Database Schema & Entities (`entity/`)

Implement the following persistence layers using **Hibernate Reactive Panache (Active Record Pattern)**. All structural IDs must be mapped as `Long` (PostgreSQL `BIGINT`).

### `Account.java`
* Maps to `accounts` table. Handles authentication.
* Fields:
  * `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;`
  * `@Column(unique = true, nullable = false) public String email;`
  * `@Column(name = "password_hash") public String passwordHash;`
  * `@Column(name = "created_at") public Instant createdAt;`

### `User.java`
* Maps to `users` table. Handles profile, social states, and rating metrics.
* Fields:
  * `@Id public Long id;` (Primary key maps 1:1 with Account ID via custom flow)
  * `@Column(unique = true, nullable = false) public String username;`
  * `@Column(name = "display_name") public String displayName;`
  * `@Column(name = "avatar_url") public String avatarUrl;`
  * `public int elo = 1500;`
  * `@Column(name = "games_played") public int gamesPlayed = 0;`
  * `@Column(name = "games_won") public int gamesWon = 0;`
  * `@Column(name = "games_lost") public int gamesLost = 0;`
  * `@Column(name = "games_drawn") public int gamesDrawn = 0;`
  * `@Column(name = "is_online") public boolean isOnline = false;`
  * `@Column(name = "last_online") public Instant lastOnline;`

### `Game.java`
* Maps to `games` table. Replaces basic historical logs.
* Fields:
  * `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;`
  * `@Column(name = "game_code", unique = true) public String gameCode;`
  * `@Column(name = "white_id") public Long whiteId;`
  * `@Column(name = "black_id") public Long blackId;`
  * `@Column(name = "time_control", nullable = false) public String timeControl;`
  * `public String mode = "casual";` (Values: 'casual', 'rated')
  * `public String status = "waiting";` (Values: 'waiting', 'ongoing', 'finished', 'aborted')
  * `public String result;` (Values: '1-0', '0-1', '1/2-1/2')
  * `@Column(name = "winner_id") public Long winnerId;`
  * `public String pgn;`
  * `@Column(name = "moves_json", columnDefinition = "jsonb") public String movesJson;` (Serialized JSON array of move details)
  * `@Column(name = "started_at") public Instant startedAt;`
  * `@Column(name = "finished_at") public Instant finishedAt;`

### `Friendship.java`
* Maps to `friendships` table.
* Fields:
  * `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;`
  * `@Column(name = "user_id", nullable = false) public Long userId;`
  * `@Column(name = "friend_id", nullable = false) public Long friendId;`
  * `public String status = "pending";` (Values: 'pending', 'accepted', 'blocked')

---

## 3. Real-Time Memory Logic & Expanded Specs (`service/`)

### `GameSession.java` (State Extension)
Mainages active boards in memory. Tracks turn actions and down-clocks.
* **State fields:** `gameId` (Long), `whitePlayerId` (Long), `blackPlayerId` (Long), `board` (Board), `movesList` (List of JSON objects), `whiteTimeMs` (long), `blackTimeMs` (long), `lastMoveTimestamp` (long).
* **Methods required:**
  * `isPlayerTurn(Long playerId): boolean`
  * `updateClock(Long activePlayerId): void` $\rightarrow$ Calculates `System.currentTimeMillis() - lastMoveTimestamp`, subtracts it from the player's remaining bank, and injects time increments (increment/bonus time defined by `time_control`).

### `GameManager.java` (`@ApplicationScoped`)
Core runtime event orchestrator.
* **Concurrent Memory Structures:**
  * `Queue<Long> waitingQueue = new ConcurrentLinkedQueue<>();`
  * `Map<Long, GameSession> activeGames = new ConcurrentHashMap<>();`
  * `Map<Long, WebSocketConnection> activeConnections = new ConcurrentHashMap<>();`
  * `Map<Long, Cancellable> activeClocks = new ConcurrentHashMap<>();` (Manages background countdown fuses)

* **Functional Requirements for the Agent:**
  1. **Connection Lifecycle (`register` & `disconnect`):**
     * On WebSocket `@OnOpen`, map `playerId` to connection, set `User.isOnline = true` via reactive database transaction, and broadcast network presence events to active accepted friends.
     * On `@OnClose`, purge from memory, set `User.isOnline = false`, and auto-forfeit any active `GameSession` via `handleEndgame()` choosing the surviving participant as the winner.
  2. **Authoritative Move Validation:**
     * Intercept `MoveRequest` containing `gameId`, `from`, `to`. Verify execution permissions.
     * Use `chesslib` to run safety checks: `board.isMoveLegal(move)`.
     * If illegal, return `GameStateEvent.error("Illegal move")`.
     * If legal, commit `board.doMove(move)`, record network performance metadata into `moves_json`, and swap the active background countdown handle (`activeClocks`).
     * Inspect board termination markers (`isMated()`, `isDraw()`, `isStalemate()`). If terminal, route execution directly to `handleEndgame()`.
  3. **Reactive Chess Clock Integration:**
     * When a match switches turns, cancel the preceding player's timer task inside `activeClocks`.
     * Deploy a non-blocking timeout scheduler using Vert.x or Mutiny:
       ```java
       Cancellable timer = Uni.createFrom().item(session)
           .onItem().delayIt().by(Duration.ofMillis(remainingTime))
           .chain(() -> Uni.createFrom().deferred(() -> triggerTimeout(session, timedOutPlayerId)))
           .subscribe().with(item -> {});
       ```
     * `triggerTimeout()` must instantly stop the game, mark the status as 'finished', and hand victory over to the opposing user due to timeout constraints.
  4. **Atomic Post-Game Calculations (`@WithTransaction`):**
     * Concurrently pull both `User` instances from PostgreSQL using `Uni.combine().all()`.
     * Compute Elo adjustments using standard Elo Distribution Math (K-Factor = 32).
     * Update descriptive statistic counters (`gamesPlayed`, `gamesWon`, `gamesLost`, `gamesDrawn`).
     * Compile structural tracking details into the new `Game` entity (including mapping structural movements to `movesJson` string data) and execute atomic updates via a database transactional commit pipeline.

---

## 4. REST & Transport Topologies (`rest/` & `websocket/`)

### `ChessWebSocket.java`
* Class Access Pattern: `@WebSocket(path = "/ws/chess/{playerId}")`
* `@OnOpen`: Registers pipeline channel. Forwards execution hooks to `GameManager.handleJoinMatchmaking(playerId)`.
* `@OnTextMessage`: Deserializes stream inputs directly into `MoveRequest` instances and executes `GameManager.processMove()`.
* `@OnClose`: Forwards events down to connection teardown modules.

### `PlayerResource.java` (Social Additions)
* Exposes standard routes for user inspection.
* **Social Management Endpoints:**
  * `POST /players/friends/request/{friendId}` $\rightarrow$ Creates a new `Friendship` database entry marked with `status = 'pending'`.
  * `POST /players/friends/accept/{requestId}` $\rightarrow$ Marks specific `Friendship` rows as `status = 'accepted'`.
  * `GET /players/{id}/friends` $\rightarrow$ Performs a database join operation returning user profile structures where `Friendship.status = 'accepted'`.

---

## 5. Technical Validation Gate Checklist for Agents
* [ ] No system thread-blocking operations are introduced.
* [ ] Every entity modification workflow operates inside transactional boundaries mapped by `@WithTransaction`.
* [ ] All asynchronous database requests and outbound frame calls compile cleanly into functional execution structures returning `Uni` or `Multi`.
* [ ] Do not leave stubbed code layers or `// TODO` comments. Write fully functional structures ready for deployment.