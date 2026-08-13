/**
 * Operator-configurable platform settings.
 *
 * <p>Settings are grouped: one {@link com.iflytek.skillhub.domain.setting.SystemSetting} row holds
 * one group serialized as JSON. Callers read a group through
 * {@link com.iflytek.skillhub.domain.setting.SystemSettingService} with a typed default, so a group
 * that has never been overridden resolves to the deployment's configured defaults rather than to
 * {@code null}.
 */
package com.iflytek.skillhub.domain.setting;
