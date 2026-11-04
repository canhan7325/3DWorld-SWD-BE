package com.project.w_3d_world.features.user.entity;

import com.project.w_3d_world.features.auth.service.SessionStateCache;

import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreUpdate;

/**
 * Xóa trạng thái phiên đã nhớ tạm ngay khi bản ghi User thay đổi (đăng xuất, khóa tài khoản, đổi mật khẩu…),
 * để việc nhớ tạm trong {@link SessionStateCache} không làm chậm việc thu hồi phiên.
 * Gọi cả trước và sau khi ghi để không có request nào kịp nạp lại trạng thái cũ ở giữa.
 */
public class UserSessionCacheListener {

    @PreUpdate
    @PostUpdate
    @PostRemove
    void evict(User user) {
        if (user.getId() != null) {
            SessionStateCache.invalidate(user.getId());
        }
    }
}
