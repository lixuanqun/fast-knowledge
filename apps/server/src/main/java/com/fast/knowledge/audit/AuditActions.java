package com.fast.knowledge.audit;

/**
 * 审计动作常量。
 */
public final class AuditActions {

    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String SEARCH = "SEARCH";
    public static final String QA = "QA";
    public static final String CHAT = "CHAT";

    public static final String CREATE_KB = "CREATE_KB";
    public static final String UPDATE_KB = "UPDATE_KB";
    public static final String DELETE_KB = "DELETE_KB";
    public static final String UPLOAD_DOC = "UPLOAD_DOC";
    public static final String DELETE_DOC = "DELETE_DOC";
    public static final String ADD_MEMBER = "ADD_MEMBER";
    public static final String UPDATE_MEMBER = "UPDATE_MEMBER";
    public static final String REMOVE_MEMBER = "REMOVE_MEMBER";
    public static final String CREATE_USER = "CREATE_USER";
    public static final String UPDATE_USER = "UPDATE_USER";
    public static final String DELETE_USER = "DELETE_USER";
    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String RESET_PASSWORD = "RESET_PASSWORD";
    public static final String INITIAL_SETUP = "INITIAL_SETUP";
    public static final String REBUILD_INDEX = "REBUILD_INDEX";

    private AuditActions() {
    }
}
