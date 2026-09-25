package com.iflytek.skillhub.domain.authoring.spec;

/**
 * Task kinds supported by behavior validation.
 */
public enum TaskType {

    /** Executes a script shipped with the skill in the isolated working directory. */
    SCRIPT,

    /** Sends a prompt to the bound agent runtime with the skill as context. */
    PROMPT
}
