# SSO 接口设计文档

## 1. 获取 SSO 登录地址

### 接口描述

返回 SSO 登录地址。

### 请求信息

- **请求方法**: `GET`
- **请求 URL**: `/auth/login-url`

### 请求参数

| 参数名 | 位置 | 类型 | 是否必填 | 描述 |
|--------|------|------|----------|------|
| `redirectUri` | Query | String | 是 | 登录成功后的回跳地址 |

### 请求头

| 参数名 | 类型 | 是否必填 | 描述 |
|--------|------|----------|------|
| `Authorization` | String | 否 | Bearer Token（认证信息） |

### 响应示例

**成功响应**

```json
{
  "data": "http://localhost:8086/sso/login?clientId=1000&redirectUri=www.baidu.com",
  "code": 1,
  "message": "成功"
}
```

**错误响应**

```json
{
  "code": 9998,
  "message": "Required request parameter 'redirectUri' for method parameter type String is not present",
  "data": null
}
```

---
## 2. 登录提交接口

### 接口描述

创建用户在前端页面输入用户名和密码后，登录提交接口。

### 请求信息

- **请求方法**: `POST`
- **请求 URL**: `http://api-dev.rainbowlab.net/sso/auth/sso/login`

### 请求参数（Body）

```json
{
  "clientId": "1000",      // 必填
  "username": "zhangbo",   // 必填
  "password": "123456"     // 必填
}
```

### 请求头

| 参数名 | 类型 | 是否必填 | 描述 |
|--------|------|----------|------|
| `Content-Type` | String | 是 | application/json |

### 响应示例

**成功响应**

```json
{
  "data": {
    "accessToken": "AT-387dd3c28d0f4f359b9c6a5778536560",
    "refreshToken": "RT-5f491a9bb6334516b8bd2e87b268d079",
    "expiresIn": 1800,
    "refreshExpiresIn": 7200,
    "loginUserVO": {
      "id": 1,
      "username": "zhangbo",
      "firstLogin": false
    }
  },
  "code": 200,
  "message": "成功"
}
```

**错误响应**

```json
{
  "data": null,
  "code": 401,
  "message": "用户不存在或密码不正确！"
}
```

---
## 3. 修改密码接口

### 接口描述

第一次登录成功后，修改密码不退出登录；登录成功后的首页的修改密码，修改完要退出登录重新登录。

### 请求信息

- **请求方法**: `POST`
- **请求 URL**: `http://api-dev.rainbowlab.net/sso/password/save-password`

### 请求参数（Body）

```json
{
  "password": "newPassword",     // 必填
  "Logoutflag": "logout"         // 必填: logout(退出登录), no_logout（不退出登录）
}
```

### 请求头

| 参数名 | 类型 | 是否必填 | 描述 |
|--------|------|----------|------|
| `Content-Type` | String | 是 | application/json |
| `authorization` | String | 是 | SSO 的 access-token |

### 响应示例

**成功响应**

```json
{
  "data": null,
  "code": 200,
  "message": "成功"
}
```

**错误响应**

```json
{
  "data": null,
  "code": 406,
  "message": "密码必须包含大小写字母和数字，长度8-20位！"
}
```

```json
{
  "data": null,
  "code": 407,
  "message": "新密码与旧密码一致，请重新设置！"
}
```

---
## 4. 退出登录

### 接口描述

退出登录接口。

### 请求信息

- **请求方法**: `GET`
- **请求 URL**: `http://api-dev.rainbowlab.net/sso/logout`

### 请求参数

无

### 请求头

| 参数名 | 类型 | 是否必填 | 描述 |
|--------|------|----------|------|
| `Content-Type` | String | 是 | application/json |
| `authorization` | String | 是 | SSO 的 access-token |

### 响应示例

**成功响应**

```json
{
  "data": "SUCCESS",
  "code": 200,
  "message": "成功"
}
```

**错误响应**

```json
{
  "data": null,
  "code": 415,
  "message": "缺少token"
}
```

---
## 5. 获取用户信息

> **更新日志**: 2026年4月1日迭代（增加部门任职信息）

### 接口描述

获取用户详情信息。

### 请求信息

- **请求方法**: `GET`
- **请求 URL**: `http://api-dev.rainbowlab.net/sso/userinfo`

