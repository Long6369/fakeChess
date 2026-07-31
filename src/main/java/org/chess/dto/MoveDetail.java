package org.chess.dto;

public class MoveDetail {
    public int number;
    public String color; // "white" hoặc "black"
    public String san;   // ví dụ: "e4"
    public String uci;   // ví dụ: "e2e4"
    public String fen;   // Trạng thái bàn cờ sau nước đi này
    public long timeSpentMs;

    // Constructor trống bắt buộc để Jackson Deserialize
    public MoveDetail() {}

    public MoveDetail(int number, String color, String san, String uci, String fen, long timeSpentMs) {
        this.number = number;
        this.color = color;
        this.san = san;
        this.uci = uci;
        this.fen = fen;
        this.timeSpentMs = timeSpentMs;
    }
}
