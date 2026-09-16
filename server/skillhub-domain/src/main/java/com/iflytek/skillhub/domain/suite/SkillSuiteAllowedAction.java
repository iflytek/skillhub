package com.iflytek.skillhub.domain.suite;

/** Suite actions the current caller may invoke for one concrete version. */
public enum SkillSuiteAllowedAction {
    EDIT,
    SUBMIT,
    PUBLISH_PRIVATE,
    REOPEN,
    CREATE_VERSION,
    YANK,
    HIDE,
    RESTORE,
    ARCHIVE,
    UNARCHIVE,
    DELETE
}
