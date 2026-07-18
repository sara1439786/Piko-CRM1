import React, { useState, useMemo } from 'react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  CartesianGrid,
  PieChart,
  Pie,
  Cell,
  AreaChart,
  Area,
  ComposedChart
} from 'recharts';
import { useAuth } from './CRM_React_Hook_useAuth';

/**
 * TypeScript definitions for Security Logging and System Health
 */
export interface SecurityEventLog {
  id: string;
  timestamp: string; // e.g. "2026-07-14 08:32"
  userEmail: string;
  action: string; // e.g. "USER_LOGIN_FAILED", "UNAUTHORIZED_API_CALL", "BULK_DATA_EXPORT"
  module: 'Authentication' | 'Leads' | 'Deals' | 'Workflows' | 'Security';
  details: string;
  ipAddress: string;
  severity: 'Low' | 'Medium' | 'High' | 'Critical';
  status: 'Flagged' | 'Mitigated' | 'Monitoring';
}

export interface FailedLoginAttempt {
  day: string; // e.g. "Mon", "Tue", etc.
  attempts: number;
  blockedIPs: number;
}

export interface SystemHealthMetric {
  time: string; // e.g. "09:00", "10:00"
  cpuLoad: number; // percentage
  memoryUsage: number; // percentage
  dbLatencyMs: number;
  apiLatencyMs: number;
}

interface SecurityDashboardProps {
  customLogs?: SecurityEventLog[];
  customFailedAttempts?: FailedLoginAttempt[];
  customHealthData?: SystemHealthMetric[];
}

/**
 * Enterprise Admin-Only Security & Telemetry Dashboard Component
 * Designed with a high-fidelity cyber-ops slate-dark theme
 */
