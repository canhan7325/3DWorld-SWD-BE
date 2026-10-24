package com.project.w_3d_world.features.auth.delivery;


public record EmailDeliveryState(
        String reference, String recipient, long version, String encryptedToken,
        long createdAt, long expiresAt, String status, int attempts, long nextAttemptAt,
        Long deliveredAt, String lastError) {
    public EmailDeliveryState prepared(String encrypted, long expiresAt) {
        return new EmailDeliveryState(reference, recipient, version, encrypted,
                createdAt, expiresAt, status, attempts, nextAttemptAt, deliveredAt, lastError);
    }

    public EmailDeliveryState result(String nextStatus, String encrypted, int count,
                                     long retryAt, Long sentAt, String error) {
        return new EmailDeliveryState(reference, recipient, version, encrypted,
                createdAt, expiresAt, nextStatus, count, retryAt, sentAt, error);
    }
}
