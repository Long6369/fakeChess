package org.chess.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    public Long id; // Map 1-1 với Account ID

    @Column(unique = true, nullable = false)
    public String username;

    @Column(name = "display_name")
    public String displayName;

    @Column(name = "avatar_url")
    public String avatarUrl;

    public String country;
    public String bio;

    public int elo = 1500;

    @Column(name = "games_played")
    public int gamesPlayed = 0;

    @Column(name = "games_won")
    public int gamesWon = 0;

    @Column(name = "games_lost")
    public int gamesLost = 0;

    @Column(name = "games_drawn")
    public int gamesDrawn = 0;

    @Column(name = "is_online")
    public boolean isOnline = false;

    @Column(name = "last_online")
    public Instant lastOnline;
}
