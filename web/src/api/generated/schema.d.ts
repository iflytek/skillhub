export interface paths {
  '/api/v1/auth/me': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['User']
          }
        }
        401: {
          content?: never
        }
      }
    }
  }
  '/api/v1/auth/providers': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['ApiResponse_OAuthProviderList']
          }
        }
      }
    }
  }
  '/api/v1/auth/logout': {
    post: {
      responses: {
        200: {
          content?: never
        }
        204: {
          content?: never
        }
      }
    }
  }
  '/api/v1/tokens': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['ApiResponse_ApiTokenList']
          }
        }
      }
    }
    post: {
      requestBody: {
        content: {
          'application/json': components['schemas']['CreateTokenRequest']
        }
      }
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['ApiResponse_CreateTokenResponse']
          }
        }
      }
    }
  }
  '/api/v1/tokens/{id}': {
    delete: {
      parameters: {
        path: {
          id: number
        }
      }
      responses: {
        204: {
          content?: never
        }
      }
    }
  }
}

export interface components {
  schemas: {
    User: {
      userId: string
      displayName: string
      email: string
      avatarUrl: string
      oauthProvider: string
      platformRoles: string[]
    }
    OAuthProvider: {
      id: string
      name: string
      authorizationUrl: string
    }
    ApiToken: {
      id: number
      name: string
      tokenPrefix: string
      createdAt: string
      expiresAt: string
      lastUsedAt: string
    }
    CreateTokenRequest: {
      name: string
      scopes?: string[]
    }
    CreateTokenResponse: {
      token: string
      id: number
      name: string
      tokenPrefix: string
      createdAt: string
      expiresAt: string
    }
    ApiResponse_OAuthProviderList: {
      data: components['schemas']['OAuthProvider'][]
    }
    ApiResponse_ApiTokenList: {
      data: components['schemas']['ApiToken'][]
    }
    ApiResponse_CreateTokenResponse: {
      data: components['schemas']['CreateTokenResponse']
    }
  }
}
