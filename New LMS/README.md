# Library Management System (HTML + CSS + JavaScript + Core Java + MySQL)

A complete project where **Issue Book** and **Return Book** are saved in your MySQL database tables.

```
Browser (HTML/CSS/JS)  --fetch-->  Core Java server (JDBC)  -->  MySQL (library_db)
```

## Features
- Staff login and Student login (separate modules, SHA-256 password hashes)
- Search books by title / author / category (60 sample books, 6 categories)
- **Issue book** -> inserts a row in `issue_records`, reduces `books.available_copies`
- **Return book** -> updates the row (`return_date`, `fine_amount`, `status`), increases `available_copies`
- Fine = **Rs. 2 per day after 7 days**
- Max 3 books per student, same book cannot be issued twice to one student
- Staff can add books, add students, and see every record. A student sees only their own records.
- Issue and return run inside a **database transaction** (rollback on any error)

## Project structure
```
lms/
 |-- sql/schema.sql            <- tables + sample data
 |-- src/lms/
 |    |-- Main.java            <- web server + REST API routes
 |    |-- Db.java              <- JDBC connection, SHA-256
 |    |-- Json.java            <- tiny JSON reader/writer
 |    |-- AuthDAO.java         <- login
 |    |-- BookDAO.java         <- search / add book
 |    |-- MemberDAO.java       <- list / add member
 |    |-- IssueDAO.java        <- issue / return / records (+ fine logic)
 |    |-- ApiException.java
 |-- frontend/
 |    |-- index.html, style.css, app.js
 |-- lib/                      <- put the MySQL driver jar here
 |-- config.properties         <- your MySQL password goes here
 |-- run.bat  /  run.sh
```

## Setup - step by step

### Step 1: Install requirements
- JDK 11 or newer (check with `java -version` and `javac -version`)
- MySQL Server 5.7 or 8.x (+ MySQL Workbench if you like)

### Step 2: Create the database and tables
**Option A - command line** (run inside the `lms` folder):
```
mysql -u root -p < sql/schema.sql
```
**Option B - MySQL Workbench:** File -> Open SQL Script -> `sql/schema.sql` -> click the lightning icon.

### Step 3: Add the MySQL JDBC driver
Download `mysql-connector-j-8.x.x.jar` from https://dev.mysql.com/downloads/connector/j/
(choose *Platform Independent*), then copy the `.jar` into the `lib` folder.

### Step 4: Put your MySQL password in `config.properties`
```
db.user=root
db.password=YOUR_MYSQL_PASSWORD
```

### Step 5: Compile and run
**Windows** (Command Prompt inside the `lms` folder):
```
run.bat
```
or manually:
```
mkdir out
javac -cp "lib/*" -d out src\lms\*.java
java -cp "out;lib/*" lms.Main
```
**Linux / macOS:**
```
chmod +x run.sh
./run.sh
```
You should see `[OK] Connected to MySQL database.`

### Step 6: Open the app
Go to **http://localhost:8080**

| Role   | Username | Password    |
|--------|----------|-------------|
| Staff  | admin    | admin123    |
| Student | abi     | member123   |
| Student | priya, karthik, divya, suresh | member123 |

## Try it and check the database
1. Login as **admin** -> Books tab -> choose a student in "Issue to" -> click **Issue** on a book.
2. Open the **Issue Records** tab -> click **Return**.
   - To test fines: type e.g. `12` in "Demo: pretend days kept" and click Return -> 5 late days x Rs. 2 = **Rs. 10**.
3. Verify in MySQL:
```sql
USE library_db;
SELECT * FROM issue_records ORDER BY issue_id DESC;
SELECT book_id, title, available_copies FROM books WHERE available_copies < total_copies;
```
(One overdue sample record for Priya is already included so you can see a fine right away.)

## REST API (for Postman testing)
All except login need the header `Authorization: Bearer <token>`.

| Method | URL | Body |
|--------|-----|------|
| POST | /api/login | `{"role":"staff","username":"admin","password":"admin123"}` |
| GET  | /api/books?q=java | |
| POST | /api/issue | `{"book_id":41,"member_id":1}` (students: only `book_id`) |
| POST | /api/return | `{"issue_id":2}` (staff may add `"days_kept":12`) |
| GET  | /api/issues?status=ALL / ISSUED / RETURNED | |
| GET / POST | /api/members | staff only |
| POST | /api/books | staff only: `{"title":"..","author":"..","category":"..","copies":2}` |

## Common problems
| Problem | Fix |
|---|---|
| `No suitable driver found` | The jar is missing in `lib/`, or you ran `java` without `-cp "out;lib/*"` |
| `Access denied for user 'root'` | Wrong `db.password` in `config.properties` |
| `Unknown database 'library_db'` | Run `sql/schema.sql` (Step 2) |
| `Address already in use` | Change `server.port` in `config.properties` |
| Page says "Cannot reach the server" | The Java server is not running |

## Notes
- Opening `index.html` with VS Code Live Server (port 5500) also works; it calls the Java server on port 8080.
- This is a learning project. For production use HTTPS, salted password hashing (bcrypt) and a connection pool.
