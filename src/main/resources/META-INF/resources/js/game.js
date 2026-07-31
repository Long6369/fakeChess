// Auth check first
const authToken = localStorage.getItem('chessAuthToken');
if (!authToken) {
    window.location.href = '/index.html';
}
// Verify token is valid
fetch('/auth/me', {
    headers: { 'Authorization': `Bearer ${authToken}` }
}).then(res => {
    if (!res.ok) {
        localStorage.removeItem('chessAuthToken');
        window.location.href = '/index.html';
    }
}).catch(() => {
    localStorage.removeItem('chessAuthToken');
    window.location.href = '/index.html';
});

// Global variables
let ws = null;
let board = null;
let game = new Chess();
let myColor = null;
let myGameId = null;
let moveCount = 1;
let currentUser = null;

// Initialize board
board = Chessboard('board', {
    position: 'start',
    pieceTheme: '/img/chesspieces/{piece}.png'
});

// Tab switcher
function switchTab(tab) {
    // Hide all content
    document.getElementById('contentPlay').classList.add('hidden');
    document.getElementById('contentPlayers').classList.add('hidden');
    document.getElementById('contentFriends').classList.add('hidden');

    // Remove active class from all tabs
    document.getElementById('tabPlay').classList.remove('tab-active');
    document.getElementById('tabPlayers').classList.remove('tab-active');
    document.getElementById('tabFriends').classList.remove('tab-active');
    document.getElementById('tabPlay').classList.add('text-slate-400', 'hover:text-white', 'hover:bg-slate-800');
    document.getElementById('tabPlayers').classList.add('text-slate-400', 'hover:text-white', 'hover:bg-slate-800');
    document.getElementById('tabFriends').classList.add('text-slate-400', 'hover:text-white', 'hover:bg-slate-800');

    // Show selected content
    document.getElementById(`content${tab.charAt(0).toUpperCase() + tab.slice(1)}`).classList.remove('hidden');
    document.getElementById(`tab${tab.charAt(0).toUpperCase() + tab.slice(1)}`).classList.add('tab-active');
    document.getElementById(`tab${tab.charAt(0).toUpperCase() + tab.slice(1)}`).classList.remove('text-slate-400', 'hover:text-white', 'hover:bg-slate-800');

    if (tab === 'players') {
        loadAllPlayers();
    }
}

