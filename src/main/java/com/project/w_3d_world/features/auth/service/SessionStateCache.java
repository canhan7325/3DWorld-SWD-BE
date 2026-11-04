package com.project.w_3d_world.features.auth.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nhớ tạm trạng thái phiên của người dùng (còn hoạt động + authVersion) để JwtAuthenticationFilter
 * không phải truy vấn bảng users ở MỌI request. Với cơ sở dữ liệu ở xa (Supabase), mỗi truy vấn tốn
 * vài trăm ms nên đây là phần lớn độ trễ của các API đã đăng nhập.
 *
 * <p>An toàn: mục nhớ chỉ sống {@link #TTL}; ngoài ra mọi thay đổi trên entity User (đăng xuất,
 * khóa tài khoản, đổi mật khẩu… đều tăng authVersion hoặc đổi trạng thái) gọi {@link #evict(int)}
 * qua {@code UserSessionCacheListener}, nên phiên bị thu hồi mất hiệu lực ngay trên instance này.
 */
public final class SessionStateCache {

    /** Thời gian tối đa một trạng thái được dùng lại mà không đọc lại cơ sở dữ liệu. */
    public static final Duration TTL = Duration.ofSeconds(15);
    private static final int MAX_ENTRIES = 10_000;

    public record State(boolean active, long authVersion, long loadedAtNanos) {
        boolean fresh() {
            return System.nanoTime() - loadedAtNanos < TTL.toNanos();
        }
    }

    private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

    private SessionStateCache() {}

    /** Trạng thái còn hạn của người dùng, hoặc null nếu cần đọc lại từ cơ sở dữ liệu. */
    public static State get(int userId) {
        State s = STATES.get(userId);
        if (s == null) return null;
        if (!s.fresh()) {
            STATES.remove(userId, s);
            return null;
        }
        return s;
    }

    public static void put(int userId, boolean active, long authVersion) {
        if (STATES.size() >= MAX_ENTRIES) STATES.clear(); // chặn tăng không giới hạn; sẽ tự nạp lại
        STATES.put(userId, new State(active, authVersion, System.nanoTime()));
    }

    public static void evict(int userId) {
        STATES.remove(userId);
    }

    /**
     * Xóa ngay, và xóa thêm lần nữa sau khi giao dịch hiện tại commit: trong lúc giao dịch thu hồi phiên
     * chưa commit, request khác vẫn đọc được authVersion cũ và có thể nạp lại vào bộ nhớ tạm.
     */
    public static void invalidate(int userId) {
        evict(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    evict(userId);
                }
            });
        }
    }
}
