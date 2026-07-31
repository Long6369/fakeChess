package org.chess.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameStateEvent {
    public String status;
    public String action;
    public String gameId;
    public String color;
    public String from;
    public String to;
    public String winnerId;
    public String message;

    public GameStateEvent() {}

    public static GameStateEvent waiting() {
        GameStateEvent event = new GameStateEvent();
        event.status = "WAITING";
        return event;
    }

    public static GameStateEvent started(String gameId, String color) {
        GameStateEvent event = new GameStateEvent();
        event.status = "STARTED";
        event.gameId = gameId;
        event.color = color;
        return event;
    }

    public static GameStateEvent opponentMoved(String from, String to) {
        GameStateEvent event = new GameStateEvent();
        event.action = "OPPONENT_MOVED";
        event.from = from;
        event.to = to;
        return event;
    }

    public static GameStateEvent moveAccepted(String from, String to) {
        GameStateEvent event = new GameStateEvent();
        event.action = "MOVE_ACCEPTED";
        event.from = from;
        event.to = to;
        event.message = "Move accepted";
        return event;
    }

    public static GameStateEvent error(String message) {
        GameStateEvent event = new GameStateEvent();
        event.status = "ERROR";
        event.message = message;
        return event;
    }

    public static GameStateEvent finished(String winnerId, String message) {
        GameStateEvent event = new GameStateEvent();
        event.status = "FINISHED";
        event.winnerId = winnerId;
        event.message = message;
        return event;
    }
}