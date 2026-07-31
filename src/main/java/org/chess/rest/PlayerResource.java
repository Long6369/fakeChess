package org.chess.rest;

import io.quarkus.hibernate.reactive.panache.Panache;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.mutiny.Uni;
import org.chess.entity.Friendship;
import org.chess.entity.User;
import org.chess.security.UserPrincipal; // Sử dụng class của bạn

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.chess.entity.User;         // Đảm bảo import đúng thực thể User của bạn
import org.chess.entity.Friendship;

import java.util.List;
import java.util.stream.Collectors;

@Path("/players")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlayerResource {

    // 1. Tiêm SecurityIdentity để lấy thông tin Token JWT
    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public Uni<List<User>> getAllPlayers() {
        return User.<User>listAll();
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getPlayerById(@PathParam("id") Long id) {
        return User.<User>findById(id)
                .onItem().ifNotNull().transform(player -> Response.ok(player).build())
                .onItem().ifNull().continueWith(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Lấy danh sách bạn bè của CHÍNH NGƯỜI DÙNG ĐANG ĐĂNG NHẬP
     */
    @GET
    @Path("/me/friends")
    @RolesAllowed("user") // Chặn nếu chưa đăng nhập hoặc sai quyền
    public Uni<Response> getMyFriends() {
        // 2. Chuyển đổi identity thành UserPrincipal để lấy ID "xịn"
        UserPrincipal principal = UserPrincipal.from(securityIdentity);
        Long myUserId = principal.getUserId();

        return Friendship.<Friendship>list("(userId = $1 or friendId = $1) and status = 'accepted'", myUserId)
                .chain(friendships -> {
                    List<Long> friendIds = friendships.stream()
                            .map(f -> f.userId.equals(myUserId) ? f.friendId : f.userId)
                            .collect(Collectors.toList());

                    if (friendIds.isEmpty()) {
                        return Uni.createFrom().item(Response.ok(List.of()).build());
                    }

                    return User.<User>list("id in $1", friendIds)
                            .onItem().transform(users -> Response.ok(users).build());
                });
    }

    /**
     * Gửi lời mời kết bạn từ CHÍNH NGƯỜI DÙNG ĐANG ĐĂNG NHẬP tới friendId
     */
    @POST
    @Path("/me/friends/request/{friendId}")
    @RolesAllowed("user")
    @WithTransaction
    public Uni<Response> sendFriendRequest(@PathParam("friendId") Long friendId) {

        // 1. Lấy thông tin Tên đăng nhập (hoặc Email) được lưu ở phần Subject (sub) của Token
//        String currentUsername = jwt.getName();
        String currentUsername = securityIdentity.getPrincipal().getName();

        if (currentUsername == null) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Token không hợp lệ hoặc không chứa thông tin người dùng").build());
        }

        // 2. Tra cứu thực thể User hiện tại từ DB để lấy chuẩn ID thực tế
            return User.<User>find("username", currentUsername).firstResult()
                    .chain(currentUser -> {
                    if (currentUser == null) {
                        return Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND)
                                .entity("Không tìm thấy thông tin tài khoản của bạn trong hệ thống").build());
                    }

                    Long currentUserId = currentUser.id;

                    // Chặn tự gửi lời mời kết bạn với chính mình
                    if (currentUserId.equals(friendId)) {
                        return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                                .entity("Bạn không thể tự gửi kết bạn với chính mình!").build());
                    }

                    // 3. Kiểm tra chéo hai chiều dưới DB xem đã có yêu cầu hoặc đã là bạn bè chưa
                    return Friendship.<Friendship>find("(userId = $1 and friendId = $2) or (userId = $2 and friendId = $1)", currentUserId, friendId)
                            .firstResult()
                            .chain(existing -> {
                                if (existing != null) {
                                    return Uni.createFrom().item(Response.status(Response.Status.CONFLICT)
                                            .entity("Yêu cầu kết bạn đã tồn tại hoặc hai người đã là bạn bè từ trước!").build());
                                }

                                // 4. Nếu mọi thứ hợp lệ, tiến hành lưu bản ghi mới với trạng thái pending
                                Friendship newRequest = new Friendship();
                                newRequest.userId = currentUserId;
                                newRequest.friendId = friendId;
                                newRequest.status = "pending";

                                return newRequest.<Friendship>persist()
                                        .replaceWith(Response.status(Response.Status.CREATED).entity(newRequest).build());
                            });
                });
    }
}