// --- Chess Functions ---
function connectAndMatch() {
    const playerId = document.getElementById('playerId').value;
    if (!playerId) return alert('Vui lòng nhập ID người chơi!');

    document.getElementById('connectBtn').disabled = true;
    document.getElementById('connectBtn').textContent = 'ĐANG GHÉP CẶP...';
    document.getElementById('status').innerHTML = '<span class="w-2 h-2 rounded-full bg-cyan-500 inline-block animate-pulse"></span> Đang ở hàng đợi...';

    ws = new WebSocket(`ws://${window.location.host}/ws/chess/${playerId}`);

    ws.onmessage = function(event) {
        const data = JSON.parse(event.data);
        console.log('Server event:', data);

        if (data.status === 'WAITING') {
            document.getElementById('status').innerHTML = '<span class="w-2 h-2 rounded-full bg-yellow-500 inline-block animate-pulse"></span> Đang tìm đối thủ...';
        }

        if (data.status === 'STARTED') {
            myGameId = data.gameId;
            myColor = data.color === 'WHITE' ? 'w' : 'b';
            moveCount = 1;

            document.getElementById('status').innerHTML = '<span class="w-2 h-2 rounded-full bg-emerald-500 inline-block"></span> Đang thi đấu';
            document.getElementById('playerColor').textContent = `Quân ${data.color} (${data.color === 'WHITE' ? 'Đi trước' : 'Đi sau'})`;
            document.getElementById('opponentName').textContent = 'Đối thủ';
            document.getElementById('opponentCard').classList.remove('opacity-50');
            document.getElementById('opponentStatus').className = 'w-3 h-3 rounded-full bg-red-500 shadow-[0_0_10px_rgba(239,68,68,0.5)]';
            document.getElementById('pgnLog').innerHTML = '<div class="text-emerald-400 text-xs font-semibold mb-2">⚔️ Trận đấu bắt đầu!</div>';

            game.reset();
            board = Chessboard('board', {
                draggable: true,
                position: 'start',
                pieceTheme: '/img/chesspieces/{piece}.png',
                orientation: data.color.toLowerCase(),
                onDragStart: onDragStart,
                onDrop: onDrop
            });
        }

        if (data.action === 'OPPONENT_MOVED') {
            game.move({ from: data.from, to: data.to, promotion: 'q' });
            board.position(game.fen());
            document.getElementById('status').innerHTML = '<span class="w-2 h-2 rounded-full bg-blue-400 inline-block animate-pulse"></span> Đến lượt bạn!';
            appendPgnLog(data.from, data.to, false);
        }

        if (data.action === 'MOVE_ACCEPTED') {
            document.getElementById('status').innerHTML = '<span class="w-2 h-2 rounded-full bg-emerald-500 inline-block animate-pulse"></span> Nước đi đã được gửi. Đang chờ đối thủ...';
        }

        if (data.status === 'FINISHED') {
            let winMsg = data.winnerId === null ? 'Trận HÒA!' : (data.winnerId === document.getElementById('playerId').value ? 'Bạn THẮNG! 🎉' : 'Bạn THUA! 💀');
            document.getElementById('pgnLog').innerHTML += `<div class="text-amber-400 font-bold text-xs mt-3">🏁 ${winMsg} (${data.message})</div>`;
            document.getElementById('status').innerHTML = '🛑 Trận đấu kết thúc';
            document.getElementById('connectBtn').disabled = false;
            document.getElementById('connectBtn').textContent = 'SẴN SÀNG TÌM TRẬN';
            document.getElementById('opponentStatus').className = 'w-3 h-3 rounded-full bg-slate-600';
        }

        if (data.status === 'ERROR') {
            alert('Lỗi: ' + data.message);
            board.position(game.fen());
            document.getElementById('status').innerHTML = '<span class="w-2 h-2 rounded-full bg-red-500 inline-block"></span> Nước đi không hợp lệ!';
        }
    };

    ws.onclose = function() {
        document.getElementById('status').innerHTML = '❌ Mất kết nối!';
        document.getElementById('connectBtn').disabled = false;
        document.getElementById('connectBtn').textContent = 'TÌM TRẬN LẠI';
        document.getElementById('opponentStatus').className = 'w-3 h-3 rounded-full bg-slate-600';
    };
}

function onDragStart(source, piece, position, orientation) {
    if (game.game_over()) return false;
    if ((myColor === 'w' && piece.search(/^b/) !== -1) || (myColor === 'b' && piece.search(/^w/) !== -1) || game.turn() !== myColor) {
        return false;
    }
}

function onDrop(source, target) {
    let move = game.move({ from: source, to: target, promotion: 'q' });
    if (move === null) return 'snapback';

    document.getElementById('status').innerHTML = '<span class="w-2 h-2 rounded-full bg-slate-500 inline-block animate-pulse"></span> Đang chờ Server...';
    appendPgnLog(source, target, true);
    ws.send(JSON.stringify({ gameId: myGameId, from: source, to: target }));
}

function appendPgnLog(from, to, isMe) {
    const log = document.getElementById('pgnLog');
    if (log.children.length === 1 && log.children[0].classList.contains('italic')) {
        log.innerHTML = '';
    }
    let badge = isMe ? '<span class="text-cyan-400">Bạn</span>' : '<span class="text-red-400">Đối thủ</span>';
    let entry = `<div class="flex justify-between py-1 border-b border-slate-700/50 text-xs"><span class="text-slate-500">#${moveCount}</span> <span>${from}→${to}</span> <span>${badge}</span></div>`;
    log.innerHTML += entry;
    log.scrollTop = log.scrollHeight;
    moveCount++;
}

