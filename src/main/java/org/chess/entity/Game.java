package org.chess.entity;
 
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "games") 
public class Game extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "game_code", unique = true)
    public String gameCode;

    @Column(name = "white_id")
    public Long whiteId;

    @Column(name = "black_id")
    public Long blackId;

    @Column(name = "time_control", nullable = false)
    public String timeControl;

    public String mode = "casual";
    public String status = "waiting";
    public String result;

    @Column(name = "winner_id")
    public Long winnerId;

    public String pgn;

    @Column(name = "moves_json", columnDefinition = "jsonb")
    public String movesJson;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "finished_at")
    public Instant finishedAt;
}
