package org.chess.model;

public class MoveRequest {
    public String gameId;
    public String from;
    public String to;

    public MoveRequest() {}

    public MoveRequest(String gameId, String from, String to) {
        this.gameId = gameId;
        this.from = from;
        this.to = to;
    }
}