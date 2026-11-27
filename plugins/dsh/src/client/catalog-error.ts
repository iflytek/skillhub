/** Only known error codes become user-visible text; upstream messages stay private. */
export function catalogErrorMessage(error: { code: string; details?: unknown }): string {
  if (error.code === 'skillhub/context-stale') return '来源已变化，请刷新后重试。';
  if (error.code === 'skillhub/source-not-configured') return '请先在插件配置中设置 SkillHub 地址。';
  if (error.code === 'skillhub/account-storage-error') {
    const reason = error.details && typeof error.details === 'object' && 'reason' in error.details ? error.details.reason : undefined;
    if (reason === 'CLEAR_FAILED') return '连接已断开，但本机保存的登录信息未清除，请重试清除。';
    if (reason === 'RESTORE_FAILED') return '暂时无法恢复本机登录信息，请重新连接或重新登录。';
    return '无法保存本机登录信息，请检查 Host 存储后重试。';
  }
  if (error.code === 'skillhub/favorites-error') {
    const reason = error.details && typeof error.details === 'object' && 'reason' in error.details ? error.details.reason : undefined;
    if (reason === 'INVALID_STORE') return '本地收藏文件无法读取，原有内容已保留。';
    if (reason === 'LIMIT_REACHED') return '本机收藏已达上限，请先取消部分收藏。';
    if (reason === 'INVALID_INPUT') return '此技能暂时无法收藏，请刷新后重试。';
    return '本地收藏暂时不可用，请重试。';
  }
  if (error.code !== 'skillhub/registry-error') return '请求失败，请稍后重试。';
  const reason = error.details && typeof error.details === 'object' && 'reason' in error.details
    ? error.details.reason : undefined;
  const errorCode = error.details && typeof error.details === 'object' && 'errorCode' in error.details
    ? error.details.errorCode : undefined;
  if (reason === 'ACCESS_DENIED' && (errorCode === 'error.namespace.member.listForbidden'
    || errorCode === 'error.namespace.global.members.platformAdmin.required')) {
    return '系统空间不支持成员列表。请选择团队空间查看成员。';
  }
  switch (reason) {
    case 'AUTH_REQUIRED': return '个人 Token 已失效，请重新登录。';
    case 'ACCESS_DENIED': return '当前 Token 权限或空间角色不足，请到来源网页核对授权。';
    case 'TOKEN_SCOPE_REQUIRED': {
      const scope = error.details && typeof error.details === 'object' && 'requiredScope' in error.details ? error.details.requiredScope : undefined;
      const name = typeof scope === 'string' && /^[a-z]{1,16}:[a-z-]{1,32}$/u.test(scope) ? `（${scope}）` : '';
      return `当前 Token 缺少所需权限${name}。请到来源网页新建个人 API Token，再到插件的个人设置中更换。`;
    }
    case 'TARGET_CHANGED': return '上传目标或可见性已变化，请重新核对。';
    case 'WRITE_UNKNOWN': return '提交结果暂时无法确认，请先刷新列表或到来源网页核对。';
    case 'INVALID_INPUT': return '请检查所选技能包、账号和输入内容。';
    case 'INVALID_SOURCE': return 'SkillHub 地址无效，请在插件配置中检查实例地址。';
    case 'ANONYMOUS_DENIED': return '当前来源不允许匿名读取此项信息，请联系实例管理员确认公开访问配置。';
    case 'NOT_FOUND': return '未找到请求的技能或接口，请检查来源地址或返回列表重新选择。';
    case 'RATE_LIMITED': {
      const seconds = error.details && typeof error.details === 'object' && 'retryAfterSeconds' in error.details
        ? error.details.retryAfterSeconds : undefined;
      return typeof seconds === 'number' && Number.isSafeInteger(seconds) && seconds > 0
        ? `SkillHub 来源暂时限流，请等待至少 ${seconds} 秒后手动重试。`
        : 'SkillHub 来源暂时限流，请稍后重试。';
    }
    case 'REGISTRY_UNAVAILABLE': return 'SkillHub 服务暂时不可用，请稍后重试。';
    case 'NETWORK_ERROR': return '无法连接 SkillHub 或请求超时，请检查网络后重试。';
    case 'CANCELLED': return '请求已取消，请重新读取。';
    case 'INVALID_RESPONSE': return 'SkillHub 返回的数据格式不符合接口约定，请联系实例管理员检查兼容性。';
    case 'RESPONSE_TOO_LARGE': return 'SkillHub 返回的内容超出读取限制，请联系实例管理员。';
    default: return '无法读取 SkillHub，请检查来源地址或稍后重试。';
  }
}

/** Preserve the authentication signal so every Registry reader clears account state. */
export function catalogError(error: { code: string; details?: unknown }): Error {
  const result = new Error(catalogErrorMessage(error));
  if (error.code === 'skillhub/account-storage-error') result.name = 'AccountStorageError';
  const details = error.details;
  if (error.code === 'skillhub/registry-error' && details && typeof details === 'object'
    && 'reason' in details) {
    if (details.reason === 'AUTH_REQUIRED') result.name = 'AccountExpired';
    if (details.reason === 'TARGET_CHANGED') result.name = 'UploadTargetChanged';
  }
  return result;
}
