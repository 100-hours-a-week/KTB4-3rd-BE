package com.ktb.moyeota.domain.notification.dto;

public record NotificationListQuery(
        String tab,
        String cursor
) {

    public boolean isReadTab() {
        return "read".equals(tab);
    }

    public boolean isValidTab() {
        return tab == null || "all".equals(tab) || "unread".equals(tab);
    }

    public boolean isValidCursor() {
        if (cursor == null) {
            return true;
        }
        try {
            return Long.parseLong(cursor) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean unreadOnly() {
        return "unread".equals(tab);
    }

    public Long cursorId() {
        return cursor == null ? null : Long.valueOf(cursor);
    }
}
