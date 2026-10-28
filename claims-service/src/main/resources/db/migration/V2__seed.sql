-- Demo plans and members (fictional people).
INSERT INTO plans (id, code, name, deductible, coinsurance_percent, annual_max, covered_services) VALUES
 (1, 'GOLD',   'Gold PPO',     500.00, 10, 250000.00, 'PRIMARY_CARE,SPECIALIST,LAB,IMAGING,PHARMACY,EMERGENCY'),
 (2, 'SILVER', 'Silver HMO',  1500.00, 20, 100000.00, 'PRIMARY_CARE,SPECIALIST,LAB,PHARMACY,EMERGENCY'),
 (3, 'BASIC',  'Basic Care',  3000.00, 30,  20000.00, 'PRIMARY_CARE,LAB,EMERGENCY');

INSERT INTO members (id, member_number, full_name, plan_id, coverage_start, coverage_end, deductible_met, benefits_used, version) VALUES
 (1, 'M-1001', 'Asha Verma',    1, '2026-01-01', NULL,         500.00,      0.00, 0),
 (2, 'M-1002', 'Rohan Mehta',   2, '2026-01-01', NULL,           0.00,      0.00, 0),
 (3, 'M-1003', 'Priya Nair',    3, '2026-01-01', NULL,        2500.00,  19800.00, 0),
 (4, 'M-1004', 'Kabir Singh',   2, '2025-01-01', '2025-12-31',   0.00,      0.00, 0),
 (5, 'M-1005', 'Meera Iyer',    1, '2026-01-01', NULL,           0.00,      0.00, 0);
