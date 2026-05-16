const TOKEN_NAME_KEY = 'synapse:tokenName'
const TOKEN_VALUE_KEY = 'synapse:tokenValue'

export function getStoredToken() {
  const tokenName = localStorage.getItem(TOKEN_NAME_KEY)
  const tokenValue = localStorage.getItem(TOKEN_VALUE_KEY)
  return tokenName && tokenValue ? { tokenName, tokenValue } : null
}

export function saveToken(tokenName: string, tokenValue: string) {
  localStorage.setItem(TOKEN_NAME_KEY, tokenName)
  localStorage.setItem(TOKEN_VALUE_KEY, tokenValue)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_NAME_KEY)
  localStorage.removeItem(TOKEN_VALUE_KEY)
}