// --- Players Functions ---
function loadAllPlayers() {
    const container = document.getElementById('playersList');
    container.innerHTML = '<p class="text-slate-500 text-sm">Đang tải...</p>';
    fetch('/players')
        .then(res => res.json())
        .then(players => {
            if (players.length === 0) {
                container.innerHTML = '<p class="text-slate-500 text-sm">Không tìm thấy người chơi nào!</p>';
                return;
            }
            container.innerHTML = players.map(player => `
                <div class="bg-slate-900 border border-slate-700 rounded-xl p-4 flex items-center justify-between hover:border-slate-600 transition-all">
                    <div class="flex items-center gap-3">
                        <div class="w-10 h-10 rounded-lg bg-gradient-to-br from-cyan-500/20 to-blue-600/20 flex items-center justify-center text-xl border border-cyan-500/30">👤</div>
                        <div>
                            <p class="font-semibold text-slate-200">${player.username || 'Người chơi'}</p>
                            <p class="text-xs text-slate-500">ID: ${player.id} • ELO: ${player.elo} • Đã chơi: ${player.gamesPlayed}</p>
                            <p class="text-xs text-slate-500">Thắng: ${player.gamesWon} • Thua: ${player.gamesLost} • Hòa: ${player.gamesDrawn}</p>
                        </div>
                    </div>
                    <div class="flex items-center gap-2">
                        <span class="w-2.5 h-2.5 rounded-full ${player.isOnline ? 'bg-emerald-500' : 'bg-slate-600'}"></span>
                        <span class="text-xs ${player.isOnline ? 'text-emerald-400' : 'text-slate-500'}">${player.isOnline ? 'Online' : 'Offline'}</span>
                    </div>
                </div>
            `).join('');
        })
        .catch(err => {
            console.error(err);
            container.innerHTML = '<p class="text-red-400 text-sm">Lỗi tải danh sách!</p>';
        });
}

