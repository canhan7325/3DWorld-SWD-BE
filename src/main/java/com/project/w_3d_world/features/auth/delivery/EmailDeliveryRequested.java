package com.project.w_3d_world.features.auth.delivery;

/** An email was queued in the current transaction; dispatch only after commit. */
public record EmailDeliveryRequested(int userId) {}
