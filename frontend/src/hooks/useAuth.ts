import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from '@/app/auth-context';

/**
 * Access the current authentication session and permission helpers.
 *
 * Must be used within an <AuthProvider> (wired in app providers). Reading
 * permissions here only affects what the UI renders; the server remains the
 * authority for every protected operation (Req 17.1).
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an <AuthProvider>.');
  }
  return ctx;
}