export const CRMAdminSecurityDashboard: React.FC<SecurityDashboardProps> = ({
  customLogs,
  customFailedAttempts,
  customHealthData
}) => {
  const { user, role, canAccessModule, hasRole } = useAuth();

  // Simulated live event feed state
  const [mitigatedIds, setMitigatedIds] = useState<string[]>([]);
  const [activeSeverityFilter, setActiveSeverityFilter] = useState<string>('ALL');

  // --- Mock Seed Data for Cyber-Ops Visualizations ---
  const seedLogs: SecurityEventLog[] = useMemo(() => [
    {
      id: 'SEC-001',
      timestamp: '2026-07-14 09:21',
      userEmail: 'malicious_actor@darknet.org',
      action: 'USER_LOGIN_FAILED',
      module: 'Authentication',
      details: 'SSH Brute Force: 14 successive credential failures',
      ipAddress: '198.51.100.42',
      severity: 'Critical',
      status: 'Flagged'
    },
    {
      id: 'SEC-002',
      timestamp: '2026-07-14 08:45',
      userEmail: 'rep_ryan@globex.com',
      action: 'BULK_DATA_EXPORT',
      module: 'Leads',
      details: 'High Volume Export: Downloaded 450 enterprise contacts',
      ipAddress: '192.168.1.102',
      severity: 'High',
      status: 'Flagged'
    },
    {
      id: 'SEC-003',
      timestamp: '2026-07-14 08:12',
      userEmail: 'unknown_host@aws-region.com',
      action: 'UNAUTHORIZED_API_CALL',
      module: 'Workflows',
      details: 'GET /api/v1/compliance/workflows - Token signature mismatch',
      ipAddress: '203.0.113.88',
      severity: 'Medium',
      status: 'Mitigated'
    },
    {
      id: 'SEC-004',
      timestamp: '2026-07-14 07:30',
      userEmail: 'sara1439@gmail.com',
      action: 'USER_LOGIN_SUCCESS',
      module: 'Authentication',
      details: 'MFA Verified: Authorized Admin Session Established',
      ipAddress: '172.56.21.9',
      severity: 'Low',
      status: 'Monitoring'
    },
    {
      id: 'SEC-005',
      timestamp: '2026-07-14 06:15',
      userEmail: 'crawler_bot@shodan.io',
      action: 'UNAUTHORIZED_API_CALL',
      module: 'Security',
      details: 'Directory Traversal Attempt: GET /etc/passwd - Blocked by WAF',
      ipAddress: '45.227.254.12',
      severity: 'High',
      status: 'Flagged'
    },
    {
      id: 'SEC-006',
      timestamp: '2026-07-14 05:02',
      userEmail: 'manager_marc@globex.com',
      action: 'USER_LOGIN_SUCCESS',
      module: 'Authentication',
      details: 'Normal Login Session Initiated',
      ipAddress: '192.168.1.5',
      severity: 'Low',
      status: 'Monitoring'
    }
  ], []);

  const seedFailedAttempts: FailedLoginAttempt[] = useMemo(() => [
    { day: 'Mon', attempts: 12, blockedIPs: 2 },
    { day: 'Tue', attempts: 24, blockedIPs: 5 },
    { day: 'Wed', attempts: 58, blockedIPs: 14 }, // Intrusion peak
    { day: 'Thu', attempts: 18, blockedIPs: 3 },
    { day: 'Fri', attempts: 32, blockedIPs: 8 },
    { day: 'Sat', attempts: 9, blockedIPs: 1 },
    { day: 'Sun', attempts: 15, blockedIPs: 4 }
  ], []);

  const seedHealthData: SystemHealthMetric[] = useMemo(() => [
    { time: '04:00', cpuLoad: 22, memoryUsage: 48, dbLatencyMs: 4, apiLatencyMs: 12 },
    { time: '05:00', cpuLoad: 25, memoryUsage: 49, dbLatencyMs: 3, apiLatencyMs: 10 },
    { time: '06:00', cpuLoad: 34, memoryUsage: 51, dbLatencyMs: 5, apiLatencyMs: 15 },
    { time: '07:00', cpuLoad: 45, memoryUsage: 54, dbLatencyMs: 8, apiLatencyMs: 22 },
    { time: '08:00', cpuLoad: 68, memoryUsage: 62, dbLatencyMs: 14, apiLatencyMs: 45 }, // Peak start
    { time: '09:00', cpuLoad: 82, memoryUsage: 71, dbLatencyMs: 28, apiLatencyMs: 89 }, // High load
    { time: '10:00', cpuLoad: 55, memoryUsage: 66, dbLatencyMs: 12, apiLatencyMs: 34 }
  ], []);

  const logs = customLogs || seedLogs;
  const failedAttempts = customFailedAttempts || seedFailedAttempts;
  const healthData = customHealthData || seedHealthData;

  // --- RBAC Restriction: Force Admin/Compliance access only ---
  const isAuthorized = role === 'Administrator' || role === 'Compliance Officer' || hasRole(['Administrator', 'Compliance Officer']);

  if (!isAuthorized) {
    return (
      <div style={{
        fontFamily: 'system-ui, sans-serif',
        backgroundColor: '#0F172A',
        color: '#F8FAFC',
        padding: '48px 24px',
        borderRadius: '16px',
        maxWidth: '700px',
        margin: '40px auto',
        textAlign: 'center',
        border: '1px solid #EF4444',
        boxShadow: '0 10px 25px -5px rgba(239, 68, 68, 0.15)'
      }}>
        <div style={{ fontSize: '64px', marginBottom: '16px' }}>⚠️</div>
        <h2 style={{ fontSize: '24px', fontWeight: 'bold', color: '#EF4444', margin: '0 0 12px 0' }}>
          ACCESS DENIED: ADMIN PRIVILEGES REQUIRED
        </h2>
        <p style={{ color: '#94A3B8', fontSize: '15px', lineHeight: '1.6', maxWidth: '500px', margin: '0 auto 24px' }}>
          This interface is strictly restricted to authenticated Administrators and Compliance Officers. 
          Your role (<strong>{role || 'Unauthenticated'}</strong>) is not cleared for compliance audit feeds or cyber telemetry.
        </p>
        <div style={{ fontSize: '12px', color: '#64748B', borderTop: '1px solid #334155', paddingTop: '16px' }}>
          SECURE AUDIT TRACE LOGGED • IP: 192.168.1.100
        </div>
      </div>
    );
  }

  // --- 1. Filtered Logs List ---
  const filteredLogs = logs.filter(log => {
    if (activeSeverityFilter === 'ALL') return true;
    return log.severity === activeSeverityFilter;
  });

  // --- 2. Calculate KPI Cards Summary ---
  const totalLogsCount = logs.length;
  const criticalThreatsCount = logs.filter(l => l.severity === 'Critical' && !mitigatedIds.includes(l.id)).length;
  const activeMitigationsCount = mitigatedIds.length;
  const avgDbLatency = Math.round(healthData.reduce((acc, curr) => acc + curr.dbLatencyMs, 0) / healthData.length);

  // --- 3. Severity Distribution Calculation for Pie Chart ---
  const severityDistribution = useMemo(() => {
    const counts = { Low: 0, Medium: 0, High: 0, Critical: 0 };
    logs.forEach(log => {
      const isMitigated = mitigatedIds.includes(log.id);
      if (!isMitigated) {
        counts[log.severity]++;
      }
    });
    return [
      { name: 'Low Alert', value: counts.Low, color: '#10B981' },
      { name: 'Medium Alert', value: counts.Medium, color: '#F59E0B' },
      { name: 'High Threat', value: counts.High, color: '#EF4444' },
      { name: 'Critical Incident', value: counts.Critical, color: '#7C3AED' }
    ].filter(item => item.value > 0);
  }, [logs, mitigatedIds]);

  const handleMitigateEvent = (id: string) => {
    if (!mitigatedIds.includes(id)) {
      setMitigatedIds([...mitigatedIds, id]);
    }
  };

  return (
    <div style={{
      fontFamily: 'system-ui, sans-serif',
      backgroundColor: '#0B0F19', // Dark Slate/Navy
      color: '#F8FAFC',
      padding: '24px',
      borderRadius: '16px',
      maxWidth: '1280px',
      margin: '0 auto',
      boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.4)',
      border: '1px solid #1E293B'
    }}>
      {/* Visual Cyber-Ops Header */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        borderBottom: '1px solid #1E293B',
        paddingBottom: '20px',
        marginBottom: '24px'
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{
              width: '10px',
              height: '10px',
              backgroundColor: '#EF4444',
              borderRadius: '50%',
              display: 'inline-block',
              animation: 'pulse 2s infinite'
            }}></span>
            <h1 style={{ margin: 0, fontSize: '24px', fontWeight: 'bold', color: '#F1F5F9', letterSpacing: '-0.5px' }}>
              Compliance & Cyber Security Console
            </h1>
          </div>
          <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#94A3B8' }}>
            B2B Multi-Tenant Isolation Logs, System Telemetry, and Intrusion Metrics
          </p>
        </div>
        
        <div style={{ display: 'flex', gap: '12px' }}>
          <div style={{
            backgroundColor: '#1E293B',
            border: '1px solid #334155',
            padding: '6px 14px',
            borderRadius: '8px',
            fontSize: '12px',
            color: '#38BDF8',
            fontWeight: 'bold'
          }}>
            SYSTEM: ACTIVE (99.99% UPTIME)
          </div>
          <div style={{
            backgroundColor: 'rgba(124, 58, 237, 0.15)',
            border: '1px solid rgba(124, 58, 237, 0.3)',
            padding: '6px 14px',
            borderRadius: '8px',
            fontSize: '12px',
            color: '#A78BFA',
            fontWeight: 'bold'
          }}>
            SECURITY LEVEL: HIGH GUARD
          </div>
        </div>
      </div>

      {/* Cyber Security KPI Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' }}>
        <div style={{ backgroundColor: '#111827', border: '1px solid #1F2937', padding: '16px', borderRadius: '12px' }}>
          <div style={{ fontSize: '12px', color: '#94A3B8', fontWeight: '600', textTransform: 'uppercase' }}>Active Incidents</div>
          <div style={{ fontSize: '28px', fontWeight: 'bold', color: criticalThreatsCount > 0 ? '#EF4444' : '#10B981', marginTop: '6px' }}>
            {criticalThreatsCount}
          </div>
          <div style={{ fontSize: '11px', color: '#64748B', marginTop: '4px' }}>Unresolved Critical Threats</div>
        </div>

        <div style={{ backgroundColor: '#111827', border: '1px solid #1F2937', padding: '16px', borderRadius: '12px' }}>
          <div style={{ fontSize: '12px', color: '#94A3B8', fontWeight: '600', textTransform: 'uppercase' }}>Mitigations</div>
          <div style={{ fontSize: '28px', fontWeight: 'bold', color: '#10B981', marginTop: '6px' }}>
            {activeMitigationsCount}
          </div>
          <div style={{ fontSize: '11px', color: '#64748B', marginTop: '4px' }}>Anomalies Remediated via Console</div>
        </div>

        <div style={{ backgroundColor: '#111827', border: '1px solid #1F2937', padding: '16px', borderRadius: '12px' }}>
          <div style={{ fontSize: '12px', color: '#94A3B8', fontWeight: '600', textTransform: 'uppercase' }}>Avg Database Latency</div>
          <div style={{ fontSize: '28px', fontWeight: 'bold', color: '#38BDF8', marginTop: '6px' }}>
            {avgDbLatency}ms
          </div>
          <div style={{ fontSize: '11px', color: '#64748B', marginTop: '4px' }}>Optimized Room/SQLite Indexing</div>
        </div>

        <div style={{ backgroundColor: '#111827', border: '1px solid #1F2937', padding: '16px', borderRadius: '12px' }}>
          <div style={{ fontSize: '12px', color: '#94A3B8', fontWeight: '600', textTransform: 'uppercase' }}>Log Ingestion Rate</div>
          <div style={{ fontSize: '28px', fontWeight: 'bold', color: '#F1F5F9', marginTop: '6px' }}>
            {totalLogsCount} / sec
          </div>
          <div style={{ fontSize: '11px', color: '#64748B', marginTop: '4px' }}>Real-time audit record parsing</div>
        </div>
      </div>

      {/* Grid of Main Analytics Charts */}
      <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: '24px', marginBottom: '24px' }}>
        
        {/* Metric 1: System Telemetry (CPU, Memory, and Network Latency) */}
        <div style={{
          backgroundColor: '#111827',
          borderRadius: '12px',
          padding: '20px',
          border: '1px solid #1F2937'
        }}>
          <h2 style={{ fontSize: '15px', fontWeight: 'bold', margin: '0 0 16px 0', color: '#E2E8F0', display: 'flex', justifyContent: 'space-between' }}>
            <span>Core System Resource Telemetry & Performance</span>
            <span style={{ fontSize: '11px', color: '#64748B' }}>Past 24 Hours</span>
          </h2>
          <div style={{ height: '280px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={healthData} margin={{ top: 10, right: 30, left: 10, bottom: 5 }}>
                <defs>
                  <linearGradient id="colorCpu" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#38BDF8" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#38BDF8" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorMem" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#818CF8" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#818CF8" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1F2937" />
                <XAxis dataKey="time" stroke="#64748B" style={{ fontSize: '11px' }} />
                <YAxis stroke="#64748B" style={{ fontSize: '11px' }} />
                <Tooltip contentStyle={{ backgroundColor: '#111827', borderColor: '#1F2937', color: '#F8FAFC' }} />
                <Legend wrapperStyle={{ fontSize: '11px' }} />
                <Area type="monotone" dataKey="cpuLoad" name="CPU Usage (%)" stroke="#38BDF8" strokeWidth={2} fillOpacity={1} fill="url(#colorCpu)" />
                <Area type="monotone" dataKey="memoryUsage" name="Memory Usage (%)" stroke="#818CF8" strokeWidth={2} fillOpacity={1} fill="url(#colorMem)" />
                <Line type="monotone" dataKey="apiLatencyMs" name="API Response (ms)" stroke="#10B981" strokeWidth={2} dot={true} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Metric 2: Failed Login Intrusion Vectors */}
        <div style={{
          backgroundColor: '#111827',
          borderRadius: '12px',
          padding: '20px',
          border: '1px solid #1F2937'
        }}>
          <h2 style={{ fontSize: '15px', fontWeight: 'bold', margin: '0 0 16px 0', color: '#E2E8F0' }}>
            Failed Logins & Suspicious IP Blocks
          </h2>
          <div style={{ height: '280px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={failedAttempts} margin={{ top: 10, right: 10, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1F2937" />
                <XAxis dataKey="day" stroke="#64748B" style={{ fontSize: '11px' }} />
                <YAxis stroke="#64748B" style={{ fontSize: '11px' }} />
                <Tooltip contentStyle={{ backgroundColor: '#111827', borderColor: '#1F2937', color: '#F8FAFC' }} />
                <Legend wrapperStyle={{ fontSize: '11px' }} />
                <Bar dataKey="attempts" name="Failed Login Attempts" fill="#EF4444" radius={[4, 4, 0, 0]} />
                <Bar dataKey="blockedIPs" name="WAF IP Blocks" fill="#F59E0B" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '24px' }}>
        
        {/* Severity Ring Status */}
        <div style={{
          backgroundColor: '#111827',
          borderRadius: '12px',
          padding: '20px',
          border: '1px solid #1F2937'
        }}>
          <h2 style={{ fontSize: '15px', fontWeight: 'bold', margin: '0 0 16px 0', color: '#E2E8F0' }}>
            Active Threat Severity Split
          </h2>
          {severityDistribution.length === 0 ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '200px', color: '#64748B', fontSize: '13px' }}>
              Zero active high/critical threats! Security state nominal.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <div style={{ width: '100%', height: '180px' }}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={severityDistribution}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={75}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {severityDistribution.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div style={{ width: '100%', marginTop: '12px' }}>
                {severityDistribution.map((item, idx) => (
                  <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid #1F2937', fontSize: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ width: '8px', height: '8px', backgroundColor: item.color, borderRadius: '20%' }}></span>
                      <span style={{ color: '#E2E8F0' }}>{item.name}</span>
                    </div>
                    <span style={{ fontWeight: 'bold', color: '#F1F5F9' }}>{item.value} Active</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Log Viewer Table */}
        <div style={{
          backgroundColor: '#111827',
          borderRadius: '12px',
          padding: '20px',
          border: '1px solid #1F2937'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h2 style={{ fontSize: '15px', fontWeight: 'bold', margin: 0, color: '#E2E8F0' }}>
              Real-time Privacy Auditing Logs
            </h2>
            <div style={{ display: 'flex', gap: '6px' }}>
              {['ALL', 'Critical', 'High', 'Medium', 'Low'].map(sev => (
                <button
                  key={sev}
                  onClick={() => setActiveSeverityFilter(sev)}
                  style={{
                    backgroundColor: activeSeverityFilter === sev ? '#1E293B' : 'transparent',
                    color: activeSeverityFilter === sev ? '#38BDF8' : '#94A3B8',
                    border: activeSeverityFilter === sev ? '1px solid #38BDF8' : '1px solid #1F2937',
                    padding: '4px 8px',
                    borderRadius: '4px',
                    fontSize: '11px',
                    cursor: 'pointer',
                    fontWeight: activeSeverityFilter === sev ? 'bold' : 'normal',
                    transition: 'all 0.2s'
                  }}
                >
                  {sev}
                </button>
              ))}
            </div>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '12px' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #1F2937', color: '#94A3B8' }}>
                  <th style={{ padding: '8px', fontWeight: '600' }}>Timestamp</th>
                  <th style={{ padding: '8px', fontWeight: '600' }}>Action</th>
                  <th style={{ padding: '8px', fontWeight: '600' }}>IP Address</th>
                  <th style={{ padding: '8px', fontWeight: '600' }}>Severity</th>
                  <th style={{ padding: '8px', fontWeight: '600', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredLogs.map(log => {
                  const isMitigated = mitigatedIds.includes(log.id);
                  const isCritical = log.severity === 'Critical';
                  const isHigh = log.severity === 'High';
                  
                  return (
                    <tr
                      key={log.id}
                      style={{
                        borderBottom: '1px solid #1F2937',
                        backgroundColor: isMitigated ? 'rgba(16, 185, 129, 0.02)' : isCritical ? 'rgba(239, 68, 68, 0.03)' : 'transparent',
                        opacity: isMitigated ? 0.6 : 1
                      }}
                    >
                      <td style={{ padding: '10px 8px', color: '#64748B' }}>{log.timestamp}</td>
                      <td style={{ padding: '10px 8px' }}>
                        <div style={{ fontWeight: 'bold', color: '#E2E8F0' }}>{log.action}</div>
                        <div style={{ fontSize: '11px', color: '#94A3B8', marginTop: '2px' }}>{log.details}</div>
                      </td>
                      <td style={{ padding: '10px 8px', fontFamily: 'monospace', color: '#38BDF8' }}>{log.ipAddress}</td>
                      <td style={{ padding: '10px 8px' }}>
                        <span style={{
                          padding: '2px 6px',
                          borderRadius: '4px',
                          fontSize: '10px',
                          fontWeight: 'bold',
                          backgroundColor: isMitigated ? '#1F2937' : isCritical ? 'rgba(239, 68, 68, 0.15)' : isHigh ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                          color: isMitigated ? '#94A3B8' : isCritical ? '#EF4444' : isHigh ? '#F59E0B' : '#10B981'
                        }}>
                          {isMitigated ? 'MITIGATED' : log.severity.toUpperCase()}
                        </span>
                      </td>
                      <td style={{ padding: '10px 8px', textAlign: 'right' }}>
                        <button
                          disabled={isMitigated}
                          onClick={() => handleMitigateEvent(log.id)}
                          style={{
                            backgroundColor: isMitigated ? '#1F2937' : 'rgba(16, 185, 129, 0.15)',
                            color: isMitigated ? '#64748B' : '#10B981',
                            border: 'none',
                            padding: '4px 8px',
                            borderRadius: '4px',
                            fontSize: '10px',
                            fontWeight: 'bold',
                            cursor: isMitigated ? 'not-allowed' : 'pointer',
                            transition: 'all 0.2s'
                          }}
                        >
                          {isMitigated ? 'Resolved' : 'Resolve'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

      </div>
    </div>
  );
};