### 请求参数

无

### 请求头

| 参数名 | 类型 | 是否必填 | 描述 |
|--------|------|----------|------|
| `Content-Type` | String | 是 | application/json |
| `authorization` | String | 是 | SSO 的 access-token |

### 响应示例

**成功响应**

```json
{
  "data": {
    "id": 53,
    "employeeId": 74,
    "nickName": "张波1",
    "username": "张波",
    "phone": "18217371537",
    "tenantNo": "8000",
    "tenantName": "盛虹石化",
    "roleCode": "EMPLOYEE",
    "roleName": "普通成员",
    "positionCode": null,
    "positionName": null,
    "tenantList": [
      {
        "employeeId": 66,
        "empName": "用户-QGJNDWWD",
        "position": null,
        "tenantNo": "6000",
        "tenantName": "江苏东方盛虹股份有限公司"
      },
      {
        "employeeId": 74,
        "empName": "张波",
        "position": null,
        "tenantNo": "8000",
        "tenantName": "盛虹石化"
      }
    ],
    "employments": [
      {
        "employeeId": 74,
        "orgCodePath": "/60000000/66000078/66000022/",
        "orgCodePathName": "/石化板块/原油供应链中心/盛虹新加坡/",
        "positionCode": "63002474",
        "positionName": "综合副经理"
      },
      {
        "employeeId": 74,
        "orgCodePath": "/60000000/",
        "orgCodePathName": "/石化板块/",
        "positionCode": "63002209",
        "positionName": "成品油部长"
      }
    ]
  },
  "code": 200,
  "message": "成功"
}
```

**错误响应**

```json
{
  "data": null,
  "code": 415,
  "message": "缺少token"
}
```

---
## 6. 刷新用户 Token

### 接口描述

用户切换租户时，需要刷新 token，把切换的目标租户写入新 token。

### 请求信息

- **请求方法**: `POST`
- **请求 URL**: `http://api-dev.rainbowlab.net/sso/auth/sso/refresh-token`

### 请求参数（Body）

```json
{
  "targentTenantNo": "6000",
  "refreshToken": "RT-748b6c7d585543aeb2bc6456c79a64c1"
}
```

### 请求头

| 参数名 | 类型 | 是否必填 | 描述 |
|--------|------|----------|------|
| `Content-Type` | String | 是 | application/json |
| `authorization` | String | 是 | SSO 的 access-token |

### 响应示例

**成功响应**

```json
{
  "data": {
    "accessToken": "AT-378c957bc4134112b78e49ce12fbba0b",
    "refreshToken": "RT-5691c7d305eb4f039c1beb736a9c29dc",
    "expiresIn": 36000,
    "refreshExpiresIn": 72000,
    "loginUserVO": {
      "id": 3,
      "username": "18217371537",
      "firstLogin": false
    }
  },
  "code": 200,
  "message": "成功"
}
```

**错误响应**

```json
{
  "data": null,
  "code": 415,
  "message": "缺少token"
}
```

---
## 7. 错误码说明

| 错误码 | 描述 |
|--------|------|
| 200 | 操作成功 |
| 201 | 资源创建成功 |
| 400 | 请求参数校验失败 |
| 401 | 用户不存在或密码不正确 |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 406 | 密码必须包含大小写字母和数字，长度8-20位！ |
| 407 | 新密码与旧密码一致，请重新设置！ |
| 415 | 缺少 token |
| 500 | 服务器内部错误 |

---
## 8. 测试示例（curl）

### GET 请求示例

```bash
curl -X GET \
  'http://api.example.com/api/users/12345?fields=name,email' \
  -H 'Authorization: Bearer your_token'
```

### POST 请求示例

```bash
curl -X POST \
  http://api.example.com/api/users \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer your_token' \
  -d '{
    "name": "王五",
    "email": "wangwu@example.com"
  }'
```

---
## 9. 其他说明

### 分页支持

对于列表接口（如 `/api/users`），可添加 `page` 和 `pageSize` 查询参数。

### 安全性

所有接口均需通过 Token 认证，Token 有效期为 2 小时。

### 版本控制

当前版本为 v1，URL 路径为 `/api/v1/...`。