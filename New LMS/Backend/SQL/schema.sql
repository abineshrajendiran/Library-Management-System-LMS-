-- =====================================================================
--  LIBRARY MANAGEMENT SYSTEM  -  MySQL schema + sample data
--  Run this whole file once (MySQL Workbench or the mysql command line).
--  Works on MySQL 5.7 and 8.x
-- =====================================================================

CREATE DATABASE IF NOT EXISTS library_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE library_db;

DROP TABLE IF EXISTS issue_records;
DROP TABLE IF EXISTS books;
DROP TABLE IF EXISTS members;
DROP TABLE IF EXISTS staff;

-- ---------------------------------------------------------------------
-- STAFF  (librarians who log in to the Staff module)
-- ---------------------------------------------------------------------
CREATE TABLE staff (
  staff_id      INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash CHAR(64)     NOT NULL,          -- SHA-256 hex
  full_name     VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- MEMBERS  (students who log in to the Member module)
-- ---------------------------------------------------------------------
CREATE TABLE members (
  member_id     INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash CHAR(64)     NOT NULL,          -- SHA-256 hex
  full_name     VARCHAR(100) NOT NULL,
  email         VARCHAR(100),
  department    VARCHAR(60),
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- BOOKS
-- ---------------------------------------------------------------------
CREATE TABLE books (
  book_id          INT AUTO_INCREMENT PRIMARY KEY,
  title            VARCHAR(200) NOT NULL,
  author           VARCHAR(120) NOT NULL,
  category         VARCHAR(60)  NOT NULL,
  total_copies     INT NOT NULL DEFAULT 1,
  available_copies INT NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- ISSUE_RECORDS  (every Issue / Return is stored here)
-- ---------------------------------------------------------------------
CREATE TABLE issue_records (
  issue_id    INT AUTO_INCREMENT PRIMARY KEY,
  book_id     INT NOT NULL,
  member_id   INT NOT NULL,
  issue_date  DATE NOT NULL,
  due_date    DATE NOT NULL,
  return_date DATE NULL,
  fine_amount DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  status      ENUM('ISSUED','RETURNED') NOT NULL DEFAULT 'ISSUED',
  CONSTRAINT fk_issue_book   FOREIGN KEY (book_id)   REFERENCES books(book_id),
  CONSTRAINT fk_issue_member FOREIGN KEY (member_id) REFERENCES members(member_id)
) ENGINE=InnoDB;

-- =====================================================================
--  SAMPLE DATA
--  Staff login   : admin    / admin123
--  Member logins : abi, priya, karthik, divya, suresh   (password: member123)
-- =====================================================================
INSERT INTO staff (username, password_hash, full_name) VALUES
('admin', SHA2('admin123', 256), 'Chief Librarian');

INSERT INTO members (username, password_hash, full_name, email, department) VALUES
('abi',     SHA2('member123', 256), 'Abi Raj',      'abi@college.edu',     'CSE'),
('priya',   SHA2('member123', 256), 'Priya S',      'priya@college.edu',   'ECE'),
('karthik', SHA2('member123', 256), 'Karthik M',    'karthik@college.edu', 'MECH'),
('divya',   SHA2('member123', 256), 'Divya R',      'divya@college.edu',   'IT'),
('suresh',  SHA2('member123', 256), 'Suresh Kumar', 'suresh@college.edu',  'CIVIL');

-- 60 books : 10 per category, 2 copies each
INSERT INTO books (title, author, category, total_copies, available_copies) VALUES
-- Epics
('The Ramayana',               'Valmiki',            'Epics', 2, 2),
('The Mahabharata',            'Vyasa',              'Epics', 2, 2),
('Kamba Ramayanam',            'Kambar',             'Epics', 2, 2),
('Villiputhurar Bharatham',    'Villiputhurar',      'Epics', 2, 2),
('Periya Puranam',             'Sekkizhar',          'Epics', 2, 2),
('Bhagavad Gita',              'Vyasa',              'Epics', 2, 2),
('Kandha Puranam',             'Kachiyappar',        'Epics', 2, 2),
('Thiruvilaiyadal Puranam',    'Paranjothi Munivar', 'Epics', 2, 2),
('Sundara Kandam',             'Valmiki',            'Epics', 2, 2),
('Adi Parva',                  'Vyasa',              'Epics', 2, 2),
-- Tamil Classics
('Thirukkural',                'Thiruvalluvar',      'Tamil Classics', 2, 2),
('Silappathikaram',            'Ilango Adigal',      'Tamil Classics', 2, 2),
('Manimekalai',                'Seethalai Sathanar', 'Tamil Classics', 2, 2),
('Naladiyar',                  'Jain Monks',         'Tamil Classics', 2, 2),
('Tolkappiyam',                'Tolkappiyar',        'Tamil Classics', 2, 2),
('Aathichoodi',                'Avvaiyar',           'Tamil Classics', 2, 2),
('Kalingathu Parani',          'Jayamkondar',        'Tamil Classics', 2, 2),
('Thiruvasagam',               'Manikkavasagar',     'Tamil Classics', 2, 2),
('Nalayira Divya Prabandham',  'Alvars',             'Tamil Classics', 2, 2),
('Purananuru',                 'Various Poets',      'Tamil Classics', 2, 2),
-- Novels
('Ponniyin Selvan',            'Kalki Krishnamurthy','Novels', 2, 2),
('Sivagamiyin Sabadham',       'Kalki Krishnamurthy','Novels', 2, 2),
('Parthiban Kanavu',           'Kalki Krishnamurthy','Novels', 2, 2),
('The Guide',                  'R. K. Narayan',      'Novels', 2, 2),
('Malgudi Days',               'R. K. Narayan',      'Novels', 2, 2),
('Train to Pakistan',          'Khushwant Singh',    'Novels', 2, 2),
('The God of Small Things',    'Arundhati Roy',      'Novels', 2, 2),
('Godaan',                     'Premchand',          'Novels', 2, 2),
('Chemmeen',                   'Thakazhi Sivasankara Pillai', 'Novels', 2, 2),
('Pather Panchali',            'Bibhutibhushan Bandyopadhyay','Novels', 2, 2),
-- Journeys
('Wings of Fire',              'A. P. J. Abdul Kalam','Journeys', 2, 2),
('Ignited Minds',              'A. P. J. Abdul Kalam','Journeys', 2, 2),
('My Journey',                 'A. P. J. Abdul Kalam','Journeys', 2, 2),
('The Story of My Experiments with Truth','M. K. Gandhi','Journeys', 2, 2),
('The Discovery of India',     'Jawaharlal Nehru',   'Journeys', 2, 2),
('Autobiography of a Yogi',    'Paramahansa Yogananda','Journeys', 2, 2),
('Long Walk to Freedom',       'Nelson Mandela',     'Journeys', 2, 2),
('Into the Wild',              'Jon Krakauer',       'Journeys', 2, 2),
('The Motorcycle Diaries',     'Ernesto Che Guevara','Journeys', 2, 2),
('Eat, Pray, Love',            'Elizabeth Gilbert',  'Journeys', 2, 2),
-- Engineering
('Head First Java',            'Kathy Sierra',       'Engineering', 2, 2),
('Core Java Volume I',         'Cay S. Horstmann',   'Engineering', 2, 2),
('Java: The Complete Reference','Herbert Schildt',   'Engineering', 2, 2),
('Database System Concepts',   'Abraham Silberschatz','Engineering', 2, 2),
('Operating System Concepts',  'Abraham Silberschatz','Engineering', 2, 2),
('Introduction to Algorithms', 'Thomas H. Cormen',   'Engineering', 2, 2),
('Computer Networks',          'Andrew S. Tanenbaum','Engineering', 2, 2),
('Clean Code',                 'Robert C. Martin',   'Engineering', 2, 2),
('Effective Java',             'Joshua Bloch',       'Engineering', 2, 2),
('Design Patterns',            'Erich Gamma',        'Engineering', 2, 2),
-- TN State Board
('Std 12 Computer Science',    'TN Textbook Corporation','TN State Board', 2, 2),
('Std 12 Physics Volume 1',    'TN Textbook Corporation','TN State Board', 2, 2),
('Std 12 Chemistry Volume 1',  'TN Textbook Corporation','TN State Board', 2, 2),
('Std 12 Mathematics Volume 1','TN Textbook Corporation','TN State Board', 2, 2),
('Std 11 Tamil',               'TN Textbook Corporation','TN State Board', 2, 2),
('Std 10 Science',             'TN Textbook Corporation','TN State Board', 2, 2),
('Std 10 Social Science',      'TN Textbook Corporation','TN State Board', 2, 2),
('Std 10 Mathematics',         'TN Textbook Corporation','TN State Board', 2, 2),
('Std 9 English',              'TN Textbook Corporation','TN State Board', 2, 2),
('Std 8 Tamil',                'TN Textbook Corporation','TN State Board', 2, 2);

-- One overdue sample record so you can see a fine straight away:
-- "Ramayana" (book 1) issued to Priya (member 2) 12 days ago -> 5 late days -> Rs. 10 fine
INSERT INTO issue_records (book_id, member_id, issue_date, due_date, status)
VALUES (1, 2, DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'ISSUED');
UPDATE books SET available_copies = available_copies - 1 WHERE book_id = 1;

-- Quick check
SELECT COUNT(*) AS total_books FROM books;
