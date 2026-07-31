package org.chess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.chess.entity.Game;
import org.chess.entity.User;
import org.chess.model.GameStateEvent;
import org.chess.model.MoveRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class GameManager {

    protected record GameOutcome(boolean checkmate, boolean draw) {
    }

    @Inject
    ObjectMapper objectMapper;

    // Bộ đếm Game ID tự tăng
    private final AtomicLong gameIdCounter = new AtomicLong(1);

    // Hàng đợi Matchmaking chứa ID của các User đang tìm trận
    private final Queue<Long> waitingQueue = new ConcurrentLinkedQueue<>();
    // Lưu trữ các phòng đấu đang diễn ra trên Memory
    private final Map<Long, GameSession> activeGames = new ConcurrentHashMap<>();
    // Lưu trữ các kết nối WebSocket đang hoạt động
    private final Map<Long, WebSocketConnection> activeConnections = new ConcurrentHashMap<>();
    // Map ngược để tìm nhanh GameID dựa vào UserID
    private final Map<Long, Long> playerToGameMap = new ConcurrentHashMap<>();
    // Map quản lý các timer timeout cho mỗi game
    private final Map<Long, Uni<Void>> activeClocks = new ConcurrentHashMap<>();
    // Set quản lý các user đang online
    private final Set<Long> onlineUsers = new ConcurrentHashSet<>();

    /**
     * Đăng ký kết nối WebSocket cho người chơi
     */
    public Uni<Void> registerConnection(Long playerId, WebSocketConnection connection) {
        activeConnections.put(playerId, connection);
        onlineUsers.add(playerId);

        // Cập nhật trạng thái isOnline trong DB
        return Panache.withTransaction(() -> 
            User.<User>findById(playerId)
                .onItem().ifNotNull().invoke(user -> {
                    user.isOnline = true;
                    user.lastOnline = Instant.now();
                })
                .onItem().ifNotNull().call(user -> user.persist())
                .replaceWithVoid()
        );
    }

    /**
     * Xóa kết nối WebSocket khi người chơi ngắt kết nối
     */
    public Uni<Void> removeConnection(Long playerId) {
        activeConnections.remove(playerId);
        waitingQueue.remove(playerId);
        onlineUsers.remove(playerId);

        // Cập nhật trạng thái isOnline trong DB
        return Panache.withTransaction(() -> 
            User.<User>findById(playerId)
                .onItem().ifNotNull().invoke(user -> {
                    user.isOnline = false;
                    user.lastOnline = Instant.now();
                })
                .onItem().ifNotNull().call(user -> user.persist())
                .chain(() -> handleDisconnect(playerId))
                .replaceWithVoid()
        );
    }

    /**
     * Xử lý Matchmaking: Kết cặp 2 User dựa trên ID Long
     */
    public Uni<Void> handleJoinMatchmaking(Long playerId) {
        WebSocketConnection conn = activeConnections.get(playerId);
        if (conn == null) return Uni.createFrom().voidItem();

        // Kiểm tra xem người chơi đã trong trận chưa
        if (playerToGameMap.containsKey(playerId)) {
            return Uni.createFrom().voidItem();
        }

        // Kiểm tra xem người chơi đã trong hàng đợi chưa
        if (waitingQueue.contains(playerId)) {
            return Uni.createFrom().voidItem();
        }

        // Thử lấy đối thủ từ hàng đợi
        Long opponentId = waitingQueue.poll();
        if (opponentId == null) {
            // Không có đối thủ, thêm vào hàng đợi
            waitingQueue.add(playerId);
            return conn.sendText(GameStateEvent.waiting());
        }

        // Tạo trận đấu mới
        Long gameId = gameIdCounter.getAndIncrement();
        boolean assignWhite = Math.random() > 0.5;
        Long whiteId = assignWhite ? playerId : opponentId;
        Long blackId = assignWhite ? opponentId : playerId;

        GameSession session = new GameSession(gameId, whiteId, blackId);
        activeGames.put(gameId, session);
        playerToGameMap.put(whiteId, gameId);
        playerToGameMap.put(blackId, gameId);

        // Khởi tạo timer cho bên trắng đi trước
        scheduleTimeout(session, whiteId);

        WebSocketConnection whiteConn = activeConnections.get(whiteId);
        WebSocketConnection blackConn = activeConnections.get(blackId);

        Uni<Void> notifyWhite = whiteConn != null ? whiteConn.sendText(GameStateEvent.started(String.valueOf(gameId), "WHITE")) : Uni.createFrom().voidItem();
        Uni<Void> notifyBlack = blackConn != null ? blackConn.sendText(GameStateEvent.started(String.valueOf(gameId), "BLACK")) : Uni.createFrom().voidItem();

        return Uni.combine().all().unis(notifyWhite, notifyBlack).discardItems();
    }

    /**
     * Xử lý nước đi từ người chơi
     */
    public Uni<Void> processMove(Long playerId, MoveRequest request) {
        Long gameId;
        try {
            gameId = Long.parseLong(request.gameId);
        } catch (NumberFormatException e) {
            WebSocketConnection currentConn = activeConnections.get(playerId);
            if (currentConn != null) {
                return currentConn.sendText(GameStateEvent.error("Invalid game ID"));
            }
            return Uni.createFrom().voidItem();
        }

        GameSession session = activeGames.get(gameId);
        WebSocketConnection currentConn = activeConnections.get(playerId);

        if (session == null || currentConn == null) {
            return Uni.createFrom().voidItem();
        }

        if (!session.isPlayerTurn(playerId)) {
            return currentConn.sendText(GameStateEvent.error("Not your turn"));
        }

        try {
            Move move = new Move(request.from + request.to, session.getBoard().getSideToMove());
            if (!session.getBoard().isMoveLegal(move, true)) {
                return currentConn.sendText(GameStateEvent.error("Illegal move"));
            }

            // Hủy timer cũ trước khi thực hiện nước đi
            cancelTimeout(gameId);

            // Cập nhật đồng hồ
            session.updateClock(playerId);

            // Thực hiện nước đi
            session.recordMove(move.toString());
            session.getBoard().doMove(move);
            session.recordMove(move, move.toString(), request.from + request.to);

            Long opponentId = playerId.equals(session.getWhitePlayerId()) ? session.getBlackPlayerId() : session.getWhitePlayerId();
            WebSocketConnection opponentConn = activeConnections.get(opponentId);

            Uni<Void> notifyCurrentPlayer = sendGameEvent(currentConn, GameStateEvent.moveAccepted(request.from, request.to));
            Uni<Void> notifyOpponent = sendGameEvent(opponentConn, GameStateEvent.opponentMoved(request.from, request.to));

            GameOutcome outcome = determineGameOutcome(session.getBoard());

            if (outcome.checkmate()) {
                Long winnerId = playerId;
                return Uni.combine().all().unis(notifyCurrentPlayer, notifyOpponent)
                        .discardItems()
                        .chain(() -> handleEndgame(session, winnerId, false))
                        .onFailure().recoverWithItem(ignored -> null);
            } else if (outcome.draw()) {
                return Uni.combine().all().unis(notifyCurrentPlayer, notifyOpponent)
                        .discardItems()
                        .chain(() -> handleEndgame(session, null, true))
                        .onFailure().recoverWithItem(ignored -> null);
            } else {
                // Đặt timer cho lượt tiếp theo
                scheduleTimeout(session, opponentId);
            }

            return Uni.combine().all().unis(notifyCurrentPlayer, notifyOpponent)
                    .discardItems()
                    .onFailure().recoverWithItem(ignored -> null);

        } catch (Exception e) {
            return currentConn.sendText(GameStateEvent.error("Invalid move syntax"));
        }
    }

    /**
     * Xử lý khi người chơi ngắt kết nối
     */
    public Uni<Void> handleDisconnect(Long playerId) {
        for (GameSession session : activeGames.values()) {
            if (session.getWhitePlayerId().equals(playerId) || session.getBlackPlayerId().equals(playerId)) {
                // Hủy timer
                cancelTimeout(session.getId());
                // Người ngắt kết nối thua
                Long survivorId = session.getWhitePlayerId().equals(playerId) ? session.getBlackPlayerId() : session.getWhitePlayerId();
                return handleEndgame(session, survivorId, false);
            }
        }
        return Uni.createFrom().voidItem();
    }

    /**
     * Lên lịch timeout cho một người chơi
     */
    private void scheduleTimeout(GameSession session, Long playerId) {
        long remainingTime = playerId.equals(session.getWhitePlayerId()) ?
                session.getWhiteTimeMs() : session.getBlackTimeMs();

        Uni<Void> timerUni = Uni.createFrom().voidItem()
                .onItem().delayIt().by(Duration.ofMillis(remainingTime))
                .chain(() -> triggerTimeout(session, playerId));

        // Lưu vào activeClocks
        activeClocks.put(session.getId(), timerUni);

        // Subscribe để timer chạy
        timerUni.subscribe().with(item -> {});
    }

    /**
     * Hủy timeout
     */
    private void cancelTimeout(Long gameId) {
        activeClocks.remove(gameId);
    }

    /**
     * Xử lý khi timeout xảy ra
     */
    private Uni<Void> triggerTimeout(GameSession session, Long timedOutPlayerId) {
        Long winnerId = timedOutPlayerId.equals(session.getWhitePlayerId()) ?
                session.getBlackPlayerId() : session.getWhitePlayerId();
        return handleEndgame(session, winnerId, false);
    }

    /**
     * Xử lý kết thúc trận đấu và lưu vào DB
     */
    protected Uni<Void> handleEndgame(GameSession session, Long winnerId, boolean isDraw) {
        // 1. Dọn dẹp trạng thái bộ nhớ tạm (In-memory) ngay lập tức
        if (activeGames.remove(session.getId()) == null) {
            return Uni.createFrom().voidItem();
        }

        // Hủy timer chạy ngầm
        cancelTimeout(session.getId());
        playerToGameMap.remove(session.getWhitePlayerId());
        playerToGameMap.remove(session.getBlackPlayerId());

        // 2. Thực hiện toàn bộ Transaction DB TRƯỚC (Tìm User -> Cập nhật Elo -> Lưu Game)
        return Panache.withTransaction(() ->
                        User.<User>findById(session.getWhitePlayerId())
                                .chain(white -> User.<User>findById(session.getBlackPlayerId())
                                        .chain(black -> {

                                            // Cập nhật thống kê và Elo cho bên Trắng
                                            if (white != null) {
                                                int blackElo = (black != null) ? black.elo : 1500;
                                                applyUserResult(white, winnerId, isDraw, true, blackElo);
                                            } else {
                                                System.err.println("Skipping stats update for white user " + session.getWhitePlayerId() + " because not found.");
                                            }

                                            // Cập nhật thống kê và Elo cho bên Đen
                                            if (black != null) {
                                                int whiteElo = (white != null) ? white.elo : 1500;
                                                applyUserResult(black, winnerId, isDraw, false, whiteElo);
                                            } else {
                                                System.err.println("Skipping stats update for black user " + session.getBlackPlayerId() + " because not found.");
                                            }

                                            // Tạo thực thể Game và lưu xuống DB
                                            Game game = createFinishedGame(session, winnerId, isDraw);
                                            return game.persist().replaceWithVoid();
                                        })
                                )
                )
                .onFailure().invoke(err -> {
                    System.err.println("Lỗi nghiêm trọng không thể ghi dữ liệu xuống DB: " + err.getMessage());
                    err.printStackTrace();
                })
                // 3. Sau khi DB đã COMMIT thành công hoàn toàn, mới tiến hành gửi WebSocket
                .chain(() -> {
                    WebSocketConnection whiteConn = activeConnections.get(session.getWhitePlayerId());
                    WebSocketConnection blackConn = activeConnections.get(session.getBlackPlayerId());

                    String message = isDraw ? "Game drawn" : "Player " + winnerId + " won";
                    GameStateEvent endEvent = GameStateEvent.finished(winnerId != null ? String.valueOf(winnerId) : null, message);

                    Uni<Void> notifyWhite = sendGameEvent(whiteConn, endEvent).onFailure().recoverWithItem(() -> null);
                    Uni<Void> notifyBlack = sendGameEvent(blackConn, endEvent).onFailure().recoverWithItem(() -> null);

                    return Uni.combine().all().unis(notifyWhite, notifyBlack).discardItems();
                })
                // Đảm bảo hàm luôn kết thúc êm đẹp mà không làm sập luồng chính
                .onFailure().recoverWithItem(() -> null);
    }

    private Uni<Void> sendGameEvent(WebSocketConnection connection, Object event) {
        if (connection == null) {
            return Uni.createFrom().voidItem();
        }
        return connection.sendText(event).onFailure().recoverWithItem(ignored -> null);
    }

    // Sửa lại hàm applyUserResult nhận thêm đối số opponentElo
    private void applyUserResult(User user, Long winnerId, boolean isDraw, boolean isWhite, int opponentElo) {
        int currentElo = user.elo;

        // 1. Công thức Elo chuẩn dựa trên chênh lệch trình độ
        double expectedScore = 1.0 / (1.0 + Math.pow(10.0, (opponentElo - currentElo) / 400.0));

        // 2. Chuyển đổi an toàn sang kiểu nguyên thủy long để so sánh chính xác tuyệt đối
        boolean isThisUserWinner = false;
        if (winnerId != null && user.id != null) {
            // .longValue() giúp lấy giá trị số thực tế, bỏ qua lớp bọc đối tượng Long của Java
            isThisUserWinner = (winnerId.longValue() == user.id.longValue());
        }

        // 3. Xác định điểm số thực tế (Thắng = 1.0, Hòa = 0.5, Thua = 0.0)
        double actualScore = 0.0;
        if (isDraw) {
            actualScore = 0.5;
        } else if (isThisUserWinner) {
            actualScore = 1.0;
        } else {
            actualScore = 0.0;
        }

        int kFactor = 32;

        // 4. Cập nhật Elo mới
        user.elo = (int) (currentElo + kFactor * (actualScore - expectedScore));
        user.gamesPlayed += 1;

        // 5. Cập nhật chính xác các cột thống kê thắng/thua/hòa vào DB
        if (isDraw) {
            user.gamesDrawn += 1;
        } else if (isThisUserWinner) {
            user.gamesWon += 1;
        } else {
            user.gamesLost += 1;
        }
    }

    protected GameOutcome determineGameOutcome(Board board) {
        boolean isKingAttacked = board.isKingAttacked();
        boolean hasLegalMoves = !board.legalMoves().isEmpty();
        boolean isCheckmate = isKingAttacked && !hasLegalMoves;
        boolean isStalemate = !isKingAttacked && !hasLegalMoves;
        boolean isDraw = board.isDraw() || board.isInsufficientMaterial() || board.isRepetition() || isStalemate;
        return new GameOutcome(isCheckmate, isDraw);
    }

    protected Game createFinishedGame(GameSession session, Long winnerId, boolean isDraw) {
        Game game = new Game();
        game.gameCode = "g" + session.getId() + UUID.randomUUID().toString().substring(0, 6);
        game.whiteId = session.getWhitePlayerId();
        game.blackId = session.getBlackPlayerId();
        game.timeControl = "10+5";
        game.status = "finished";
        game.startedAt = session.getStartedAt();
        game.finishedAt = Instant.now();

        if (isDraw) {
            game.result = "1/2-1/2";
            game.winnerId = null;
        } else {
            if (winnerId != null && winnerId.equals(session.getWhitePlayerId())) {
                game.result = "1-0";
                game.winnerId = session.getWhitePlayerId();
            } else if (winnerId != null) {
                game.result = "0-1";
                game.winnerId = session.getBlackPlayerId();
            }
        }

        game.pgn = session.getPgnString();
        try {
            game.movesJson = objectMapper.writeValueAsString(session.getMoveList());
        } catch (Exception e) {
            game.movesJson = "[]";
        }
        return game;
    }

    public GameSession getGameById(Long gameId) {
        return activeGames.get(gameId);
    }
}
