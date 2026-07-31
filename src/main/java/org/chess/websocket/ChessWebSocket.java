package org.chess.websocket;


import org.chess.model.GameStateEvent;
import org.chess.model.MoveRequest;
import org.chess.service.GameManager;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@WebSocket(path = "/ws/chess/{playerId}")
public class ChessWebSocket {

    @Inject
    GameManager gameManager;

    @OnOpen
    public Uni<Void> onOpen(WebSocketConnection connection, @PathParam("playerId") String playerIdStr) {
        try {
            Long playerId = Long.parseLong(playerIdStr);
            return gameManager.registerConnection(playerId, connection)
                    .chain(() -> gameManager.handleJoinMatchmaking(playerId));
        } catch (NumberFormatException e) {
            return connection.sendText(GameStateEvent.error("Invalid player ID"));
        }
    }

    @OnTextMessage
    public Uni<Void> onMessage(MoveRequest request, @PathParam("playerId") String playerIdStr) {
        try {
            Long playerId = Long.parseLong(playerIdStr);
            return gameManager.processMove(playerId, request);
        } catch (NumberFormatException e) {
            return Uni.createFrom().voidItem();
        }
    }

    @OnClose
    public Uni<Void> onClose(@PathParam("playerId") String playerIdStr) {
        try {
            Long playerId = Long.parseLong(playerIdStr);
            return gameManager.removeConnection(playerId);
        } catch (NumberFormatException e) {
            return Uni.createFrom().voidItem();
        }
    }
}
