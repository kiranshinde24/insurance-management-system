-- ============================================================
-- Insurance DB Migration Script
-- Run this AFTER your original InsuranceDB setup script
-- Adds columns required by the Java backend
-- ============================================================

USE InsuranceDB;

-- ===== Customer table additions =====
-- Add username and password for login
ALTER TABLE Customer
    ADD COLUMN IF NOT EXISTS Username VARCHAR(50) UNIQUE,
    ADD COLUMN IF NOT EXISTS Password VARCHAR(255),
    ADD COLUMN IF NOT EXISTS Agent_ID INT;

-- ===== Agent table additions =====
-- Add username and password for agent login
ALTER TABLE Agent
    ADD COLUMN IF NOT EXISTS Username VARCHAR(50) UNIQUE,
    ADD COLUMN IF NOT EXISTS Password VARCHAR(255);

-- ===== Claim table additions =====
-- Add description, approved amount, and attachment
ALTER TABLE Claim
    ADD COLUMN IF NOT EXISTS Description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS Approved_Amount DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS Attachment LONGTEXT;

-- ===== Policy_Type table additions =====
-- Base_Rate and Risk_Factor drive the automatic premium calculation engine
ALTER TABLE Policy_Type
    ADD COLUMN IF NOT EXISTS Base_Rate DECIMAL(6,2) DEFAULT 1.00,
    ADD COLUMN IF NOT EXISTS Risk_Factor DECIMAL(4,2) DEFAULT 1.00;

-- Example: tune per-type pricing (edit to taste)
-- UPDATE Policy_Type SET Base_Rate = 1.20, Risk_Factor = 1.00 WHERE Type_Name = 'Term Life';
-- UPDATE Policy_Type SET Base_Rate = 1.80, Risk_Factor = 1.50 WHERE Type_Name = 'Health';
-- UPDATE Policy_Type SET Base_Rate = 0.90, Risk_Factor = 1.00 WHERE Type_Name = 'Vehicle';

-- ===== Policy table additions =====
-- Sum_Assured is the insured amount the premium is calculated against
ALTER TABLE Policy
    ADD COLUMN IF NOT EXISTS Sum_Assured DECIMAL(12,2);

-- ===== Update existing data: set default admin (optional seeding) =====
-- The admin user is hardcoded in Java, no DB record needed.

-- ===== Verify =====
DESCRIBE Customer;
DESCRIBE Agent;
DESCRIBE Claim;
DESCRIBE Policy_Type;
DESCRIBE Policy;
