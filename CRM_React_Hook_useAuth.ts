import { useEffect, useState } from 'react';
import { getAuth, onIdTokenChanged, User } from 'firebase/auth';

/**
 * Enterprise User Roles for OmniCRM
 */
export type UserRole = 'Administrator' | 'Compliance Officer' | 'Sales Representative';

/**
 * CRM Modules that require Role-Based Access Control (RBAC)
 */
export type CRMModule = 'dashboard' | 'leads' | 'contacts' | 'deals' | 'workflows' | 'security';

export interface UseAuthResult {
  user: User | null;
  loading: boolean;
  role: UserRole | null;
  tenantId: string | null;
  isFirebaseActive: boolean;
  canAccessModule: (module: CRMModule) => boolean;
  hasRole: (roles: UserRole[]) => boolean;
}

/**
 * Decodes the JWT payload from Firebase ID Token
 */
function decodeTokenPayload(token: string): any {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (error) {
    console.error('Failed to decode Firebase custom claims JWT:', error);
    return null;
  }
}

/**
 * Custom React Hook: useAuth
 *
 * Extract and reactive-binds Firebase Custom Claims (role, tenantId)
 * to provide granular B2B role-based conditional access to CRM modules.
 */
export function useAuth(): UseAuthResult {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [role, setRole] = useState<UserRole | null>(null);
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [isFirebaseActive, setIsFirebaseActive] = useState<boolean>(false);

  useEffect(() => {
    const auth = getAuth();
    setIsFirebaseActive(!!auth);

    // Reactively listen to ID Token changes (including custom claims updates)
    const unsubscribe = onIdTokenChanged(auth, async (currentUser) => {
      setUser(currentUser);
      
      if (currentUser) {
        try {
          // Force refresh token to pull latest custom claims
          const idTokenResult = await currentUser.getIdTokenResult(true);
          const claims = idTokenResult.claims as Record<string, any>;

          // Fallback to manual payload extraction if claims are not yet populated in cache
          const decoded = decodeTokenPayload(idTokenResult.token);
          
          const userRole = (claims.role || decoded?.role || 'Sales Representative') as UserRole;
          const userTenant = (claims.tenantId || decoded?.tenantId || 'Tenant_A') as string;

          setRole(userRole);
          setTenantId(userTenant);
        } catch (error) {
          console.error('Error fetching custom claims:', error);
          setRole('Sales Representative');
          setTenantId('Tenant_A');
        }
      } else {
        setRole(null);
        setTenantId(null);
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  /**
   * Evaluates if the authenticated user has access to a specific CRM module
   */
  const canAccessModule = (module: CRMModule): boolean => {
    if (!user || !role) return false;

    switch (module) {
      case 'dashboard':
      case 'contacts':
        // All roles can view basic dashboard and general business contacts
        return true;

      case 'leads':
      case 'deals':
        // Sales representatives, compliance officers, and admins have access
        return true;

      case 'workflows':
        // Only Compliance Officers and Admins can configure enterprise automation
        return role === 'Administrator' || role === 'Compliance Officer';

      case 'security':
        // Strict separation of concerns: Only Compliance Officers and Admins can view audit/telemetry
        return role === 'Administrator' || role === 'Compliance Officer';

      default:
        return false;
    }
  };

  /**
   * Utility to check if user has one of the allowed roles
   */
  const hasRole = (allowedRoles: UserRole[]): boolean => {
    if (!role) return false;
    return allowedRoles.includes(role);
  };

  return {
    user,
    loading,
    role,
    tenantId,
    isFirebaseActive,
    canAccessModule,
    hasRole,
  };
}
