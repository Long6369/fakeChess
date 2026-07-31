package org.chess.rest;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.chess.entity.Account;
import org.chess.entity.User;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Duration;
import java.time.Instant;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/register")
    @PermitAll
    @WithTransaction
    public Uni<Response> register(RegisterRequest request) {
        if (request.email == null || request.email.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Email is required").build());
        }
        if (request.password == null || request.password.length() < 8) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Password must be at least 8 characters").build());
        }
        if (request.username == null || request.username.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Username is required").build());
        }

        return Account.<Account>find("email", request.email)
                .firstResult()
                .chain(existingAccount -> {
                    if (existingAccount != null) {
                        return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Email already registered").build());
                    }

                    String passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt());

                    Account account = new Account();
                    account.email = request.email;
                    account.passwordHash = passwordHash;
                    account.createdAt = Instant.now();
                    account.updatedAt = Instant.now();

                    return account.<Account>persist()
                            .chain(savedAccount -> {
                                User user = new User();
                                user.id = savedAccount.id;
                                user.username = request.username;
                                user.displayName = request.username;
                                user.elo = 1500;
                                user.gamesPlayed = 0;
                                user.gamesWon = 0;
                                user.gamesLost = 0;
                                user.gamesDrawn = 0;
                                user.isOnline = false;

                                return user.<User>persist()
                                        .map(savedUser -> Response.status(Response.Status.CREATED).entity(savedUser).build());
                            });
                });
    }

    @POST
    @Path("/login")
    @PermitAll
    @WithTransaction
    public Uni<Response> login(LoginRequest request) {
        if (request.email == null || request.email.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Email is required").build());
        }
        if (request.password == null || request.password.isBlank()) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Password is required").build());
        }

        return Account.<Account>find("email", request.email)
                .firstResult()
                .chain(account -> {
                    if (account == null) {
                        return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED).entity("Invalid credentials").build());
                    }

                    if (!BCrypt.checkpw(request.password, account.passwordHash)) {
                        return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED).entity("Invalid credentials").build());
                    }

                    String token = Jwt.issuer("fakechess")
                            .subject(account.id.toString())
                            .claim("email", account.email)
                            .claim("groups", "user")
                            .expiresIn(Duration.ofHours(24))
                            .sign();

                    return Uni.createFrom().item(Response.ok(new LoginResponse(token)).build());
                });
    }

    @GET
    @Path("/me")
    @RolesAllowed("user")
    @WithTransaction
    public Uni<Response> getCurrentUser() {
        Long userId = Long.parseLong(jwt.getSubject());

        return User.<User>findById(userId)
                .onItem().ifNotNull().transform(user -> Response.ok(user).build())
                .onItem().ifNull().continueWith(() -> Response.status(Response.Status.NOT_FOUND).entity("User not found").build());
    }

    public static class RegisterRequest {
        public String email;
        public String password;
        public String username;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class LoginResponse {
        public String token;

        public LoginResponse(String token) {
            this.token = token;
        }
    }
}