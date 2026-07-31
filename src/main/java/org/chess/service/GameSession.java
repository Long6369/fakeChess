package org.chess.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import java.time.Instant;
import java.util.ArrayList;
import org.chess.dto.MoveDetail;
import java.util.List;

public class GameSession {
    private final Long gameId;
    private final Long whitePlayerId;
    private final Long blackPlayerId;
    private final Board board;
    private long lastMoveTimestamp;
    private long whiteTimeMs;
    private long blackTimeMs;
    private int fullMoveCounter = 1;
    private final List<String> pgnLedger;
    private final List<MoveDetail> moveList;
    private final Instant startedAt;

    public GameSession(Long gameId, Long whitePlayerId, Long blackPlayerId) {
        this.gameId = gameId;
        this.whitePlayerId = whitePlayerId;
        this.blackPlayerId = blackPlayerId;
        this.board = new Board();
        this.pgnLedger = new ArrayList<>();
        this.moveList = new ArrayList<>();
        this.lastMoveTimestamp = System.currentTimeMillis();
        this.startedAt = Instant.now();
        // Thời gian mặc định: 10 phút = 600000 ms
        this.whiteTimeMs = 600000;
        this.blackTimeMs = 600000;
    }

    public Long getId() { return gameId; }
    public Long getWhitePlayerId() { return whitePlayerId; }
    public Long getBlackPlayerId() { return blackPlayerId; }
    public Board getBoard() { return board; }
    public List<MoveDetail> getMoveList() { return moveList; }
    public long getWhiteTimeMs() { return whiteTimeMs; }
    public long getBlackTimeMs() { return blackTimeMs; }
    public Instant getStartedAt() { return startedAt; }

    public Long getPlayerIdBySide(Side side) {
        return side == Side.WHITE ? whitePlayerId : blackPlayerId;
    }

    public boolean isPlayerTurn(Long playerId) {
        Side currentSide = board.getSideToMove();
        return (currentSide == Side.WHITE && playerId.equals(whitePlayerId)) ||
                (currentSide == Side.BLACK && playerId.equals(blackPlayerId));
    }

    public void updateClock(Long activePlayerId) {
        long now = System.currentTimeMillis();
        long timeSpent = now - this.lastMoveTimestamp;
        this.lastMoveTimestamp = now;

        if (activePlayerId.equals(whitePlayerId)) {
            this.whiteTimeMs -= timeSpent;
        } else {
            this.blackTimeMs -= timeSpent;
        }
    }

    public void recordMove(String moveSan) {
        pgnLedger.add(moveSan);
    }

    public String getPgnString() {
        return String.join(" ", pgnLedger);
    }

    // Ghi nhận nước đi hợp lệ và chuyển đổi sang cấu trúc MoveDetail JSONB
    public void recordMove(Move move, String san, String uci) {
        long now = System.currentTimeMillis();
        long timeSpent = now - this.lastMoveTimestamp;
        this.lastMoveTimestamp = now;

        // Xác định màu vừa đi quân
        String colorStr = board.getSideToMove().toString().toLowerCase(); // "white" hoặc "black"

        // Tạo bản ghi MoveDetail
        MoveDetail detail = new MoveDetail(
                this.fullMoveCounter,
                colorStr,
                san,
                uci,
                board.getFen(),
                timeSpent
        );
        this.moveList.add(detail);

        // Nếu là bên Đen vừa đi xong, tăng bộ đếm số lượt (Full Move) của ván đấu lên 1
        if ("black".equals(colorStr)) {
            this.fullMoveCounter++;
        }
    }
}