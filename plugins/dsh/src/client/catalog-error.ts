/** Only known error codes become user-visible text; upstream messages stay private. */
export function catalogErrorMessage(error: { code: string; details?: unknown }): string {
  if (error.code === 'skillhub/context-stale') return '来源已变化，请刷新后重试。';
  if (error.code === 'skillhub/source-not-configured') return '请先在插件配置中设置 SkillHub 地址。';
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
  switch (reason) {
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
