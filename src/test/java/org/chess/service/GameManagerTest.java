package org.chess.service;

import com.github.bhlangonijr.chesslib.Board;
import org.chess.entity.Game;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameManagerTest {

    @Test
    void createFinishedGameShouldLetDatabaseGenerateId() {
        GameManager gameManager = new GameManager();
        GameSession session = new GameSession(42L, 7L, 8L);

        Game game = gameManager.createFinishedGame(session, 7L, false);

        assertNull(game.id);
        assertEquals(7L, game.whiteId);
        assertEquals(8L, game.blackId);
        assertEquals("finished", game.status);
        assertEquals("1-0", game.result);
        assertEquals(7L, game.winnerId);
    }

    @Test
    void shouldDetectCheckmateFromKnownPosition() {
        GameManager gameManager = new GameManager();
        Board board = new Board();
        board.loadFromFen("7k/6Q1/6Q1/8/8/8/8/7K b - - 0 1");

        GameManager.GameOutcome outcome = gameManager.determineGameOutcome(board);

        assertTrue(outcome.checkmate());
        assertFalse(outcome.draw());
    }

    @Test
    void shouldGenerateUniqueGameCodeForFinishedGames() {
        GameManager gameManager = new GameManager();
        GameSession firstSession = new GameSession(42L, 7L, 8L);
        GameSession secondSession = new GameSession(42L, 7L, 8L);

        Game firstGame = gameManager.createFinishedGame(firstSession, 7L, false);
        Game secondGame = gameManager.createFinishedGame(secondSession, 7L, false);

        assertFalse(firstGame.gameCode.equals(secondGame.gameCode));
    }
}