// --- Friends Functions ---
//function sendFriendRequest() {
//    const myId = document.getElementById('myIdForFriend').value;
//    const friendId = document.getElementById('friendIdToAdd').value;
//    if (!myId || !friendId) return alert('Vui lòng nhập đầy đủ thông tin!');
//
//    fetch(`/players/me/friends/request/${friendId}`, { method: 'POST' })
//        .then(async res => {
//            if (!res.ok) {
//                const errMsg = await res.text();
//                throw new Error(errMsg);
//            }
//            alert('Đã gửi lời mời kết bạn!');
//            document.getElementById('friendIdToAdd').value = '';
//        })
//        .catch(err => alert('Lỗi: ' + err.message));
//}
function sendFriendRequest(friendId) {
    if (!friendId) return alert('Vui lòng nhập ID người chơi!');

    // LẤY TRỰC TIẾP TOKEN TẠI ĐÂY để đảm bảo không bị lấy giá trị cũ/null
    const token = localStorage.getItem('chessAuthToken');

    if (!token) {
        alert('Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại!');
        window.location.href = '/index.html';
        return;
    }

    fetch(`/players/me/friends/request/${friendId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            // Đảm bảo dấu cách giữa Bearer và Token chuẩn xác
            'Authorization': 'Bearer ' + token
        }
    })
    .then(async res => {
        if (res.status === 201) {
            alert('Đã gửi lời mời kết bạn thành công!');
            document.getElementById('friendIdInput').value = ''; // Xóa chữ trong ô nhập sau khi gửi thành công
        } else if (res.status === 409) {
            alert('Lời mời đã tồn tại hoặc hai người đã là bạn bè!');
        } else if (res.status === 400) {
            alert('Bạn không thể tự kết bạn với chính mình!');
        } else if (res.status === 401) {
            alert('Phiên đăng nhập hết hạn (401), vui lòng đăng nhập lại!');
            logout();
        } else {
            const errMsg = await res.text();
            throw new Error(errMsg);
        }
    })
    .catch(err => alert('Lỗi: ' + err.message));
}

function loadFriends() {
    // 1. Sửa đúng ID container hiển thị danh sách bạn bè trong game.html
    const container = document.getElementById('friendsListContainer');
    if (!container) return;

    container.innerHTML = '<p class="text-slate-400 text-sm">Đang tải...</p>';

    // 2. Lấy token trực tiếp từ localStorage để chứng minh danh tính với Backend
    const token = localStorage.getItem('chessAuthToken');

    fetch(`/players/me/friends`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            // BẮT BUỘC: Đính kèm token này để không bị lỗi 401/404
            'Authorization': 'Bearer ' + token
        }
    })
    .then(async res => {
        // Kiểm tra nếu lỗi (ví dụ token hết hạn) thì ném lỗi ra xử lý ở catch
        if (!res.ok) {
            const errMsg = await res.text();
            throw new Error(errMsg);
        }
        return res.json();
    })
    .then(friends => {
        if (!friends || friends.length === 0) {
            container.innerHTML = '<p class="text-slate-500 text-sm">Bạn chưa có bạn bè nào!</p>';
            return;
        }

        // Render dữ liệu đẹp đẽ ra giao diện
        container.innerHTML = friends.map(friend => `
            <div class="bg-slate-900 border border-slate-700 rounded-xl p-4 flex items-center justify-between hover:border-slate-600 transition-all mb-2">
                <div class="flex items-center gap-3">
                    <div class="w-10 h-10 rounded-lg bg-gradient-to-br from-purple-500/20 to-pink-600/20 flex items-center justify-center text-xl border border-purple-500/30">🤝</div>
                    <div>
                        <p class="font-semibold text-slate-200">${friend.username || 'Người chơi'}</p>
                        <p class="text-xs text-slate-500">ID: ${friend.id} • ELO: ${friend.elo}</p>
                    </div>
                </div>
                <div class="flex items-center gap-2">
                    <span class="w-2.5 h-2.5 rounded-full ${friend.isOnline ? 'bg-emerald-500' : 'bg-slate-600'}"></span>
                    <span class="text-xs ${friend.isOnline ? 'text-emerald-400' : 'text-slate-500'}">${friend.isOnline ? 'Online' : 'Offline'}</span>
                </div>
            </div>
        `).join('');
    })
    .catch(err => {
        console.error("Lỗi tải danh sách bạn bè:", err);
        container.innerHTML = `<p class="text-red-400 text-sm">Lỗi: ${err.message}</p>`;
    });
}
function acceptFriendRequest() {
    const myId = document.getElementById('myIdForAccept').value;
    const requestId = document.getElementById('requestIdToAccept').value;
    if (!myId || !requestId) return alert('Vui lòng nhập đầy đủ thông tin!');

    fetch(`/players/${myId}/friends/accept/${requestId}`, { method: 'POST' })
        .then(async res => {
            if (!res.ok) {
                const errMsg = await res.text();
                throw new Error(errMsg);
            }
            alert('Đã chấp nhận lời mời!');
            document.getElementById('requestIdToAccept').value = '';
        })
        .catch(err => alert('Lỗi: ' + err.message));
}

// --- Auth Functions ---
function logout() {
    localStorage.removeItem('chessAuthToken');
    window.location.href = '/index.html';
}

async function loadCurrentUser() {
    try {
        const res = await fetch('/auth/me', {
            headers: { 'Authorization': `Bearer ${authToken}` }
        });
        if (!res.ok) {
            throw new Error('Unauthorized');
        }
        currentUser = await res.json();
        // Update UI
        document.getElementById('currentUsername').textContent = currentUser.username;
        document.getElementById('currentUserElo').textContent = `ELO: ${currentUser.elo}`;
        document.getElementById('playerName').textContent = currentUser.username;
        document.getElementById('playerId').value = currentUser.id;
        document.getElementById('myIdForFriend').value = currentUser.id;
        document.getElementById('myIdForList').value = currentUser.id;
        document.getElementById('myIdForAccept').value = currentUser.id;
    } catch (err) {
        logout();
    }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    loadCurrentUser();
});