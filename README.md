# ♟️ Reactive Online Chess Platform Backend (PvP & Social)

Hệ thống Backend cho nền tảng Cờ Vua Online 1vs1 (Người đấu với Người) toàn diện, được xây dựng trên nền tảng **Quarkus (Reactive Stack)**. Dự án tận dụng tối đa sức mạnh của **Lập trình Reactive (Non-blocking)** và **WebSockets** để tối ưu hóa độ trễ thời gian thực, kết hợp với hệ thống cơ sở dữ liệu phân lớp bền vững trên **PostgreSQL**.

---

## 🚀 Tính năng cốt lõi (Core Features)

- **Kiến trúc Hybrid (REST + WebSockets Next):** Tách biệt luồng dữ liệu tĩnh (Xử lý Profile, Kết bạn, Lịch sử đấu qua REST) và luồng giao tiếp hai chiều thời gian thực (Tìm trận, Đi quân, Trạng thái Online qua WebSockets).
- **Authoritative Game Server:** Toàn bộ trạng thái và luật cờ vua được tính toán, xác thực nghiêm ngặt tại Backend thông qua thư viện Bitboard `chesslib`. Chống tuyệt đối các hành vi hack/cheat hoặc can thiệp dữ liệu từ phía Client (Frontend).
- **Reactive Chess Clock:** Hệ thống đồng hồ đếm ngược (Time Control) quản lý thời gian từng nước đi chạy ngầm bằng Reactive Scheduler (Non-blocking), tự động xử thua khi người chơi hết giờ (Timeout).
- **Matchmaking & Direct Invitations:** Hỗ trợ cả luồng tìm trận ngẫu nhiên qua sảnh chờ (In-Memory Queue) lẫn cơ chế gửi lời mời thách đấu trực tiếp (Direct Challenge) giữa những người bạn đang Online.
- **Real-time Presence:** Tự động đồng bộ hóa trạng thái Online/Offline của người chơi và phát thông báo tức thời tới danh sách bạn bè qua kênh WebSocket ngay khi kết nối thay đổi.
- **Advanced DB Schema & Tracking:** Hệ thống cơ sở dữ liệu chuẩn hóa tách biệt `accounts` (Bảo mật) và `users` (Thống kê chỉ số Elo, Trận thắng/thua/hòa). Toàn bộ biên bản nước đi chi tiết được nén và lưu trữ dưới dạng định dạng `JSONB` tối ưu.

---

## 🏛️ Kiến trúc Hệ thống & Luồng Dữ liệu (Data Flow)

Dự án được tổ chức theo mô hình **Event-Driven (Hướng sự kiện)** phân lớp tinh gọn:

1. **Transport Layer:** `ChessWebSocket` (Xử lý kết nối, bắt sự kiện mạng) & `PlayerResource` (Xử lý các truy vấn HTTP).
2. **In-Memory Service Layer:** `GameManager` (Điều phối sảnh chờ, quản lý kết nối trong RAM) & `GameSession` (Giữ trạng thái bàn cờ, bộ đếm giờ đang chạy của từng trận đấu).
3. **Domain Engine:** Thư viện `com.github.bhlangonijr.chesslib` (Trọng tài xử lý luật bằng toán học bitboard tốc độ cao trên RAM).
4. **Persistence Layer:** `Account`, `User`, `Game`, `Friendship` (Entities tương tác bất đồng bộ với PostgreSQL thông qua Hibernate Reactive Panache).

---

## 📁 Cấu trúc thư mục dự án

```text
src/main/java/com/yourname/chess/
│
├── entity/                 # Tầng Database Entities (Hibernate Reactive Panache)
│   ├── Account.java        # Quản lý định danh, thông tin đăng nhập
│   ├── User.java           # Hồ sơ người chơi, điểm Elo, chỉ số thắng/thua, online
│   ├── Game.java           # Chi tiết trận đấu, kết quả, pgn, moves_json
│   └── Friendship.java     # Mối quan hệ bạn bè (pending, accepted, blocked)
│
├── model/                  # Tầng Dữ liệu truyền nhận (DTOs / JSON Payloads)
│   ├── MoveRequest.java    # Gói tin nước đi gửi lên: {"gameId":..., "from":"e2", "to":"e4"}
│   └── GameStateEvent.java # Gói tin trạng thái Server chủ động đẩy về Client
│
├── service/                # Tầng Nghiệp vụ cốt lõi & Quản lý trạng thái trong RAM
│   ├── GameManager.java    # Singleton điều phối sảnh, kết nối, clock scheduler, lưu DB
│   └── GameSession.java    # Instance lưu thông tin 1 ván đấu thực tế (Board, Clocks, PGN Ledger)
│
├── rest/                   # Tầng REST API Routers (Giao tiếp HTTP ngắn)
│   └── PlayerResource.java # Các API kết bạn, chấp nhận lời mời, xem danh sách bạn bè
│
└── websocket/              # Tầng WebSockets (Giao tiếp thời gian thực)
    └── ChessWebSocket.java # Cổng hứng kết nối: @OnOpen, @OnMessage, @OnClose


##  🛠️ Công nghệ sử dụng (Tech Stack)
Backend Framework: Quarkus (Supersonic Subatomic Java)

Reactive Engine: SmallRye Mutiny / Eclipse Vert.x

Real-time Gateway: Quarkus WebSockets Next

ORM / Database Driver: Hibernate Reactive với Panache / Reactive PostgreSQL Client

Database: PostgreSQL (Có tích hợp Triggers tự động sinh Profile và đồng bộ Timestamps)

Chess Logic Library: com.github.bhlangonijr:chesslib:1.3.3 (Phân phối qua kho lưu trữ JitPack)


##🛠️ Quy tắc phát triển & Bảo trì cho Hệ thống (Dành cho Lập trình viên/Agent)
Zero-Blocking Policy: Tuyệt đối không sử dụng các hàm gây nghẽn luồng. Toàn bộ các thao tác xử lý luồng mạng và Database bắt buộc phải trả về các Reactive Stream (Uni hoặc Multi).

Clean Code Policy: Khi tiến hành Refactor hoặc thay đổi logic, hãy xóa hoàn toàn code cũ không sử dụng. Không để lại code được comment hay tag `DEPRECATED_OLD_LOGIC` để giữ codebase sạch sẽ và chuyên nghiệp. Hạn chế sử dụng comment.